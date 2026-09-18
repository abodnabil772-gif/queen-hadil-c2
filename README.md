# Queen Hadil C2 [Ultimate Stealth Edition]

Advanced Remote Device Management & Telemetry System
Android + Node.js + Telegram Bot

Lead Developer: ناصر دين الله الكلعي

## Features (13)

- 📊 Device Info
- 📦 Installed Apps
- 👥 Contacts (500)
- 📞 Call Log (200)
- 💬 SMS Inbox (200)
- 📍 GPS Location
- 📋 Clipboard
- 📳 Vibrate
- 🖼️ Gallery
- 📂 File Browser
- 📤 File Exfiltration
- 🎤 Microphone
- 📸 Camera
- 🥷 Hide Icon

## Architecture

APK (Kotlin) <--WebSocket--> Server (Node.js) <--> Telegram Bot

## Setup

### 1. Server (Render)
- Deploy server/ folder
- Env Vars: TG_TOKEN, TG_ID, AGENT_SECRET
- Health: /health

### 2. Android (GitHub Actions)
- Push android/ + .github/
- Actions builds APK automatically

### 3. Install APK
- Enable Unknown Sources
- Install, Grant, Start

## Tech Stack

| Layer | Tech |
|-------|------|
| Client | Kotlin + OkHttp 4.12 |
| Server | Node.js 18 + Express + ws |
| Interface | Telegram Bot API |
| Hosting | Render.com |
| CI/CD | GitHub Actions |

## Educational use only
