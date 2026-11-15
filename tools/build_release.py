"""Build JAR-free CurseForge, mrpack and dedicated-server distributions."""
import argparse
import hashlib
import html
import json
import re
import shutil
import subprocess
import zipfile
from pathlib import Path, PurePosixPath
from urllib.parse import quote
from project import ROOT, version, check


def read(path):
    return json.loads((ROOT / path).read_text('utf-8'))


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def hashes(path):
    values = {name: hashlib.new(name) for name in ('sha1', 'sha256', 'sha512')}
    with path.open('rb') as stream:
        while chunk := stream.read(1024 * 1024):
            for value in values.values():
                value.update(chunk)
    return {name: value.hexdigest() for name, value in values.items()}


def safe_path(path):
    p = PurePosixPath(path)
    if not path or p.is_absolute() or '..' in p.parts or '\\' in path or ':' in path or '\0' in path or str(p) != path or path == '.':
        raise ValueError('Unsafe relative package path: ' + path)
    return path


def validate_download(row):
    safe_path(row['path'])
    if row['size'] < 1:
        raise ValueError('Invalid file size')
    for key, length in [('sha1', 40), ('sha256', 64), ('sha512', 128)]:
        if not re.fullmatch('[0-9a-f]{' + str(length) + '}', row[key]):
            raise ValueError('Missing pinned hash: ' + row['path'])
    row['urls'] = [quote(url, safe=":/?#[]@!$&'()*+,;=%") for url in row['urls']]
    if not row['urls'] or any(not url.startswith('https://') for url in row['urls']):
        raise ValueError('HTTPS source required')


def server_file(row, profile):
    ids = {mod['modId'] for mod in row.get('mods', [])}
    return row.get('side') != 'client' and not ids.intersection(profile['exclude_mod_ids'])


def server_source(path, profile):
    if any(path.startswith(prefix) for prefix in profile['exclude_source_prefixes']):
        return False
    if path in profile['exclude_source_files']:
        return False
    if path.startswith(('config/', 'defaultconfigs/')) and path.endswith(tuple(profile['exclude_config_suffixes'])):
        return False
    return True


def sources():
    result = {}
    for row in read('pack/source-paths.json'):
        name = safe_path(row['path'])
        if '/.index/' in name:
            continue
        source = (ROOT / row['source']).resolve()
        if not source.is_relative_to(ROOT) or not source.is_file():
            raise ValueError('Invalid checked-in source: ' + row['source'])
        if name in result or source.suffix.lower() == '.jar':
            raise ValueError('Duplicate or binary mod in overrides: ' + name)
        result[name] = source
    return result


def mod_index(row, side):
    quoted = lambda text: json.dumps(text, ensure_ascii=False)
    text = 'filename = ' + quoted(PurePosixPath(row['path']).name) + '\nname = ' + quoted(row.get('name', row['path']))
    text += '\nside = ' + quoted(side) + '\n\n[download]\nurl = ' + quoted(row['urls'][0])
    text += '\nhash-format = "sha256"\nhash = "' + row['sha256'] + '"\n'
    cf = row.get('curseforge')
    mr = row.get('source_metadata', {}).get('modrinth')
    if cf:
        text += '\n[update.curseforge]\nproject-id = ' + str(cf['projectID']) + '\nfile-id = ' + str(cf['fileID']) + '\n'
    elif mr:
        text += '\n[update.modrinth]\nmod-id = ' + quoted(mr['mod-id']) + '\nversion = ' + quoted(mr['version']) + '\n'
    return text.encode('utf-8')


def download_manifest(rows, kind, project, value):
    manifest = {'schema': 1, 'kind': kind, 'name': project['name'], 'version': value,
        'minecraft': project['minecraft'], 'forge': project['forge'], 'java': project['java'], 'files': rows}
    if kind == 'server':
        manifest['loader'] = read('pack/forge-installer.lock.json')
    return manifest


