package com.pronunciationcoach.app.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class PhonemeTemporalAlignerTest {

    @Test
    fun testDictionaryLookup() {
        val thinkPhonemes = PhonemeTemporalAligner.getPhonemesForWord("think")
        assertEquals(listOf("θ", "ɪ", "ŋ", "k"), thinkPhonemes)

        val funkPhonemes = PhonemeTemporalAligner.getPhonemesForWord("funk")
        assertEquals(listOf("f", "ʌ", "ŋ", "k"), funkPhonemes)

        val redPhonemes = PhonemeTemporalAligner.getPhonemesForWord("red")
        assertEquals(listOf("r", "e", "d"), redPhonemes)

        val seePhonemes = PhonemeTemporalAligner.getPhonemesForWord("see")
        assertEquals(listOf("s", "iː"), seePhonemes)

        // Case-insensitivity test
        val upperWord = PhonemeTemporalAligner.getPhonemesForWord("THINK")
        assertEquals(listOf("θ", "ɪ", "ŋ", "k"), upperWord)

        // Unknown fallback
        val unknown = PhonemeTemporalAligner.getPhonemesForWord("xyz")
        assertTrue("Unknown word should fall back to character tokens", unknown.isNotEmpty())
    }

    @Test
    fun testPhonemeClassification() {
        assertEquals(PhonemeType.VOWEL, PhonemeTemporalAligner.classifyPhoneme("iː"))
        assertEquals(PhonemeType.VOWEL, PhonemeTemporalAligner.classifyPhoneme("ʌ"))
        assertEquals(PhonemeType.VOWEL, PhonemeTemporalAligner.classifyPhoneme("ɑ"))

        assertEquals(PhonemeType.DIPHTHONG, PhonemeTemporalAligner.classifyPhoneme("eɪ"))
        assertEquals(PhonemeType.DIPHTHONG, PhonemeTemporalAligner.classifyPhoneme("aʊ"))

        assertEquals(PhonemeType.PLOSIVE, PhonemeTemporalAligner.classifyPhoneme("p"))
        assertEquals(PhonemeType.PLOSIVE, PhonemeTemporalAligner.classifyPhoneme("t"))
        assertEquals(PhonemeType.PLOSIVE, PhonemeTemporalAligner.classifyPhoneme("k"))

        assertEquals(PhonemeType.FRICATIVE, PhonemeTemporalAligner.classifyPhoneme("s"))
        assertEquals(PhonemeType.FRICATIVE, PhonemeTemporalAligner.classifyPhoneme("θ"))
        assertEquals(PhonemeType.FRICATIVE, PhonemeTemporalAligner.classifyPhoneme("f"))

        assertEquals(PhonemeType.AFFRICATE, PhonemeTemporalAligner.classifyPhoneme("tʃ"))
        assertEquals(PhonemeType.AFFRICATE, PhonemeTemporalAligner.classifyPhoneme("dʒ"))

        assertEquals(PhonemeType.NASAL, PhonemeTemporalAligner.classifyPhoneme("m"))
        assertEquals(PhonemeType.NASAL, PhonemeTemporalAligner.classifyPhoneme("ŋ"))

        assertEquals(PhonemeType.APPROXIMANT, PhonemeTemporalAligner.classifyPhoneme("l"))
        assertEquals(PhonemeType.APPROXIMANT, PhonemeTemporalAligner.classifyPhoneme("r"))
    }

    @Test
    fun testAlignPhonemesSyntheticAudio() {
        val sampleRate = 16000
        val durationSec = 1.0f
        val totalSamples = (sampleRate * durationSec).toInt()
        val pcm = ByteArray(totalSamples * 2)

        // Synthesize 440Hz tone with envelope
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val amp = (sin(2.0 * Math.PI * 440.0 * t) * 15000.0).toInt().toShort()
            pcm[i * 2] = (amp.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((amp.toInt() shr 8) and 0xFF).toByte()
        }

        val alignment = PhonemeTemporalAligner.alignPhonemes(
            pcmData = pcm,
            sampleRate = sampleRate,
            targetWord = "funk"
        )

        assertEquals("Should align 4 phonemes for 'funk'", 4, alignment.segments.size)
        assertEquals("f", alignment.segments[0].phoneme)
        assertEquals("ʌ", alignment.segments[1].phoneme)
        assertEquals("ŋ", alignment.segments[2].phoneme)
        assertEquals("k", alignment.segments[3].phoneme)

        // Monotonicity & bounds check
        for (i in alignment.segments.indices) {
            val seg = alignment.segments[i]
            assertTrue("Segment start must be >= 0", seg.startMs >= 0)
            assertTrue("Segment end must be > start", seg.endMs > seg.startMs)
            assertTrue("PCM chunk should not be empty", seg.pcmChunk.isNotEmpty())

            if (i > 0) {
                val prev = alignment.segments[i - 1]
                assertTrue("Segments must be ordered monotonically", seg.startMs >= prev.startMs)
            }
        }
    }

    @Test
    fun testEmptyOrSilenceAudioFallback() {
        val sampleRate = 16000
        val pcmSilence = ByteArray(16000 * 2) // 1 second of silence

        val alignment = PhonemeTemporalAligner.alignPhonemes(
            pcmData = pcmSilence,
            sampleRate = sampleRate,
            targetWord = "think"
        )

        assertEquals(4, alignment.segments.size)
        assertEquals("θ", alignment.segments[0].phoneme)
        assertEquals("k", alignment.segments[3].phoneme)
        assertTrue(alignment.segments.all { it.pcmChunk.isNotEmpty() })
    }
}
