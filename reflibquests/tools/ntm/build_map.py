import argparse, csv, json, os, re, collections

ap = argparse.ArgumentParser()
ap.add_argument('--old-csv', required=True)
ap.add_argument('--old-lang', required=True)
ap.add_argument('--new-dir', required=True)
ap.add_argument('--pack-ids', default='')
ap.add_argument('--new-jar', default='')
ap.add_argument('--pack-dir', default='')
ap.add_argument('--out', required=True)
ap.add_argument('--report', required=True)
a = ap.parse_args()

new_items = set(f[:-5] for f in os.listdir(os.path.join(a.new_dir, 'assets/hbm/items')) if f.endswith('.json'))
new_lang = json.load(open(os.path.join(a.new_dir, 'assets/hbm/lang/en_us.json'), encoding='utf8'))
new_fluids = set(f[:-5] for f in os.listdir(os.path.join(a.new_dir, 'data/hbm/fluid_property')))
new_fluids |= set(k[len('hbmfluid.'):] for k in new_lang if k.startswith('hbmfluid.'))
old_lang = {}
for l in open(a.old_lang, encoding='utf8'):
    l = l.rstrip('\n')
    if '=' in l and not l.startswith('#'):
        k, v = l.split('=', 1)
        old_lang[k] = v

def new_name(i):
    return new_lang.get('item.hbm.' + i) or new_lang.get('block.hbm.' + i)

by_name = collections.defaultdict(list)
for i in sorted(new_items):
    n = new_name(i)
    if n and '%s' not in n:
        by_name[n].append(i)

old_fluid_by_name = collections.defaultdict(list)
for k, v in old_lang.items():
    if k.startswith('hbmfluid.'):
        old_fluid_by_name[v].append(k[len('hbmfluid.'):])

rows = list(csv.DictReader(open(a.old_csv, encoding='utf8')))
stacks = collections.OrderedDict()
for r in rows:
    if not r['ID'].startswith('hbm:'):
        continue
    key = (r['ID'][4:], int(r['Metadata']))
    if key not in stacks or (stacks[key]['NBT Tag Compound'] and not r['NBT Tag Compound']):
        stacks[key] = r

if a.pack_ids:
    for l in open(a.pack_ids, encoding='utf8'):
        p = l.split()
        if len(p) == 3 and p[0].startswith('hbm:') and p[1].isdigit():
            stacks.setdefault((p[0][4:], int(p[1])), None)

FLUID_ITEMS = {'fluid_icon': 'hbm:fluid_icon', 'fluid_tank_full': 'hbm:fluid_tank', 'fluid_barrel_full': 'hbm:fluid_barrel'}

SCRAP_MATERIALS = set()
for _m in re.finditer(r'"when": "([a-z_0-9]+)"', open(os.path.join(a.new_dir, 'assets/hbm/items/scraps.json'), encoding='utf8').read()):
    SCRAP_MATERIALS.add(_m.group(1))
MATERIAL_SYNONYMS = {'high_speed_steel': 'dura_steel', 'aluminum': 'aluminium', 'schrabidium_steel': 'schrabidate'}

def clean(s):
    return re.sub(r'§.', '', s).strip()

def fluid_for(display):
    d = clean(display)
    for pre in ('Universal Fluid Tank: ', 'Fluid Barrel: '):
        if d.startswith(pre):
            d = d[len(pre):]
    ids = old_fluid_by_name.get(d, [])
    ids = [i for i in ids if i in new_fluids or i in ('water', 'lava')]
    if len(ids) == 1:
        return ids[0]
    return None

