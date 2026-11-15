"""Check staged sources and all reachable history before uploading."""
import json
import re
import subprocess
from pathlib import PurePosixPath
from project import ROOT, check

check()
git = ['git', '-C', str(ROOT)]
objects = {}
files = []
for row in subprocess.check_output(git + ['ls-files', '--stage', '-z']).split(b'\0'):
    if not row:
        continue
    metadata, name = row.split(b'\t', 1)
    _, oid, stage = metadata.split()
    if stage != b'0':
        raise SystemExit('Resolve merge conflicts before uploading')
    name = name.decode('utf-8')
    objects[oid.decode()] = name
    files.append(name)
for row in subprocess.check_output(git + ['rev-list', '--objects', '--all']).splitlines():
    parts = row.split(b' ', 1)
    if len(parts) == 2:
        objects.setdefault(parts[0].decode(), parts[1].decode('utf-8'))
findings = []
credential = re.compile(r'gh[pousr]_[A-Za-z0-9]{25,}|github_pat_[A-Za-z0-9_]{25,}|-----BEGIN [A-Z ]*PRIVATE KEY-----|(?:accessToken|refreshToken)\s*[=:]\s*"[A-Za-z0-9]{20,}')
personal_path = re.compile(r'(?i)(?:[A-Z]:[/\\]+|/)(?:Users[/\\]+|home/(?!runner\b))[^/\\\s"\']+')
process = subprocess.Popen(git + ['cat-file', '--batch'], stdin=subprocess.PIPE, stdout=subprocess.PIPE)
blobs = 0
for oid, name in objects.items():
    process.stdin.write((oid + '\n').encode())
    process.stdin.flush()
    header = process.stdout.readline().split()
    data = process.stdout.read(int(header[2]))
    process.stdout.read(1)
    if header[1] != b'blob':
        continue
    blobs += 1
    path = PurePosixPath(name)
    if any(part in {'saves', 'logs', 'backups', 'revisions', '.cache'} for part in path.parts):
        findings.append((name, 'private/generated directory'))
    if path.name in {'accounts.json', 'servers.dat', 'session.lock'} or path.name.startswith('.env'):
        findings.append((name, 'private file'))
    if len(data) > 10 * 1024 * 1024:
        findings.append((name, 'large generated file'))
    if path.suffix in {'.jar', '.png', '.nbt'}:
        continue
    text = data.decode('utf-8-sig', errors='replace')
    if credential.search(text):
        findings.append((name, 'possible credential in source/history'))
    if personal_path.search(text):
        findings.append((name, 'personal filesystem path in source/history'))
process.stdin.close()
process.wait()
if findings:
    raise SystemExit(json.dumps(sorted(set(findings)), ensure_ascii=False))
ledger = json.loads((ROOT / 'pack/modification-id-register.json').read_text('utf-8'))
ids = [row['id'] for row in ledger['entries']]
assert len(ids) == len(set(ids))
assert ledger['next_sequence'] == 1 + max(int(value.removeprefix('GN-')) for value in ids)
print('REPOSITORY_OK', len(files), 'tracked files;', blobs, 'historical blobs checked; no credential or personal-path pattern found')