def install_guide(kind, value):
    common = f'GN Frontier {value}\nMinecraft 1.20.1 / Forge 47.4.20 / Java 17\n\n'
    if kind == 'server':
        text = '''サーバー用配布です。MODやForgeのJARは同梱していません。
新しいフォルダーに解凍し、Java 17を用意してください。
Windows: startserver.bat を実行します。PowerShell 5.1以上を使用します。
Linux: Python 3.9以上を用意し、bash startserver.sh を実行します。
初回は固定メタデータから原版MODとForgeを取得し、ハッシュを確認します。
MinecraftのEULAを確認し、同意する場合はeula.txtをeula=trueにして再度起動してください。
EULA: https://www.minecraft.net/eula
メモリはuser_jvm_args.txt、サーバー設定はserver.propertiesで変更します。
別のJavaを使う場合は環境変数GN_JAVAにJava 17の実行ファイルを指定してください。
インストールだけ: Windows startserver.bat -InstallOnly / Linux bash startserver.sh --install-only
取得済みファイルの照合だけ: -VerifyOnly / --verify-only
初期設定はサバイバル・ハード、専用のLost Cities生成プロファイルです。
既存サーバーへの更新前にはワールドと設定をバックアップし、設定の差分を確認してください。
このZIPにはワールド・プレイヤーデータ・認証情報を含めていません。
'''
    else:
        text = '''CurseForge形式のクライアントパックです。MODのJARは同梱していません。
1. ランチャーでこのCurseForge ZIPをインポートします。
2. 作成されたインスタンスのゲームフォルダーを開きます。
3. WindowsはInstall-Extras.bat、Linuxはbash install-extras.shを一度実行します。
   LinuxではPython 3.9以上が必要です。Windowsは標準のPowerShellを使用します。
4. 外部依存の取得が完了したら、ランチャーからJava 17で起動します。
CurseForge標準のインポートは外部URLのメタデータを処理しないため、3の補完が必要です。
補完処理は未掲載MOD、専用コアMOD、taczフォルダーに置くガンパックを原版URLから取得します。
インポート時の自動取得を使う場合は、同じReleaseの.mrpackをPrism Launcherで選んでください。
Prismから外部配布元の確認が出た場合は、取得元を確認して続行してください。
.mrpackはForgeCDNや作者MavenのURLも参照するため、Modrinthサイトへの投稿用ではありません。
'''
    return (common + text + '\n取得元とハッシュ: GN-Frontier-Setup/downloads.json\n'
        + 'ソースと不具合報告: https://github.com/syarukasu/GN-Frontier\n'
        + '専用MOD: https://github.com/syarukasu/GN-Frontier/tree/main/addon\n').encode('utf-8')


def bootstrap(target, kind):
    target['GN-Frontier-Setup/setup.py'] = ROOT / 'pack/bootstrap/setup.py'
    target['GN-Frontier-Setup/setup.ps1'] = b'\xef\xbb\xbf' + (ROOT / 'pack/bootstrap/setup.ps1').read_bytes()
    names = ['startserver.bat', 'startserver.sh'] if kind == 'server' else ['Install-Extras.bat', 'install-extras.sh']
    for name in names:
        target[name] = ROOT / 'pack/bootstrap' / name