def resolve(i, meta, r):
    if r is None:
        old_name = old_lang.get('item.%s.name' % i) or old_lang.get('tile.%s.name' % i) or ''
        r_missing = True
    else:
        r_missing = False
    old_name = old_name if r is None else clean(r['Display Name'])
    tkey = r['Translation Key'] if r else ''
    model = r['Model Name'].split('#')[0] if r else ''
    cls = r['Class Name'].rsplit('.', 1)[-1] if r else ''
    if i in FLUID_ITEMS and r is not None:
        f = fluid_for(r['Display Name']) if r else None
        if f is None:
            return None, 'unresolved', 'fluid "%s" has no unique 26.1.2 fluid' % old_name
        fid = 'minecraft:' + f if f in ('water', 'lava') else 'hbm:' + f
        return {'id': FLUID_ITEMS[i], 'components': {'hbm:fluid': fid, 'hbm:fluid_amount': 1000}}, 'component', 'fluid %s' % fid
    if i in new_items and meta == 0:
        nn = new_name(i)
        if nn is None or nn == old_name or not old_name:
            return {'id': 'hbm:' + i}, 'same', ''
        return {'id': 'hbm:' + i}, 'renamed', '"%s" is now "%s"' % (old_name, nn)
    cands = []
    if model.startswith('hbm:items/'):
        cands.append(model[len('hbm:items/'):].replace('.', '_').replace('-', '_'))
    if tkey.startswith('item.'):
        cands.append(tkey[5:].replace('.', '_'))
    if model.startswith('hbm:') and not model.startswith('hbm:items/'):
        cands.append(model[4:].replace('.', '_').replace('-', '_'))
    for c in cands:
        if c not in new_items:
            continue
        if c == i and meta != 0:
            continue
        nn = new_name(c)
        st = 'derived' if (nn is None or nn == old_name) else 'derived-renamed'
        return {'id': 'hbm:' + c}, st, ('"%s" is now "%s"' % (old_name, nn)) if st == 'derived-renamed' else ''
    if i == 'bedrock_ore_new':
        kinds = ['light', 'heavy', 'rare', 'actinide', 'nonmetal', 'crystal']
        group, idx = {0: ('bedrock_ore_base_', 0), 32: ('bedrock_ore_base_washed_', 32), 64: ('bedrock_ore_base_roasted_', 64), 208: ('bedrock_ore_crumbs_', 208)}, None
        for start, (prefix, _) in group.items():
            if start <= meta < start + 6 and (prefix + kinds[meta - start]) in new_items:
                return {'id': 'hbm:' + prefix + kinds[meta - start]}, 'derived', 'name "%s"' % old_name
    if i == 'bedrock_ore_fragment' and old_name.endswith(' Bedrock Ore Fragment'):
        mat = re.sub(r'[^a-z0-9]+', '_', old_name[:-len(' Bedrock Ore Fragment')].lower()).strip('_')
        mat = {'tantalum': 'tantalium', 'polonium_210': 'polonium', 'radium_226': 'ra226', 'uranium_238': 'u238', 'technetium_99': 'technetium'}.get(mat, mat)
        if 'bedrock_ore_fragment_' + mat in new_items:
            return {'id': 'hbm:bedrock_ore_fragment_' + mat}, 'derived', 'name "%s"' % old_name
    if i == 'scraps' and old_name.endswith(' Scraps'):
        mat = re.sub(r'[^a-z0-9]+', '_', old_name[:-len(' Scraps')].lower()).strip('_')
        if mat in SCRAP_MATERIALS:
            return {'id': 'hbm:scraps', 'components': {'hbm:material': mat}}, 'component', 'hbm:material component, confirmed in game'
    tpl = old_lang.get('item.%s.name' % i, '')
    if '%s' in tpl and old_name and meta != 0:
        pre, _, post = tpl.partition('%s')
        if old_name.startswith(pre) and old_name.endswith(post):
            mat = old_name[len(pre):len(old_name) - len(post)] if post else old_name[len(pre):]
            slug = re.sub(r'[^a-z0-9]+', '_', mat.lower()).strip('_')
            slug = MATERIAL_SYNONYMS.get(slug, slug)
            if (i + '_' + slug) in new_items:
                return {'id': 'hbm:%s_%s' % (i, slug)}, 'derived', 'material "%s"' % mat
    if old_name and len(by_name.get(old_name, [])) == 1:
        return {'id': 'hbm:' + by_name[old_name][0]}, 'by-name', 'matched on the name "%s"' % old_name
    if old_name and by_name.get(old_name):
        return None, 'unresolved', 'name "%s" matches several: %s' % (old_name, ', '.join(by_name[old_name][:6]))
    near = [n for n in sorted(new_items) if n.startswith(i.split('_')[0] + '_') or n.startswith(i)][:5]
    return None, 'unresolved', 'no 26.1.2 item for "%s" (%s); nearest ids: %s' % (old_name, tkey, ', '.join(near))

OVERRIDES = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'overrides.json'), encoding='utf8'))
table = collections.OrderedDict()
for (i, meta), r in stacks.items():
    t, st, note = resolve(i, meta, r)
    ov = OVERRIDES.get('hbm:%s@%d' % (i, meta))
    if ov:
        t, st, note = (ov['to'] if isinstance(ov['to'], dict) else {'id': ov['to']}), ov['status'], ov['note']
    table['hbm:%s@%d' % (i, meta)] = {'to': t, 'status': st, 'note': note, 'name': clean(r['Display Name']) if r else ''}

