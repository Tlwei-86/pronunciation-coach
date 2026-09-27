package com.pronunciationcoach.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pronunciationcoach.app.core.AcousticFeatureExtractor
import com.pronunciationcoach.app.core.PronunciationCoreBridge
import com.pronunciationcoach.app.domain.MicrophoneAudioSource
import com.pronunciationcoach.app.vision.LiveFaceMouthMetrics
import com.pronunciationcoach.app.vision.RealFaceLandmarkAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var rawResultJson by remember { mutableStateOf<String?>(null) }
    var currentTestMode by remember { mutableStateOf("Ready") }

    // Live Visual Mouth Tracking Metrics (From Real CameraX + ML Kit)
    var liveMouthMetrics by remember {
        mutableStateOf(
            LiveFaceMouthMetrics(
                jawOpen = 0.40f,
                lipRoundness = 0.12f,
                mouthWidthNormalized = 0.45f,
                isFaceDetected = false,
                statusText = "前置摄像头加载中..."
            )
        )
    }

    // Hardware Permissions
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var hasAudioPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasCameraPermission = perms[Manifest.permission.CAMERA] == true
        hasAudioPermission = perms[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasAudioPermission) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    val micAudioSource = remember { MicrophoneAudioSource() }
    var isLiveRecording by remember { mutableStateOf(false) }
    var liveAudioEnergy by remember { mutableStateOf(0f) }
    var liveDetectedVowel by remember { mutableStateOf("Ready") }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pronunciation Coach V0.2", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E2C),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF12121A)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Target Word Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF222233))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Target Word", fontSize = 14.sp, color = Color.Gray)
                    Text("funk", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4E95FF))
                    Text("/fʌŋk/", fontSize = 20.sp, color = Color(0xFFAAAAAA))
                }
            }

            // Real CameraX / ML Kit Mouth Tracking Viewport
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B26))
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (hasCameraPermission) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }

                                    // Real-time mouth landmark analysis
                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                        .also { analysis ->
                                            analysis.setAnalyzer(
                                                cameraExecutor,
                                                RealFaceLandmarkAnalyzer { metrics ->
                                                    liveMouthMetrics = metrics
                                                }
                                            )
                                        }

                                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            cameraSelector,
                                            preview,
                                            imageAnalysis
                                        )
                                    } catch (e: Exception) {
                                        println("[CameraX] Bind error: ${e.message}")
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📷 摄像头未授权", color = Color.LightGray, fontSize = 16.sp)
                            Text("请开启相机权限以实时监测口唇开合度", color = Color.Gray, fontSize = 12.sp)
                        }
                    }

                    // Live Vision HUD Metrics Bar
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xDD12121A)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(
                                    "👄 实时唇形开合: ${(liveMouthMetrics.jawOpen * 100).toInt()}% | ${liveMouthMetrics.statusText}",
                                    color = if (liveMouthMetrics.jawOpen > 0.60f) Color(0xFFFF8A80) else Color(0xFF81C784),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Face: ${if (liveMouthMetrics.isFaceDetected) "已锁定" else "寻找中..."} | Mic: ${if (isLiveRecording) "🔴 采集中" else "空闲"}",
                                    color = Color.LightGray,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            // Real Live Recording Controls with DSP Formant Recognition
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("真机实时声学与口唇综合测评", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "基于 16kHz 傅里叶频谱共振峰 + 真实面部下颌开合",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (!isLiveRecording) {
                                isLiveRecording = true
                                currentTestMode = "Live Recording (16kHz Mono)"
                                coroutineScope.launch {
                                    micAudioSource.startRecording()
                                }
                            } else {
                                isLiveRecording = false
                                coroutineScope.launch {
                                    val sample = micAudioSource.stopRecording()
                                    val rms = MicrophoneAudioSource.calculateRms(sample.pcmData)
                                    liveAudioEnergy = rms

                                    // 1. Run real DSP Formant/Spectrum Extraction on PCM data
                                    val acousticAcuity = withContext(Dispatchers.Default) {
                                        AcousticFeatureExtractor.analyzePcmBuffer(sample.pcmData)
                                    }
                                    liveDetectedVowel = "${acousticAcuity.detectedPhoneme} (${acousticAcuity.vowelQuality})"

                                    // 2. Feed real live visual jaw openness + real live acoustic formant probability into Rust Core
                                    val currentJaw = liveMouthMetrics.jawOpen
                                    val currentRoundness = liveMouthMetrics.lipRoundness

                                    rawResultJson = PronunciationCoreBridge.safeScoreFunk(
                                        acousticAcuity.targetProb,
                                        acousticAcuity.confusionProb,
                                        currentJaw,
                                        currentRoundness
                                    )
                                    currentTestMode = "Live Test: ${acousticAcuity.detectedPhoneme} [Jaw: ${(currentJaw * 100).toInt()}%]"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLiveRecording) Color(0xFFD32F2F) else Color(0xFF4E95FF)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isLiveRecording) "⏹ 停止录音并评测 (真实分析中)" else "🎙 按下开始实时录音评测",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Quick Verification Test Buttons (Benchmark Presets)
            Text("仿真金标对比 (Benchmark Presets)", color = Color.Gray, fontSize = 13.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        currentTestMode = "Golden: funk_good.wav"
                        rawResultJson = PronunciationCoreBridge.safeScoreFunk(0.92f, 0.05f, 0.45f, 0.10f)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text("funk_good", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        currentTestMode = "Confusion: funk_ah_like.wav"
                        rawResultJson = PronunciationCoreBridge.safeScoreFunk(0.48f, 0.42f, 0.72f, 0.08f)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("funk_ah (/ɑ/)", fontSize = 12.sp)
                }
            }

            // Score & Analysis Results
            rawResultJson?.let { jsonStr ->
                val jsonObj = remember(jsonStr) { runCatching { JSONObject(jsonStr) }.getOrNull() }
                if (jsonObj != null) {
                    val overall = jsonObj.optInt("overall_score", 0)
                    val acoustic = jsonObj.optInt("acoustic_accuracy", 0)
                    val visual = jsonObj.optInt("visual_accuracy", 0)
                    val guidanceArray = jsonObj.optJSONArray("guidance")
                    val phonemeArray = jsonObj.optJSONArray("phoneme_scores")

                    // Overall Score Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF222233))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Assessment: $currentTestMode", color = Color.Gray, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Overall Score", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("Acoustic: $acoustic | Visual: $visual", color = Color.LightGray, fontSize = 13.sp)
                                }
                                Text(
                                    "$overall",
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (overall >= 80) Color(0xFF4CAF50) else Color(0xFFFFB300)
                                )
                            }

                            Spacer(Modifier.height(16.dp))
                            Text("Phoneme Breakdown:", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))

                            // Phonemes Row
                            if (phonemeArray != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    for (i in 0 until phonemeArray.length()) {
                                        val p = phonemeArray.getJSONObject(i)
                                        val ph = p.getString("phoneme")
                                        val sc = p.getInt("score")
                                        val isPrimary = p.optBoolean("is_primary_issue", false)

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .background(
                                                    if (isPrimary) Color(0xFF4A1A1A) else Color(0xFF1E1E2C),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(ph, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Text("$sc", color = if (sc >= 80) Color(0xFF81C784) else Color(0xFFFF8A80), fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Guidance Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2A38))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("💡 AI Coach Guidance", color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            if (guidanceArray != null) {
                                for (i in 0 until guidanceArray.length()) {
                                    Text("• ${guidanceArray.getString(i)}", color = Color.White, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
