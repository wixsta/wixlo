package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Converts microphone speech to text for Android voice calls.
 * The web app streams PCM to Gemini Live; Android uses STT → Gemini text → TTS instead.
 */
class CallSpeechRecognizer(context: Context) {

    private val appContext = context.applicationContext
    private var speechRecognizer: SpeechRecognizer? = null
    private var onResultCallback: ((String) -> Unit)? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _inputAmplitude = MutableStateFlow(0f)
    val inputAmplitude: StateFlow<Float> = _inputAmplitude.asStateFlow()

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _isListening.value = true
        }

        override fun onBeginningOfSpeech() {
            _isListening.value = true
        }

        override fun onRmsChanged(rmsdB: Float) {
            val normalized = ((rmsdB + 2f) / 10f).coerceIn(0f, 1f)
            _inputAmplitude.value = normalized
        }

        override fun onEndOfSpeech() {
            _isListening.value = false
            _inputAmplitude.value = 0f
        }

        override fun onError(error: Int) {
            Log.w(TAG, "Speech recognition error: $error")
            _isListening.value = false
            _inputAmplitude.value = 0f
        }

        override fun onResults(results: Bundle?) {
            _isListening.value = false
            _inputAmplitude.value = 0f
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim()
            if (!text.isNullOrEmpty()) {
                onResultCallback?.invoke(text)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull()?.trim()
            if (!partial.isNullOrEmpty()) {
                _inputAmplitude.value = 0.35f
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    fun startListening(onResult: (String) -> Unit) {
        if (!isAvailable) {
            Log.w(TAG, "Speech recognition not available on this device")
            return
        }
        onResultCallback = onResult
        ensureRecognizer()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start listening", e)
            _isListening.value = false
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Throwable) {
            Log.w(TAG, "stopListening failed", e)
        }
        _isListening.value = false
        _inputAmplitude.value = 0f
    }

    fun release() {
        stopListening()
        try {
            speechRecognizer?.destroy()
        } catch (e: Throwable) {
            Log.w(TAG, "destroy failed", e)
        }
        speechRecognizer = null
        onResultCallback = null
    }

    private fun ensureRecognizer() {
        if (speechRecognizer != null) return
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(recognitionListener)
        }
    }

    companion object {
        private const val TAG = "CallSpeechRecognizer"
    }
}
