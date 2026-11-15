"""Fetch checksum-locked originals and optionally install/start a Forge server.

Python 3.9+, standard library only. Windows users can use setup.ps1 instead.
"""
import argparse
import datetime
import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
import urllib.request
from pathlib import Path, PurePosixPath


def destination(root, name):
    if not name or '\\' in name or ':' in name or '\0' in name:
        raise ValueError('Unsafe relative path: ' + name)
    parts = PurePosixPath(name)
    if parts.is_absolute() or '..' in parts.parts or str(parts) != name or name == '.':
        raise ValueError('Unsafe relative path: ' + name)
    target = root.joinpath(*parts.parts)
    for part in [target, *target.parents]:
        if part == root:
            break
        if part.is_symlink() or (part.exists() and getattr(part.lstat(), 'st_file_attributes', 0) & 1024):
            raise ValueError('Linked destination is not supported: ' + name)
    if not target.resolve().is_relative_to(root.resolve()):
        raise ValueError('Destination escapes the instance')
    return target


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        while chunk := stream.read(1024 * 1024):
            h.update(chunk)
    return h.hexdigest()


def matches(path, entry):
    return path.is_file() and path.stat().st_size == entry['size'] and digest(path) == entry['sha256']


def validate(root, manifest):
    if manifest.get('schema') != 1 or manifest.get('kind') not in {'client-extras', 'server'}:
        raise ValueError('Unsupported download manifest')
    seen = set()
    entries = list(manifest['files'])
    if manifest.get('loader'):
        entries.append(manifest['loader'])
    for entry in entries:
        destination(root, entry['path'])
        key = entry['path'].casefold()
        if key in seen:
            raise ValueError('Duplicate download path')
        seen.add(key)
        if not re.fullmatch('[0-9a-f]{64}', entry['sha256']) or not isinstance(entry['size'], int) or entry['size'] < 1:
            raise ValueError('Invalid pinned file hash/size')
        if not entry['urls'] or any(not url.startswith('https://') or ' ' in url for url in entry['urls']):
            raise ValueError('Encoded HTTPS download URLs are required')


def fetch(root, entry, verify_only=False, opener=urllib.request.urlopen):
    target = destination(root, entry['path'])
    if matches(target, entry):
        return 'verified'
    if verify_only:
        raise ValueError('Missing or changed: ' + entry['path'])
    target.parent.mkdir(parents=True, exist_ok=True)
    failure = None
    for url in entry['urls']:
        descriptor, filename = tempfile.mkstemp(prefix='.gn-', suffix='.part', dir=target.parent)
        temporary = Path(filename)
        backup = None
        try:
            request = urllib.request.Request(url, headers={'User-Agent': 'GN-Frontier-Setup/1'})
            with os.fdopen(descriptor, 'wb') as output, opener(request, timeout=90) as response:
                if not response.geturl().startswith('https://'):
                    raise ValueError('Refusing an insecure redirect')
                received = 0
                while chunk := response.read(1024 * 1024):
                    received += len(chunk)
                    if received > entry['size']:
                        raise ValueError('Download exceeds its pinned size')
                    output.write(chunk)
            if not matches(temporary, entry):
                raise ValueError('Downloaded hash/size differs')
            # A changed local file is preserved only after its replacement was verified.
            if target.exists():
                stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
                backup = destination(root, 'GN-Frontier-Setup/backups/' + stamp + '/' + entry['path'])
                backup.parent.mkdir(parents=True, exist_ok=True)
                target.rename(backup)
            os.replace(temporary, target)
            return 'downloaded'
        except Exception as error:
            failure = error
            if backup is not None and backup.exists() and not target.exists():
                backup.rename(target)
        finally:
            temporary.unlink(missing_ok=True)
    raise RuntimeError('Cannot fetch ' + entry['path'] + ': ' + str(failure))


def java_executable():
    configured = os.environ.get('GN_JAVA')
    java_home = os.environ.get('JAVA_HOME')
    java = configured or (str(Path(java_home) / 'bin' / 'java') if java_home else shutil.which('java'))
    if not java:
        raise RuntimeError('Java 17 is required; set GN_JAVA to its java executable')
    result = subprocess.run([java, '-version'], capture_output=True, text=True)
    match = re.search(r'version "(\d+)', result.stderr + result.stdout)
    if result.returncode or not match or int(match[1]) != 17:
        raise RuntimeError('GN Frontier requires Java 17; set GN_JAVA to Java 17')
    return java


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument('--manifest', type=Path, default=Path(__file__).with_name('downloads.json'))
    parser.add_argument('--verify-only', action='store_true')
    parser.add_argument('--install-only', action='store_true')
    args = parser.parse_args()
    root = args.root.resolve()
    if not root.is_dir():
        raise ValueError('Instance/server directory does not exist')
    manifest = json.loads(args.manifest.read_text('utf-8-sig'))
    validate(root, manifest)
    counts = {'verified': 0, 'downloaded': 0}
    for i, entry in enumerate(manifest['files'], 1):
        state = fetch(root, entry, args.verify_only)
        counts[state] += 1
        print('[' + str(i) + '/' + str(len(manifest['files'])) + '] ' + state + ': ' + entry['path'], flush=True)
    if args.verify_only:
        print(json.dumps({'status': 'VERIFIED', **counts}))
        return
    receipt = destination(root, 'GN-Frontier-Setup/installed-files.json')
    receipt.parent.mkdir(parents=True, exist_ok=True)
    receipt.write_text(json.dumps({'version': manifest['version'], 'kind': manifest['kind'],
        'files': [{'path': f['path'], 'sha256': f['sha256']} for f in manifest['files']]}, indent=2) + '\n', encoding='utf-8')
    if manifest['kind'] == 'client-extras':
        print('外部依存物の準備が完了しました。ランチャーから起動してください。')
        return
    java = java_executable()
    loader = manifest['loader']
    args_name = 'win_args.txt' if os.name == 'nt' else 'unix_args.txt'
    forge_args = 'libraries/net/minecraftforge/forge/' + manifest['minecraft'] + '-' + manifest['forge'] + '/' + args_name
    if not destination(root, forge_args).is_file():
        fetch(root, loader)
        subprocess.run([java, '-jar', str(destination(root, loader['path'])), '--installServer'], cwd=root, check=True)
    if not destination(root, forge_args).is_file():
        raise RuntimeError('Forge installation did not produce launch arguments')
    if args.install_only:
        print('サーバーの準備が完了しました。起動は行っていません。')
        return
    eula = root / 'eula.txt'
    if not eula.is_file() or not re.search(r'^\s*eula\s*=\s*true\s*$', eula.read_text('utf-8-sig'), re.M):
        raise RuntimeError('https://www.minecraft.net/eula を確認し、同意する場合は eula.txt を eula=true にしてください。')
    raise SystemExit(subprocess.call([java, '@user_jvm_args.txt', '@' + forge_args, 'nogui'], cwd=root))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        raise SystemExit('GN Frontier: ' + str(error))
