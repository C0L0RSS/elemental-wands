"""Export the block model's material labels and the UV islands paint.py packs for them.

The approved atlas was painted onto these islands; build_mod.py checks the sculpt still packs them.
"""
import json,sys
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
sys.path.insert(0,str(ROOT/'art/storm_roc_mod'))
import build_mod
from paint import paint
study=json.loads((ROOT/'art/storm_roc/candidate/storm_roc.geo.json').read_text())['minecraft:geometry'][0]['bones']
model=build_mod.sculpt(study)
_,_,atlas=paint(model.parts)
manifest={'source':'.','parts':model.parts,'atlas':list(atlas)}
(HERE/'layout.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(f'Read {len(model.parts)} pieces and their UV islands')