fluids = {}
for k, v in old_lang.items():
    if k.startswith('hbmfluid.'):
        f = k[len('hbmfluid.'):]
        if f in new_fluids:
            fluids[f] = 'hbm:' + f
        elif f in ('water', 'lava'):
            fluids[f] = 'minecraft:' + f

static = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'static.json'), encoding='utf8'))

def snake(s):
    s = re.sub(r'([a-z0-9])([A-Z])', lambda m: m.group(1) + '_' + m.group(2), s)
    s = re.sub(r'([A-Z]+)([A-Z][a-z])', lambda m: m.group(1) + '_' + m.group(2), s)
    return s.lower()

oredict = dict(static.get('oredict', {}))
if a.new_jar and a.pack_dir:
    import zipfile
    tags = set()
    for n in zipfile.ZipFile(a.new_jar).namelist():
        m = re.match(r'data/c/tags/item/(.+)\.json$', n)
        if m:
            tags.add(m.group(1))
    names = set()
    for d, _, fs in os.walk(a.pack_dir):
        for f in fs:
            if f.endswith('.snbt'):
                for m in re.finditer(r'id: "ore",\s*data: "([^"]*)"', open(os.path.join(d, f), encoding='utf8').read()):
                    names.add(m.group(1))
    prefixes = [('plateTriple', 'plates_cast'), ('plate', 'plates'), ('ingot', 'ingots'), ('dust', 'dusts'), ('nugget', 'nuggets'),
                ('block', 'storage_blocks'), ('ore', 'ores'), ('gem', 'gems'), ('bolt', 'bolts'), ('crystal', 'crystals'), ('billet', 'billets'),
                ('wireDense', 'wires_dense'), ('wire', 'wires'), ('shell', 'shells'), ('pipe', 'pipes'), ('fragment', 'fragments')]
    for n in sorted(names):
        if n in oredict:
            continue
        for pre, cat in prefixes:
            if n.startswith(pre) and len(n) > len(pre) and n[len(pre)].isupper():
                tag = '%s/%s' % (cat, snake(n[len(pre):]))
                if tag in tags:
                    oredict[n] = {'tag': 'c:' + tag, 'status': 'derived', 'note': ''}
                    break
        else:
            if n not in oredict:
                oredict[n] = {'tag': None, 'status': 'unresolved', 'note': 'no tag of that name in the 26.1.2 mod'}

KINDS = ['light', 'heavy', 'rare', 'actinide', 'nonmetal', 'crystal']
BEDROCK = {
    'bedrockOreFullWashed': ('bedrock_ore_base_washed_', 'derived', 'items named "Washed <kind> Bedrock Ore" in the 1.12.2 dump'),
    'bedrockOrePrimaryFraction': ('bedrock_ore_primary_', 'inferred', 'by name'),
    'bedrockOreRoastedPrimaryFraction': ('bedrock_ore_primary_roasted_', 'inferred', 'by name'),
    'bedrockOreSulfuricPrimaryFraction': ('bedrock_ore_primary_sulfuric_', 'inferred', 'by name'),
    'bedrockOreDissolvedPrimaryFraction': ('bedrock_ore_primary_solvent_', 'inferred', 'Dissolved read as solvent from the naming pattern'),
    'bedrockOreCleanedPrimaryFraction': ('bedrock_ore_primary_rad_', 'inferred', 'Cleaned read as rad from the naming pattern'),
    'bedrockOreSulfuric': ('bedrock_ore_sulfuric_byproduct_', 'inferred', 'by name'),
    'bedrockOreSulfuricRoasted': ('bedrock_ore_sulfuric_roasted_', 'inferred', 'by name'),
    'bedrockOreSulfuricSeared': ('bedrock_ore_sulfuric_arc_', 'inferred', 'Seared read as arc from the naming pattern'),
    'bedrockOreSulfuricWashed': ('bedrock_ore_sulfuric_washed_', 'inferred', 'by name'),
    'bedrockOreDissolved': ('bedrock_ore_solvent_byproduct_', 'inferred', 'Dissolved read as solvent from the naming pattern'),
    'bedrockOreDissolvedRoasted': ('bedrock_ore_solvent_roasted_', 'inferred', 'Dissolved read as solvent from the naming pattern'),
    'bedrockOreDissolvedSeared': ('bedrock_ore_solvent_arc_', 'inferred', 'Dissolved/Seared read as solvent/arc from the naming pattern'),
    'bedrockOreDissolvedWashed': ('bedrock_ore_solvent_washed_', 'inferred', 'Dissolved read as solvent from the naming pattern'),
    'bedrockOreCleaned': ('bedrock_ore_rad_byproduct_', 'inferred', 'Cleaned read as rad from the naming pattern'),
    'bedrockOreCleanedRoasted': ('bedrock_ore_rad_roasted_', 'inferred', 'Cleaned read as rad from the naming pattern'),
    'bedrockOreCleanedSeared': ('bedrock_ore_rad_arc_', 'inferred', 'Cleaned/Seared read as rad/arc from the naming pattern'),
    'bedrockOreCleanedWashed': ('bedrock_ore_rad_washed_', 'inferred', 'Cleaned read as rad from the naming pattern'),
}
for n, (pre, st, why) in BEDROCK.items():
    ids = ['hbm:' + pre + k for k in KINDS if (pre + k) in new_items]
    if len(ids) == 6 and oredict.get(n, {}).get('tag') is None:
        oredict[n] = {'tag': None, 'items': ids, 'status': st, 'note': why}

