"""Which masa hotkeys only act while a boolean option is on.

Reads javap -c -p output (dis/<mod>/*.txt). For every reference to a Hotkeys.FIELD:
  - callback branch: `key == Hotkeys.X.getKeybind()` -> the body until the next branch; every effect (a call
    that is not a plain getter) and the conditions that must hold for it; what is returned when a gate fails.
  - held/pressed check: `Hotkeys.X.getKeybind().isKeybindHeld()` -> the conditions dominating the code that
    runs when the key is held, plus gates chained right after it (`held && option`).
Conditions are `getstatic <option>; getBooleanValue; ifeq/ifne`; others are shown as ?call / ?local.
"""
import os, re, sys, json
from collections import defaultdict

MOD = sys.argv[1]            # tw / mh / lm
HOTKEYS = sys.argv[2]        # fi.dy.masa.tweakeroo.config.Hotkeys
D = f'dis/{MOD}'

INS = re.compile(r'^\s*(\d+): (\w+)\s*(.*)$')
METHOD = re.compile(r'^  (?:[\w$.<>\[\], ]+ )?([\w$<>]+)\((.*)\)(?: throws .*)?;$|^  static \{\};$')

def parse(path):
    """{method name: [(off, op, arg)]} plus switch targets folded into arg lists."""
    methods = {}; cur = None; ins = None; sw = None
    for line in open(path, encoding='utf-8', errors='replace'):
        line = line.rstrip('\n')
        m = METHOD.match(line)
        if m:
            name = 'static{}' if 'static {}' in line else m.group(1)
            key = name; i = 2
            while key in methods: key = f'{name}#{i}'; i += 1
            cur = key; ins = methods[cur] = []; sw = None
            continue
        if ins is None: continue
        if sw is not None:
            t = re.match(r'^\s*(?:-?\d+|default): (\d+)$', line)
            if t: sw[2].append(int(t.group(1))); continue
            if line.strip() == '}': sw = None; continue
        mi = INS.match(line)
        if mi:
            off, op, arg = int(mi.group(1)), mi.group(2), mi.group(3)
            if op in ('tableswitch', 'lookupswitch'):
                sw = (off, op, []); ins.append(sw); continue
            ins.append((off, op, arg))
    return methods

def ref(arg):
    m = re.search(r'// (?:Field|Method|InterfaceMethod) ([\w/$]+)\.([\w$<>]+):', arg)
    return (m.group(1).replace('/', '.'), m.group(2)) if m else (None, None)

def short(cls, f):
    if cls is None: return '?'
    return cls.split('.')[-1] + '.' + str(f)

JUMPS = {'ifeq','ifne','iflt','ifge','ifgt','ifle','if_icmpeq','if_icmpne','if_icmplt','if_icmpge','if_icmpgt','if_icmple',
         'if_acmpeq','if_acmpne','ifnull','ifnonnull','goto','goto_w'}
ENDS = {'ireturn','areturn','return','lreturn','freturn','dreturn','athrow'}

def cfg(ins):
    offs = [i[0] for i in ins]
    idx = {o: k for k, o in enumerate(offs)}
    leaders = {offs[0]}
    for k, (o, op, a) in enumerate(ins):
        if op in JUMPS:
            leaders.add(int(a.split()[0]))
            if k + 1 < len(ins): leaders.add(offs[k + 1])
        elif op in ('tableswitch', 'lookupswitch'):
            leaders.update(a)
            if k + 1 < len(ins): leaders.add(offs[k + 1])
        elif op in ENDS and k + 1 < len(ins):
            leaders.add(offs[k + 1])
    starts = sorted(l for l in leaders if l in idx)
    block_of = {}
    blocks = {}
    for bi, s in enumerate(starts):
        e = starts[bi + 1] if bi + 1 < len(starts) else None
        k = idx[s]; lst = []
        while k < len(ins) and (e is None or ins[k][0] < e):
            lst.append(ins[k]); block_of[ins[k][0]] = s; k += 1
        blocks[s] = lst
    succ = {}
    for s, lst in blocks.items():
        o, op, a = lst[-1]
        nxt = None
        k = idx[o] + 1
        if k < len(ins): nxt = ins[k][0]
        if op in ('goto', 'goto_w'): succ[s] = [int(a.split()[0])]
        elif op in JUMPS: succ[s] = [x for x in (int(a.split()[0]), nxt) if x is not None]
        elif op in ('tableswitch', 'lookupswitch'): succ[s] = list(set(a))
        elif op in ENDS: succ[s] = []
        else: succ[s] = [nxt] if nxt is not None else []
    preds = defaultdict(list)
    for s, ss in succ.items():
        for t in ss: preds[t].append(s)
    # dominators
    allb = list(blocks)
    entry = starts[0]
    dom = {b: set(allb) for b in allb}; dom[entry] = {entry}
    changed = True
    while changed:
        changed = False
        for b in allb:
            if b == entry: continue
            ps = [dom[p] for p in preds[b]]
            new = (set.intersection(*ps) if ps else set()) | {b}
            if new != dom[b]: dom[b] = new; changed = True
    return blocks, succ, preds, dom, block_of, idx

