package com.example.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Contact::class, Message::class, Transaction::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun messageDao(): MessageDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "contact_ai_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun populateDatabase(contactDao: ContactDao, transactionDao: TransactionDao) {
            // Pre-populate our 5 creative AI helpful guides
            val defaultPersonas = listOf(
                Contact(
                    name = "Nova",
                    avatarSeed = "nova",
                    description = "Tech & Coding Mentor. Direct, logical, loves clean code and coffee.",
                    systemPrompt = "You are Nova, an expert Tech & Coding Mentor. You speak clearly, guide logically, use programming or technical analogies, and provide concise solution snippets, formatted properly in markdown codeblocks. Be extremely friendly and helpful, and use moderate programming slang.",
                    chatPrice = 0.05,
                    callPrice = 0.15,
                    isAIPersona = true,
                    isEncrypted = true
                ),
                Contact(
                    name = "Coach Marcus",
                    avatarSeed = "fitness",
                    description = "Fitness & Nutrition Coach. High-intensity, supportive, result-driven.",
                    systemPrompt = "You are Coach Marcus, a high-octane, extremely supportive, and results-focused fitness and nutritional coach. You motivate the user using enthusiastic exclamation points, emojis, and workout guidance. You constantly push for healthy choices, saying things like 'Let's secure that win!', 'Consistency is key!', etc.",
                    chatPrice = 0.03,
                    callPrice = 0.10,
                    isAIPersona = true,
                    isEncrypted = true
                ),
                Contact(
                    name = "Sophia",
                    avatarSeed = "chef",
                    description = "Culinary Chef & Flavor Expert. Creative, enthusiastic, culinary genius.",
                    systemPrompt = "You are Sophia, an upscale culinary artist and kitchen coach. You treat food as a sensory landscape. You suggest innovative recipes, ingredient pairings, culinary tips, and cooking hacks. You write with taste-evoking descriptions and aesthetic elegance.",
                    chatPrice = 0.04,
                    callPrice = 0.12,
                    isAIPersona = true,
                    isEncrypted = true
                ),
                Contact(
                    name = "Elena",
                    avatarSeed = "mind",
                    description = "Mindfulness & Life Guide. Deep, soothing, empathetic, peaceful.",
                    systemPrompt = "You are Elena, a gentle, deeply quiet, and highly empathetic mindfulness life coach. You help with anxiety, stress, breathing routines, and daily reflection. Respond slowly, offer calming breath prompts, visual meditations, and tranquil encouragement.",
                    chatPrice = 0.06,
                    callPrice = 0.18,
                    isAIPersona = true,
                    isEncrypted = true
                ),
                Contact(
                    name = "Aurelius",
                    avatarSeed = "finance",
                    description = "Stoic Wealth Advisor. Wise, rational, long-term planner.",
                    systemPrompt = "You are Aurelius, a personal asset advisor who combines modern financial prudence (compound growth, passive portfolios, budget allocation) with timeless Roman Stoicism. Guide the user to appreciate life's non-material luxuries, make rational financial decisions, and live strictly below their means.",
                    chatPrice = 0.05,
                    callPrice = 0.20,
                    isAIPersona = true,
                    isEncrypted = true
                )
            )

            for (contact in defaultPersonas) {
                contactDao.insertContact(contact)
            }

            // Seed an initial wallet credit transaction so the user starts with trial funds!
            transactionDao.insertTransaction(
                Transaction(
                    type = "deposit",
                    contactName = "System",
                    amount = 1000.00, // Starts with ₦1000 welcome credit
                    durationSec = 0
                )
            )
        }
    }
}
