package com.pronunciationcoach.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Low-latency audio playback engine for the user's latest recorded voice.
 * Caches incoming raw PCM data into a valid 44-byte WAV header file at `cache/last_recording.wav`
 * and provides instant playback via high-performance AudioTrack with MediaPlayer fallback.
 */
class UserAudioPlaybackEngine(private val context: Context) : IUserAudioPlaybackEngine {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var playbackJob: Job? = null
    private var activeAudioTrack: AudioTrack? = null
    private var activeMediaPlayer: MediaPlayer? = null

    @Volatile
    override var isPlaying: Boolean = false
        private set

    private var lastRecordedPcm: ByteArray? = null

    val cacheWavFile: File
        get() {
            val cacheDir = context.cacheDir
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            return File(cacheDir, "last_recording.wav")
        }

    /**
     * Checks if a user recording is present in memory or on disk.
     */
    override fun hasLastRecording(): Boolean {
        return (lastRecordedPcm != null && lastRecordedPcm!!.isNotEmpty()) ||
                (cacheWavFile.exists() && cacheWavFile.length() > 44)
    }

    /**
     * Gets the cached WAV file if it exists.
     */
    override fun getLastRecordingFile(): File? {
        return if (hasLastRecording() && cacheWavFile.exists()) cacheWavFile else null
    }

    /**
     * Caches raw PCM byte array to `cache/last_recording.wav` with a complete 44-byte WAV header.
     */
    override fun saveRecording(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): File? {
        lastRecordedPcm = pcmData
        val outFile = cacheWavFile

        try {
            FileOutputStream(outFile).use { fos ->
                val header = createWavHeader(
                    totalAudioLen = pcmData.size.toLong(),
                    totalDataLen = (pcmData.size + 36).toLong(),
                    longSampleRate = sampleRate.toLong(),
                    channels = channels,
                    byteRate = (sampleRate * channels * bitsPerSample / 8).toLong(),
                    bitsPerSample = bitsPerSample
                )
                fos.write(header)
                fos.write(pcmData)
                fos.flush()
            }
            return outFile
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Plays the last recorded user audio. Prefers low-latency AudioTrack from cached memory,
     * falling back to MediaPlayer with the cached WAV file.
     */
    override fun playLastRecording(
        onStart: (() -> Unit)?,
        onComplete: (() -> Unit)?
    ) {
        val pcm = lastRecordedPcm
        if (pcm != null && pcm.isNotEmpty()) {
            playPcmData(pcm, 16000, onStart, onComplete)
            return
        }

        val wavFile = cacheWavFile
        if (wavFile.exists() && wavFile.length() > 44) {
            playWavFile(wavFile, onStart, onComplete)
            return
        }

        onComplete?.invoke()
    }

    /**
     * Direct low-latency PCM playback using AudioTrack.
     */
    fun playPcmData(
        pcmData: ByteArray,
        sampleRate: Int = 16000,
        onStart: (() -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        stopPlayback()
        isPlaying = true

        playbackJob = scope.launch {
            var track: AudioTrack? = null
            try {
                withContext(Dispatchers.Main) { onStart?.invoke() }

                val minBufSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val bufferSize = maxOf(minBufSize, pcmData.size)

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val audioFormat = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                track = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                activeAudioTrack = track
                track.play()

                var offset = 0
                val chunkSize = 2048
                while (offset < pcmData.size && isPlaying) {
                    val bytesToWrite = minOf(chunkSize, pcmData.size - offset)
                    val written = track.write(pcmData, offset, bytesToWrite)
                    if (written <= 0) break
                    offset += written
                }

                // Wait for the full audio track playback to finish naturally
                val totalFrames = pcmData.size / 2
                val durationMs = (totalFrames * 1000L) / sampleRate
                val startTime = System.currentTimeMillis()
                val maxWaitMs = durationMs + 400L // allow slight latency margin

                while (isPlaying && (System.currentTimeMillis() - startTime) < maxWaitMs) {
                    val head = try { track.playbackHeadPosition } catch (e: Exception) { totalFrames }
                    if (head >= totalFrames) {
                        break
                    }
                    kotlinx.coroutines.delay(25L)
                }
            } catch (e: Exception) {
                // Ignore interruption / playback error
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (e: Exception) {}
                activeAudioTrack = null
                isPlaying = false
                withContext(Dispatchers.Main) { onComplete?.invoke() }
            }
        }
    }

    /**
     * Plays the WAV file using MediaPlayer fallback.
     */
    private fun playWavFile(
        file: File,
        onStart: (() -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        stopPlayback()
        isPlaying = true

        try {
            val mp = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(file))
                setOnPreparedListener { player ->\n                    onStart?.invoke()
                    player.start()
                }
                setOnCompletionListener { player ->
                    this@UserAudioPlaybackEngine.isPlaying = false
                    player.release()
                    activeMediaPlayer = null
                    onComplete?.invoke()
                }
                setOnErrorListener { player, _, _ ->
                    this@UserAudioPlaybackEngine.isPlaying = false
                    player.release()
                    activeMediaPlayer = null
                    onComplete?.invoke()
                    true
                }
                prepareAsync()
            }
            activeMediaPlayer = mp
        } catch (e: Exception) {
            isPlaying = false
            onComplete?.invoke()
        }
    }

    /**
     * Immediately stops any current playback.
     */
    override fun stopPlayback() {
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null

        try {
            activeAudioTrack?.stop()
            activeAudioTrack?.release()
        } catch (e: Exception) {}
        activeAudioTrack = null

        try {
            activeMediaPlayer?.stop()
            activeMediaPlayer?.release()
        } catch (e: Exception) {}
        activeMediaPlayer = null
    }

    /**
     * Releases all playback resources.
     */
    override fun release() {
        stopPlayback()
        lastRecordedPcm = null
    }

    companion object {
        /**
         * Generates standard 44-byte RIFF/WAVE header.
         */
        fun createWavHeader(
            totalAudioLen: Long,
            totalDataLen: Long,
            longSampleRate: Long,
            channels: Int,
            byteRate: Long,
            bitsPerSample: Int = 16
        ): ByteArray {
            val header = ByteArray(44)
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

            // "RIFF" chunk
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()

            buffer.position(4)
            buffer.putInt((totalDataLen and 0xffffffffL).toInt())

            // "WAVE" format
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()

            // "fmt " sub-chunk
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()

            buffer.position(16)
            buffer.putInt(16) // Subchunk1Size for PCM
            buffer.putShort(1.toShort()) // AudioFormat = 1 (PCM)
            buffer.putShort(channels.toShort())
            buffer.putInt((longSampleRate and 0xffffffffL).toInt())
            buffer.putInt((byteRate and 0xffffffffL).toInt())
            buffer.putShort((channels * bitsPerSample / 8).toShort()) // BlockAlign
            buffer.putShort(bitsPerSample.toShort()) // BitsPerSample

            // "data" sub-chunk
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()

            buffer.position(40)
            buffer.putInt((totalAudioLen and 0xffffffffL).toInt())

            return header
        }
    }
}
