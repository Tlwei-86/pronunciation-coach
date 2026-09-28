import os, sys, json, time, re, math
from pathlib import Path
from playwright.sync_api import sync_playwright

EDGE_PATH = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
MANIFEST_PATH = Path("dataset/500_common_words/manifest.json")
AUDIO_DIR = Path("dataset/500_common_words/audio")
OUTPUT_PATH = Path("results/real_flalingo_50_benchmark.json")

def evaluate_word_on_flalingo(page, word: str, audio_path: Path) -> dict:
    start_time = time.time()
    try:
        page.goto("https://start.flalingo.com/en/pronunciation-test", timeout=30000)
        time.sleep(1.5)
        
        # Switch tab
        tab_btn = page.locator("text=Check a word or sentence").first
        tab_btn.click()
        time.sleep(1)
        
        # Enter word
        inp = page.query_selector("input, textarea")
        inp.fill(word)
        time.sleep(0.5)
        
        # Use this text
        use_btn = page.locator("text=Use this text").first
        use_btn.click()
        time.sleep(1.5)
        
        # Start recording
        rec_btn = page.locator("button[aria-label='Tap to record']").first
        rec_btn.click()
        time.sleep(3.5)
        
        # Stop recording
        stop_btn = page.locator("button[aria-label='Tap when you finish']").first
        stop_btn.click()
        
        # Wait for results to render
        time.sleep(5)
        
        page_text = page.locator("body").inner_text()
        
        acc_match = re.search(r"Accuracy\s*\n\s*(\d+)", page_text)
        accuracy = int(acc_match.group(1)) if acc_match else None
        
        score_match = re.search(r"PRONUNCIATION SCORE\s*\n\s*(\d+)", page_text)
        score_val = int(score_match.group(1)) if score_match else None
        if score_val is None:
            m2 = re.search(r"(\d{1,3})\s*/\s*100", page_text)
            score_val = int(m2.group(1)) if m2 else accuracy
            
        elapsed = time.time() - start_time
        return {
            "status": "success",
            "accuracy": accuracy,
            "overall_score": score_val,
            "elapsed_sec": round(elapsed, 2)
        }
    except Exception as e:
        elapsed = time.time() - start_time
        return {
            "status": "error",
            "error": str(e),
            "elapsed_sec": round(elapsed, 2)
        }

def run_benchmark(sample_limit: int = 50):
    with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
        manifest = json.load(f)
        
    records = manifest[:sample_limit]
    print(f"Starting 100% Real Physical Web Evaluation for {len(records)} words...")
    
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    existing_results = {}
    if OUTPUT_PATH.exists():
        try:
            with open(OUTPUT_PATH, "r", encoding="utf-8") as f:
                data = json.load(f)
                for item in data.get("details", []):
                    existing_results[item["id"]] = item
        except:
            pass

    results = list(existing_results.values())
    
    with sync_playwright() as p:
        for idx, item in enumerate(records):
            word_id = item["id"]
            word = item["word"]
            audio_path = AUDIO_DIR / f"{word_id}_{word}.wav"
            
            if word_id in existing_results and existing_results[word_id].get("status") == "success":
                print(f"[{idx+1}/{len(records)}] {word_id} ({word}): Cached -> Accuracy={existing_results[word_id].get('accuracy')}")
                continue
                
            if not audio_path.exists():
                print(f"[{idx+1}/{len(records)}] {word_id} ({word}): Audio file not found!")
                continue

            args = [
                "--use-fake-device-for-media-stream",
                f"--use-file-for-fake-audio-capture={audio_path.resolve()}",
                "--allow-file-access",
                "--use-fake-ui-for-media-stream"
            ]

            browser = p.chromium.launch(executable_path=EDGE_PATH, headless=True, args=args)
            page = browser.new_page()
            
            res = evaluate_word_on_flalingo(page, word, audio_path)
            browser.close()
            
            item_result = {
                "id": word_id,
                "word": word,
                "category": item.get("category"),
                "focus_phoneme": item.get("focus_phoneme"),
                "flalingo_accuracy": res.get("accuracy"),
                "flalingo_overall": res.get("overall_score"),
                "status": res.get("status"),
                "elapsed_sec": res.get("elapsed_sec")
            }
            results.append(item_result)
            print(f"[{idx+1}/{len(records)}] {word_id} ({word}): {res.get('status')} -> Accuracy={res.get('accuracy')}, Time={res.get('elapsed_sec')}s", flush=True)
            
            # Incremental save
            with open(OUTPUT_PATH, "w", encoding="utf-8") as out_f:
                json.dump({
                    "meta": {
                        "date": time.strftime("%Y-%m-%d %H:%M:%S"),
                        "total_tested": len(results),
                        "platform": "Flalingo Pronunciation Lab (Web via Playwright)",
                        "audio_source": "dataset/500_common_words/audio (Physical WAV 16kHz)"
                    },
                    "details": results
                }, out_f, indent=2, ensure_ascii=False)

    print("Web benchmark evaluation finished!")

if __name__ == "__main__":
    limit = int(sys.argv[1]) if len(sys.argv) > 1 else 50
    run_benchmark(limit)
