"""Download transaction tests and checks of the actual release archives."""
import argparse
import hashlib
import importlib.util
import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('bootstrap', ROOT / 'pack/bootstrap/setup.py')
bootstrap = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bootstrap)


class Response(io.BytesIO):
    def geturl(self):
        return 'https://example.invalid/original.jar'


class Downloads(unittest.TestCase):
    def setUp(self):
        scratch = ROOT / '.cache/distribution/tests'
        scratch.mkdir(parents=True, exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(dir=scratch)
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.body = b'verified original fixture'
        self.entry = {'path': 'mods/fixture.jar', 'size': len(self.body),
                      'sha256': hashlib.sha256(self.body).hexdigest(),
                      'urls': ['https://example.invalid/original.jar']}
        self.target = self.root / self.entry['path']
        self.target.parent.mkdir()

    def opener(self, *_args, **_kwargs):
        return Response(self.body)

    def test_rejects_escaping_and_aliased_paths(self):
        for path in ['../escape.jar', '/escape.jar', 'C:/escape.jar', 'mods\\x.jar',
                     'mods/../x.jar', 'mods//x.jar', './mods/x.jar', '.', 'mods/x\0.jar']:
            with self.subTest(path=path), self.assertRaises(ValueError):
                bootstrap.destination(self.root, path)

    def test_rejects_duplicate_casefold_paths_before_download(self):
        other = {**self.entry, 'path': 'MODS/FIXTURE.JAR'}
        with self.assertRaises(ValueError):
            bootstrap.validate(self.root, {'schema': 1, 'kind': 'server', 'files': [self.entry, other]})

    def test_reuses_valid_original_without_network(self):
        self.target.write_bytes(self.body)
        def unavailable(*_args, **_kwargs):
            self.fail('Valid originals must not require a network request')
        self.assertEqual('verified', bootstrap.fetch(self.root, self.entry, opener=unavailable))

    def test_bad_download_retains_changed_local_file(self):
        self.target.write_bytes(b'local copy')
        with self.assertRaises(RuntimeError):
            bootstrap.fetch(self.root, self.entry, opener=lambda *_a, **_k: Response(b'wrong data'))
        self.assertEqual(b'local copy', self.target.read_bytes())
        self.assertFalse(list(self.root.rglob('*.part')))
        self.assertFalse((self.root / 'GN-Frontier-Setup/backups').exists())

    def test_oversized_response_is_rejected(self):
        with self.assertRaises(RuntimeError):
            bootstrap.fetch(self.root, self.entry, opener=lambda *_a, **_k: Response(self.body + b'!'))
        self.assertFalse(self.target.exists())
        self.assertFalse(list(self.root.rglob('*.part')))

    def test_verified_replacement_keeps_backup(self):
        self.target.write_bytes(b'local copy')
        self.assertEqual('downloaded', bootstrap.fetch(self.root, self.entry, opener=self.opener))
        self.assertEqual(self.body, self.target.read_bytes())
        backups = list((self.root / 'GN-Frontier-Setup/backups').rglob('fixture.jar'))
        self.assertEqual(1, len(backups))
        self.assertEqual(b'local copy', backups[0].read_bytes())

    def test_failed_replace_restores_local_file(self):
        self.target.write_bytes(b'local copy')
        with patch.object(bootstrap.os, 'replace', side_effect=OSError('fixture failure')):
            with self.assertRaises(RuntimeError):
                bootstrap.fetch(self.root, self.entry, opener=self.opener)
        self.assertEqual(b'local copy', self.target.read_bytes())

    def test_fallback_source_is_checked(self):
        self.entry['urls'].append('https://example.invalid/fallback.jar')
        calls = []
        def request(req, **_kwargs):
            calls.append(req.full_url)
            return Response(b'wrong data' if len(calls) == 1 else self.body)
        self.assertEqual('downloaded', bootstrap.fetch(self.root, self.entry, opener=request))
        self.assertEqual(2, len(calls))
        self.assertEqual(self.body, self.target.read_bytes())

    def test_verify_only_does_not_create_files(self):
        with self.assertRaises(ValueError):
            bootstrap.fetch(self.root, self.entry, verify_only=True, opener=self.opener)
        self.assertEqual([], list(self.target.parent.iterdir()))


def check_archives(directory):
    receipt = json.loads((directory / 'release.json').read_text('utf-8'))
    value = receipt['version']
    readers = {}
    try:
        for suffix in ['-CurseForge.zip', '.mrpack', '-Server.zip']:
            archive = zipfile.ZipFile(directory / ('GN-Frontier-' + value + suffix))
            readers[suffix] = archive
            names = archive.namelist()
            assert archive.testzip() is None
            assert len(names) == len({name.casefold() for name in names})
            assert not any(name.lower().endswith('.jar') for name in names)
            for name in names:
                assert not {'saves', 'logs', 'crash-reports', 'backups'} & set(Path(name).parts), name
                assert Path(name).name not in {'accounts.json', 'servers.dat', 'session.lock'}
                if name.endswith('.sh'):
                    assert b'\r' not in archive.read(name), name
        cf = readers['-CurseForge.zip']
        mr = readers['.mrpack']
        server = readers['-Server.zip']
        cfm = json.loads(cf.read('manifest.json'))
        extras = json.loads(cf.read('overrides/GN-Frontier-Setup/downloads.json'))
        index = json.loads(mr.read('modrinth.index.json'))
        server_manifest = json.loads(server.read('GN-Frontier-Setup/downloads.json'))
        locked = json.loads((directory / 'downloads.lock.json').read_text('utf-8'))['files']
        assert cfm['version'] == index['versionId'] == server_manifest['version'] == value
        paths = {row['path'] for row in locked}
        assert {row['path'] for row in index['files']} == paths
        cf_ids = {(row['projectID'], row['fileID']) for row in cfm['files']}
        covered = {r['path'] for r in locked if r.get('delivery') == 'manifest'
                   and (r['curseforge']['projectID'], r['curseforge']['fileID']) in cf_ids}
        assert covered.isdisjoint({r['path'] for r in extras['files']})
        assert covered | {r['path'] for r in extras['files']} == paths
        # Resource-pack selections must use the file names written by each distribution.
        for reader, options, prefixes in (
            (cf, 'overrides/options.txt', ('overrides/',)),
            (mr, 'client-overrides/options.txt', ('client-overrides/', 'overrides/')),
        ):
            line = next(line for line in reader.read(options).decode('utf-8').splitlines()
                        if line.startswith('resourcePacks:'))
            for selection in json.loads(line.split(':', 1)[1]):
                if selection.startswith('file/'):
                    resource = 'resourcepacks/' + selection.removeprefix('file/')
                    assert resource in paths or any(prefix + resource in reader.namelist() for prefix in prefixes), resource
        servers = {r['path'] for r in server_manifest['files']}
        assert servers == {r['path'] for r in index['files'] if r['env']['server'] == 'required'}
        assert all(r['env']['client'] == 'required' for r in index['files'])
        for row in index['files']:
            assert len(row['hashes']['sha1']) == 40 and len(row['hashes']['sha512']) == 128
            assert row['fileSize'] > 0 and all(u.startswith('https://') and ' ' not in u for u in row['downloads'])
        assert len([r for r in locked if r['path'].startswith('mods/gn-frontier-')]) == 1
        assert not any(n.startswith(('resourcepacks/', 'shaderpacks/', 'kubejs/client_scripts/')) for n in server.namelist())
        assert b'eula=false' in server.read('eula.txt')
        assert b'difficulty=hard' in server.read('server.properties')
        assert b'gn_azzz15flat_nature' in server.read('defaultconfigs/lostcities-server.toml')
        for artifact in receipt['artifacts']:
            file = directory / artifact['name']
            assert file.stat().st_size == artifact['size']
            assert hashlib.sha256(file.read_bytes()).hexdigest() == artifact['sha256']
        print('DISTRIBUTION_OK', value, 'JAR-free archives; client/server manifests cover every dependency')
    finally:
        for archive in readers.values():
            archive.close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--release', type=Path)
    args = parser.parse_args()
    result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(Downloads))
    if not result.wasSuccessful():
        raise SystemExit(1)
    if args.release:
        check_archives(args.release)
