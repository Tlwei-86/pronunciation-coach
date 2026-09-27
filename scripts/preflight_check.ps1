# Local Pre-Flight Check Script
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " [Pre-Flight Gate] Starting Validation" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$hasError = $false

# 1. Rust Formatter Check
Write-Host "[1/4] Checking Rust code formatting (cargo fmt --check)..." -NoNewline
$fmtOut = & cargo fmt --check --manifest-path pronunciation-core/Cargo.toml 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host " [FAILED]" -ForegroundColor Red
    Write-Host $fmtOut
    $hasError = $true
} else {
    Write-Host " [PASSED]" -ForegroundColor Green
}

# 2. Rust Clippy Linter Check
Write-Host "[2/4] Checking Rust linter warnings (cargo clippy -- -D warnings)..." -NoNewline
$clippyOut = & cargo clippy --manifest-path pronunciation-core/Cargo.toml -- -D warnings 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host " [FAILED]" -ForegroundColor Red
    Write-Host $clippyOut
    $hasError = $true
} else {
    Write-Host " [PASSED]" -ForegroundColor Green
}

# 3. Rust Unit Tests
Write-Host "[3/4] Running Rust unit tests (cargo test)..." -NoNewline
$testOut = & cargo test --manifest-path pronunciation-core/Cargo.toml 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host " [FAILED]" -ForegroundColor Red
    Write-Host $testOut
    $hasError = $true
} else {
    Write-Host " [PASSED]" -ForegroundColor Green
}

# 4. Line Ending Check on gradlew
Write-Host "[4/4] Verifying Linux line endings (LF) for gradlew..." -NoNewline
$gradlewPath = "android-app/gradlew"
if (Test-Path $gradlewPath) {
    $content = [System.IO.File]::ReadAllText($gradlewPath)
    if ($content.Contains([char]13)) {
        Write-Host " [FAILED] gradlew contains Windows CRLF! Converting to LF..." -ForegroundColor Yellow
        [System.IO.File]::WriteAllText($gradlewPath, $content.Replace("`r`n", "`n"))
        Write-Host " [CONVERTED TO LF]" -ForegroundColor Green
    } else {
        Write-Host " [PASSED]" -ForegroundColor Green
    }
} else {
    Write-Host " [SKIP - gradlew not found]" -ForegroundColor Yellow
}

Write-Host "========================================" -ForegroundColor Cyan
if ($hasError) {
    Write-Host " [Pre-Flight Gate] REJECTED: Fix issues before pushing!" -ForegroundColor Red
    exit 1
} else {
    Write-Host " [Pre-Flight Gate] ALL CHECKS PASSED: Safe to commit & push." -ForegroundColor Green
    exit 0
}