FAMILIES = {
    'bobblehead': (lambda i: i.startswith('bobblehead_'), 'derived', 'every bobblehead_* item'),
    'drillbit': (lambda i: i.startswith('drillbit_') and not i.endswith('_diamond'), 'inferred', 'drillbit_* items without the diamond tip'),
    'tippedDrillbit': (lambda i: i.startswith('drillbit_') and i.endswith('_diamond'), 'inferred', 'drillbit_* items with the diamond tip'),
}
for n, (test, st, why) in FAMILIES.items():
    ids = ['hbm:' + i for i in sorted(new_items) if test(i)]
    if ids and oredict.get(n, {}).get('tag') is None:
        oredict[n] = {'tag': None, 'items': ids, 'status': st, 'note': why}
oredict['container1000estradiol'] = {'tag': None, 'items': ['hbm:fluid_tank'], 'status': 'substitute-weak',
                                     'note': 'the 1.12.2 filter meant a tank holding estradiol; the 26.1.2 filter here accepts any universal fluid tank (fluid cannot be tested with item())'}

members = collections.defaultdict(list)
for r in rows:
    for od in (r['OreDict Names'] or '').split(','):
        od = od.strip()
        if od:
            members[od].append((r['ID'], int(r['Metadata'])))
for n, v in list(oredict.items()):
    if v['tag'] is None and members.get(n):
        ids = []
        for (i, m) in members[n]:
            if i.startswith('hbm:'):
                e = table.get('%s@%d' % (i, m))
                to = e['to'] if e else {'id': i}
                if to and 'components' not in to and to['id'] not in ids:
                    ids.append(to['id'])
        if ids:
            oredict[n] = {'tag': None, 'items': ids, 'status': 'from-dump', 'note': 'members of this ore dictionary name in the 1.12.2 dump, translated'}

items_out = collections.OrderedDict()
for k, v in table.items():
    if v['status'] == 'same':
        continue
    items_out[k] = {'to': v['to'], 'status': v['status'], 'note': v['note']}
json.dump({'items': items_out, 'fluids': fluids, 'vanilla': static['vanilla'], 'entities': static['entities'], 'oredict': oredict,
           'images': static['images'], 'keep_mods': static['keep_mods']},
          open(a.out, 'w', encoding='utf8'), indent=0, ensure_ascii=False, separators=(',', ':'))
cnt = collections.Counter(v['status'] for v in table.values())
print(len(table), dict(cnt))

used = collections.Counter()
if a.pack_ids:
    for l in open(a.pack_ids, encoding='utf8'):
        p = l.split()
        if len(p) == 3 and p[0].startswith('hbm:') and p[1].isdigit():
            used[(p[0] + '@' + p[1])] += int(p[2])
with open(a.report, 'w', encoding='utf8') as f:
    f.write('# Nuclear Tech 1.12.2 to 26.1.2 id table (generated)\n\n')
    f.write('Statuses: %s\n\n' % ', '.join('%s=%d' % kv for kv in sorted(cnt.items())))
    f.write('## Used by the quest pack and not simply "same"\n\n| old | uses | name | new | status | note |\n|---|---|---|---|---|---|\n')
    for k, c in sorted(used.items()):
        v = table.get(k)
        if v is None:
            f.write('| %s | %d | ? | - | not in dump | the 1.12.2 dump has no such stack |\n' % (k, c))
        elif v['status'] != 'same':
            f.write('| %s | %d | %s | %s | %s | %s |\n' % (k, c, v['name'], json.dumps(v['to']) if v['to'] else '-', v['status'], v['note']))
print('used', len(used), 'unresolved used', sum(1 for k in used if k not in table or table[k]['status'] == 'unresolved'))
