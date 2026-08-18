package com.boonjabby.racketscore.mobile

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import java.util.Locale

class PhoneFeedback(context: Context) : TextToSpeech.OnInitListener {
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val speech = TextToSpeech(context, this)
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) speech.language = Locale.getDefault()
    }

    fun tap() = vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
    fun announce(text: String) { if (ready) speech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "score") }
    fun close() = speech.shutdown()
}
