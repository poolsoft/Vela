#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import io
import os
import shutil
import tarfile
import urllib.request

HF_BASE = "https://huggingface.co/mihuai/turkish-stt-66m-v2/resolve/main"
VAD_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"

def download_file(url, dest):
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        print(f"Already exists: {dest}")
        return
    print(f"Downloading {url} -> {dest}")
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req) as resp, open(dest, "wb") as f:
        shutil.copyfileobj(resp, f)

def package_kws_model(archive_name="vela-kws-turkish.tar.gz"):
    work_dir = "temp_kws_turkish"
    os.makedirs(work_dir, exist_ok=True)

    files = [
        "encoder.int8.onnx",
        "decoder.int8.onnx",
        "joiner.int8.onnx",
        "tokens.txt",
        "bpe.model",
    ]

    for fname in files:
        url = f"{HF_BASE}/{fname}"
        dest = os.path.join(work_dir, fname)
        download_file(url, dest)

    print(f"Creating archive {archive_name}...")
    with tarfile.open(archive_name, "w:gz") as tar:
        for fname in os.listdir(work_dir):
            file_path = os.path.join(work_dir, fname)
            tar.add(file_path, arcname=fname)

    size_mb = os.path.getsize(archive_name) / (1024 * 1024)
    print(f"Done! {archive_name} created ({size_mb:.2f} MB)")

if __name__ == "__main__":
    package_kws_model()
