"""Retire duplicate GN review copies without discarding unique historical data.

Plan and apply are separate. Saves, current review, source and live instances are
outside the removal scope. Each distinct file is retained once by SHA-256.
Only individual verified files and then empty directories are removed.
"""
import argparse
import ctypes
from functools import lru_cache
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
POLICY = json.loads((ROOT / 'pack/storage-policy.json').read_text('utf-8'))
REPARSE = getattr(stat, 'FILE_ATTRIBUTE_REPARSE_POINT', 0x400)

def inside(relative):
    # Resolving every file through Win32 handles is prohibitively expensive for
    # hundreds of thousands of tiny assets. Validate the lexical boundary here;
    # roots and parent directory chains receive the separate reparse check.
    path = Path(os.path.abspath(ROOT / relative))
    if not path.is_relative_to(ROOT) or path == ROOT:
        raise ValueError('Path must remain below this checkout')
    return path

def digest(path):
    with path.open('rb') as handle:
        return hashlib.file_digest(handle, 'sha256').hexdigest()

def safe_chain(path):
    inside(path.relative_to(ROOT))
    for part in (path, *path.parents):
        if part == ROOT:
            break
        if part.is_symlink() or part.stat().st_file_attributes & REPARSE:
            raise RuntimeError('Reparse point is not allowed: ' + str(part))

@lru_cache(maxsize=100000)
def checked_parent(path):
    if path == ROOT:
        return
    checked_parent(path.parent)
    if path.is_symlink() or path.stat().st_file_attributes & REPARSE:
        raise RuntimeError('Reparse directory')

def inventory(args):
    output = inside(args.output)
    output.mkdir(parents=True, exist_ok=True)
    rows = []
    stack = [ROOT / 'revisions']
    while stack:
        directory = stack.pop()
        with os.scandir(directory) as entries:
            for entry in entries:
                info = entry.stat(follow_symlinks=False)
                if info.st_file_attributes & REPARSE:
                    raise RuntimeError('Reparse inventory entry')
                if entry.is_dir(follow_symlinks=False):
                    stack.append(Path(entry.path))
                elif entry.is_file(follow_symlinks=False):
                    if args.jars_only and not entry.name.lower().endswith('.jar'):
                        continue
                    rows.append({'path': Path(entry.path).relative_to(ROOT).as_posix(),
                                 'bytes': info.st_size, 'mtimeNs': info.st_mtime_ns})
    (output / 'inventory.json').write_text(json.dumps(rows, ensure_ascii=False), encoding='utf-8')
    print(json.dumps({'files': len(rows), 'bytes': sum(r['bytes'] for r in rows),
                      'artifactScope': 'jars-only' if args.jars_only else 'all-files'}))

