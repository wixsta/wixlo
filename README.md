# Wixlo (Android)

Minimal AI messaging and calling app with wallet billing.

## Features

- **Chats** — message AI contacts (billed per message)
- **Calls** — voice calls via speech recognition + Gemini + TTS (billed per minute)
- **Wallet** — balance, top-ups, transaction history

## Setup

1. Copy `.env.example` to `.env` and set `GEMINI_API_KEY`
2. Open the project in Android Studio
3. Build and run on a device or emulator

## Permissions

- `INTERNET` — Gemini API
- `RECORD_AUDIO` — voice calls