GETTERS = re.compile(r'\.(getBooleanValue|getKeybind|isKeybindHeld|isPressed|getIntegerValue|getDoubleValue|getStringValue|'
                     r'getOptionListValue|getInstance|equals|ordinal|values|valueOf|getKeys|hasToolItem|getToolMode|'
                     r'booleanValue|intValue|getBooleanValueDefault|isModifierKeyHeld|isScreenOpen|getScreen|screen)\b')

def condition(lst):
    """For a block ending in ifeq/ifne after getBooleanValue: (name, jump_if_true?)"""
    o, op, a = lst[-1]
    if op not in ('ifeq', 'ifne'): return None
    if len(lst) >= 2:
        _, op2, a2 = lst[-2]
        c2, m2 = ref(a2)
        if op2.startswith('invoke') and m2 == 'getBooleanValue' and len(lst) >= 3:
            c3, f3 = ref(lst[-3][2])
            if lst[-3][1] == 'getstatic':
                return (short(c3, f3), op == 'ifne')
        if op2.startswith('invoke') and c2:
            return ('?call:' + short(c2, m2), op == 'ifne')
        if op2 == 'iload' or op2.startswith('iload_'):
            return ('?local' + (a2 or op2.split('_')[-1]), op == 'ifne')
        if op2 == 'getstatic' or op2 == 'getfield':
            return ('?field:' + short(*ref(a2)), op == 'ifne')
    return ('?cond', op == 'ifne')

def gates_for(target, blocks, succ, preds, dom, idx):
    """Conditions that must hold to reach block `target`: for a condition block B with successors, if one
    successor S dominates target and S's only predecessor is B, the edge to S is required."""
    out = []
    for b in dom[target]:
        lst = blocks[b]
        c = condition(lst)
        if not c: continue
        name, jump_if_true = c
        jt = int(lst[-1][2].split()[0])
        ss = succ[b]
        ft = [s for s in ss if s != jt]
        for s, val in ((jt, jump_if_true), (ft[0] if ft else None, not jump_if_true)):
            if s is None or s == target and b == target: continue
            if s in dom[target] and preds[s] == [b]:
                out.append(('+' if val else '-') + name)
    return out

hk = {}  # FIELD -> name
expect = False; last = None
for line in open(f'{D}/{HOTKEYS}.txt'):
    if re.search(r'new\s+#\d+\s+// class fi/dy/masa/malilib/config/options/ConfigHotkey$', line):
        expect = True; last = None; continue
    m = re.search(r'ldc(?:_w)?\s+#\d+\s+// String (\w+)$', line)
    if m and expect and last is None: last = m.group(1)
    m2 = re.search(r'putstatic.*Field (\w+):Lfi/dy/masa/malilib/config/options/ConfigHotkey;', line)
    if m2:
        hk[m2.group(1)] = last or m2.group(1); expect = False; last = None

