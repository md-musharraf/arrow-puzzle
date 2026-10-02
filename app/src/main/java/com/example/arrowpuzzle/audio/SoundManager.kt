package com.example.arrowpuzzle.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.arrowpuzzle.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class SoundManager(context: Context) {

    private val prefs = PreferencesManager(context)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val sampleRate = 44100

    private val pentatonicScale = listOf(
        523.25, // C5
        587.33, // D5
        659.25, // E5
        783.99, // G5
        880.00, // A5
        1046.50, // C6
        1174.66, // D6
        1318.51, // E6
        1567.98  // G6
    )

    fun playArrowLaunch(combo: Int = 0) {
        if (!prefs.isSoundEnabled) return
        scope.launch {
            val noteIndex = (combo.coerceAtLeast(0)) % pentatonicScale.size
            val baseFreq = pentatonicScale[noteIndex]
            val durationMs = 180
            val samples = (sampleRate * (durationMs / 1000.0)).toInt()
            val buffer = ShortArray(samples)

            for (i in 0 until samples) {
                val t = i.toDouble() / sampleRate
                val envelope = exp(-t * 18.0) // smooth pluck decay
                // Fundamental + harmonic
                val wave = 0.75 * sin(2.0 * PI * baseFreq * t) + 0.25 * sin(4.0 * PI * baseFreq * t)
                buffer[i] = (wave * envelope * Short.MAX_VALUE * 0.7).toInt().toShort()
            }

            playPcmBuffer(buffer)
        }
    }

    fun playBlockedCollision() {
        if (!prefs.isSoundEnabled) return
        scope.launch {
            val durationMs = 140
            val samples = (sampleRate * (durationMs / 1000.0)).toInt()
            val buffer = ShortArray(samples)

            for (i in 0 until samples) {
                val t = i.toDouble() / sampleRate
                // Frequency drops rapidly for a wooden thud
                val freq = 180.0 * exp(-t * 25.0) + 70.0
                val envelope = exp(-t * 30.0)
                val noise = (Math.random() * 2.0 - 1.0) * 0.15
                val wave = sin(2.0 * PI * freq * t) + noise
                buffer[i] = (wave * envelope * Short.MAX_VALUE * 0.8).toInt().toShort()
            }

            playPcmBuffer(buffer)
        }
    }

    fun playVictory() {
        if (!prefs.isSoundEnabled) return
        scope.launch {
            val notes = listOf(523.25, 659.25, 783.99, 1046.50) // C-E-G-C arpeggio
            val noteDurationMs = 120
            val totalDurationMs = noteDurationMs * notes.size + 300
            val totalSamples = (sampleRate * (totalDurationMs / 1000.0)).toInt()
            val buffer = ShortArray(totalSamples)

            notes.forEachIndexed { index, freq ->
                val startSample = (sampleRate * (index * noteDurationMs / 1000.0)).toInt()
                val noteSamples = (sampleRate * 0.45).toInt() // ring out

                for (i in 0 until noteSamples) {
                    val targetIdx = startSample + i
                    if (targetIdx < totalSamples) {
                        val t = i.toDouble() / sampleRate
                        val envelope = exp(-t * 8.0)
                        val wave = 0.7 * sin(2.0 * PI * freq * t) + 0.3 * sin(4.0 * PI * freq * t)
                        val currentVal = buffer[targetIdx].toInt()
                        val sampleVal = (wave * envelope * Short.MAX_VALUE * 0.45).toInt()
                        buffer[targetIdx] = (currentVal + sampleVal).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                }
            }

            playPcmBuffer(buffer)
        }
    }

    fun playDefeat() {
        if (!prefs.isSoundEnabled) return
        scope.launch {
            val notes = listOf(440.0, 392.0, 349.23, 293.66) // Descending A4-G4-F4-D4
            val noteDurationMs = 150
            val totalDurationMs = noteDurationMs * notes.size + 300
            val totalSamples = (sampleRate * (totalDurationMs / 1000.0)).toInt()
            val buffer = ShortArray(totalSamples)

            notes.forEachIndexed { index, freq ->
                val startSample = (sampleRate * (index * noteDurationMs / 1000.0)).toInt()
                val noteSamples = (sampleRate * 0.4).toInt()

                for (i in 0 until noteSamples) {
                    val targetIdx = startSample + i
                    if (targetIdx < totalSamples) {
                        val t = i.toDouble() / sampleRate
                        val envelope = exp(-t * 10.0)
                        val wave = sin(2.0 * PI * freq * t)
                        val currentVal = buffer[targetIdx].toInt()
                        val sampleVal = (wave * envelope * Short.MAX_VALUE * 0.4).toInt()
                        buffer[targetIdx] = (currentVal + sampleVal).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                }
            }

            playPcmBuffer(buffer)
        }
    }

    fun playButtonClick() {
        if (!prefs.isSoundEnabled) return
        scope.launch {
            val durationMs = 40
            val samples = (sampleRate * (durationMs / 1000.0)).toInt()
            val buffer = ShortArray(samples)

            for (i in 0 until samples) {
                val t = i.toDouble() / sampleRate
                val envelope = exp(-t * 80.0)
                val wave = sin(2.0 * PI * 1200.0 * t)
                buffer[i] = (wave * envelope * Short.MAX_VALUE * 0.4).toInt().toShort()
            }

            playPcmBuffer(buffer)
        }
    }

    private fun playPcmBuffer(buffer: ShortArray) {
        try {
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
            // Clean up when done
            scope.launch {
                val durationMs = (buffer.size * 1000L) / sampleRate + 50
                kotlinx.coroutines.delay(durationMs)
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }
}
