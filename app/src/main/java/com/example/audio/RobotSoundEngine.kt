package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

private const val TAG = "RobotSoundEngine"
private const val SAMPLE_RATE = 44_100

class RobotSoundEngine {

  private val executor = Executors.newSingleThreadExecutor()
  @Volatile private var isReleased = false

  fun playWakeUp() {
    playSequence(
      listOf(
        ToneSegment(520.0, 740.0, 95, 0.26),
        ToneSegment(0.0, 0.0, 25, 0.0),
        ToneSegment(780.0, 1180.0, 130, 0.30),
        ToneSegment(1180.0, 1460.0, 110, 0.24),
      )
    )
  }

  fun playWink() {
    playSequence(
      listOf(
        ToneSegment(880.0, 1320.0, 75, 0.28),
        ToneSegment(0.0, 0.0, 20, 0.0),
        ToneSegment(1380.0, 1080.0, 65, 0.22),
      )
    )
  }

  fun playHappyPurr() {
    playSequence(
      listOf(
        ToneSegment(660.0, 880.0, 70, 0.25),
        ToneSegment(880.0, 1175.0, 75, 0.26),
        ToneSegment(1175.0, 1568.0, 115, 0.28),
      )
    )
  }

  fun playDizzy() {
    playSequence(
      listOf(
        ToneSegment(1100.0, 620.0, 90, 0.26),
        ToneSegment(620.0, 980.0, 90, 0.25),
        ToneSegment(980.0, 520.0, 110, 0.24),
        ToneSegment(520.0, 820.0, 90, 0.20),
      )
    )
  }

  fun playListeningBeep() {
    playSequence(
      listOf(
        ToneSegment(740.0, 1046.0, 80, 0.22),
      )
    )
  }

  fun playSleepSigh() {
    playSequence(
      listOf(
        ToneSegment(620.0, 410.0, 150, 0.16),
        ToneSegment(410.0, 290.0, 180, 0.12),
      )
    )
  }

  private data class ToneSegment(
    val startFreqHz: Double,
    val endFreqHz: Double,
    val durationMs: Int,
    val amplitude: Double,
  )

  private fun playSequence(segments: List<ToneSegment>) {
    if (isReleased) return
    executor.execute {
      if (isReleased) return@execute
      var track: AudioTrack? = null
      try {
        val totalSamples = segments.sumOf { (SAMPLE_RATE * it.durationMs) / 1000 }
        if (totalSamples <= 0) return@execute

        val pcm = ShortArray(totalSamples)
        var cursor = 0
        var phase = 0.0

        for (seg in segments) {
          val segSamples = (SAMPLE_RATE * seg.durationMs) / 1000
          for (i in 0 until segSamples) {
            if (seg.startFreqHz <= 0.0 || seg.amplitude <= 0.0) {
              pcm[cursor++] = 0
              continue
            }
            val progress = i.toDouble() / segSamples.coerceAtLeast(1)
            val freq = seg.startFreqHz + (seg.endFreqHz - seg.startFreqHz) * progress
            val attack = (i / (SAMPLE_RATE * 0.008)).coerceIn(0.0, 1.0)
            val release = ((segSamples - i) / (SAMPLE_RATE * 0.015)).coerceIn(0.0, 1.0)
            val env = attack * release * exp(-0.4 * progress)
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val sample = (sin(phase) * seg.amplitude * env * Short.MAX_VALUE).toInt()
            pcm[cursor++] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
          }
        }

        val bufferSizeBytes = pcm.size * 2
        val minBuf =
          AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
          )
        val finalBufferBytes = maxOf(bufferSizeBytes, minBuf)

        track =
          AudioTrack.Builder()
            .setAudioAttributes(
              AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            )
            .setAudioFormat(
              AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            )
            .setBufferSizeInBytes(finalBufferBytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(pcm, 0, pcm.size)
        track.play()
        val totalDurationMs = segments.sumOf { it.durationMs }.toLong()
        Thread.sleep(totalDurationMs + 40L)
      } catch (e: Exception) {
        Log.w(TAG, "Error playing synthetic robot sound", e)
      } finally {
        try {
          track?.stop()
          track?.release()
        } catch (_: Exception) {}
      }
    }
  }

  fun release() {
    isReleased = true
    executor.shutdownNow()
  }
}
