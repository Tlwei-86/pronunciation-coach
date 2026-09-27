package com.pronunciationcoach.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pronunciationcoach.app.core.PronunciationCoreBridge
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen() {
    var rawResultJson by remember { mutableStateOf<String?>(null) }
    var currentTestMode by remember { mutableStateOf("Ready") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pronunciation Coach V0.1", fontWeight = FontWeight.Bold) },
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

            // Camera / Viseme Preview Area
            Card(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B26))
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📷 CameraX / Viseme Viewport", color = Color.LightGray, fontSize = 16.sp)
                        Text("Mouth Geometry & Landmark Detection", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }

            // Quick Verification Test Buttons (Agent-Friendly Mock / Physical Test)
            Text("Simulate / Live Test Input", color = Color.White, fontWeight = FontWeight.SemiBold)
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
                                                    if (isPrimary) Color(0x33FF5252) else Color(0x1FFFFFFF),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(ph, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Text("\$sc", color = if (sc >= 80) Color(0xFF81C784) else Color(0xFFFF8A80), fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // AI Coach Guidance Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2838))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("💡 AI Coach Guidance", color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            if (guidanceArray != null) {
                                for (i in 0 until guidanceArray.length()) {
                                    Text("• \${guidanceArray.getString(i)}", color = Color.White, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
