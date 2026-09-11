# Gathered Mass model

Original custom 3D mesh with 18 separately animated rock fragments and 468 beveled
polygon faces. The JSON is shared with the Minecraft renderer; the OBJ is an
editable export. The preview uses the exact mesh and a local copy of the approved
Stone texture, with charge controls and rotation. It is not gameplay footage.

Generate geometry: `python3 tools/build_stone_cluster_model.py` from the repository
root. The Stone texture generator uses this mesh for the primary HUD icon.

Serve only this directory:

```sh
python3 -m http.server 8341 --bind 127.0.0.1 --directory art/stone/cluster
```

Open `http://127.0.0.1:8341/preview.html` while the local server is running.
