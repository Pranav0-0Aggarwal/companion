# Companion

A private Android companion that reads your SMS, app notifications, WhatsApp and Gmail and turns them into one calm place for money, bills, codes, deliveries, meetings and food. Everything runs on the phone. Nothing leaves it.

<p align="center">
  <a href="https://github.com/Pranav0-0Aggarwal/companion/releases/download/v0.6.1/companion-launch.mp4"><img src="docs/media/launch.gif" width="720" alt="Companion launch video: Today, Money, Ask, Food and Inbox running on the phone"></a>
</p>
<p align="center">
  <a href="https://github.com/Pranav0-0Aggarwal/companion/releases/download/v0.6.1/companion-launch.mp4">Watch with sound</a> · <a href="https://github.com/Pranav0-0Aggarwal/companion/releases">Download the app</a>
</p>

## Features

- **Today.** One timeline for the day: payments, deliveries, bills due, meetings from your calendar, meals and anything that needs you.
- **Inbox.** Every message and notification is sorted into spends, bills, deliveries, alerts, promos and spam. Tap a card to read the full message, correct it once and similar ones follow. Clean up settles a whole backlog in one tap and Clear notifications empties your notification shade of everything Companion already has, both with Undo.
- **Money.** A ledger with categories, a monthly budget, bills and card cycles, the card with the longest interest free window, and trips. Duplicate alerts are caught by their UPI reference and transfers between your own accounts stay out of spending.
- **Codes.** OTPs appear with a countdown, copy in one tap, fill the cover screen when they arrive and disappear at expiry. Dismiss one from the card or the notification.
- **Ask.** Chat in English or Hinglish: "Swiggy this week", "what needs me", "suggest a veg lunch under 600 kcal". Common questions are answered instantly without a model. Anything that changes data waits for your Confirm and offers Undo.
- **Food.** Log meals by chat or reply to a nudge, see calories and macros by meal, get meal ideas and track weight. Syncs with Health Connect.
- **Vault.** Keep documents with expiry reminders.
- **Cover screen.** Widgets and a full Today view for the Galaxy Z Flip outer screen, plus Quick Settings tiles.

## Private by design

- All data lives in an encrypted database (SQLCipher with an Android Keystore key). No accounts, no analytics, no content logging, backups off.
- The models run on the phone in isolated processes with no network and no permissions.
- The only network use is the model download you start from GitHub Releases, and Gmail if you connect it.
- One small model at a time, only when needed. Heavy work waits for the charger and backs off when the phone is warm.

## Install

Download the APK from [Releases](https://github.com/Pranav0-0Aggarwal/companion/releases) and sideload it. Android 14 or newer, arm64 with dot product and i8mm (Snapdragon 8 Gen 1, Exynos 2200, Tensor G3 or newer) for the on device models; older phones run on rules alone. The models download from Settings, On device AI, over Wi Fi.

## Build

```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :core:test
./gradlew :app:assembleDebug
```

Needs the NDK and CMake: `sdkmanager "ndk;28.2.13676358" "cmake;3.22.1"`. `local.properties` holds `sdk.dir` and an optional `gmail.webClientId`.

`:core` is plain Kotlin with all the parsing, sorting, duplicate and chat logic, tested without Android. `:app` is the Compose app, database, ingest, widgets and model services. How it all fits together, the models, memory budget and security model are in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
