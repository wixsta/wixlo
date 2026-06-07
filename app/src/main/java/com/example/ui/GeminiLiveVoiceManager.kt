package com.example.ui

import android.content.Context
import android.util.Log
import com.example.api.GeminiClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android voice-call pipeline: speech/text in → Gemini text API → on-device TTS out.
 * Unlike the web app, this does not use Gemini Live WebSocket audio streaming.
 */
class GeminiLiveVoiceManager(private val context: Context) {

    private val _micAmplitude = MutableStateFlow(0f)
    val micAmplitude: StateFlow<Float> = _micAmplitude.asStateFlow()

    private val _liveCaption = MutableStateFlow("")
    val liveCaption: StateFlow<String> = _liveCaption.asStateFlow()

    private var isSessionActive = false
    private var speakDelegate: ((String) -> Unit)? = null
    private var stopSpeakingDelegate: (() -> Unit)? = null
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var amplitudeJob: Job? = null
    private val voiceHistory = mutableListOf<Pair<String, String>>()
    private var systemPromptBuffer = ""
    private var contactNameBuffer = ""

    fun startLiveSession(
        contactName: String,
        systemPrompt: String,
        scope: CoroutineScope,
        onSpeak: (String) -> Unit,
        onStopSpeaking: () -> Unit
    ) {
        if (isSessionActive) stopLiveSession()
        isSessionActive = true
        speakDelegate = onSpeak
        stopSpeakingDelegate = onStopSpeaking
        systemPromptBuffer = systemPrompt
        contactNameBuffer = contactName
        voiceHistory.clear()

        _liveCaption.value = "Connecting voice line..."
        _micAmplitude.value = 0f

        scope.launch {
            delay(800)
            if (!isSessionActive) return@launch
            val greeting = VoiceCallSoundManager.getWelcomeMessage(contactName)
            _liveCaption.value = greeting
            voiceHistory.add("model" to greeting)
            speakViaTts(greeting)
        }
    }

    fun submitUserVoiceInput(text: String, scope: CoroutineScope) {
        if (!isSessionActive) return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        stopAmplitudeSimulation()
        stopTtsSpeech()

        _liveCaption.value = "You: $trimmed"
        voiceHistory.add("user" to trimmed)

        scope.launch(Dispatchers.IO) {
            _liveCaption.value = "Thinking..."
            try {
                val responseText = GeminiClient.generatePersonaResponse(
                    systemPrompt = systemPromptBuffer,
                    history = voiceHistory.toList()
                )

                if (isSessionActive) {
                    _liveCaption.value = responseText
                    voiceHistory.add("model" to responseText)
                    speakViaTts(responseText)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to generate voice response", e)
                if (isSessionActive) {
                    val fallback = "Sorry, I lost the connection for a moment. Could you repeat that?"
                    _liveCaption.value = fallback
                    voiceHistory.add("model" to fallback)
                    speakViaTts(fallback)
                }
            }
        }
    }

    fun setInputAmplitude(level: Float) {
        if (isSessionActive) {
            _micAmplitude.value = level.coerceIn(0f, 1f)
        }
    }

    private fun speakViaTts(text: String) {
        speakDelegate?.invoke(text)
        runAmplitudeSimulation()
    }

    private fun stopTtsSpeech() {
        stopSpeakingDelegate?.invoke()
        stopAmplitudeSimulation()
    }

    private fun runAmplitudeSimulation() {
        stopAmplitudeSimulation()
        amplitudeJob = managerScope.launch {
            var tick = 0
            while (isActive) {
                val base = if (tick % 2 == 0) 0.15f else 0.45f
                val noise = (Math.random() * 0.4).toFloat()
                _micAmplitude.value = (base + noise).coerceIn(0f, 1f)
                delay(180)
                tick++
            }
        }
    }

    private fun stopAmplitudeSimulation() {
        amplitudeJob?.cancel()
        amplitudeJob = null
        if (!isSessionActive) {
            _micAmplitude.value = 0f
        }
    }

    fun stopLiveSession() {
        isSessionActive = false
        stopAmplitudeSimulation()
        stopTtsSpeech()

        speakDelegate = null
        stopSpeakingDelegate = null

        _micAmplitude.value = 0f
        _liveCaption.value = ""
        voiceHistory.clear()
        contactNameBuffer = ""
    }

    fun release() {
        stopLiveSession()
        managerScope.cancel()
    }

    companion object {
        private const val TAG = "GeminiLiveVoiceManager"
    }
}
