# Companion

A private, on-device Android companion that reads SMS, app notifications and Gmail, and turns them into a ledger, bills, reminders and an expiring OTP list. Sideloaded, single user, nothing leaves the phone.

## Modules

- `:core` pure Kotlin/JVM. Extraction, dedup, classifier slot, date and time slots, suggestions, query planner and validator, SentencePiece tokenizer and Decide input builder. Tested without the Android SDK.
- `:app` Android app: Compose UI, encrypted Room database, ingest, widget, tiles, reminders, calendar.

## Build

```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :core:test
./gradlew :app:assembleDebug
```

`local.properties` (gitignored): `sdk.dir=...` and `gmail.webClientId=` (empty disables Gmail). compileSdk is 37 because current AndroidX and SQLCipher releases require it; targetSdk is 35, minSdk 34. `android.uniquePackageNames=false` is set because `litert` and `litert-api` share one manifest namespace.

## Ask

The search field answers data questions locally, for example "how much did I spend on food last month" or "Swiggy this week". `RulePlanner` (English and Hinglish) turns the text into typed queries, `Validator` rejects anything out of range, and the answer card drills into the Ledger. Reads run immediately; reminders and events need one tap to confirm. A second planner backend (a small tool-calling model in an isolated, permissionless process) is wired as `NeedleService` but ships without its native library.

## On-device models and memory budget

Two components, never resident together: `Governor` holds a mutex so only one model is loaded, unloads Decide after 30 s idle, refuses to load below 1.5 GB available or when the system reports low memory, and unloads everything on `onTrimMemory(UI_HIDDEN)` and above. Model files live in the app files dir (`files/models/`), are never in the repo or APK, and are checked against a pinned SHA-256 before loading.

| Budget | Limit |
| --- | --- |
| App | 200 MB |
| Decide (LiteRT, GLiNER2.5-Decide int8) | 600 MB |
| Needle (isolated service, later) | 60 MB |
| Peak | about 0.8 GB |
| Hard ceiling | 2 GB |

Decide runs on LiteRT `CompiledModel` (NPU, then GPU, then CPU) and only when rules are unsure; its answer stays an ASK item and never auto-files. Install a model with:

```
adb push decide.tflite /data/local/tmp/
adb shell run-as app.companion sh -c 'mkdir -p files/models && cp /data/local/tmp/decide.tflite files/models/'
```

## Security

Encrypted database (SQLCipher, passphrase wrapped by an Android Keystore AES-GCM key), `allowBackup=false`, no analytics, no logging of content, Gmail read-only with the token held in memory only, exported components limited to the launcher, SMS receiver, notification listener, Quick Settings tiles and the widget receiver.
