package com.example.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class VoiceCallSoundManager(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var toneGenerator: ToneGenerator? = null
    private var isTtsInitialized = false
    private var ringingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Tracks current text being spoken for caption overlays
    private var _spokenCaption = MutableStateFlow("")
    val spokenCaption: StateFlow<String> = _spokenCaption.asStateFlow()

    init {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let { am ->
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.95).toInt().coerceAtLeast(1), 0)
                Log.d("VoiceCallSoundManager", "Forced STREAM_MUSIC volume to modern levels")
            }
        } catch (e: Throwable) {
            Log.e("VoiceCallSoundManager", "Failed to force volume: ${e.message}")
        }
        try {
            tts = TextToSpeech(context.applicationContext, this)
            // Use STREAM_MUSIC stream for visual audio integration in emulators so user can hear the call!
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Throwable) {
            Log.e("VoiceCallSoundManager", "Failed to initialize ToneGenerator or TTS", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let {
                try {
                    val result = it.setLanguage(Locale.US)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        it.language = Locale.getDefault()
                    }
                    val audioAttributes = android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                        .build()
                    it.setAudioAttributes(audioAttributes)
                    isTtsInitialized = true
                } catch (e: Throwable) {
                    Log.e("VoiceCallSoundManager", "Failed to bind language or audio attributes: ${e.message}")
                }
            }
        } else {
            Log.e("VoiceCallSoundManager", "TTS initialization failed.")
        }
    }

    /**
     * Loops a standard telephony ringing tone simulating a realistic dialing phase.
     */
    fun startRinging() {
        stopRinging()
        ringingJob = scope.launch {
            try {
                _spokenCaption.value = "[Phone Dialing Ringing...]"
                while (isActive) {
                    // Start a standard 2-second ringing tone
                    toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 2000)
                    // Wait for the ringing cycle (2s sound + 2s silence)
                    delay(4000)
                }
            } catch (e: CancellationException) {
                // Normal job cancellation
            } catch (e: Throwable) {
                Log.e("VoiceCallSoundManager", "Ringing interval failed", e)
            }
        }
    }

    /**
     * Halts all active ringing immediately.
     */
    fun stopRinging() {
        ringingJob?.cancel()
        ringingJob = null
        try {
            toneGenerator?.stopTone()
        } catch (e: Throwable) {
            // Safe ignore
        }
    }

    /**
     * Speaks the specified text out loud using on-device TTS synthesizer.
     */
    fun speak(text: String) {
        scope.launch {
            // Update live captions
            _spokenCaption.value = text
            if (isTtsInitialized && tts != null) {
                try {
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "PersonaVoiceID")
                } catch (e: Throwable) {
                    Log.e("VoiceCallSoundManager", "Speak operation failed", e)
                }
            } else {
                // Fallback debug logging
                Log.w("VoiceCallSoundManager", "TTS not initialized, skipping speak: $text")
            }
        }
    }

    /**
     * Speaks custom text if TTS is available.
     * Silences all synthesized speaking voices immediately.
     */
    fun stopSpeaking() {
        _spokenCaption.value = ""
        try {
            tts?.stop()
        } catch (e: Throwable) {
            // Safe ignore
        }
    }

    /**
     * Destroys objects and releases media assets to avoid system memory leaks.
     */
    fun release() {
        stopRinging()
        stopSpeaking()
        scope.cancel()
        try {
            tts?.shutdown()
            tts = null
        } catch (e: Throwable) {
            // Safe ignore
        }
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Throwable) {
            // Safe ignore
        }
    }

    companion object {
        fun getWelcomeMessage(name: String): String {
            return when (name.lowercase()) {
                "nova" -> "Hello! I am Nova. What coding or engineering topic are we working on today?"
                "coach marcus" -> "Hello, I am Marcus! Let's discuss your training goals and fitness progress today."
                "sophia" -> "Hello, I am Sophia! Let's plan some wonderful culinary recipes today. What are we cooking?"
                "elena" -> "Hello, I am Elena. Let us take a moment to focus. How can I help you find calm today?"
                "aurelius" -> "Greetings, I am Aurelius. Let us discuss philosophy, decision-making, or strategic thinking today. What is on your mind?"
                else -> "Hello! I am $name. It is wonderful to talk with you. How can I help you today?"
            }
        }
    }
}
