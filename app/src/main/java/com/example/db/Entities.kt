package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val avatarSeed: String,
    val description: String,
    val systemPrompt: String,
    val chatPrice: Double, // Cost per message interaction
    val callPrice: Double, // Cost per minute of mock call
    val isAIPersona: Boolean = true,
    val isEncrypted: Boolean = true
)

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val contactId: Int,
    val sender: String, // "user" or "bot" / contact name
    val encryptedContent: String, // AES Encrypted text stored in database
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "text" or "call" or "deposit"
    val contactName: String, // Contact involved in interaction (or "System" for deposit)
    val amount: Double, // Cost or credit added (+) / debited (-)
    val durationSec: Int = 0, // Duration in seconds for call transactions
    val timestamp: Long = System.currentTimeMillis()
)
