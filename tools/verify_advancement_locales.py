"""Check resolved Japanese from the real client, plus display-only data overrides."""
import argparse, json, re, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JP=re.compile(r'[ぁ-んァ-ヶ一-龯]')
KEY=re.compile(r'\b(?:advancement[s]?|item|block|gui)\.[a-z_][a-z_0-9.]+')

def verify(audit,game):
    data=json.loads(audit.read_text(encoding='utf8'))
    assert data['language']=='ja_jp'
    failures=[];technical=[];fields=0
    for row in data['rows']:
        for field in ('title','description'):
            value=row[field];fields+=1
            if KEY.search(value):failures.append([row['id'],field,'unresolved translation',value])
            plain=re.sub(r'§[0-9a-fk-or]','',value)
            if re.search('[A-Za-z]',plain) and not JP.search(plain):
                # Model identifiers and units are intentionally retained. Whole English
                # phrases must be reviewed; they cannot silently become an exemption.
                if re.fullmatch(r'(?:[A-Z][A-Z0-9.\-]*|[0-9]+[A-Z]+)',plain.strip()):technical.append([row['id'],field,plain])
                else:failures.append([row['id'],field,'English-only text',value])
    overrides=[]
    for path in (ROOT/'overlay/kubejs/data/creatingspace/advancements').glob('*.json'):
        entry='data/creatingspace/advancements/'+path.name
        with zipfile.ZipFile(next((game/'mods').glob('creatingspace-*.jar'))) as jar:original=json.loads(jar.read(entry))
        patched=json.loads(path.read_text(encoding='utf8'))
        for field in ('title','description'):
            del original['display'][field];del patched['display'][field]
        assert original==patched, 'Advancement behavior changed: '+entry
        overrides.append(entry)
    result={'status':'PASS' if not failures else 'FAIL','language':'ja_jp','registeredVisibleAdvancements':len(data['rows']),'resolvedFields':fields,'untranslated':failures,'technicalNames':technical,'displayOnlyOverrides':overrides,'behaviorPreserved':True}
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('audit',type=Path);p.add_argument('--game',type=Path,default=ROOT/'revisions/version-control-r39/runtime/minecraft');p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    result=verify(a.audit,a.game);a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps(result,ensure_ascii=False))
    if result['status']!='PASS':raise SystemExit(1)
