#!/usr/bin/env python3
"""Switches the sources to one Minecraft version.

Java files mark version-specific code like this:

    //#if MC >= 1.21.9
    code for 1.21.9 and newer
    //#elif MC >= 1.21.6
    //$ code for 1.21.6 - 1.21.8
    //#else
    //$ code for older versions
    //#endif

Lines of branches that don't match the target are commented out with "//$ ", the matching
branch is uncommented. Running it again with another version switches back, so the files in
git always stay in the 1.21.11 state.

tools/renames.json lists plain renames (class moved or renamed between versions) that are
applied to every file for the versions matching their condition; these are only done in CI.

A file that starts with "//#file <condition>" is deleted when the condition doesn't match, and
its mixin is dropped from whiteclient.mixins.json.

Conditions: MC <op> <version> with op in >=, >, <=, <, ==, !=, joined with && and ||.

Usage: pp.py <minecraft node> [src dir ...]
"""
import json
import os
import re
import sys

PREFIX = '//$ '


def ver(s):
    return tuple(int(p) for p in s.split('.'))


def check(cond, mc):
    cond = cond.strip()
    for part in cond.split('||'):
        ok = True
        for term in part.split('&&'):
            m = re.fullmatch(r'\s*MC\s*(>=|<=|==|!=|>|<)\s*([0-9.]+)\s*', term)
            if not m:
                raise ValueError('bad condition: ' + cond)
            a, b = ver(mc), ver(m.group(2))
            op = m.group(1)
            ok = ok and {'>=': a >= b, '<=': a <= b, '==': a == b, '!=': a != b, '>': a > b, '<': a < b}[op]
        if ok:
            return True
    return False


def comment(line):
    stripped = line.lstrip()
    if stripped.startswith('//$'):
        return line
    indent = line[:len(line) - len(stripped)]
    if stripped == '':
        return line
    return indent + PREFIX + stripped


def uncomment(line):
    stripped = line.lstrip()
    indent = line[:len(line) - len(stripped)]
    if stripped.startswith(PREFIX):
        return indent + stripped[len(PREFIX):]
    if stripped == PREFIX.rstrip():
        return indent
    return line


def process(text, mc, path='?'):
    lines = text.split('\n')
    out = []
    # stack of [active_parent, branch_taken, current_active]
    stack = []

    def active():
        return all(s[2] for s in stack)

    for n, line in enumerate(lines, 1):
        s = line.strip()
        if s.startswith('//#if '):
            parent = active()
            ok = check(s[6:], mc)
            stack.append([parent, ok, ok])
            out.append(line)
            continue
        if s.startswith('//#elif '):
            if not stack:
                raise ValueError(f'{path}:{n}: #elif without #if')
            top = stack[-1]
            ok = not top[1] and check(s[8:], mc)
            top[2] = ok
            top[1] = top[1] or ok
            out.append(line)
            continue
        if s == '//#else':
            if not stack:
                raise ValueError(f'{path}:{n}: #else without #if')
            top = stack[-1]
            top[2] = not top[1]
            top[1] = True
            out.append(line)
            continue
        if s == '//#endif':
            if not stack:
                raise ValueError(f'{path}:{n}: #endif without #if')
            stack.pop()
            out.append(line)
            continue
        if stack:
            out.append(uncomment(line) if active() else comment(line))
        else:
            out.append(line)
    if stack:
        raise ValueError(f'{path}: missing #endif')
    return '\n'.join(out)


def main():
    mc = sys.argv[1]
    roots = sys.argv[2:] or ['src']
    removed = set()
    changed = 0
    for root in roots:
        for d, _, files in os.walk(root):
            for f in files:
                if not f.endswith('.java'):
                    continue
                p = os.path.join(d, f)
                text = open(p, encoding='utf-8').read()
                first = text.lstrip().split('\n', 1)[0].strip()
                if first.startswith('//#file ') and not check(first[8:], mc):
                    os.remove(p)
                    removed.add(f[:-5])
                    changed += 1
                    continue
                new = process(text, mc, p)
                if new != text:
                    open(p, 'w', encoding='utf-8').write(new)
                    changed += 1
        for d, _, files in os.walk(root):
            for f in files:
                if f.endswith('.mixins.json') and removed:
                    p = os.path.join(d, f)
                    cfg = json.load(open(p, encoding='utf-8'))
                    for key in ('mixins', 'client', 'server'):
                        if key in cfg:
                            cfg[key] = [m for m in cfg[key] if m.split('.')[-1] not in removed]
                    open(p, 'w', encoding='utf-8').write(json.dumps(cfg, indent='\t') + '\n')
    for root in roots:
        for d, _, files in os.walk(root):
            for f in files:
                if f.endswith('.java'):
                    p = os.path.join(d, f)
                    text = open(p, encoding='utf-8').read()
                    new = re.sub(r'(MC_NODE\s*=\s*")[^"]*(")', r'\g<1>' + mc + r'\g<2>', text)
                    if new != text:
                        open(p, 'w', encoding='utf-8').write(new)
    renames = []
    rp = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'renames.json')
    if os.path.exists(rp):
        for r in json.load(open(rp, encoding='utf-8')):
            if check(r['if'], mc):
                pat = r['regex'] if 'regex' in r else re.escape(r['from'])
                renames.append((re.compile(pat), r['to']))
    renamed = 0
    if renames:
        for root in roots:
            for d, _, files in os.walk(root):
                for f in files:
                    if f.endswith('.java'):
                        p = os.path.join(d, f)
                        text = open(p, encoding='utf-8').read()
                        new = text
                        for pat, to in renames:
                            new = pat.sub(to, new)
                        if new != text:
                            open(p, 'w', encoding='utf-8').write(new)
                            renamed += 1
    print(f'pp: {mc}: {changed} files switched, {len(removed)} files removed, {renamed} files renamed')


if __name__ == '__main__':
    main()
