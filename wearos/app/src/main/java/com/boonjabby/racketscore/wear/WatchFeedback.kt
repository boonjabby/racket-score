package com.boonjabby.racketscore.wear

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale

class WatchFeedback(private val context: Context) : TextToSpeech.OnInitListener {
    private val speech = TextToSpeech(context, this)
    private var speechReady = false

    override fun onInit(status: Int) {
        speechReady = status == TextToSpeech.SUCCESS
        if (speechReady) speech.language = Locale.getDefault()
    }

    fun announce(text: String) {
        if (!speechReady) return
        speech.stop()
        speech.setSpeechRate(1.12f)
        speech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "racket-score")
    }

    fun tap() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun close() {
        speech.stop()
        speech.shutdown()
    }
}
