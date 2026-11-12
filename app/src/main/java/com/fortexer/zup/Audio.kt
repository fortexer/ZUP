package com.fortexer.zup

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Fully synthesized audio (no asset files). Every call is guarded so audio problems
 * on any device can never crash the game.
 */
class Audio {

    private val rate = 22050
    private val sfx = HashMap<String, AudioTrack>()
    private var music: AudioTrack? = null
    private var paused = false
    private var musicFrames = 0
    var soundOn = true
    var musicOn = true

    init {
        try {
            // each segment: startFreq, endFreq, durationMs, volume
            add("click", floatArrayOf(900f, 700f, 40f, 0.5f))
            add("start", floatArrayOf(440f, 440f, 80f, 0.6f, 660f, 660f, 80f, 0.6f, 880f, 880f, 150f, 0.6f))
            add("crystal", floatArrayOf(1175f, 1175f, 55f, 0.55f, 1568f, 1568f, 120f, 0.55f))
            add("buy", floatArrayOf(660f, 660f, 70f, 0.6f, 880f, 880f, 70f, 0.6f, 1320f, 1320f, 170f, 0.6f))
            add("record", floatArrayOf(523f, 523f, 90f, 0.6f, 659f, 659f, 90f, 0.6f, 784f, 784f, 90f, 0.6f, 1047f, 1047f, 280f, 0.6f))
            add("over", floatArrayOf(392f, 360f, 150f, 0.6f, 330f, 300f, 150f, 0.6f, 262f, 180f, 320f, 0.6f))
            add("error", floatArrayOf(240f, 200f, 130f, 0.5f))
            val m = makeTrack(buildMusic())
            m.setLoopPoints(0, musicFrames, -1)
            m.setVolume(0.28f)
            music = m
        } catch (e: Throwable) {
        }
    }

    private fun add(name: String, segs: FloatArray) {
        sfx[name] = makeTrack(synth(segs))
    }

    private fun synth(segs: FloatArray): ShortArray {
        var total = 0
        var i = 0
        while (i < segs.size) {
            total += (rate * segs[i + 2] / 1000f).toInt()
            i += 4
        }
        val out = ShortArray(total)
        var pos = 0
        i = 0
        while (i < segs.size) {
            val f0 = segs[i]
            val f1 = segs[i + 1]
            val n = (rate * segs[i + 2] / 1000f).toInt()
            val vol = segs[i + 3]
            var phase = 0f
            for (s in 0 until n) {
                val frac = s.toFloat() / n
                val f = f0 + (f1 - f0) * frac
                phase += 2f * 3.14159265f * f / rate
                val env = min(1f, s / (rate * 0.004f)) * exp(-2.5f * frac)
                val v = (sin(phase) * 0.7f + sin(phase * 2f) * 0.2f) * env * vol
                val iv = (v * 26000f).toInt().coerceIn(-32000, 32000)
                out[pos + s] = iv.toShort()
            }
            pos += n
            i += 4
        }
        return out
    }

    private fun buildMusic(): ShortArray {
        val stepSec = 0.3f
        val chords = arrayOf(
            floatArrayOf(220f, 261.63f, 329.63f),
            floatArrayOf(174.61f, 220f, 261.63f),
            floatArrayOf(261.63f, 329.63f, 392f),
            floatArrayOf(196f, 246.94f, 293.66f)
        )
        val bass = floatArrayOf(110f, 87.31f, 130.81f, 98f)
        val pattern = intArrayOf(0, 1, 2, 1, 0, 1, 2, 1)
        val steps = pattern.size
        val total = (rate * stepSec * steps * chords.size).toInt()
        val buf = FloatArray(total)
        for (ci in chords.indices) {
            val chordStart = (ci * steps * stepSec * rate).toInt()
            addNote(buf, chordStart, bass[ci], 2.4f, 0.22f, 1.1f)
            for (s in 0 until steps) {
                val start = chordStart + (s * stepSec * rate).toInt()
                addNote(buf, start, chords[ci][pattern[s]] * 2f, 0.6f, 0.16f, 6f)
            }
        }
        musicFrames = total
        return ShortArray(total) { (buf[it].coerceIn(-1f, 1f) * 30000f).toInt().toShort() }
    }

    private fun addNote(buf: FloatArray, start: Int, freq: Float, dur: Float, amp: Float, decay: Float) {
        val n = (rate * dur).toInt()
        for (i in 0 until n) {
            val t = i.toFloat() / rate
            val env = (1f - exp(-t * 250f)) * exp(-t * decay)
            val v = (sin(2f * 3.14159265f * freq * t) + 0.3f * sin(4f * 3.14159265f * freq * t)) * env * amp
            buf[(start + i) % buf.size] += v
        }
    }

    private fun makeTrack(data: ShortArray): AudioTrack {
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(data.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(data, 0, data.size)
        return t
    }

    fun play(name: String) {
        if (!soundOn) return
        try {
            val t = sfx[name] ?: return
            t.stop()
            t.reloadStaticData()
            t.play()
        } catch (e: Throwable) {
        }
    }

    private fun applyMusic() {
        try {
            val m = music ?: return
            if (musicOn && !paused) {
                if (m.playState != AudioTrack.PLAYSTATE_PLAYING) m.play()
            } else if (m.playState == AudioTrack.PLAYSTATE_PLAYING) {
                m.pause()
            }
        } catch (e: Throwable) {
        }
    }

    fun setMusic(on: Boolean) {
        musicOn = on
        applyMusic()
    }

    fun pause() {
        paused = true
        applyMusic()
    }

    fun resume() {
        paused = false
        applyMusic()
    }

    fun release() {
        try {
            for ((_, t) in sfx) t.release()
            music?.release()
        } catch (e: Throwable) {
        }
        sfx.clear()
        music = null
    }
}
