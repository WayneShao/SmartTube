"""Execute the actual Java-embedded submit branch with a small DOM event fixture."""
import json
from pathlib import Path
import subprocess
import sys
source = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('embedded-input/src/main/java/io/github/wayneshao/rayneo/ime/EmbeddedWebView.java')
line = next(line for line in source.read_text(encoding='utf-8').splitlines() if "if(op==='submit')" in line)
code = json.loads(line[line.index('"'):line.rindex('"') + 1])
fixture = r"""
const assert = require('node:assert/strict');
global.Event = class { constructor(type, init) { this.type=type; Object.assign(this, init); } };
global.KeyboardEvent = global.Event;
const submit = new Function('target', 'data', 'op', SOURCE);
for (const allow of [true, false]) {
  let requests=0, blur=0; const events=[];
  const target={tagName:'INPUT', form:{requestSubmit(){requests++;}},
    dispatchEvent(e){events.push(e); return e.type==='keydown'?allow:true;}, blur(){blur++;}};
  submit(target,()=>({id:1}),'submit');
  assert.equal(requests,allow?1:0);
  assert.deepEqual(events.map(e=>e.type),['change','keydown','keyup']);
  assert.equal(events[1].cancelable,true); assert.equal(events[1].key,'Enter');
  assert.equal(blur,1);
}
let requests=0;
submit({tagName:'TEXTAREA',form:{requestSubmit(){requests++;}},dispatchEvent(){return true;},blur(){}},()=>({}),'submit');
assert.equal(requests,0);
console.log('PASS: form submit once, preventDefault respected, Enter events, textarea not submitted');
""".replace('SOURCE', json.dumps(code))
subprocess.run(['node', '-e', fixture], check=True)
