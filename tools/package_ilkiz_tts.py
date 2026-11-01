#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import io
import json
import os
import shutil
import tarfile
import urllib.request
import onnx

HF_BASE = "https://huggingface.co/ilkeraktuna/piper-tts-tr-ilkiz-medium"
ONNX_URL = f"{HF_BASE}/resolve/main/ilkiz.onnx"
JSON_URL = f"{HF_BASE}/raw/main/ilkiz.onnx.json"
DFKI_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-tr_TR-dfki-medium.tar.bz2"

OUT_DIR_NAME = "vits-piper-tr_TR-ilkiz-medium"
ARCHIVE_NAME = "vits-piper-tr_TR-ilkiz-medium.tar.bz2"

def main():
    print(f"Creating build dir: {OUT_DIR_NAME}")
    if os.path.exists(OUT_DIR_NAME):
        shutil.rmtree(OUT_DIR_NAME)
    os.makedirs(OUT_DIR_NAME, exist_ok=True)

    # 1. Download onnx.json
    print("Downloading ilkiz.onnx.json...")
    req = urllib.request.Request(JSON_URL, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req) as resp:
        cfg = json.loads(resp.read().decode("utf-8"))

    # Also save tr_TR-ilkiz-medium.onnx.json into directory
    json_dest = os.path.join(OUT_DIR_NAME, "tr_TR-ilkiz-medium.onnx.json")
    with open(json_dest, "w", encoding="utf-8", newline="\n") as f:
        json.dump(cfg, f, indent=2, ensure_ascii=False)

    # 2. Generate tokens.txt from phoneme_id_map (ensure UNIX \n line endings!)
    print("Generating tokens.txt with UNIX line endings...")
    phoneme_id_map = cfg.get("phoneme_id_map", {})
    id_to_phoneme = []
    for p, ids in phoneme_id_map.items():
        for i in ids:
            id_to_phoneme.append((i, p))
    id_to_phoneme.sort(key=lambda x: x[0])

    tokens_path = os.path.join(OUT_DIR_NAME, "tokens.txt")
    with open(tokens_path, "w", encoding="utf-8", newline="\n") as f:
        for i, p in id_to_phoneme:
            f.write(f"{p} {i}\n")

    # 3. Download ilkiz.onnx
    print("Downloading ilkiz.onnx (approx 63MB)...")
    onnx_dest = os.path.join(OUT_DIR_NAME, "tr_TR-ilkiz-medium.onnx")
    req = urllib.request.Request(ONNX_URL, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req) as resp, open(onnx_dest, "wb") as out_file:
        shutil.copyfileobj(resp, out_file)

    # 3b. Update ONNX metadata for sherpa-onnx VITS compatibility
    print("Injecting sherpa-onnx metadata into ONNX model...")
    model = onnx.load(onnx_dest)
    meta_data = {
        "model_type": "vits",
        "comment": "piper",
        "language": "Turkish",
        "voice": "tr",
        "version": "1",
        "has_espeak": "1",
        "has_g2pw": "0",
        "n_speakers": "1",
        "sample_rate": "22050",
    }
    # Clear existing metadata and set proper values
    while len(model.metadata_props) > 0:
        model.metadata_props.pop()
    for k, v in meta_data.items():
        prop = model.metadata_props.add()
        prop.key = k
        prop.value = str(v)
    onnx.save(model, onnx_dest)
    print("ONNX metadata updated successfully.")

    # 4. Extract espeak-ng-data from sherpa-onnx dfki model
    print("Downloading & extracting espeak-ng-data from sherpa-onnx...")
    req = urllib.request.Request(DFKI_URL, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req) as resp:
        with tarfile.open(fileobj=io.BytesIO(resp.read()), mode="r:bz2") as tar:
            for member in tar.getmembers():
                if "espeak-ng-data" in member.name:
                    prefix = "vits-piper-tr_TR-dfki-medium/"
                    if member.name.startswith(prefix):
                        member.name = member.name[len(prefix):]
                        tar.extract(member, path=OUT_DIR_NAME)

    # 5. Create tar.bz2 archive
    print(f"Packaging {ARCHIVE_NAME}...")
    with tarfile.open(ARCHIVE_NAME, "w:bz2") as tar:
        tar.add(OUT_DIR_NAME, arcname=OUT_DIR_NAME)

    size_mb = os.path.getsize(ARCHIVE_NAME) / (1024 * 1024)
    print(f"SUCCESS! {ARCHIVE_NAME} created ({size_mb:.2f} MB)")

if __name__ == "__main__":
    main()
