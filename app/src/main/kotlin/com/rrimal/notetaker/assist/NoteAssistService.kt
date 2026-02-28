package com.rrimal.notetaker.assist

import android.content.Intent
import android.service.voice.VoiceInteractionService
import com.rrimal.notetaker.NoteCaptureActivity

class NoteAssistService : VoiceInteractionService() {

    override fun onLaunchVoiceAssistFromKeyguard() {
        val intent = Intent(this, NoteCaptureActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}
