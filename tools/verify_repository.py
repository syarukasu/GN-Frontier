"""Check tracked release sources before committing or uploading."""
import json
import re
import subprocess
from pathlib import Path
from project import ROOT, check

check()
files = subprocess.check_output(['git', '-C', str(ROOT), 'ls-files', '-z']).decode('utf-8').split('\0')
files = [name for name in files if name]
findings = []
credential = re.compile(r'gh[pousr]_[A-Za-z0-9]{25,}|-----BEGIN [A-Z ]*PRIVATE KEY-----|(?:accessToken|refreshToken)\s*[=:]\s*"[A-Za-z0-9]{20,}')
for name in files:
    path = ROOT / name
    if not path.is_file():
        findings.append((name, 'tracked file missing'))
        continue
    if any(part in {'saves', 'logs', 'backups', 'revisions', '.cache'} for part in path.relative_to(ROOT).parts):
        findings.append((name, 'private/generated directory'))
    if path.name in {'accounts.json', 'servers.dat', 'session.lock'} or path.name.startswith('.env'):
        findings.append((name, 'private file'))
    if path.stat().st_size > 10 * 1024 * 1024:
        findings.append((name, 'large generated file'))
    if path.suffix not in {'.jar', '.png', '.nbt'} and credential.search(path.read_text('utf-8-sig', errors='replace')):
        findings.append((name, 'possible credential'))
if findings:
    raise SystemExit(json.dumps(findings, ensure_ascii=False))
ledger = json.loads((ROOT / 'pack/modification-id-register.json').read_text('utf-8'))
ids = [row['id'] for row in ledger['entries']]
assert len(ids) == len(set(ids))
assert ledger['next_sequence'] == 1 + max(int(value.removeprefix('GN-')) for value in ids)
print('REPOSITORY_OK', len(files), 'tracked files; no credential or private-file pattern found')
