"""Portable version and original build-dependency management (Python 3.11+)."""
import argparse
import hashlib
import json
import os
import re
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def version():
    value = (ROOT / 'VERSION').read_text('utf-8').strip()
    if not re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.[1-9]\d*', value):
        raise ValueError('Expected answer.video.update; update starts at 1')
    return value


def change_version(value):
    if not re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.[1-9]\d*', value):
        raise ValueError('Invalid version')
    config = ROOT / 'overlay/config/bcc-common.toml'
    body, count = re.subn(r'^modpackVersion = ".*"$', 'modpackVersion = "' + value + '"',
                          config.read_text('utf-8'), flags=re.M)
    if count != 1:
        raise ValueError('Expected one modpackVersion setting')
    (ROOT / 'VERSION').write_text(value + '\n', encoding='utf-8', newline='\n')
    config.write_text(body, encoding='utf-8', newline='\n')
    print('Pack and core version:', value)


def dependencies():
    lock = json.loads((ROOT / 'pack/build-dependencies.lock.json').read_text('utf-8'))
    for entry in lock['files']:
        target = (ROOT / entry['path']).resolve()
        if not target.is_relative_to(ROOT) or not entry['path'].startswith(
                ('.cache/game/mods/', 'addon/src/main/resources/META-INF/jarjar/')):
            raise ValueError('Invalid dependency destination')
        if target.is_file() and sha(target) == entry['sha256']:
            print('Verified', target.name)
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        failure = None
        for url in entry['urls']:
            if not url.startswith('https://'):
                raise ValueError('HTTPS required')
            temporary = target.with_suffix(target.suffix + '.part')
            try:
                request = urllib.request.Request(url, headers={'User-Agent': 'GNFrontier-Build/' + version()})
                with urllib.request.urlopen(request, timeout=90) as response, temporary.open('wb') as out:
                    while chunk := response.read(1024 * 1024):
                        out.write(chunk)
                if sha(temporary) != entry['sha256']:
                    raise ValueError('Dependency checksum mismatch: ' + target.name)
                with zipfile.ZipFile(temporary) as archive:
                    if archive.testzip():
                        raise ValueError('Invalid archive: ' + target.name)
                os.replace(temporary, target)
                print('Downloaded and verified', target.name)
                break
            except Exception as error:
                failure = error
                temporary.unlink(missing_ok=True)
        else:
            raise RuntimeError('Cannot fetch ' + target.name) from failure


def check(jar=None):
    value = version()
    import tomllib
    config = tomllib.loads((ROOT / 'overlay/config/bcc-common.toml').read_text('utf-8'))
    if config['general']['modpackVersion'] != value:
        raise ValueError('Pack version differs from VERSION')
    build = (ROOT / 'addon/build.gradle').read_text('utf-8')
    if "version = file('../VERSION').text.trim()" not in build:
        raise ValueError('Core must use the shared VERSION file')
    if jar:
        with zipfile.ZipFile(jar) as archive:
            metadata = tomllib.loads(archive.read('META-INF/mods.toml').decode())
            own = [m for m in metadata['mods'] if m['modId'] == 'gnfrontier']
            if len(own) != 1 or own[0]['version'] != value:
                raise ValueError('Built core version differs')
            if ('Implementation-Version: ' + value) not in archive.read('META-INF/MANIFEST.MF').decode():
                raise ValueError('Manifest version differs')
            lock = json.loads((ROOT / 'pack/build-dependencies.lock.json').read_text('utf-8'))
            for entry in lock['files']:
                prefix = 'addon/src/main/resources/'
                if entry['path'].startswith(prefix):
                    data = archive.read(entry['path'][len(prefix):])
                    if hashlib.sha256(data).hexdigest() != entry['sha256']:
                        raise ValueError('Embedded library differs: ' + entry['path'])
    print('VERSION_OK', value, 'pack = core' + (' = built JAR' if jar else ''))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest='action', required=True)
    sub.add_parser('deps')
    sub.add_parser('version').add_argument('value')
    sub.add_parser('check').add_argument('--jar', type=Path)
    args = parser.parse_args()
    if args.action == 'deps':
        dependencies()
    elif args.action == 'version':
        change_version(args.value)
    else:
        check(args.jar)
