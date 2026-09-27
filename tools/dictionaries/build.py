#!/usr/bin/env python3
"""Build reproducible, licensed, filtered Lumi SQLite packages. Never put corpora in APK assets."""
import argparse
import collections
import copy
import gzip
import hashlib
import json
import re
import sqlite3
import unicodedata
import urllib.parse
import urllib.request
import concurrent.futures
import time
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
POLICY = ROOT / 'app/src/main/assets/dictionaries/filter-policy.json'
VERSION = '2026.09.27.1'
XINHUA_REV = 'fe6d6c2e8baa82187f4c96bbe042e43f96c05666'
BASE = 'https://github.com/huangder/Lumi_Books/releases/download/dictionaries-' + VERSION


def canonical(obj):
    return json.dumps(obj, ensure_ascii=False, sort_keys=True, separators=(',', ':'))


def digest(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def normalize(text):
    return ' '.join(unicodedata.normalize('NFKC', text).lower().split())


class Policy:
    def __init__(self, obj):
        self.obj = obj
        self.version = obj['version']
        self.mapping = str.maketrans(obj['charMap'])
        self.rules = [(r, self.norm(r['text'])) for r in obj['rules']]
        self.hits = collections.Counter()

    def norm(self, text):
        return normalize(text).translate(self.mapping)

    def matches(self, text, field, dictionary, entry, sense=None):
        value = self.norm(text)
        for rule, needle in self.rules:
            if field not in rule['fields']:
                continue
            if any(e['dictionaryId'] == dictionary and e['entryId'] == entry and
                   (not e.get('senseId') or e['senseId'] == sense) for e in rule.get('exceptions', [])):
                continue
            if rule['mode'] == 'exact':
                hit = value == needle
            elif rule['mode'] == 'phrase':
                hit = needle in value
            else:
                hit = any((m.start() == 0 or not value[m.start()-1].isalnum()) and
                          (m.end() == len(value) or not value[m.end()].isalnum())
                          for m in re.finditer(re.escape(needle), value))
            if hit:
                self.hits[rule['category'] + ':' + rule['id']] += 1
                return True
        return False

    def apply(self, dictionary, original):
        entry = copy.deepcopy(original)
        eid = entry['id']
        if any(self.matches(w, 'headword', dictionary, eid) for w in [entry['headword']] + entry.get('aliases', [])):
            return None, True
        filtered = entry.get('partiallyFiltered', False)
        senses = []
        for sense in entry['senses']:
            sid = sense['id']
            if self.matches(sense['definition'], 'definition', dictionary, eid, sid) or self.matches(sense.get('partOfSpeech', ''), 'definition', dictionary, eid, sid):
                filtered = True
                continue
            examples = [x for x in sense.get('examples', []) if not
                        (self.matches(x['text'], 'example', dictionary, eid, sid) or
                         self.matches(x.get('translation', ''), 'example', dictionary, eid, sid))]
            related = [x for x in sense.get('related', []) if not self.matches(x, 'related', dictionary, eid, sid)]
            filtered |= len(examples) != len(sense.get('examples', [])) or len(related) != len(sense.get('related', []))
            sense.update(examples=examples, related=related)
            senses.append(sense)
        if not senses:
            return None, True
        for field in ('pronunciation', 'kind'):
            if self.matches(entry.get(field, ''), 'related', dictionary, eid):
                entry[field] = ''
                filtered = True
        entry.update(senses=senses, partiallyFiltered=bool(filtered))
        return entry, bool(filtered)


def fetch(url, path, lock):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists():
        print('Downloading ' + path.name, flush=True)
        temporary = path.with_suffix(path.suffix + '.part')
        req = urllib.request.Request(url, headers={'User-Agent': 'LumiDictionaryBuilder/1.0'})
        if url.endswith('raw-wiktextract-data.jsonl.gz'):
            # Range requests avoid a single multi-minute connection stalling. Pin the ETag
            # across all pieces so an upstream replacement can never mix snapshots.
            ranged_download(url, temporary)
        else:
            with urllib.request.urlopen(req, timeout=60) as source, temporary.open('wb') as dest:
                while chunk := source.read(1024 * 1024):
                    dest.write(chunk)
        temporary.replace(path)
    record = {'url': url, 'sha256': digest(path), 'sizeBytes': path.stat().st_size}
    if path.name in lock and lock[path.name] != record:
        raise ValueError('Source changed: ' + path.name + '; use a new source snapshot directory')
    lock[path.name] = record
    return path


def ranged_download(url, target):
    with urllib.request.urlopen(urllib.request.Request(url,headers={'Range':'bytes=0-0','User-Agent':'LumiDictionaryBuilder/1.0'}), timeout=30) as response:
        if response.status != 206:
            raise ValueError('Range downloads unavailable')
        total=int(response.headers['Content-Range'].split('/')[-1])
        etag=response.headers['ETag']
        response.read()
    size=512*1024
    parts=target.parent/('chunks-'+hashlib.sha256(etag.encode()).hexdigest()[:16])
    parts.mkdir(exist_ok=True)
    def one(i):
        start=i*size; end=min(total,start+size)-1; part=parts/str(i)
        if part.exists() and part.stat().st_size==end-start+1: return
        for attempt in range(4):
            try:
                request=urllib.request.Request(url,headers={'Range':f'bytes={start}-{end}','If-Range':etag,'User-Agent':'LumiDictionaryBuilder/1.0'})
                with urllib.request.urlopen(request,timeout=30) as response:
                    if response.status!=206 or response.headers.get('ETag')!=etag or response.headers.get('Content-Range')!=f'bytes {start}-{end}/{total}':
                        raise ValueError('Upstream snapshot changed')
                    data=response.read(end-start+2)
                if len(data)!=end-start+1: raise ValueError('Incomplete source range')
                part.write_bytes(data); return
            except Exception:
                if attempt==3: raise
                time.sleep(attempt+1)
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        for count,_ in enumerate(pool.map(one,range((total+size-1)//size)),1):
            if count%50==0: print(f'{count*size//1048576} MiB downloaded',flush=True)
    with target.open('wb') as out:
        for i in range((total+size-1)//size): out.write((parts/str(i)).read_bytes())


def make_entry(headword, definition, kind, pronunciation='', examples=None, aliases=None, source_url=''):
    eid = hashlib.sha256((kind + '\0' + headword + '\0' + definition).encode()).hexdigest()[:32]
    return {'id': eid, 'headword': headword, 'aliases': aliases or [], 'pronunciation': pronunciation,
            'kind': kind, 'sourceUrl': source_url, 'senses': [{'id': '1', 'definition': definition,
            'examples': examples or [], 'related': []}]}


def xinhua_entries(source):
    for filename, kind, key in [('word', '汉字', 'word'), ('ci', '词语', 'ci'), ('idiom', '成语', 'word'), ('xiehouyu', '歇后语', 'riddle')]:
        for obj in json.loads((source / (filename + '.json')).read_text('utf-8')):
            head = obj.get(key, '').strip()
            definition = obj.get('answer' if filename == 'xiehouyu' else 'explanation', '').strip()
            if not definition:
                continue
            examples = []
            if obj.get('example', '').strip() and obj['example'] != '无':
                examples.append({'text': obj['example'], 'translation': ''})
            entry = make_entry(head, definition, kind, obj.get('pinyin', ''), examples,
                               [obj['oldword']] if obj.get('oldword') and obj['oldword'] != head else [],
                               'https://github.com/pwxcoo/chinese-xinhua/tree/' + XINHUA_REV)
            # Full character explanation is a separate sense so one blocked paragraph does not remove everything.
            if obj.get('more', '').strip():
                entry['senses'].append({'id': '2', 'definition': obj['more'], 'examples': [], 'related': []})
            yield entry


def wordnet_entries(path):
    with gzip.open(path, 'rb') as f:
        tree = ET.parse(f)
    root = tree.getroot()
    synsets = {s.attrib['id']: s for s in root.iter('Synset')}
    words = collections.defaultdict(list)
    for entry in root.iter('LexicalEntry'):
        lemma = entry.find('Lemma').attrib['writtenForm']
        for sense in entry.findall('Sense'):
            words[sense.attrib['synset']].append(lemma)
    for lex in root.iter('LexicalEntry'):
        lemma = lex.find('Lemma')
        head = lemma.attrib['writtenForm']
        senses = []
        for s in lex.findall('Sense'):
            syn = synsets.get(s.attrib['synset'])
            if syn is None:
                continue
            definition = syn.findtext('Definition', '').strip()
            if definition:
                senses.append({'id': s.attrib['id'], 'definition': definition,
                    'partOfSpeech': lemma.attrib.get('partOfSpeech', ''),
                    'examples': [{'text': x.text or '', 'translation': ''} for x in syn.findall('Example')],
                    'related': sorted(set(words[s.attrib['synset']]) - {head})})
        if senses:
            yield {'id': lex.attrib['id'], 'headword': head,
                   'aliases': sorted({f.attrib['writtenForm'] for f in lex.findall('Form')}),
                   'pronunciation': '', 'kind': 'English', 'senses': senses,
                   'sourceUrl': 'https://github.com/globalwordnet/english-wordnet'}


def wiki_entries(path, languages, converter):
    with gzip.open(path, 'rt', encoding='utf-8') as source:
        for line in source:
            obj = json.loads(line)
            if obj.get('lang_code') not in languages:
                continue
            head = obj.get('word', '').strip()
            if not head:
                continue
            senses = []
            for i, s in enumerate(obj.get('senses', [])):
                glosses = s.get('glosses', [])
                definition = '；'.join(glosses).strip()
                # External quotations may have different copyright; omit them and all media.
                examples = [{'text': converter.convert(e.get('text', '')), 'translation': converter.convert(e.get('translation', ''))}
                            for e in s.get('examples', []) if e.get('text') and not any(e.get(k) for k in ('ref','reference','author','title','date','source','literal_meaning'))
                            and not any(k in e for k in ('license','copyright'))]
                if definition:
                    tags = s.get('tags', []) + s.get('raw_tags', [])
                    definition = (('[' + ', '.join(tags) + '] ') if tags else '') + definition
                    senses.append({'id': str(i+1), 'definition': converter.convert(definition),
                        'partOfSpeech': converter.convert(obj.get('pos_title', obj.get('pos', ''))),
                        'examples': examples, 'related': [converter.convert(x['word']) for x in s.get('synonyms', []) if x.get('word')]})
            if not senses:
                continue
            aliases = {head, converter.convert(head)}
            aliases.update(f['form'] for f in obj.get('forms', []) if f.get('form') and len(f['form']) <= 256)
            aliases.update(converter.convert(x) for x in list(aliases))
            sounds = []
            for sound in obj.get('sounds', []):
                for key in ('ipa', 'zh_pron', 'other'):
                    if isinstance(sound.get(key), str):
                        sounds.append(sound[key])
            eid = hashlib.sha256(canonical({'head':head,'pos':obj.get('pos'),'senses':senses}).encode()).hexdigest()[:32]
            yield {'id': eid, 'headword': converter.convert(head), 'aliases': sorted(aliases),
                'pronunciation': ' / '.join(dict.fromkeys(sounds)), 'kind': converter.convert(obj.get('lang','')),
                'senses': senses, 'sourceUrl': 'https://zh.wiktionary.org/wiki/' + urllib.parse.quote(head, safe='')}


def build_package(descriptor, entries, output, policy, licenses, sources):
    did = descriptor['id']
    stage = output / did
    stage.mkdir(parents=True, exist_ok=True)
    dbpath = stage / 'dictionary.sqlite'
    dbpath.unlink(missing_ok=True)
    db = sqlite3.connect(dbpath)
    db.executescript('''PRAGMA user_version=1;
        CREATE TABLE entries(id TEXT PRIMARY KEY,payload TEXT NOT NULL);
        CREATE TABLE aliases(key TEXT NOT NULL,entry_id TEXT NOT NULL,PRIMARY KEY(key,entry_id));
        CREATE TABLE blocked_keys(hash TEXT PRIMARY KEY);
        CREATE TABLE metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL);''')
    report = {'input':0,'entries':0,'removed':0,'partial':0,'samples':[]}
    from opencc import OpenCC
    to_simplified, to_traditional = OpenCC('t2s'), OpenCC('s2t')
    for original in entries:
        aliases = set(original.get('aliases', [])) | {original['headword']}
        aliases.update(to_simplified.convert(x) for x in list(aliases))
        aliases.update(to_traditional.convert(x) for x in list(aliases))
        original['aliases'] = sorted(aliases)
        report['input'] += 1
        entry, filtered = policy.apply(did, original)
        if not entry:
            for key in {normalize(x) for x in [original['headword']] + original.get('aliases', [])}:
                db.execute('INSERT OR IGNORE INTO blocked_keys VALUES(?)', (hashlib.sha256(key.encode()).hexdigest(),))
            report['removed'] += 1
        else:
            inserted = db.execute('INSERT OR IGNORE INTO entries VALUES(?,?)', (entry['id'], canonical(entry))).rowcount
            if not inserted:
                continue
            for key in {normalize(x) for x in [entry['headword']] + entry.get('aliases', []) if x.strip()}:
                db.execute('INSERT OR IGNORE INTO aliases VALUES(?,?)', (key, entry['id']))
            report['entries'] += 1
            if filtered: report['partial'] += 1
        if filtered and len(report['samples']) < 100:
            report['samples'].append({'id':original['id'],'headword':original['headword'],'removed':not bool(entry)})
    descriptor.update(entryCount=report['entries'], filterVersion=policy.version, formatVersion=1)
    for key, value in {'id':did,'version':descriptor['version'],'filterVersion':str(policy.version)}.items():
        db.execute('INSERT INTO metadata VALUES(?,?)',(key,value))
    db.commit()
    assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
    db.close()
    (stage / 'licenses.txt').write_text(licenses,encoding='utf-8')
    (stage / 'sources.json').write_text(canonical(sources),encoding='utf-8')
    package_metadata = {k:v for k,v in descriptor.items() if k not in ('downloadUrl','sizeBytes','installedBytes','sha256')}
    (stage / 'metadata.json').write_text(canonical(package_metadata),encoding='utf-8')
    filename = did + '-' + descriptor['version'] + '.zip'
    archive = output / filename
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for file in sorted(stage.iterdir()):
            info=zipfile.ZipInfo(file.name, date_time=(2026,9,27,0,0,0))
            info.compress_type=zipfile.ZIP_DEFLATED
            z.writestr(info,file.read_bytes())
    descriptor.update(downloadUrl=BASE+'/'+filename,sha256=digest(archive),sizeBytes=archive.stat().st_size,
                      installedBytes=sum(f.stat().st_size for f in stage.iterdir()))
    report['ruleHits']=dict(policy.hits)
    (output/(did+'-review.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(canonical({'id':did,'entries':report['entries'],'removed':report['removed'],'partial':report['partial'],'bytes':descriptor['sizeBytes']}),flush=True)
    return descriptor


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--output',type=Path,default=ROOT/'artifacts/dictionaries')
    parser.add_argument('--only',choices=['all','chinese-xinhua','wordnet','wiki'],default='all')
    parser.add_argument('--fetch-only',action='store_true')
    args=parser.parse_args()
    output=args.output.resolve(); output.mkdir(parents=True,exist_ok=True)
    sources=output/'sources'; sources.mkdir(exist_ok=True)
    lockpath=sources/'sources.lock.json'
    lock=json.loads(lockpath.read_text('utf-8')) if lockpath.exists() else {}
    def download(url,name):
        result=fetch(url,sources/name,lock)
        lockpath.write_text(json.dumps(lock,indent=2),encoding='utf-8')
        return result
    catalog=json.loads((ROOT/'app/src/main/assets/dictionaries/catalog.json').read_text('utf-8'))
    previous=output/'catalog.json'
    if previous.exists(): catalog=json.loads(previous.read_text('utf-8'))
    for desc in catalog['dictionaries']:
        did=desc['id']
        if args.only!='all' and not (args.only=='wiki' and did.startswith('wiki-')) and did!=args.only: continue
        desc['version']=VERSION
        if did=='chinese-xinhua':
            for name in ('word','ci','idiom','xiehouyu'):
                download(f'https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/{XINHUA_REV}/data/{name}.json',name+'.json')
            license_path=download(f'https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/{XINHUA_REV}/LICENSE','xinhua-LICENSE.txt')
            licenses=license_path.read_text('utf-8'); entries=xinhua_entries(sources)
        elif did=='wordnet':
            path=download('https://github.com/globalwordnet/english-wordnet/releases/download/2025-edition/english-wordnet-2025.xml.gz','english-wordnet-2025.xml.gz')
            lp=download('https://raw.githubusercontent.com/globalwordnet/english-wordnet/2025-edition/LICENSE.md','wordnet-LICENSE.md')
            licenses=lp.read_text('utf-8')
            wn_license=download('https://raw.githubusercontent.com/globalwordnet/english-wordnet/2025-edition/WNDB_License.txt','wordnet-WNDB-License.txt')
            cc_license=download('https://creativecommons.org/licenses/by/4.0/legalcode.txt','CC-BY-4.0.txt')
            licenses+='\n\n'+wn_license.read_text('utf-8')+'\n\n'+cc_license.read_text('utf-8')
            entries=wordnet_entries(path)
        else:
            path=download('https://kaikki.org/zhwiktionary/raw-wiktextract-data.jsonl.gz','zhwiktionary-20260901.jsonl.gz')
            lp=download('https://creativecommons.org/licenses/by-sa/4.0/legalcode.txt','CC-BY-SA-4.0.txt')
            licenses='Wiktionary contributors / 维基词典贡献者\n'+lp.read_text('utf-8')
            licenses+='\n词条的 sourceUrl 链接用于署名及访问原页历史。提取工具：Wiktextract / Kaikki.org。\n简体转换：OpenCC (Apache-2.0)。\n'
            opencc_license=download('https://raw.githubusercontent.com/BYVoid/OpenCC/ver.1.1.9/LICENSE','OpenCC-LICENSE.txt')
            licenses+=opencc_license.read_text('utf-8')
            if not args.fetch_only:
                from opencc import OpenCC
                entries=wiki_entries(path,{'zh','cmn'} if did=='wiki-zh' else {'en'},OpenCC('t2s'))
        if not args.fetch_only:
            licenses+='\n\nLUMI 整理说明\n'+desc['modifications']+'\n来源：'+desc['sourceUrl']+'\n上游版本：'+desc['upstreamVersion']+'\n'
            lp = download('https://raw.githubusercontent.com/BYVoid/OpenCC/ver.1.1.9/LICENSE','OpenCC-LICENSE.txt')
            if not did.startswith('wiki'):
                licenses += '\n\n检索别名字形转换：OpenCC\n' + lp.read_text('utf-8')
            policy=Policy(json.loads(POLICY.read_text('utf-8')))
            build_package(desc,entries,output,policy,licenses,lock)
            previous.write_text(json.dumps(catalog,ensure_ascii=False,indent=2),encoding='utf-8')
    if not args.fetch_only:
        policybytes=POLICY.read_bytes()
        (output/'filter-policy.json').write_bytes(policybytes)
        catalog['filter']={'version':json.loads(policybytes)['version'],'url':BASE+'/filter-policy.json','sha256':hashlib.sha256(policybytes).hexdigest()}
        previous.write_text(json.dumps(catalog,ensure_ascii=False,indent=2),encoding='utf-8')


if __name__=='__main__': main()
