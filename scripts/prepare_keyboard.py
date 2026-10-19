"""Verify pinned inputs and stage independent Android-library sources/assets/JNI."""
from pathlib import Path
import shutil
from keyboard_assets import prepare, ROOT
try:
    sources, entries = prepare(fetch=False)
except FileNotFoundError:
    sources, entries = prepare(fetch=True)
out = ROOT / 'embedded-input/build/generated/keyboard'
for src in sources:
    dest = out / 'java' / str(src).replace('\\', '/').split('/sherpa/java/', 1)[1]
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, dest)
for name, data in entries.items():
    dest = out / (name.replace('lib/', 'jniLibs/', 1) if name.startswith('lib/') else name)
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(data)
print(f'Keyboard: verified {len(sources)} sources and {len(entries)} payloads')
