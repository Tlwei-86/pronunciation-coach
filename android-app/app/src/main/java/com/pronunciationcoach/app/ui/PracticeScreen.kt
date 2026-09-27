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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pronunciationcoach.app.domain.DEFAULT_PRACTICE_WORDS
import com.pronunciationcoach.app.domain.EvaluationStatus
import com.pronunciationcoach.app.domain.PhonemeEvaluation
import com.pronunciationcoach.app.domain.PracticeWord
import com.pronunciationcoach.app.ui.components.MouthTrackingOverlay
import com.pronunciationcoach.app.vision.LiveFaceMouthMetrics
import com.pronunciationcoach.app.vision.RealFaceLandmarkAnalyzer
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize ViewModel if not provided
    val vm = viewModel ?: remember { PracticeViewModel(context = context.applicationContext) }
    val uiState by vm.uiState.collectAsState()

    // Live Visual Mouth Tracking Metrics
    var liveMouthMetrics by remember {
        mutableStateOf(
            LiveFaceMouthMetrics(
                jawOpen = 0.38f,
                lipRoundness = 0.15f,
                mouthWidthNormalized = 0.48f,
                isFaceDetected = false,
                statusText = "对准前置镜头"
            )
        )
    }

    // Word Switcher Dialog State
    var showWordSwitcherDialog by remember { mutableStateOf(false) }

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

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "英语发音私教",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF2A2B3D)
                        ) {
                            Text(
                                uiState.selectedCategory,
                                fontSize = 11.sp,
                                color = Color(0xFF90CAF9),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF14141F),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0D0D14)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ========================================================
            // SECTION 1: Target Word Card
            // ========================================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B2A)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.targetWord,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF60A5FA),
                        letterSpacing = 1.sp
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = uiState.targetIpa,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE2E8F0)
                    )

                    if (uiState.wordDescription.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = uiState.wordDescription,
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Action buttons: Standard Pronunciation & Word Switcher
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { vm.playStandardPronunciation() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.isPlayingStandard) Color(0xFF1E3A8A) else Color(0xFF2563EB)
                            )
                        ) {
                            Text(
                                if (uiState.isPlayingStandard) "🔊 播放中..." else "🔊 听标准发音",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = { showWordSwitcherDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF93C5FD)),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(Color(0xFF3B82F6), Color(0xFF60A5FA))))
                        ) {
                            Text(
                                "切换练习词汇",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // ========================================================
            // SECTION 2: Mirror Camera View (Pure Front Camera)
            // ========================================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141420))
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
                                                    vm.updateFaceMetrics(metrics.jawOpen, metrics.lipRoundness, metrics.isFaceDetected)
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
                                        // Ignore preview bind error in non-camera environments
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(20.dp))
                        )
                    }

                    // Subtle Mouth Tracking Contour HUD
                    MouthTrackingOverlay(
                        metrics = liveMouthMetrics,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Minimal status badge at bottom center
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 12.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xCC0D0D14),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.horizontalGradient(
                                    if (uiState.isFaceDetected) listOf(Color(0xFF10B981), Color(0xFF059669))
                                    else listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                                )
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (uiState.isFaceDetected) Color(0xFF10B981) else Color(0xFFF59E0B),
                                    modifier = Modifier.size(8.dp)
                                ) {}
                                Text(
                                    text = if (uiState.isFaceDetected) "面部已对准" else "请将面部对准前置镜头",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // ========================================================
            // SECTION 3: Single Prominent Action Button
            // ========================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isEvaluating) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF3B82F6),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            "多模态智能评测中...",
                            color = Color(0xFF93C5FD),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    val recordingColor by animateColorAsState(
                        targetValue = if (uiState.isRecording) Color(0xFFDC2626) else Color(0xFF2563EB),
                        label = "btnColor"
                    )

                    Button(
                        onClick = { vm.toggleLiveRecording() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = recordingColor),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp)
                    ) {
                        Text(
                            text = if (uiState.isRecording) "⏹ 说完了，查看评分" else "🎙️ 点击开始发音",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // ========================================================
            // SECTION 4: Minimalist Diagnosis Result Card
            // ========================================================
            uiState.evaluationResult?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B2A))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        // Top row: Score + Playback
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        "${result.overallScore}",
                                        fontSize = 40.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = when {
                                            result.overallScore >= 80 -> Color(0xFF10B981)
                                            result.overallScore >= 60 -> Color(0xFFF59E0B)
                                            else -> Color(0xFFEF4444)
                                        }
                                    )
                                    Text(
                                        "分",
                                        fontSize = 16.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }
                                Text(
                                    when {
                                        result.overallScore >= 85 -> "发音非常标准！"
                                        result.overallScore >= 70 -> "发音良好，个别音素需微调"
                                        else -> "存在明显发音动作偏差"
                                    },
                                    fontSize = 13.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                            }

                            // User voice replay button
                            if (uiState.hasUserRecording) {
                                FilledTonalButton(
                                    onClick = { vm.playUserRecording() },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (uiState.isPlayingUserRecording) Color(0xFF4C1D95) else Color(0xFF312E81)
                                    )
                                ) {
                                    Text(
                                        if (uiState.isPlayingUserRecording) "▶ 播放中..." else "▶ 听我的发音",
                                        fontSize = 13.sp,
                                        color = Color(0xFFC7D2FE)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Interactive Phoneme Chips Strip
                        Text(
                            "点击音标查看器官动作指南:",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            for (p in result.phonemeEvaluations) {
                                val isSelected = (p.symbol == uiState.selectedPhonemeSymbol)
                                val chipBorderColor = when {
                                    isSelected -> Color(0xFF60A5FA)
                                    p.score >= 80 -> Color(0xFF059669)
                                    p.score >= 60 -> Color(0xFFD97706)
                                    else -> Color(0xFFDC2626)
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) Color(0xFF252945) else Color(0xFF141422),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(
                                        brush = Brush.horizontalGradient(listOf(chipBorderColor, chipBorderColor))
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { vm.selectPhoneme(p.symbol) }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                p.ipa,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            if (p.isPrimaryIssue) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color(0xFFEF4444),
                                                    modifier = Modifier.size(6.dp)
                                                ) {}
                                            }
                                        }

                                        Text(
                                            "${p.score}分",
                                            fontSize = 12.sp,
                                            color = when {
                                                p.score >= 80 -> Color(0xFF34D399)
                                                p.score >= 60 -> Color(0xFFFBBF24)
                                                else -> Color(0xFFF87171)
                                            },
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ========================================================
                // SECTION 5: Dedicated Articulatory Guidance Card
                // ========================================================
                val selectedPhoneme = uiState.selectedPhoneme
                if (selectedPhoneme != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131A2A)),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(Color(0xFF1D4ED8), Color(0xFF3B82F6)))
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "🎯 【${selectedPhoneme.ipa} ${selectedPhoneme.name}】发音动作要领",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF93C5FD)
                                )
                                if (selectedPhoneme.isPrimaryIssue) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF7F1D1D)
                                    ) {
                                        Text(
                                            "主要改进音",
                                            fontSize = 11.sp,
                                            color = Color(0xFFFCA5A5),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // Standard overall physiological action
                            if (selectedPhoneme.standardAction.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF1B243B)
                                ) {
                                    Text(
                                        "📌 动作总则: ${selectedPhoneme.standardAction}",
                                        fontSize = 13.sp,
                                        color = Color(0xFFE2E8F0),
                                        lineHeight = 18.sp,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            // Typical error cause & tip
                            if (selectedPhoneme.typicalErrorCause.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF261D24)
                                ) {
                                    Text(
                                        "⚠️ 纠错要领: ${selectedPhoneme.typicalErrorCause}",
                                        fontSize = 13.sp,
                                        color = Color(0xFFF9A8D4),
                                        lineHeight = 18.sp,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            // Physiological Action Cues (Tongue, Lips, Airflow)
                            if (selectedPhoneme.actionCues.isNotEmpty()) {
                                Text(
                                    "器官发音细节口诀:",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8),
                                    fontWeight = FontWeight.SemiBold
                                )

                                for (cue in selectedPhoneme.actionCues) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val icon = when {
                                            cue.contains("舌") -> "👅"
                                            cue.contains("唇") || cue.contains("齿") || cue.contains("口型") -> "👄"
                                            cue.contains("气流") || cue.contains("声门") -> "💨"
                                            cue.contains("声带") || cue.contains("共鸣") -> "📯"
                                            else -> "🔹"
                                        }
                                        Text(icon, fontSize = 14.sp)
                                        Text(
                                            cue,
                                            fontSize = 13.sp,
                                            color = Color(0xFFCBD5E1),
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ========================================================
    // Word Switcher Dialog
    // ========================================================
    if (showWordSwitcherDialog) {
        var customWordInput by remember { mutableStateOf("") }
        var selectedCategoryFilter by remember { mutableStateOf("全部") }

        val categories = remember {
            listOf("全部") + DEFAULT_PRACTICE_WORDS.map { it.category }.distinct()
        }

        val filteredWords = remember(selectedCategoryFilter) {
            if (selectedCategoryFilter == "全部") DEFAULT_PRACTICE_WORDS
            else DEFAULT_PRACTICE_WORDS.filter { it.category == selectedCategoryFilter }
        }

        AlertDialog(
            onDismissRequest = { showWordSwitcherDialog = false },
            title = {
                Text(
                    "选择或自定义练习词汇",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Custom word input row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customWordInput,
                            onValueChange = { customWordInput = it },
                            placeholder = { Text("输入任意英文单词", fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Button(
                            onClick = {
                                if (customWordInput.trim().isNotEmpty()) {
                                    vm.setCustomWord(customWordInput)
                                    showWordSwitcherDialog = false
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("确定", fontSize = 13.sp)
                        }
                    }

                    // Category filters
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (cat in categories) {
                            FilterChip(
                                selected = (selectedCategoryFilter == cat),
                                onClick = { selectedCategoryFilter = cat },
                                label = { Text(cat, fontSize = 11.sp) }
                            )
                        }
                    }

                    // Curated words list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredWords) { item ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (item.word == uiState.targetWord) Color(0xFF253352) else Color(0xFF1E1E2C),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.selectPracticeWord(item)
                                        showWordSwitcherDialog = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                item.word,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                item.ipa,
                                                fontSize = 13.sp,
                                                color = Color(0xFF90CAF9)
                                            )
                                        }
                                        if (item.description.isNotEmpty()) {
                                            Text(
                                                item.description,
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }

                                    if (item.word == uiState.targetWord) {
                                        Text("✓ 当前", fontSize = 12.sp, color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWordSwitcherDialog = false }) {
                    Text("关闭", color = Color(0xFF90CAF9))
                }
            },
            containerColor = Color(0xFF171724)
        )
    }
}
