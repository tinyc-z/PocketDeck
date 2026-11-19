#!/usr/bin/env python3
"""Set / remove Android string resources across locales.

usage: strset.py RES_DIR JSON_FILE
JSON: {"set": {"key": {"en": "...", "zh-CN": "...", "zh-TW": "..."}}, "remove": ["key", ...]}
"""
import json, re, sys, os
res, spec = sys.argv[1], json.load(open(sys.argv[2]))
files = {"en": "values/strings.xml", "zh-CN": "values-zh-rCN/strings.xml", "zh-TW": "values-zh-rTW/strings.xml"}

def esc(v):
    v = v.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    v = v.replace("'", "\\'").replace('"', '\\"')
    if v.startswith('@') or v.startswith('?'):
        v = '\\' + v
    return v

for loc, rel in files.items():
    p = os.path.join(res, rel)
    s = open(p, encoding='utf-8').read()
    for k in spec.get("remove", []):
        s = re.sub(r'\n?[ \t]*<string name="%s"[^>]*>.*?</string>' % re.escape(k), '', s, flags=re.S)
    for k, vals in spec.get("set", {}).items():
        v = vals.get(loc) or vals.get("en")
        if v is None:
            continue
        line = '<string name="%s">%s</string>' % (k, esc(v))
        pat = re.compile(r'<string name="%s"[^>]*>.*?</string>' % re.escape(k), re.S)
        if pat.search(s):
            s = pat.sub(lambda m: line, s, count=1)
        else:
            s = s.replace('</resources>', '    %s\n</resources>' % line)
    open(p, 'w', encoding='utf-8').write(s)
print("ok")
