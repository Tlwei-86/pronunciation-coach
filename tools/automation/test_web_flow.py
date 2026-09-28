from playwright.sync_api import sync_playwright
import os, time, sys

edge_path = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
wav_sample = os.path.abspath("dataset/500_common_words/audio/W001_think.wav")

args = [
    "--use-fake-device-for-media-stream",
    f"--use-file-for-fake-audio-capture={wav_sample}",
    "--allow-file-access",
    "--use-fake-ui-for-media-stream"
]

print("Starting Playwright...", flush=True)
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=edge_path, headless=True, args=args)
    page = browser.new_page()
    print("Navigating to Flalingo...", flush=True)
    page.goto("https://start.flalingo.com/en/pronunciation-test", timeout=30000)
    time.sleep(2)
    
    print("Switching tab to 'Check a word or sentence'...", flush=True)
    page.locator("text=Check a word or sentence").first.click()
    time.sleep(1)
    
    print("Typing 'think'...", flush=True)
    inp = page.query_selector("input, textarea")
    inp.fill("think")
    time.sleep(1)
    
    print("Clicking 'Use this text'...", flush=True)
    page.locator("text=Use this text").first.click()
    time.sleep(2)
    
    # 1. Start recording
    rec_btn = page.locator("button[aria-label='Tap to record']").first
    print("Clicking 'Tap to record'...", flush=True)
    rec_btn.click()
    print("Streaming audio for 3.5 seconds...", flush=True)
    time.sleep(3.5)
    
    # 2. Stop recording with 'Tap when you finish'
    stop_btn = page.locator("button[aria-label='Tap when you finish']").first
    print("Found stop button ('Tap when you finish'), clicking...", flush=True)
    stop_btn.click()
    
    print("Waiting 6 seconds for results...", flush=True)
    time.sleep(6)
    
    os.makedirs("results", exist_ok=True)
    page.screenshot(path="results/flalingo_test_result.png")
    print("Screenshot saved to results/flalingo_test_result.png", flush=True)
    
    # Dump evaluation elements
    print("Scraping results from page:", flush=True)
    for el in page.query_selector_all("div, span, p, h1, h2, h3, h4"):
        txt = el.inner_text().strip()
        # Look for score numbers or phoneme text
        if any(w in txt.lower() for w in ["score", "accuracy", "%", "sound", "overall"]) and len(txt) < 100:
            print("  Score/Metric text:", repr(txt), flush=True)

    browser.close()
    print("Test completed successfully!", flush=True)