def archive(path, content):
    seen = set()
    with zipfile.ZipFile(path, 'x', zipfile.ZIP_DEFLATED, compresslevel=6) as output:
        for name, source in sorted(content.items()):
            safe_path(name)
            if name.casefold() in seen or name.lower().endswith('.jar'):
                raise ValueError('Duplicate or JAR in distribution ZIP: ' + name)
            seen.add(name.casefold())
            info = zipfile.ZipInfo(name, (2026, 11, 15, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = ((0o100755 if name.endswith('.sh') else 0o100644) << 16)
            data = source.read_bytes() if isinstance(source, Path) else source
            if name.endswith(('.sh', '.ps1', '.py')) or '/GN-Frontier-Docs/' in '/' + name:
                data = data.replace(b'\r\n', b'\n')
            elif name.endswith('.bat'):
                data = data.replace(b'\r\n', b'\n').replace(b'\n', b'\r\n')
            output.writestr(info, data)
    with zipfile.ZipFile(path) as output:
        if output.testzip() or any(name.lower().endswith('.jar') for name in output.namelist()):
            raise ValueError('Invalid or non-JAR-free archive')


def build(output_dir, core_jar=None, cache=None):
    value = version()
    jar = (core_jar or ROOT / f'addon/build/libs/gn-frontier-{value}.jar').resolve()
    check(jar)
    project = read('pack/project.json')
    profile = read('pack/server-profile.json')
    rows = read('pack/runtime.lock.json')['files']
    for row in rows:
        validate_download(row)
        if cache and hashes(cache / row['path'])['sha256'] != row['sha256']:
            raise ValueError('Changed original: ' + row['path'])
    own = {'path': 'mods/' + jar.name, 'name': 'GN Frontier', 'side': 'both', 'size': jar.stat().st_size,
        **hashes(jar), 'urls': [f'https://github.com/{project["github_repository"]}/releases/download/v{value}/{jar.name}'],
        'page': f'https://github.com/{project["github_repository"]}/tree/main/addon',
        'mods': [{'modId': 'gnfrontier', 'version': value}], 'delivery': 'external-download', 'declared_license': 'MIT'}
    validate_download(own)
    rows.append(own)
    if len({row['path'].casefold() for row in rows}) != len(rows):
        raise ValueError('Duplicate runtime file')
    server_rows = [row for row in rows if server_file(row, profile)]
    external = [row for row in rows if not row.get('curseforge') or row.get('delivery') != 'manifest']
    originals = sources()
    client = dict(originals)
    server = {p: source for p, source in originals.items() if server_source(p, profile)}
    for row in rows:
        parent = PurePosixPath(row['path']).parent
        stem = re.sub('[^a-z0-9._-]', '-', PurePosixPath(row['path']).stem.lower())
        name = str(parent / '.index' / (stem + '.pw.toml'))
        side = 'both' if server_file(row, profile) else 'client'
        client[name] = mod_index(row, side)
        if server_file(row, profile):
            server[name] = mod_index(row, side)
    for path in sorted((ROOT / 'licenses').iterdir()):
        if path.is_file():
            client['GN-Frontier-Docs/licenses/' + path.name] = path
            server['GN-Frontier-Docs/licenses/' + path.name] = path
    for name in ['LICENSE', 'THIRD_PARTY.md']:
        client['GN-Frontier-Docs/' + name] = ROOT / name
        server['GN-Frontier-Docs/' + name] = ROOT / name
    server_defaults = {p.name: p.read_text('utf-8').encode('utf-8') for p in (ROOT / 'pack/server').iterdir() if p.is_file()}
    cf_files = [row['curseforge'] for row in rows if row.get('curseforge') and row.get('delivery') == 'manifest']
    if len({f['projectID'] for f in cf_files}) != len(cf_files):
        raise ValueError('Duplicate CurseForge project')
    cf = {'minecraft': {'version': project['minecraft'], 'modLoaders': [{'id': 'forge-' + project['forge'], 'primary': True}]},
        'manifestType': 'minecraftModpack', 'manifestVersion': 1, 'name': project['name'], 'version': value,
        'author': 'syarukasu', 'files': sorted(cf_files, key=lambda f: f['projectID']), 'overrides': 'overrides'}
    cf_content = {'overrides/' + p: source for p, source in client.items()}
    extra = {'GN-Frontier-Setup/downloads.json': json_bytes(download_manifest(external, 'client-extras', project, value)),
        'GN-Frontier-Docs/INSTALL.md': install_guide('client', value)}
    bootstrap(extra, 'client')
    cf_content.update({'overrides/' + p: source for p, source in extra.items()})
    cf_content['manifest.json'] = json_bytes(cf)
    cf_content['README-INSTALL.txt'] = install_guide('client', value)
    modlist = ''.join('<li><a href="' + html.escape(row['page'], quote=True) + '">' + html.escape(row.get('name', row['path'])) + '</a></li>' for row in rows)
    cf_content['modlist.html'] = ('<!doctype html><meta charset="utf-8"><title>GN Frontier</title><ul>' + modlist + '</ul>').encode()
    mr = {'formatVersion': 1, 'game': 'minecraft', 'versionId': value, 'name': project['name'],
        'summary': 'GN Frontier: ruins, salvage, factions and industrial survival.',
        'dependencies': {'minecraft': project['minecraft'], 'forge': project['forge']}, 'files': []}
    for row in rows:
        mr['files'].append({'path': row['path'], 'hashes': {'sha1': row['sha1'], 'sha512': row['sha512']},
            'env': {'client': 'required', 'server': 'required' if server_file(row, profile) else 'unsupported'},
            'downloads': row['urls'], 'fileSize': row['size']})
    mr_content = {'overrides/' + p: source for p, source in server.items()}
    mr_content.update({'client-overrides/' + p: source for p, source in client.items() if p not in server})
    mr_content.update({'server-overrides/' + p: source for p, source in server_defaults.items()})
    mr_content['modrinth.index.json'] = json_bytes(mr)
    mr_content['overrides/GN-Frontier-Docs/INSTALL.md'] = (f'GN Frontier {value}\n\nPrism Launcherでこの.mrpackをインポートしてください。\nMOD・ガンパックを原版URLから取得し、ハッシュで検証します。\nJava 17を使用してください。\nhttps://github.com/syarukasu/GN-Frontier\n').encode('utf-8')
    server.update(server_defaults)
    bootstrap(server, 'server')
    server['GN-Frontier-Setup/downloads.json'] = json_bytes(download_manifest(server_rows, 'server', project, value))
    server['README-SERVER.md'] = install_guide('server', value)
    output_dir = output_dir.resolve()
    if output_dir.exists():
        raise ValueError('Preserve existing output; specify a new --output directory')
    output_dir.mkdir(parents=True)
    artifacts = []
    for name, content in [(f'GN-Frontier-{value}-CurseForge.zip', cf_content),
                          (f'GN-Frontier-{value}.mrpack', mr_content),
                          (f'GN-Frontier-{value}-Server.zip', server)]:
        target = output_dir / name
        archive(target, content)
        artifacts.append({'name': name, 'size': target.stat().st_size, 'sha256': hashes(target)['sha256'], 'jar_entries': 0})
    shutil.copy2(jar, output_dir / jar.name)
    artifacts.append({'name': jar.name, 'size': own['size'], 'sha256': own['sha256'], 'role': 'standalone core download'})
    (output_dir / 'downloads.lock.json').write_bytes(json_bytes({'schema': 1, 'version': value, 'files': rows}))
    artifacts.append({'name': 'downloads.lock.json', 'size': (output_dir / 'downloads.lock.json').stat().st_size,
        'sha256': hashes(output_dir / 'downloads.lock.json')['sha256']})
    (output_dir / 'SHA256SUMS.txt').write_text(''.join(a['sha256'] + '  ' + a['name'] + '\n' for a in artifacts), encoding='utf-8')
    result = {'version': value, 'core_version': value, 'core_sha256': own['sha256'], 'artifacts': artifacts,
        'client_downloads': len(rows), 'curseforge_manifest_files': len(cf_files), 'client_external_downloads': len(external),
        'server_downloads': len(server_rows), 'server_mod_jars': sum(r['path'].endswith('.jar') for r in server_rows),
        'server_excluded': [r['path'] for r in rows if not server_file(r, profile)],
        'curseforge_native_external_downloads': False, 'modrinth_site_upload_compatible': False,
        'published': False, 'source_commit': subprocess.check_output(['git', '-C', str(ROOT), 'rev-parse', 'HEAD']).decode().strip()}
    (output_dir / 'release.json').write_bytes(json_bytes(result))
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path)
    parser.add_argument('--core-jar', type=Path)
    parser.add_argument('--cache', type=Path, help='Optional existing verified originals; no downloads or environment copies')
    args = parser.parse_args()
    build(args.output or ROOT / 'dist' / ('GN-Frontier-' + version()), args.core_jar, args.cache)
