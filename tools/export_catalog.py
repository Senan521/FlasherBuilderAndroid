#!/usr/bin/env python3
"""Flasher_Builder fork'undan cihaz/tür/bölge kataloğunu üretir.

Kullanım (fork'un kök klasöründen):
    python3 /yol/export_catalog.py > catalog.json
Çıktıyı app/src/main/assets/catalog.json olarak kaydedin. Repo yeni cihaz eklediğinde yeniden üretin.
"""
import json, sys
sys.path.insert(0, '.')
from flasher_builder.backend import Catalog

c = Catalog('.')
devices = []
for name in c.devices:
    types = []
    for kind in c.types(name):
        regions = [{"region": r, "needsBase": c.builders[(kind, c.devices[name], r)] == 'BUILD_ACE6_OOS.sh'}
                   for r in c.regions(name, kind)]
        types.append({"kind": kind, "regions": regions})
    devices.append({"name": name, "types": types})
json.dump({"devices": devices}, sys.stdout, ensure_ascii=False, indent=1)
