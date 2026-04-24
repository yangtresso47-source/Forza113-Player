#!/usr/bin/env python3
"""
Rebrand StreamVault → Kuqforza IPTV Premium
Package: com.kuqforza.iptv
"""

import os

ROOT = os.path.expanduser("~/Forza113-Player")
os.chdir(ROOT)

# ═══ 1. Replace in all text files ═══
replacements = [
    ("com.streamvault.app", "com.kuqforza.iptv"),
    ("com/streamvault/app", "com/kuqforza/iptv"),
    ("StreamVault", "Kuqforza"),
    ("streamvault", "kuqforza"),
    ("Stream Vault", "Kuqforza IPTV Premium"),
]

extensions = ('.kt', '.kts', '.xml', '.json', '.properties', '.pro', '.cfg', '.yml', '.yaml', '.md', '.txt', '.gradle')

count = 0
for dirpath, dirnames, filenames in os.walk(ROOT):
    if '.git' in dirpath:
        continue
    for fname in filenames:
        if not any(fname.endswith(ext) for ext in extensions):
            continue
        fpath = os.path.join(dirpath, fname)
        try:
            with open(fpath, 'r', encoding='utf-8', errors='ignore') as f:
                content = f.read()
            original = content
            for old, new in replacements:
                content = content.replace(old, new)
            if content != original:
                with open(fpath, 'w', encoding='utf-8') as f:
                    f.write(content)
                count += 1
        except Exception:
            pass

print(f'1. Replaced text in {count} files')

# ═══ 2. Rename directories ═══
for base in ['app/src/main/java', 'app/src/androidTest/java', 'app/src/test/java',
             'data/src/main/java', 'data/src/test/java',
             'domain/src/main/java', 'domain/src/test/java',
             'player/src/main/java', 'player/src/test/java']:
    old_dir = os.path.join(ROOT, base, 'com', 'streamvault', 'app')
    new_dir = os.path.join(ROOT, base, 'com', 'kuqforza', 'iptv')
    if os.path.exists(old_dir):
        os.makedirs(os.path.dirname(new_dir), exist_ok=True)
        os.rename(old_dir, new_dir)
        sv_dir = os.path.join(ROOT, base, 'com', 'streamvault')
        if os.path.exists(sv_dir) and not os.listdir(sv_dir):
            os.rmdir(sv_dir)
        print(f'2. Renamed: {base}/com/streamvault/app → com/kuqforza/iptv')

# ═══ 3. Rename files with StreamVault in name ═══
for dirpath, dirnames, filenames in os.walk(ROOT):
    if '.git' in dirpath:
        continue
    for fname in filenames:
        if 'StreamVault' in fname or 'streamvault' in fname:
            old_path = os.path.join(dirpath, fname)
            new_fname = fname.replace('StreamVault', 'Kuqforza').replace('streamvault', 'kuqforza')
            new_path = os.path.join(dirpath, new_fname)
            os.rename(old_path, new_path)
            print(f'3. Renamed: {fname} → {new_fname}')

# ═══ 4. Update strings.xml ═══
strings_path = os.path.join(ROOT, 'app/src/main/res/values/strings.xml')
if os.path.exists(strings_path):
    with open(strings_path, 'r', encoding='utf-8') as f:
        s = f.read()
    s = s.replace('>Kuqforza<', '>Kuqforza IPTV Premium<')
    s = s.replace('Kuqforza for Android TV', 'Kuqforza IPTV Premium')
    with open(strings_path, 'w', encoding='utf-8') as f:
        f.write(s)
    print('4. Updated strings.xml')

print('\nDone!')
