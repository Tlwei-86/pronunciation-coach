$adb = "C:\ADBTools\adb.exe"
$device = "e3193eb7"
$targetDir = "C:\Users\tlwei_Rd\.gemini\antigravity-acp\brain\d431a2db-d132-4ee8-ad8c-ab4e8bdcabda\telemetry"
New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
$logFile = "$targetDir\monitor_2min.jsonl"
$rawLogcat = "$targetDir\logcat_2min.txt"

# Clear previous buffer
& $adb -s $device logcat -c

$job = Start-Job -ScriptBlock {
    param($adb, $device, $rawLogcat)
    & $adb -s $device logcat -v threadtime > $rawLogcat
} -ArgumentList $adb, $device, $rawLogcat

$startTime = Get-Date
$durationSec = 120
$lastHash = ""

Write-Host "Started 2-minute real-time telemetry monitor at $startTime. Will run for $durationSec seconds..."

while (((Get-Date) - $startTime).TotalSeconds -lt $durationSec) {
    $now = Get-Date
    $elapsed = [math]::Round(($now - $startTime).TotalSeconds, 1)
    
    # 1. Capture UI hierarchy
    & $adb -s $device shell uiautomator dump /sdcard/live_dump.xml 2>$null
    $ui = & $adb -s $device shell cat /sdcard/live_dump.xml 2>$null
    
    if ($ui -and $ui.Length -gt 100) {
        $currHash = $ui.GetHashCode().ToString()
        if ($currHash -ne $lastHash) {
            $lastHash = $currHash
            
            $lipMatch = [regex]::Match($ui, '实时唇形开合:\s*(\d+)%')
            $faceStatusMatch = [regex]::Match($ui, 'Face:\s*([^\|]+)\|\s*Mic:\s*([^\"]+)')
            $overallScoreMatch = [regex]::Match($ui, 'Acoustic:\s*(\d+)\s*\|\s*Visual:\s*(\d+)')
            $tongueHeightMatch = [regex]::Match($ui, '舌位高度 \(Height\)[^<]*<node[^>]*text="(\d+)%"')
            $tongueBacknessMatch = [regex]::Match($ui, '舌位前后 \(Backness\)[^<]*<node[^>]*text="(\d+)%"')
            $guidanceMatch = [regex]::Match($ui, '舌位发音诊断:\s*([^\"]+)')
            $rawStatusMatch = [regex]::Match($ui, 'Live:\s*([^\"]+)')
            
            $entry = [PSCustomObject]@{
                elapsed_sec = $elapsed
                timestamp = $now.ToString("yyyy-MM-dd HH:mm:ss.fff")
                lip_open_pct = if ($lipMatch.Success) { $lipMatch.Groups[1].Value } else { "N/A" }
                face_status = if ($faceStatusMatch.Success) { $faceStatusMatch.Groups[1].Value.Trim() } else { "N/A" }
                mic_status = if ($faceStatusMatch.Success) { $faceStatusMatch.Groups[2].Value.Trim() } else { "N/A" }
                acoustic_score = if ($overallScoreMatch.Success) { $overallScoreMatch.Groups[1].Value } else { "N/A" }
                visual_score = if ($overallScoreMatch.Success) { $overallScoreMatch.Groups[2].Value } else { "N/A" }
                tongue_height = if ($tongueHeightMatch.Success) { $tongueHeightMatch.Groups[1].Value } else { "N/A" }
                tongue_backness = if ($tongueBacknessMatch.Success) { $tongueBacknessMatch.Groups[1].Value } else { "N/A" }
                guidance = if ($guidanceMatch.Success) { $guidanceMatch.Groups[1].Value } else { "N/A" }
                status_banner = if ($rawStatusMatch.Success) { $rawStatusMatch.Groups[1].Value } else { "N/A" }
            }
            $entry | ConvertTo-Json -Compress | Out-File -FilePath $logFile -Append -Encoding utf8
            Write-Host "[$elapsed s] UI Change: Lip=$($entry.lip_open_pct)% | Face=$($entry.face_status) | Mic=$($entry.mic_status)"
        }
    }
    
    # 2. Grab periodic snapshot every 30s
    if ([int]$elapsed % 30 -eq 0 -and [int]$elapsed -gt 0) {
        $snapName = "snap_${elapsed}s.png"
        & $adb -s $device shell screencap -p "/sdcard/$snapName"
        & $adb -s $device pull "/sdcard/$snapName" "$targetDir\$snapName" 2>$null
    }
    
    Start-Sleep -Seconds 1
}

Stop-Job $job -ErrorAction SilentlyContinue
Receive-Job $job -ErrorAction SilentlyContinue | Out-Null
Remove-Job $job -ErrorAction SilentlyContinue

Write-Host "Completed 2-minute telemetry collection successfully!"
