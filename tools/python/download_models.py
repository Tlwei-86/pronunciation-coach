#!/usr/bin/env python3
"""
Model preparation script for Wav2Vec2 CTC phoneme models.
References:
- Base: facebook/wav2vec2-lv-60-espeak-cv-ft
- ONNX Community: onnx-community/wav2vec2-lv-60-espeak-cv-ft-ONNX
"""

import os
import sys

MODELS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "models"))

def main():
    os.makedirs(MODELS_DIR, exist_ok=True)
    print(f"[Model Downloader] Target directory: {MODELS_DIR}")
    print("[Model Downloader] Supported deployment quantization targets:")
    print("  1. FP16  (Golden Reference, ~632MB)")
    print("  2. INT8  (Candidate A, ~318MB)")
    print("  3. Q4F16 (Candidate B, ~197MB)")
    print("[Model Downloader] Use HuggingFace Hub or wget to cache deployment ONNX models.")

if __name__ == "__main__":
    main()
