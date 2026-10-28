from pathlib import Path
import xml.etree.ElementTree as E, re, json, csv, collections
ROOT=Path(__file__).resolve().parents[1]; OUT=ROOT/'.logs/turkish-locale-audit';OUT.mkdir(parents=True,exist_ok=True)
issues=[]; summary=[]
def read(paths):
 result={};duplicates=[]
 for folder in paths:
  for path in sorted(folder.glob('*.xml')):
   raw=path.read_text(encoding='utf-8-sig')
   try: tree=E.fromstring(raw)
   except E.ParseError as error:
    issues.append(dict(scope=str(folder),kind='invalid_xml',name='',source=str(path.relative_to(ROOT)),base='',tr=str(error)));continue
   for e in tree:
    if e.tag not in ('string','plurals','string-array') or not e.get('name'):continue
    key=(e.tag,e.get('name'));vals={'text':''.join(e.itertext())} if e.tag=='string' else {c.get('quantity',str(i)):''.join(c.itertext()) for i,c in enumerate(e)}
    line=raw[:raw.find('name="'+e.get('name')+'"')].count('\n')+1
    if key in result and result[key]['path']==str(path.relative_to(ROOT)):duplicates.append(key)
    result[key]=dict(path=str(path.relative_to(ROOT)),line=line,vals=vals,translate=e.get('translatable')!='false')
 return result,duplicates
neutral={'Vela','Vela Maps','Google','Google Maps','OpenStreetMap','Material You','Android','ONNX','TTS','DSP','GPS','Wi-Fi','Bluetooth','USB','API','APK','CPU','RAM','MB','GB','km','m','km/h','mph','mph / km/h','OK','Park','Podcast','Podcasts','Online','Offline','Standard','Default','Auto','Navigation'}
# Only unmistakable brands/units are suppressed; identical words such as Default still need review.
neutral-= {'OK','Online','Offline','Standard','Default','Auto','Navigation','Podcasts'}
neutral.update({'Normal','Modern','Overture Maps','Overture','Smart Focus','Street View'})
def norm(x):return re.sub(r'\s+',' ',x).strip()
def isneutral(x):
 x=norm(x)
 stripped=re.sub(r'%(?:\d+\$)?[\d.]*[a-zA-Z]','',x)
 words=re.findall(r'[A-Za-z]+',stripped)
 units_only=all(w in {'m','km','MB','GB','ms','mph','Vela'} for w in words) and not re.search(r'[çğıöşüÇĞİÖŞÜ]',stripped)
 return units_only or x in neutral or bool(re.fullmatch(r'[\d\s%$.,:;/+×−–°?★⭐↗↙∞#\-]+',re.sub(r'%(?:\d+\$)?[\d.]*[a-zA-Z]','',x))) or bool(re.match(r'^(?:https?://|@(?:string|android):|[A-Z][A-Z0-9_ /+.-]{1,20}$)',x))
def placeholders(x):
 out=[];index=0
 for m in re.finditer(r'%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z])',x.replace('%%','')):
  if m[2]=='n':continue
  index+=1;out.append((int(m[1] or index),m[2]))
 return sorted(out)
scopes={'app/main':[ROOT/'app/src/main/res'],'app/car':[ROOT/'app/src/main/res',ROOT/'app/src/car/res'], 'app/standard':[ROOT/'app/src/main/res',ROOT/'app/src/standard/res'], 'core':[ROOT/'core/src/main/res']}
for scope, roots in scopes.items():
 base,bd=read([p/'values' for p in roots]);tr,td=read([p/'values-tr' for p in roots]);counts=collections.Counter()
 for (typ,name), b in base.items():
  if not b['translate']:continue
  counts['base']+=1;t=tr.get((typ,name))
  def add(kind,bv='',tv=''):
   counts[kind]+=1;issues.append(dict(scope=scope,kind=kind,type=typ,name=name,source=(t or b)['path'],line=(t or b)['line'],base=bv,tr=tv))
  if not t:add('missing',json.dumps(b['vals'],ensure_ascii=False));continue
  counts['present']+=1
  if typ=='string-array' and len(b['vals'])!=len(t['vals']):add('array_length',str(len(b['vals'])),str(len(t['vals'])))
  if typ=='plurals' and 'other' not in t['vals']:add('plural_other_missing')
  if all(norm(v)==norm(t['vals'].get(k,t['vals'].get('other',''))) for k,v in b['vals'].items()):
   text=' | '.join(b['vals'].values());add('identical_neutral' if isneutral(text) else 'identical_candidate',text,text)
  for k,v in t['vals'].items():
   original=b['vals'].get(k,b['vals'].get('other',''))
   if not v.strip() and original.strip():add('empty',original,v)
   if placeholders(original)!=placeholders(v):add('placeholder_mismatch',original,v)
  text=' | '.join(t['vals'].values())
  if '\ufffd' in text or any(s in text for s in ['Ã¼','Ã¶','Ä±','ÅŸ','ÄŸ','Ä°','Åž']):add('encoding_suspect','',text)
 summary.append(dict(scope=scope,**counts))
(OUT/'audit.json').write_text(json.dumps(dict(summary=summary,issues=issues),ensure_ascii=False,indent=2),encoding='utf-8')
with (OUT/'audit.tsv').open('w',encoding='utf-8-sig',newline='') as f:
 w=csv.DictWriter(f,fieldnames=['scope','kind','type','name','source','line','base','tr'],delimiter='\t',extrasaction='ignore');w.writeheader();w.writerows(issues)
print(json.dumps(summary,ensure_ascii=False))
print('\nMain identical examples:')
for x in [x for x in issues if x['scope']=='app/main' and x['kind']=='identical_candidate'][:35]:print(x['name'],':',x['tr'][:150])
print('\nMain missing examples:')
for x in [x for x in issues if x['scope']=='app/main' and x['kind']=='missing'][:18]:print(x['name'],':',x['base'][:140])
print('\nFormat issues:')
for x in issues:
 if x['kind'] in ('placeholder_mismatch','invalid_xml','empty','encoding_suspect'):print(x['scope'],x['kind'],x['name'],x.get('tr','')[:100])