def plan(args):
    inventory = json.loads(inside(args.inventory).read_text('utf-8'))
    roots = sorted({row['path'].split('/minecraft/mods/')[0] + '/minecraft'
                    for row in inventory if '/minecraft/mods/' in row['path']
                    and row['path'].startswith('revisions/')})
    active = POLICY['activeReview']
    if active not in roots or not (inside(active) / 'mods').is_dir():
        raise RuntimeError('The retained review must exist')
    roots.remove(active)
    for relative in roots:
        safe_chain(inside(relative))
    output = inside(args.output)
    output.mkdir(parents=True, exist_ok=False)
    candidates = [row for row in inventory
                  if any(row['path'].startswith(prefix + '/') for prefix in roots)
                  and '/saves/' not in row['path']]
    total = 0
    unique = {}
    with (output / 'files.jsonl').open('w', encoding='utf-8', newline='\n') as handle:
        for number, row in enumerate(candidates, 1):
            path = inside(row['path'])
            checked_parent(path.parent)
            info = path.stat()
            if info.st_size != row['bytes'] or info.st_mtime_ns != row['mtimeNs']:
                raise RuntimeError('Changed since inventory: ' + row['path'])
            if info.st_file_attributes & REPARSE:
                raise RuntimeError('Reparse file')
            sha = digest(path)
            record = {'path': row['path'], 'bytes': info.st_size,
                      'mtimeNs': info.st_mtime_ns, 'sha256': sha}
            handle.write(json.dumps(record, ensure_ascii=False, separators=(',', ':')) + '\n')
            total += info.st_size
            unique.setdefault(sha, info.st_size)
            if number % 20000 == 0:
                print(json.dumps({'plannedFiles': number, 'readGB': round(total / 1e9, 2)}), flush=True)
    report = {'schema': 1, 'root': str(ROOT), 'time': time.time(), 'roots': roots,
              'retainedReview': active, 'files': len(candidates), 'bytes': total,
              'distinctFiles': len(unique), 'distinctBytes': sum(unique.values()),
              'estimatedDuplicateBytes': total - sum(unique.values()),
              'preservesAllSaves': True, 'sourceUploaded': False,
              'artifactScope': 'jars-only' if inventory and all(
                  row['path'].lower().endswith('.jar') for row in inventory) else 'all-files'}
    (output / 'plan.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps({k: v for k, v in report.items() if k not in ('roots', 'root')}, ensure_ascii=False), flush=True)

def idle():
    if os.name != 'nt':
        raise RuntimeError('This apply implementation expects Windows')
    # Do not print command lines: launcher arguments may contain credentials.
    command = "$ErrorActionPreference='Stop'; $taskRoot='" + str(ROOT) + "'; @(Get-CimInstance Win32_Process | Where-Object { $_.Name -in @('java.exe','javaw.exe','prismlauncher.exe') -and ([string]$_.CommandLine).Replace('/','\\').IndexOf($taskRoot,[StringComparison]::OrdinalIgnoreCase) -ge 0 }).Count"
    result = subprocess.run(['powershell', '-NoProfile', '-NonInteractive', '-Command', command],
                            capture_output=True, text=True, check=True,
                            creationflags=subprocess.CREATE_NO_WINDOW)
    if int(result.stdout.strip()):
        raise RuntimeError('The GN checkout is in use; do not retire any environment')

@lru_cache(maxsize=1)
def win_handles():
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    create = kernel.CreateFileW
    create.argtypes = [ctypes.c_wchar_p, ctypes.c_ulong, ctypes.c_ulong, ctypes.c_void_p,
                       ctypes.c_ulong, ctypes.c_ulong, ctypes.c_void_p]
    create.restype = ctypes.c_void_p
    close = kernel.CloseHandle
    close.argtypes = [ctypes.c_void_p]
    return create, close

def exclusive_readable(path):
    create, close = win_handles()
    handle = create(str(path), 0x80000000, 0, None, 3, 0x80, None)
    if handle == ctypes.c_void_p(-1).value:
        raise OSError(ctypes.get_last_error(), 'File is in use or inaccessible: ' + str(path))
    close(handle)

def apply(args):
    directory = inside(args.output)
    report = json.loads((directory / 'plan.json').read_text('utf-8'))
    if report['root'] != str(ROOT) or report['retainedReview'] != POLICY['activeReview']:
        raise RuntimeError('Plan belongs to another checkout or policy')
    if (directory / 'result.json').exists():
        raise RuntimeError('Plan already finished')
    idle()
    roots = [inside(relative) for relative in report['roots']]
    for path in roots:
        if not path.relative_to(ROOT).as_posix().startswith('revisions/') or path.name != 'minecraft':
            raise RuntimeError('Only explicit generated minecraft folders may be retired')
        safe_chain(path)
    before = shutil.disk_usage(ROOT).free
    objects = inside(POLICY['historyObjectStore'])
    objects.mkdir(parents=True, exist_ok=True)
    checked_objects = set()
    count = logical = 0
    with (directory / 'files.jsonl').open(encoding='utf-8') as handle:
        for line in handle:
            row = json.loads(line)
            if report.get('artifactScope') == 'jars-only' and not row['path'].lower().endswith('.jar'):
                raise RuntimeError('A JAR-only plan contains another file type')
            path = inside(row['path'])
            if 'saves' in path.relative_to(ROOT).parts or not any(path.is_relative_to(root) for root in roots):
                raise RuntimeError('Protected or unplanned path')
            checked_parent(path.parent)
            sha = row['sha256']
            retained = objects / sha[:2] / sha[2:]
            if not path.exists():
                if not retained.is_file() or digest(retained) != sha:
                    raise RuntimeError('Missing source and retained object')
                count += 1
                continue
            info = path.stat()
            if info.st_size != row['bytes'] or info.st_mtime_ns != row['mtimeNs'] or info.st_file_attributes & REPARSE:
                raise RuntimeError('File changed after plan: ' + row['path'])
            exclusive_readable(path)
            if digest(path) != sha:
                raise RuntimeError('Content changed after plan')
            retained.parent.mkdir(parents=True, exist_ok=True)
            if not retained.exists():
                # Same-volume hard link: retain the first unique file with no data copy.
                os.link(path, retained)
            if sha not in checked_objects:
                if digest(retained) != sha:
                    raise RuntimeError('Retained object verification failed')
                checked_objects.add(sha)
            path.unlink()
            count += 1
            logical += row['bytes']
            if count % 20000 == 0:
                print(json.dumps({'retiredFiles': count, 'processedGB': round(logical / 1e9, 2)}), flush=True)
    for root in roots:
        for base, dirs, _ in os.walk(root, topdown=False, followlinks=False):
            path = Path(base)
            if 'saves' in path.relative_to(ROOT).parts:
                continue
            try:
                path.rmdir()  # Empty only. Unexpected files are retained.
            except OSError:
                pass
    result = {'status': 'PASS', 'filesRetired': count, 'retiredEnvironmentCopies': len(roots),
              'retainedReview': POLICY['activeReview'], 'distinctHistoricalFiles': report['distinctFiles'],
              'allDistinctHistoricalContentsRetained': True, 'allSavesRetainedInPlace': True,
              'actualFreeBytesIncrease': shutil.disk_usage(ROOT).free - before,
              'logicalDuplicateBytesRemoved': report['estimatedDuplicateBytes'],
              'screenshots': 0, 'inputInjected': False}
    (directory / 'result.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps(result), flush=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('inventory', 'plan', 'apply'))
    parser.add_argument('--inventory')
    parser.add_argument('--output', required=True)
    parser.add_argument('--jars-only', action='store_true',
                        help='Inventory only JARs; exclude saves, settings and other artifacts from the plan')
    options = parser.parse_args()
    if options.jars_only and options.mode != 'inventory':
        parser.error('--jars-only is an inventory option')
    {'inventory': inventory, 'plan': plan, 'apply': apply}[options.mode](options)
