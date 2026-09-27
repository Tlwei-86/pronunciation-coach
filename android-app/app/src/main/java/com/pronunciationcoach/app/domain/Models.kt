package com.pronunciationcoach.app.domain

import com.pronunciationcoach.app.core.PronunciationCoreBridge
import org.json.JSONArray
import org.json.JSONObject

/**
 * Status of phoneme evaluation.
 */
enum class EvaluationStatus {
    GOOD,
    WARNING,
    ERROR
}

/**
 * Evaluation of an individual phoneme in the target word,
 * with complete Chinese articulatory guidance and physiological action cues.
 */
data class PhonemeEvaluation(
    val symbol: String,              // e.g. "f", "ʌ", "ŋ", "k", "θ"
    val ipa: String,                 // e.g. "/f/", "/ʌ/", "/ŋ/", "/k/", "/θ/"
    val score: Int,                  // 0..100
    val status: EvaluationStatus,
    val name: String = "",
    val standardAction: String = "",
    val typicalErrorCause: String = "",
    val actionCues: List<String> = emptyList(),
    val isPrimaryIssue: Boolean = false,
    val guidanceLips: String = "",
    val guidanceTeeth: String = "",
    val guidanceTongue: String = "",
    val guidanceAirflow: String = "",
    val guidanceVocalCords: String = "",
    val detectedPhoneme: String? = null,
    val note: String? = null,
    val visualDeviation: String? = null
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("symbol", symbol)
            put("ipa", ipa)
            put("score", score)
            put("status", status.name)
            put("name", name)
            put("standardAction", standardAction)
            put("typicalErrorCause", typicalErrorCause)
            val cuesArr = JSONArray()
            actionCues.forEach { cuesArr.put(it) }
            put("actionCues", cuesArr)
            put("isPrimaryIssue", isPrimaryIssue)
            put("guidanceLips", guidanceLips)
            put("guidanceTeeth", guidanceTeeth)
            put("guidanceTongue", guidanceTongue)
            put("guidanceAirflow", guidanceAirflow)
            put("guidanceVocalCords", guidanceVocalCords)
            put("detectedPhoneme", detectedPhoneme ?: JSONObject.NULL)
            put("note", note ?: JSONObject.NULL)
            put("visualDeviation", visualDeviation ?: JSONObject.NULL)
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): PhonemeEvaluation {
            val rawSym = if (obj.has("symbol")) obj.optString("symbol") else obj.optString("phoneme", "")
            val sym = rawSym.replace("/", "").trim()
            val sc = obj.optInt("score", 0)
            val ipaStr = if (obj.has("ipa")) obj.optString("ipa") else "/$sym/"
            val defaultStatus = if (sc >= 80) EvaluationStatus.GOOD else if (sc >= 60) EvaluationStatus.WARNING else EvaluationStatus.ERROR
            val status = try {
                if (obj.has("status")) {
                    val sStr = obj.optString("status").uppercase()
                    EvaluationStatus.valueOf(sStr)
                } else defaultStatus
            } catch (e: Exception) {
                defaultStatus
            }

            // Consult knowledge base fallback if fields missing
            val kbInfo = PronunciationCoreBridge.PHONEME_KNOWLEDGE_BASE[sym]

            val nameStr = when {
                obj.has("name") -> obj.optString("name")
                kbInfo != null -> kbInfo.name
                else -> "音标 $sym"
            }

            val stdAction = when {
                obj.has("standard_action") -> obj.optString("standard_action")
                obj.has("standardAction") -> obj.optString("standardAction")
                kbInfo != null -> kbInfo.standardAction
                else -> ""
            }

            val errorCause = when {
                obj.has("typical_error_cause") -> obj.optString("typical_error_cause")
                obj.has("typicalErrorCause") -> obj.optString("typicalErrorCause")
                obj.has("cause") -> obj.optString("cause")
                kbInfo != null -> kbInfo.typicalErrorCause
                else -> ""
            }

            val cues = mutableListOf<String>()
            val cuesArr = obj.optJSONArray("action_cues") ?: obj.optJSONArray("actionCues")
            if (cuesArr != null) {
                for (i in 0 until cuesArr.length()) {
                    cues.add(cuesArr.getString(i))
                }
            } else if (kbInfo != null) {
                cues.addAll(kbInfo.actionCues)
            }

            val isPrimary = obj.optBoolean("is_primary_issue", obj.optBoolean("isPrimaryIssue", false))

            val guidanceObj = obj.optJSONObject("guidance")
            val lips = guidanceObj?.optString("lips", "") ?: obj.optString("guidanceLips", "")
            val teeth = guidanceObj?.optString("teeth", "") ?: obj.optString("guidanceTeeth", "")
            val tongue = guidanceObj?.optString("tongue", "") ?: obj.optString("guidanceTongue", "")
            val airflow = guidanceObj?.optString("airflow", "") ?: obj.optString("guidanceAirflow", "")
            val vocalCords = guidanceObj?.optString("vocal_cords", guidanceObj.optString("vocalCords", ""))
                ?: obj.optString("guidanceVocalCords", "")

            val noteStr = when {
                !obj.isNull("note") -> obj.optString("note")
                !obj.isNull("notes") -> obj.optString("notes")
                else -> null
            }
            val detected = when {
                !obj.isNull("detectedPhoneme") -> obj.optString("detectedPhoneme")
                !obj.isNull("detected_phoneme") -> obj.optString("detected_phoneme")
                else -> null
            }

            return PhonemeEvaluation(
                symbol = sym,
                ipa = ipaStr,
                score = sc,
                status = status,
                name = nameStr,
                standardAction = stdAction,
                typicalErrorCause = errorCause,
                actionCues = cues,
                isPrimaryIssue = isPrimary,
                guidanceLips = lips,
                guidanceTeeth = teeth,
                guidanceTongue = tongue,
                guidanceAirflow = airflow,
                guidanceVocalCords = vocalCords,
                detectedPhoneme = detected,
                note = noteStr,
                visualDeviation = if (obj.isNull("visualDeviation")) null else obj.optString("visualDeviation")
            )
        }
    }
}