result = defaultdict(lambda: {'callback': [], 'sites': []})
hkcls = HOTKEYS
for fn in os.listdir(D):
    cls = fn[:-4]
    try:
        methods = parse(f'{D}/{fn}')
    except Exception as e:
        print('parse fail', fn, e, file=sys.stderr); continue
    for mname, ins in methods.items():
        if not ins: continue
        refs = [k for k, (o, op, a) in enumerate(ins) if op == 'getstatic' and ref(a)[0] == hkcls and ref(a)[1] in hk]
        if not refs: continue
        if cls == hkcls: continue
        try:
            blocks, succ, preds, dom, block_of, idx = cfg(ins)
        except Exception as e:
            print('cfg fail', cls, mname, e, file=sys.stderr); continue
        for k in refs:
            field = ref(ins[k][2])[1]
            name = hk[field]
            nxt = [ins[j] for j in range(k + 1, min(k + 4, len(ins)))]
            ops = [(op, ref(a)[1]) for _, op, a in nxt]
            if len(ops) >= 2 and ops[0][1] == 'getKeybind' and ops[1][1] == 'setCallback':
                result[name]['callback'].append(short(cls, mname)); continue
            if len(ops) >= 2 and ops[0][1] == 'getKeybind' and ops[1][0] in ('if_acmpne', 'if_acmpeq'):
                o, op, a = nxt[1]
                jt = int(a.split()[0])
                body = nxt[1][0]
                body_start = ins[idx[o] + 1][0] if op == 'if_acmpne' else jt
                body_end = jt if op == 'if_acmpne' else None
                # effects in the body range and their gates
                effects = []
                for j in range(idx[body_start], len(ins)):
                    oo, op2, a2 = ins[j]
                    if body_end is not None and oo >= body_end: break
                    if op2.startswith('invoke'):
                        c2, m2 = ref(a2)
                        if c2 and not GETTERS.search('.' + m2) and not (c2.startswith('java.') or c2.startswith('com.google')):
                            g = gates_for(block_of[oo], blocks, succ, preds, dom, idx)
                            effects.append((short(c2, m2), [x for x in g if True]))
                    if op2 in ('putstatic', 'putfield'):
                        g = gates_for(block_of[oo], blocks, succ, preds, dom, idx)
                        effects.append(('set ' + short(*ref(a2)), g))
                result[name]['sites'].append({'kind': 'callback-branch', 'in': short(cls, mname), 'effects': effects})
                continue
            if len(ops) >= 2 and ops[0][1] == 'getKeybind' and ops[1][1] in ('isKeybindHeld', 'isPressed'):
                j = idx[nxt[1][0]] + 1
                if j < len(ins) and ins[j][1] in ('ifeq', 'ifne'):
                    o, op, a = ins[j]
                    jt = int(a.split()[0]); ft = ins[j + 1][0]
                    held = jt if op == 'ifne' else ft
                    g = gates_for(block_of[held], blocks, succ, preds, dom, idx) if held in block_of else ['?unreachable']
                    # chained gates right after: blocks that are only a condition test
                    b = held; seen = set()
                    while b in blocks and b not in seen:
                        seen.add(b)
                        c = condition(blocks[b])
                        if not c or len(blocks[b]) > 4: break
                        nm, jit = c
                        jt2 = int(blocks[b][-1][2].split()[0])
                        ft2 = [s for s in succ[b] if s != jt2]
                        # continue on the edge that leads further into the action (assume: the fall-through for ifeq)
                        if blocks[b][-1][1] == 'ifeq':
                            g.append('+' + nm); b = ft2[0] if ft2 else None
                        else:
                            g.append('-' + nm); b = ft2[0] if ft2 else None
                    result[name]['sites'].append({'kind': ops[1][1], 'in': short(cls, mname), 'gates': g})
                else:
                    result[name]['sites'].append({'kind': ops[1][1] + '(value used)', 'in': short(cls, mname)})
                continue
            result[name]['sites'].append({'kind': 'other:' + ','.join(f'{a}/{b}' for a, b in ops[:2]), 'in': short(cls, mname)})

for f, n in sorted(hk.items(), key=lambda x: x[1]):
    r = result.get(n)
    print(f'## {n}  ({f}){"  CALLBACK" if r and r["callback"] else ""}')
    if not r: print('   (no uses)'); continue
    for s in r['sites']:
        if s['kind'] == 'callback-branch':
            eff = s['effects']
            print(f'   branch in {s["in"]}: ' + ('; '.join(f'{e}[{" ".join(g)}]' for e, g in eff[:6]) or 'no effects'))
        elif 'gates' in s:
            print(f'   {s["kind"]} in {s["in"]}: gates {" ".join(s["gates"]) or "-"}')
        else:
            print(f'   {s["kind"]} in {s["in"]}')
