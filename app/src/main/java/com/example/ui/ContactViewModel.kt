package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiClient
import com.example.db.*
import com.example.security.EncryptionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface CallState {
    object Idle : CallState
    data class Outgoing(val contact: Contact) : CallState
    data class Connected(val contact: Contact, val durationSec: Int) : CallState
    data class Disconnected(val contact: Contact, val durationSec: Int, val totalCost: Double) : CallState
}

class ContactViewModel(application: Application) : AndroidViewModel(application) {
    private val database: AppDatabase = AppDatabase.getDatabase(application)
    private val contactDao = database.contactDao()
    private val messageDao = database.messageDao()
    private val transactionDao = database.transactionDao()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (contactDao.getContactCount() == 0) {
                    AppDatabase.populateDatabase(contactDao, transactionDao)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    val contacts: StateFlow<List<Contact>> = contactDao.getAllContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<Transaction>> = transactionDao.getAllTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val balance: StateFlow<Double> = transactions.map { list ->
        list.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1000.00)

    private val _selectedContactId = MutableStateFlow<Int?>(null)
    val selectedContactId: StateFlow<Int?> = _selectedContactId.asStateFlow()

    val selectedContact: StateFlow<Contact?> = _selectedContactId.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else contacts.map { list -> list.find { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chatMessages: StateFlow<List<Message>> = _selectedContactId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else messageDao.getMessagesForContact(id).map { list ->
            list.map { msg ->
                msg.copy(encryptedContent = EncryptionHelper.decrypt(msg.encryptedContent))
            }
        }.flowOn(Dispatchers.IO)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isAITyping = MutableStateFlow(false)
    val isAITyping: StateFlow<Boolean> = _isAITyping.asStateFlow()

    private val _notificationMessage = MutableStateFlow<String?>(null)
    val notificationMessage: StateFlow<String?> = _notificationMessage.asStateFlow()

    private val audioManager = VoiceCallSoundManager(application)

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val liveVoiceManager = GeminiLiveVoiceManager(application)
    private val callSpeechRecognizer = CallSpeechRecognizer(application)

    val isListeningForSpeech: StateFlow<Boolean> = callSpeechRecognizer.isListening

    val micAmplitude: StateFlow<Float> = combine(
        liveVoiceManager.micAmplitude,
        callSpeechRecognizer.inputAmplitude
    ) { aiLevel, inputLevel ->
        maxOf(aiLevel, inputLevel)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val spokenCaption: StateFlow<String> = callState.flatMapLatest { state ->
        if (state is CallState.Connected) {
            liveVoiceManager.liveCaption
        } else {
            audioManager.spokenCaption
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private var callJob: Job? = null

    fun selectContact(contactId: Int?) {
        _selectedContactId.value = contactId
    }

    fun showNotification(message: String) {
        viewModelScope.launch {
            _notificationMessage.value = message
            delay(3000)
            if (_notificationMessage.value == message) {
                _notificationMessage.value = null
            }
        }
    }

    fun addFunds(amount: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            transactionDao.insertTransaction(
                Transaction(
                    type = "deposit",
                    contactName = "System",
                    amount = amount
                )
            )
            showNotification("Deposited ₦$amount to your wallet.")
        }
    }

    fun createCustomContact(
        name: String,
        description: String,
        systemPrompt: String,
        chatPrice: Double,
        callPrice: Double
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val contact = Contact(
                name = name,
                avatarSeed = name.take(2).uppercase(),
                description = description,
                systemPrompt = systemPrompt.ifBlank { "You are $name, a helpful conversational guide." },
                chatPrice = chatPrice,
                callPrice = callPrice,
                isAIPersona = true,
                isEncrypted = true
            )
            contactDao.insertContact(contact)
            showNotification("Contact $name added.")
        }
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch(Dispatchers.IO) {
            contactDao.deleteContact(contact)
            messageDao.clearMessagesForContact(contact.id)
            if (_selectedContactId.value == contact.id) {
                _selectedContactId.value = null
            }
            showNotification("${contact.name} deleted.")
        }
    }

    fun clearChat(contactId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            messageDao.clearMessagesForContact(contactId)
            showNotification("Chat cleared.")
        }
    }

    fun sendChatMessage(contactId: Int, contentText: String) {
        if (contentText.isBlank()) return

        val contact = contacts.value.find { it.id == contactId } ?: return
        val currentBalance = balance.value

        if (currentBalance < contact.chatPrice) {
            showNotification("Insufficient balance. Messaging ${contact.name} costs ₦${String.format("%.2f", contact.chatPrice)}.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val encryptedMessage = EncryptionHelper.encrypt(contentText)
            messageDao.insertMessage(
                Message(
                    contactId = contactId,
                    sender = "user",
                    encryptedContent = encryptedMessage
                )
            )

            transactionDao.insertTransaction(
                Transaction(
                    type = "text",
                    contactName = contact.name,
                    amount = -contact.chatPrice
                )
            )

            _isAITyping.value = true
            delay(800)

            val currentHistoryRaw = messageDao.getMessagesForContact(contactId).first().takeLast(10)
            val conversationHistory = currentHistoryRaw.map {
                val decoded = EncryptionHelper.decrypt(it.encryptedContent)
                val formattedSender = if (it.sender == "user") "user" else "model"
                Pair(formattedSender, decoded)
            }

            val aiResponsePlain = GeminiClient.generatePersonaResponse(
                systemPrompt = contact.systemPrompt,
                history = conversationHistory
            )

            val encryptedResponse = EncryptionHelper.encrypt(aiResponsePlain)
            messageDao.insertMessage(
                Message(
                    contactId = contactId,
                    sender = contact.name,
                    encryptedContent = encryptedResponse
                )
            )
            _isAITyping.value = false
        }
    }

    fun startCall(contact: Contact) {
        if (_callState.value !is CallState.Idle) return

        if (balance.value < contact.callPrice) {
            showNotification("Insufficient balance. Calling ${contact.name} costs ₦${String.format("%.2f", contact.callPrice)}/min.")
            return
        }

        audioManager.stopSpeaking()
        _callState.value = CallState.Outgoing(contact)
        audioManager.startRinging()

        viewModelScope.launch {
            delay(3000)
            if (_callState.value is CallState.Outgoing) {
                audioManager.stopRinging()
                _callState.value = CallState.Connected(contact, 0)

                if (GeminiClient.resolveApiKey().isEmpty()) {
                    showNotification("Gemini API key missing. Add GEMINI_API_KEY to .env and rebuild.")
                }

                liveVoiceManager.startLiveSession(
                    contactName = contact.name,
                    systemPrompt = contact.systemPrompt,
                    scope = viewModelScope,
                    onSpeak = { text -> audioManager.speak(text) },
                    onStopSpeaking = { audioManager.stopSpeaking() }
                )

                startCallTimer(contact)
            }
        }
    }

    private fun startCallTimer(contact: Contact) {
        callJob?.cancel()
        callJob = viewModelScope.launch {
            var duration = 0
            while (true) {
                delay(1000)
                duration++
                _callState.value = CallState.Connected(contact, duration)

                val runningCost = kotlin.math.max(contact.callPrice, (duration / 60.0) * contact.callPrice)
                if (runningCost >= balance.value) {
                    endCallLive(contact, duration, forceInsufficient = true)
                    break
                }
            }
        }
    }

    fun endCall() {
        val state = _callState.value
        callJob?.cancel()
        audioManager.stopRinging()
        audioManager.stopSpeaking()
        callSpeechRecognizer.stopListening()
        liveVoiceManager.stopLiveSession()
        if (state is CallState.Connected) {
            endCallLive(state.contact, state.durationSec, forceInsufficient = false)
        } else {
            _callState.value = CallState.Idle
        }
    }

    fun submitCallVoiceInput(text: String) {
        callSpeechRecognizer.stopListening()
        liveVoiceManager.submitUserVoiceInput(text, viewModelScope)
    }

    fun toggleCallSpeechInput() {
        if (_callState.value !is CallState.Connected) return

        if (callSpeechRecognizer.isListening.value) {
            callSpeechRecognizer.stopListening()
            return
        }

        if (!callSpeechRecognizer.isAvailable) {
            showNotification("Speech recognition unavailable. Type your message instead.")
            return
        }

        audioManager.stopSpeaking()
        callSpeechRecognizer.startListening { recognized ->
            submitCallVoiceInput(recognized)
        }
    }

    private fun endCallLive(contact: Contact, durationSec: Int, forceInsufficient: Boolean) {
        callJob?.cancel()
        audioManager.stopRinging()
        audioManager.stopSpeaking()
        callSpeechRecognizer.stopListening()
        liveVoiceManager.stopLiveSession()
        viewModelScope.launch(Dispatchers.IO) {
            val calculatedCost = if (durationSec > 0) {
                kotlin.math.max(contact.callPrice, (durationSec / 60.0) * contact.callPrice)
            } else {
                0.0
            }

            if (calculatedCost > 0.0) {
                transactionDao.insertTransaction(
                    Transaction(
                        type = "call",
                        contactName = contact.name,
                        amount = -calculatedCost,
                        durationSec = durationSec
                    )
                )
            }

            _callState.value = CallState.Disconnected(contact, durationSec, calculatedCost)

            if (forceInsufficient) {
                showNotification("Call ended: insufficient funds.")
            } else {
                showNotification("Call ended: ${durationSec}s | Cost: ₦${String.format("%.2f", calculatedCost)}")
            }

            delay(2500)
            if (_callState.value is CallState.Disconnected) {
                _callState.value = CallState.Idle
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioManager.release()
        callSpeechRecognizer.release()
        liveVoiceManager.release()
    }
}