/**
 * Practice Word model representing a target word with category and IPA.
 */
data class PracticeWord(
    val word: String,
    val ipa: String,
    val category: String,
    val description: String = ""
)

/**
 * Curated practice word library organized by phonetic challenge families.
 */
val DEFAULT_PRACTICE_WORDS: List<PracticeWord> = listOf(
    // 齿间擦音 /θ, ð/
    PracticeWord("think", "/θɪŋk/", "齿间擦音 /θ, ð/", "清齿间音：舌尖轻探门牙，吹出柔和清气流"),
    PracticeWord("this", "/ðɪs/", "齿间擦音 /θ, ð/", "浊齿间音：舌尖探出门牙，声带强烈震动发麻"),
    PracticeWord("thank", "/θæŋk/", "齿间擦音 /θ, ð/", "清齿间音：舌尖不可缩在牙后"),
    PracticeWord("mother", "/ˈmʌðər/", "齿间擦音 /θ, ð/", "词中浊齿间音：平滑伸舌震动"),

    // 唇齿擦音 /f, v/
    PracticeWord("funk", "/fʌŋk/", "唇齿擦音 /f, v/", "清唇齿音：上门牙轻咬下唇内侧，不可闭唇"),
    PracticeWord("van", "/væn/", "唇齿擦音 /f, v/", "浊唇齿音：上牙咬下唇，声带强烈震动发麻"),
    PracticeWord("face", "/feɪs/", "唇齿擦音 /f, v/", "清唇齿音：摩擦气流丝丝吹出"),
    PracticeWord("voice", "/vɔɪs/", "唇齿擦音 /f, v/", "浊唇齿音：严禁噘成圆唇 /w/"),

    // 卷舌与边音 /r, l/
    PracticeWord("red", "/red/", "卷舌与边音 /r, l/", "美式卷舌：舌尖向后上方悬空卷起，严禁碰牙槽"),
    PracticeWord("light", "/laɪt/", "卷舌与边音 /r, l/", "舌侧边音：舌尖坚决抵住上牙龈中心不动"),
    PracticeWord("right", "/raɪt/", "卷舌与边音 /r, l/", "卷舌音对比：舌尖高高悬空，喉音饱满"),
    PracticeWord("look", "/lʊk/", "卷舌与边音 /r, l/", "舌侧边音：气流顺舌头两侧边缘滑出"),

    // 前元音与微笑音 /iː, ɪ, e, æ/
    PracticeWord("sheep", "/ʃiːp/", "前元音与微笑音", "长元音：嘴角用力向两侧拉开微笑，高舌位"),
    PracticeWord("ship", "/ʃɪp/", "前元音与微笑音", "短元音：嘴角自然放松，下颌微松一指宽"),
    PracticeWord("bed", "/bed/", "前元音与微笑音", "前半开元音：嘴唇扁平展开，下巴落一指半宽"),
    PracticeWord("bad", "/bæd/", "前元音与微笑音", "大开音：下巴充分向下沉开两指宽，舌尖抵下齿"),

    // 央后元音与口型开度 /ʌ, ɑː, uː, ʊ/
    PracticeWord("cup", "/kʌp/", "央后元音 /ʌ, ɑː, uː, ʊ/", "央半低音：嘴唇完全放松不圆唇，短促发力"),
    PracticeWord("cap", "/kæp/", "央后元音 /ʌ, ɑː, uː, ʊ/", "大开口对比：下巴明显下落两指宽"),
    PracticeWord("father", "/ˈfɑːðər/", "央后元音 /ʌ, ɑː, uː, ʊ/", "大开后元音：如同医生看扁桃体彻底打开下巴"),
    PracticeWord("boot", "/buːt/", "央后元音 /ʌ, ɑː, uː, ʊ/", "紧后高元音：双唇缩至最小圆孔明显向前噘起"),
    PracticeWord("put", "/pʊt/", "央后元音 /ʌ, ɑː, uː, ʊ/", "松后半高音：双唇微圆肌肉不紧绷，短促利落"),

    // 塞音爆破与清浊 /p, b, t, d, k, g/
    PracticeWord("pat", "/pæt/", "塞音爆破 /p, b, t, d, k, g/", "清双唇塞音：上下唇紧抿憋气瞬间弹开"),
    PracticeWord("bat", "/bæt/", "塞音爆破 /p, b, t, d, k, g/", "浊双唇塞音：双唇弹开瞬间声带用力震动"),
    PracticeWord("tap", "/tæp/", "塞音爆破 /p, b, t, d, k, g/", "清齿龈塞音：舌尖紧抵上牙龈憋气后弹下"),
    PracticeWord("dad", "/dæd/", "塞音爆破 /p, b, t, d, k, g/", "浊齿龈塞音：舌尖弹下同时声带浊化"),
    PracticeWord("cat", "/kæt/", "塞音爆破 /p, b, t, d, k, g/", "清软腭塞音：舌根用力后抬顶紧软腭憋气爆破"),
    PracticeWord("get", "/ɡet/", "塞音爆破 /p, b, t, d, k, g/", "浊软腭塞音：舌根顶紧软腭带出低沉轰鸣"),

    // 鼻音与舌位阻断 /m, n, ŋ/
    PracticeWord("man", "/mæn/", "鼻音与舌位阻断", "双唇鼻音：上下唇闭紧，气流完全从鼻腔哼出"),
    PracticeWord("no", "/noʊ/", "鼻音与舌位阻断", "齿龈鼻音：舌尖紧贴上牙龈，双唇微开鼻腔发音"),
    PracticeWord("sing", "/sɪŋ/", "鼻音与舌位阻断", "后软腭鼻音：舌根抬起贴死软腭，声音在后鼻腔共鸣"),

    // 双元音滑动
    PracticeWord("say", "/seɪ/", "双元音滑动", "合口双元音：从前中扁唇滑向高位 /ɪ/，提拉下颌"),
    PracticeWord("my", "/maɪ/", "双元音滑动", "大开合双元音：从大开唇滑向微闭，动作果断"),
    PracticeWord("boy", "/bɔɪ/", "双元音滑动", "圆到扁滑动：双唇由圆圈迅速拉伸成扁平微笑"),
    PracticeWord("now", "/naʊ/", "双元音滑动", "聚圆滑动：下颌由大落合拢，嘴唇向前噘圆"),
    PracticeWord("go", "/ɡoʊ/", "双元音滑动", "微圆到紧圆：双唇由松到紧向前拢成小圆圈")
)

