#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import io
import os
import shutil
import tarfile
import urllib.request

HF_SEDA_TINY = "https://huggingface.co/atasoglu/seda-v0.2-tiny/resolve/main"
HF_SEDA = "https://huggingface.co/atasoglu/seda-v0.2/resolve/main"

def download_file(url, dest):
    print(f"Downloading {url} -> {dest}")
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req) as resp, open(dest, "wb") as f:
        shutil.copyfileobj(resp, f)

def package_model(name, base_url, archive_name):
    print(f"\n=== Packaging {name} ({archive_name}) ===")
    work_dir = f"temp_{name}"
    if os.path.exists(work_dir):
        shutil.rmtree(work_dir)
    os.makedirs(work_dir, exist_ok=True)

    files = [
        "encoder.int8.onnx",
        "decoder.onnx",
        "joiner.int8.onnx",
        "tokens.txt",
    ]

    for fname in files:
        url = f"{base_url}/{fname}"
        dest = os.path.join(work_dir, fname)
        download_file(url, dest)

    # VAD file
    vad_url = f"{base_url}/vad/silero_vad.onnx"
    vad_dest = os.path.join(work_dir, "silero_vad.onnx")
    download_file(vad_url, vad_dest)

    print(f"Creating archive {archive_name}...")
    with tarfile.open(archive_name, "w:gz") as tar:
        for fname in os.listdir(work_dir):
            file_path = os.path.join(work_dir, fname)
            tar.add(file_path, arcname=fname)

    shutil.rmtree(work_dir)
    size_mb = os.path.getsize(archive_name) / (1024 * 1024)
    print(f"Done! {archive_name} created ({size_mb:.2f} MB)")

def main():
    package_model("seda_tiny", HF_SEDA_TINY, "vela-asr-seda-tiny.tar.gz")
    package_model("seda", HF_SEDA, "vela-asr-seda.tar.gz")
    print("\nAll packages created successfully!")

if __name__ == "__main__":
    main()

