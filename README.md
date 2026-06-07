# Wixlo

Minimal AI calling and messaging app with wallet billing.

## Features

- **Contacts** — browse and search AI contacts
- **Messaging** — text chat billed per message
- **Calling** — voice calls via Gemini Live, billed per minute
- **Wallet** — balance, top-ups, and transaction history

## Run locally

**Prerequisites:** Node.js

1. Install dependencies: `npm install`
2. Copy `.env.example` to `.env.local` and set `GEMINI_API_KEY`
3. Start the app: `npm run dev`

Open http://localhost:3000

## Scripts

- `npm run dev` — development server
- `npm run build` — production build
- `npm run preview` — preview production build
- `npm run lint` — TypeScript check