/**
 * Acoustic feature representation extracted from audio signal.
 */
data class AcousticFeatures(
    val durationMs: Long,
    val targetPhonemeProb: Float,     // e.g. 0.85 for /ʌ/
    val confusionPhonemeProb: Float,  // e.g. 0.20 for /ɑ/ or /ə/
    val energyRms: Float = 0.05f,
    val pitchHz: Float = 140.0f
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("durationMs", durationMs)
            put("targetPhonemeProb", targetPhonemeProb.toDouble())
            put("confusionPhonemeProb", confusionPhonemeProb.toDouble())
            put("energyRms", energyRms.toDouble())
            put("pitchHz", pitchHz.toDouble())
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): AcousticFeatures {
            return AcousticFeatures(
                durationMs = obj.optLong("durationMs", 0L),
                targetPhonemeProb = obj.optDouble("targetPhonemeProb", 0.0).toFloat(),
                confusionPhonemeProb = obj.optDouble("confusionPhonemeProb", 0.0).toFloat(),
                energyRms = obj.optDouble("energyRms", 0.0).toFloat(),
                pitchHz = obj.optDouble("pitchHz", 0.0).toFloat()
            )
        }
    }
}

/**
 * Visual geometry features (normalized 0.0..1.0) extracted from mouth / face landmarks.
 */
