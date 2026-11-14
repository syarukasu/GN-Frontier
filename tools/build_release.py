"""Build a local CurseForge candidate from checked-in sources and pinned originals.

Strict mode rejects off-CurseForge mods. --local-candidate is explicitly an
unpublished review artifact, never an assertion of CurseForge completeness.
"""
import argparse
import hashlib
import html
import json
import shutil
import zipfile
from pathlib import Path
from project import ROOT, version, check


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def copy(source, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)


def build(cache, candidate):
    value = version()
    jar = ROOT / f'addon/build/libs/gn-frontier-{value}.jar'
    check(jar)
    lock = json.loads((ROOT / 'pack/runtime.lock.json').read_text('utf-8'))
    project = json.loads((ROOT / 'pack/project.json').read_text('utf-8'))
    absent = [row['path'] for row in lock['files'] if row['path'].startswith('mods/') and not row.get('curseforge')]
    if not project['curseforge']['core_project_id'] or not project['curseforge']['core_file_id']:
        absent.append(jar.name)
    if absent and not candidate:
        raise SystemExit('CurseForge publication blocked; missing listings: ' + ', '.join(absent))
    dist = ROOT / 'dist' / ('GN-Frontier-' + value)
    stage = dist / ('local-candidate' if candidate else 'curseforge')
    if stage.exists():
        raise SystemExit('Preserve existing output; choose a new version or remove it after review')
    overrides = stage / 'overrides'
    stage.mkdir(parents=True)
    sources = json.loads((ROOT / 'pack/source-paths.json').read_text('utf-8'))
    for entry in sources:
        if entry['path'] == 'mods/.index/gnfrontier.pw.toml':
            continue
        target = (overrides / entry['path']).resolve()
        source = (ROOT / entry['source']).resolve()
        if not target.is_relative_to(overrides.resolve()) or not source.is_relative_to(ROOT):
            raise ValueError('Unsafe source path')
        copy(source, target)
    files = []
    for row in lock['files']:
        source = cache / row['path']
        if not source.is_file() or sha(source) != row['sha256']:
            raise ValueError('Missing or changed upstream original: ' + row['path'])
        if row.get('curseforge') and row['delivery'] == 'manifest':
            files.append(row['curseforge'])
        else:
            copy(source, overrides / row['path'])
    if not project['curseforge']['core_file_id']:
        copy(jar, overrides / 'mods' / jar.name)
    else:
        files.append({'projectID': project['curseforge']['core_project_id'], 'fileID': project['curseforge']['core_file_id'], 'required': True})
    own_index = overrides / 'mods/.index/gnfrontier.pw.toml'
    own_index.parent.mkdir(parents=True, exist_ok=True)
    body = f'filename = "{jar.name}"\nname = "GN Frontier"\nside = "both"\n\n[download]\nhash-format = "sha256"\nhash = "{sha(jar)}"\n'
    if project['curseforge']['core_file_id']:
        body += f'\n[update.curseforge]\nproject-id = {project["curseforge"]["core_project_id"]}\nfile-id = {project["curseforge"]["core_file_id"]}\n'
    own_index.write_text(body, encoding='utf-8')
    if len({row['projectID'] for row in files}) != len(files):
        raise ValueError('Duplicate CurseForge project')
    for name in ['README.md', 'CHANGELOG.md', 'LICENSE', 'THIRD_PARTY.md']:
        copy(ROOT / name, overrides / 'GN-Frontier-Docs' / name)
    for p in (ROOT / 'docs').glob('*.md'):
        if p.name in {'STATUS.md', 'VERSIONING.md', 'DEVELOPMENT.md', 'CURSEFORGE.md'}:
            copy(p, overrides / 'GN-Frontier-Docs/docs' / p.name)
    for p in (ROOT / 'licenses').glob('*'):
        if p.is_file():
            copy(p, overrides / 'GN-Frontier-Docs/licenses' / p.name)
    manifest = {'minecraft': {'version': project['minecraft'], 'modLoaders': [{'id': 'forge-' + project['forge'], 'primary': True}]},
                'manifestType': 'minecraftModpack', 'manifestVersion': 1,
                'name': 'GN Frontier', 'version': value, 'author': 'syarukasu',
                'files': sorted(files, key=lambda row: row['projectID']), 'overrides': 'overrides'}
    write(stage / 'manifest.json', manifest)
    links = ''.join('<li><a href="' + html.escape(row['page'], quote=True) + '">' + html.escape(row['path']) + '</a></li>' for row in lock['files'])
    (stage / 'modlist.html').write_text('<!doctype html><meta charset="utf-8"><title>GN Frontier ' + value + '</title><ul>' + links + '</ul>', encoding='utf-8')
    archive = dist / f'GN-Frontier-{value}-CurseForge{"-local-candidate" if candidate else ""}.zip'
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as out:
        for path in sorted(stage.rglob('*')):
            if path.is_file():
                info = zipfile.ZipInfo(path.relative_to(stage).as_posix(), (2026, 11, 15, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                out.writestr(info, path.read_bytes())
    with zipfile.ZipFile(archive) as out:
        if out.testzip():
            raise ValueError('Archive CRC check failed')
        for path in stage.rglob('*'):
            if path.is_file() and out.read(path.relative_to(stage).as_posix()) != path.read_bytes():
                raise ValueError('Archive content differs')
    copy(jar, dist / jar.name)
    write(dist / 'release.json', {'version': value, 'core_version': value, 'core_sha256': sha(jar),
          'archive': archive.name, 'archive_sha256': sha(archive), 'manifest_files': len(files),
          'curseforge_all_mods': not absent, 'curseforge_unresolved': absent, 'published': False,
          'local_candidate': candidate})
    print('BUILT', archive, 'manifest:', len(files), 'unresolved:', absent)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--cache', required=True, type=Path, help='Verified original game directory, not a running instance')
    parser.add_argument('--local-candidate', action='store_true')
    args = parser.parse_args()
    build(args.cache.resolve(), args.local_candidate)
