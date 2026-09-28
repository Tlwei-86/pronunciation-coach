import asyncio
import io
import json
import os
import sys
import time
from pathlib import Path
import edge_tts
import soxr
import soundfile as sf

VOICE = "en-US-JennyNeural"
MANIFEST_PATH = Path("dataset/500_common_words/manifest.json")
OUTPUT_DIR = Path("dataset/500_common_words/audio")
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
    items = json.load(f)

print(f"Total items in manifest: {len(items)}")

semaphore = asyncio.Semaphore(5)

async def synthesize_word(item, idx, total):
    word_id = item["id"]
    # clean word name (strip any _1 etc)
    raw_word = item["word"].split("_")[0]
    out_wav = OUTPUT_DIR / f"{word_id}_{raw_word}.wav"
    
    if out_wav.exists() and out_wav.stat().st_size > 1000:
        return True

    async with semaphore:
        for retry in range(3):
            try:
                communicate = edge_tts.Communicate(raw_word, VOICE)
                audio_bytes = bytearray()
                async for chunk in communicate.stream():
                    if chunk["type"] == "audio":
                        audio_bytes.extend(chunk["data"])
                
                if len(audio_bytes) == 0:
                    raise ValueError("Empty audio received")

                data, sr = sf.read(io.BytesIO(audio_bytes))
                if sr != 16000:
                    data = soxr.resample(data, sr, 16000)
                
                sf.write(str(out_wav), data, 16000, subtype="PCM_16")
                if (idx + 1) % 50 == 0 or idx == total - 1:
                    print(f"[{idx+1}/{total}] Generated: {out_wav.name} ({out_wav.stat().st_size} bytes)")
                return True
            except Exception as e:
                if retry == 2:
                    print(f"Failed to generate {raw_word}: {e}")
                    return False
                await asyncio.sleep(1.0)

async def main():
    start_time = time.time()
    tasks = [synthesize_word(item, i, len(items)) for i, item in enumerate(items)]
    results = await asyncio.gather(*tasks)
    success_count = sum(1 for r in results if r)
    elapsed = time.time() - start_time
    print(f"Completed physical TTS synthesis: {success_count}/{len(items)} audio files in {elapsed:.1f}s.")

asyncio.run(main())