data class VisualFeatures(
    val jawOpen: Float,         // 0.0 = closed, 1.0 = wide open. Target for /ʌ/: ~0.35 - 0.45.
    val lipRoundness: Float,    // 0.0 = unrounded/spread, 1.0 = fully rounded. Target for /ʌ/: unrounded (< 0.30).
    val mouthWidth: Float = 0.5f,
    val mouthHeight: Float = 0.3f,
    val faceDetected: Boolean = true
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("jawOpen", jawOpen.toDouble())
            put("lipRoundness", lipRoundness.toDouble())
            put("mouthWidth", mouthWidth.toDouble())
            put("mouthHeight", mouthHeight.toDouble())
            put("faceDetected", faceDetected)
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): VisualFeatures {
            return VisualFeatures(
                jawOpen = obj.optDouble("jawOpen", 0.0).toFloat(),
                lipRoundness = obj.optDouble("lipRoundness", 0.0).toFloat(),
                mouthWidth = obj.optDouble("mouthWidth", 0.5).toFloat(),
                mouthHeight = obj.optDouble("mouthHeight", 0.3).toFloat(),
                faceDetected = obj.optBoolean("faceDetected", true)
            )
        }
    }
}

/**
 * Comprehensive Evidence Payload submitted to scoring core or reasoning providers.
 */
data class EvidencePayload(
    val targetWord: String = "funk",
    val targetIpa: String = "fʌŋk",
    val acousticFeatures: AcousticFeatures,
    val visualFeatures: VisualFeatures,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("targetWord", targetWord)
            put("targetIpa", targetIpa)
            put("timestamp", timestamp)
            put("acoustic", acousticFeatures.toJsonObject())
            put("visual", visualFeatures.toJsonObject())
        }.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): EvidencePayload {
            val obj = JSONObject(jsonStr)
            val acoustic = AcousticFeatures.fromJsonObject(obj.getJSONObject("acoustic"))
            val visual = VisualFeatures.fromJsonObject(obj.getJSONObject("visual"))
            return EvidencePayload(
                targetWord = obj.optString("targetWord", "funk"),
                targetIpa = obj.optString("targetIpa", "fʌŋk"),
                acousticFeatures = acoustic,
                visualFeatures = visual,
                timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            )
        }
    }
}

/**
 * Result returned by ReasoningProvider or native scoring engine.
 */
