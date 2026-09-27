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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.pronunciationcoach.app.R
import com.pronunciationcoach.app.audio.TongueEstimator
import com.pronunciationcoach.app.audio.TongueEvaluation
import com.pronunciationcoach.app.core.AcousticFeatureExtractor
import com.pronunciationcoach.app.core.PronunciationCoreBridge
import com.pronunciationcoach.app.core.TestArtifactPipeline
import com.pronunciationcoach.app.core.OnnxAcousticEngine
import com.pronunciationcoach.app.domain.MicrophoneAudioSource
import com.pronunciationcoach.app.ui.components.MouthTrackingOverlay
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
    var currentTongueEval by remember { mutableStateOf<TongueEvaluation?>(null) }

    // Live Visual Mouth Tracking Metrics
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
    val onnxEngine = remember { OnnxAcousticEngine(context) }
    var isLiveRecording by remember { mutableStateOf(false) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pronunciation Coach (Neural Pipeline)", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Target Word Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF222233))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Target Word", fontSize = 13.sp, color = Color.Gray)
                    Text("funk", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4E95FF))
                    Text("/fʌŋk/", fontSize = 18.sp, color = Color(0xFFAAAAAA))
                }
            }

            // Real CameraX / ML Kit Mouth Tracking Viewport
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
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
                    }

                    // Compose Dynamic Mouth Tracking HUD Overlay (Mesh, Reticle, Caliper)
                    MouthTrackingOverlay(
                        metrics = liveMouthMetrics,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Live Vision HUD Metrics Bar
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xDD12121A)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
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

            // Real Live Recording Controls
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("真机实时声学与口唇测评", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
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
                                    val neuralResult = withContext(Dispatchers.Default) {
                                        onnxEngine.inferAudio(sample.pcmData)
                                    }
                                    val currentJaw = liveMouthMetrics.jawOpen
                                    val currentRoundness = liveMouthMetrics.lipRoundness

                                    val tongueEval = TongueEstimator.evaluate(
                                        sample.pcmData,
                                        currentJaw,
                                        currentRoundness
                                    )
                                    currentTongueEval = tongueEval

                                    rawResultJson = PronunciationCoreBridge.safeScoreFunkWithTongue(
                                        neuralResult.targetProb,
                                        neuralResult.confusionProb,
                                        currentJaw,
                                        currentRoundness,
                                        tongueEval.height,
                                        tongueEval.backness
                                    )
                                    currentTestMode = "Live: ${neuralResult.dominantPhoneme} [Jaw: ${(currentJaw * 100).toInt()}% | Tongue: ${(tongueEval.height * 100).toInt()}%]"
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
                            if (isLiveRecording) "⏹ 停止录音并测评" else "🎙 按下开始实时录音测评",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Real Physical File Pipeline Verification (Strict Test Artifact Execution)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🧪 物理测试文件真实端到端推理", color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("ONNX Runtime 声学神经网络 + ML Kit 视觉神经网络 + 声学反演舌位", color = Color(0xFF81C784), fontSize = 11.sp)
                    Spacer(Modifier.height(10.dp))

                    // Row 1: Good Golden Test
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                currentTestMode = "TC-01: 黄金标准 (funk_good.wav + standard_face.jpg)"
                                val pcm = TestArtifactPipeline.readPcmFromRawResource(context, R.raw.funk_good)
                                val acousticNeural = withContext(Dispatchers.Default) { onnxEngine.inferAudio(pcm) }
                                val vision = TestArtifactPipeline.analyzeTestImageFromAssets(
                                    context, "test_images/face_standard_caret.jpg"
                                )
                                liveMouthMetrics = vision
                                val tongueEval = TongueEstimator.evaluate(pcm, vision.jawOpen, vision.lipRoundness)
                                currentTongueEval = tongueEval

                                rawResultJson = PronunciationCoreBridge.safeScoreFunkWithTongue(
                                    acousticNeural.targetProb,
                                    acousticNeural.confusionProb,
                                    vision.jawOpen,
                                    vision.lipRoundness,
                                    tongueEval.height,
                                    tongueEval.backness
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🟢 [实测 TC-01] 黄金标准 (funk_good.wav)", fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(8.dp))

                    // Row 2: Over-opened /ɑ/ Confusion Test
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                currentTestMode = "TC-02: 混淆发音 (funk_ah_like.wav + wide_open_face.jpg)"
                                val pcm = TestArtifactPipeline.readPcmFromRawResource(context, R.raw.funk_ah_like)
                                val acousticNeural = withContext(Dispatchers.Default) { onnxEngine.inferAudio(pcm) }
                                val vision = TestArtifactPipeline.analyzeTestImageFromAssets(
                                    context, "test_images/face_wide_open_ah.jpg"
                                )
                                liveMouthMetrics = vision
                                val tongueEval = TongueEstimator.evaluate(pcm, vision.jawOpen, vision.lipRoundness)
                                currentTongueEval = tongueEval

                                rawResultJson = PronunciationCoreBridge.safeScoreFunkWithTongue(
                                    acousticNeural.targetProb,
                                    acousticNeural.confusionProb,
                                    vision.jawOpen,
                                    vision.lipRoundness,
                                    tongueEval.height,
                                    tongueEval.backness
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🔴 [实测 TC-02] 混淆发音 (funk_ah_like.wav)", fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(8.dp))

                    // Row 3: Chinese Mismatch Negative Test
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                currentTestMode = "TC-03: 对抗负样本 (chinese_mismatch.wav + closed_mouth.jpg)"
                                val pcm = TestArtifactPipeline.readPcmFromRawResource(context, R.raw.chinese_mismatch)
                                val acousticNeural = withContext(Dispatchers.Default) { onnxEngine.inferAudio(pcm) }
                                val vision = TestArtifactPipeline.analyzeTestImageFromAssets(
                                    context, "test_images/face_closed_mouth.jpg"
                                )
                                liveMouthMetrics = vision
                                val tongueEval = TongueEstimator.evaluate(pcm, vision.jawOpen, vision.lipRoundness)
                                currentTongueEval = tongueEval

                                rawResultJson = PronunciationCoreBridge.safeScoreFunkWithTongue(
                                    acousticNeural.targetProb,
                                    acousticNeural.confusionProb,
                                    vision.jawOpen,
                                    vision.lipRoundness,
                                    tongueEval.height,
                                    tongueEval.backness
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("⚠️ [实测 TC-03] 对抗负样本 (chinese_mismatch.wav)", fontSize = 13.sp)
                    }
                }
            }

            // Score & Analysis Results Display
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
                                    color = when {
                                        overall >= 80 -> Color(0xFF4CAF50)
                                        overall >= 60 -> Color(0xFFFFB300)
                                        else -> Color(0xFFFF5252)
                                    }
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

                    // Articulatory Tongue Position Card
                    currentTongueEval?.let { tEval ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2333))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "👅 声学反演舌位分析 (Tongue Inversion)",
                                        color = Color(0xFF80D8FF),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (tEval.score >= 80) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                                    ) {
                                        Text(
                                            "得分 ${tEval.score}",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("舌位高度 (Height)", color = Color.Gray, fontSize = 11.sp)
                                        Text(
                                            "${(tEval.height * 100).toInt()}%",
                                            color = if (tEval.height in 0.40f..0.70f) Color(0xFF00E676) else Color(0xFFFF5252),
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text("标准区间: 45% - 65%", color = Color.Gray, fontSize = 10.sp)
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("舌位前后 (Backness)", color = Color.Gray, fontSize = 11.sp)
                                        Text(
                                            "${(tEval.backness * 100).toInt()}%",
                                            color = if (tEval.backness in 0.35f..0.60f) Color(0xFF00E676) else Color(0xFFFFD54F),
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text("标准区间: 38% - 58%", color = Color.Gray, fontSize = 10.sp)
                                    }
                                }

                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "🗣️ 舌位发音诊断: ${tEval.guidance}",
                                    color = Color(0xFFE0E0E0),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
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