data class ReasoningResult(
    val overallScore: Int,
    val acousticScore: Int,
    val visualScore: Int,
    val phonemeEvaluations: List<PhonemeEvaluation>,
    val feedbackSummary: String,
    val actionableTips: List<String>,
    val providerUsed: String,
    val detectedConfusion: String? = null,
    val primaryIssuePhoneme: String? = null
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("overallScore", overallScore)
            put("acousticScore", acousticScore)
            put("visualScore", visualScore)
            put("feedbackSummary", feedbackSummary)
            put("providerUsed", providerUsed)
            put("detectedConfusion", detectedConfusion ?: JSONObject.NULL)
            put("primaryIssuePhoneme", primaryIssuePhoneme ?: JSONObject.NULL)
            val tipsArr = JSONArray()
            actionableTips.forEach { tipsArr.put(it) }
            put("actionableTips", tipsArr)
            val phonemesArr = JSONArray()
            phonemeEvaluations.forEach { phonemesArr.put(it.toJsonObject()) }
            put("phonemeEvaluations", phonemesArr)
        }.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): ReasoningResult {
            val obj = JSONObject(jsonStr)
            val phonemes = mutableListOf<PhonemeEvaluation>()
            val phonemesArr = obj.optJSONArray("phonemeEvaluations")
                ?: obj.optJSONArray("phoneme_scores")
                ?: obj.optJSONArray("phonemes")
            if (phonemesArr != null) {
                for (i in 0 until phonemesArr.length()) {
                    phonemes.add(PhonemeEvaluation.fromJsonObject(phonemesArr.getJSONObject(i)))
                }
            }
            val tips = mutableListOf<String>()
            val tipsArr = obj.optJSONArray("actionableTips") ?: obj.optJSONArray("guidance")
            if (tipsArr != null) {
                for (i in 0 until tipsArr.length()) {
                    val item = tipsArr.opt(i)
                    if (item is String) tips.add(item)
                }
            }
            val overall = when {
                obj.has("overallScore") -> obj.optInt("overallScore")
                obj.has("overall_score") -> obj.optInt("overall_score")
                else -> 0
            }
            val acoustic = when {
                obj.has("acousticScore") -> obj.optInt("acousticScore")
                obj.has("acoustic_accuracy") -> obj.optInt("acoustic_accuracy")
                obj.has("acoustic_score") -> obj.optInt("acoustic_score")
                else -> 0
            }
            val visual = when {
                obj.has("visualScore") -> obj.optInt("visualScore")
                obj.has("visual_accuracy") -> obj.optInt("visual_accuracy")
                obj.has("visual_score") -> obj.optInt("visual_score")
                else -> 0
            }
            val summary = when {
                obj.has("feedbackSummary") -> obj.optString("feedbackSummary")
                obj.has("guidance") && obj.opt("guidance") is String -> obj.optString("guidance")
                obj.has("summary_text") -> obj.optString("summary_text")
                obj.has("feedback_summary") -> obj.optString("feedback_summary")
                else -> ""
            }
            val confusion = when {
                !obj.isNull("detectedConfusion") -> obj.optString("detectedConfusion")
                !obj.isNull("detected_confusion") -> obj.optString("detected_confusion")
                else -> null
            }
            val primaryPhoneme = when {
                !obj.isNull("primary_issue_phoneme") -> obj.optString("primary_issue_phoneme")
                !obj.isNull("primaryIssuePhoneme") -> obj.optString("primaryIssuePhoneme")
                else -> phonemes.minByOrNull { it.score }?.symbol
            }

            return ReasoningResult(
                overallScore = overall,
                acousticScore = acoustic,
                visualScore = visual,
                phonemeEvaluations = phonemes,
                feedbackSummary = summary,
                actionableTips = tips,
                providerUsed = obj.optString("providerUsed", "Pronunciation Core Engine"),
                detectedConfusion = confusion,
                primaryIssuePhoneme = primaryPhoneme
            )
        }
    }
}

/**
 * Raw audio sample data captured from Microphone or loaded from WAV.
 */
data class AudioSampleData(
    val sampleRate: Int = 16000,
    val channelCount: Int = 1,
    val pcmData: ByteArray,
    val durationMs: Long,
    val sourceDescription: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioSampleData
        return pcmData.contentEquals(other.pcmData) &&
                sampleRate == other.sampleRate &&
                channelCount == other.channelCount &&
                durationMs == other.durationMs
    }

    override fun hashCode(): Int {
        var result = sampleRate
        result = 31 * result + channelCount
        result = 31 * result + pcmData.contentHashCode()
        result = 31 * result + durationMs.hashCode()
        return result
    }
}

/**
 * Single video frame metric.
 */
data class VisualFrameMetrics(
    val timestampMs: Long,
    val jawOpen: Float,
    val lipRoundness: Float,
    val mouthWidth: Float = 0.5f,
    val mouthHeight: Float = 0.3f,
    val faceDetected: Boolean = true
)

/**
 * Aggregated visual sample data from a recording or video file.
 */
data class VisualSampleData(
    val frameMetricsList: List<VisualFrameMetrics>,
    val averageJawOpen: Float,
    val averageLipRoundness: Float,
    val sourceDescription: String
)
