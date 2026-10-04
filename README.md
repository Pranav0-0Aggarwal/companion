# Companion

A private, on-device Android companion that reads SMS, app notifications and Gmail, and turns them into a ledger, bills, reminders and an expiring OTP list. Sideloaded, single user, nothing leaves the phone.

## Modules

- `:core` pure Kotlin/JVM. Extraction, dedup, classifier slot, date and time slots, suggestions, query planner and validator, SentencePiece tokenizer, Decide input builder, calibration, template hashing and learned-rule logic. Tested without the Android SDK.
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

Decide runs on LiteRT `CompiledModel` (NPU, then GPU, then CPU) and only when rules are unsure. It settles a message only when its calibrated probability clears that label's own bar; otherwise the item stays ASK. Install the model and its calibration with:

```
adb push decide.tflite calibration.json /data/local/tmp/
adb shell run-as app.companion sh -c 'mkdir -p files/models && cp /data/local/tmp/decide.tflite /data/local/tmp/calibration.json files/models/'
```

## Calibration

`files/models/calibration.json` is checked against a pinned SHA-256 (`Models.calibration`) like `decide.tflite`, and loaded only when it matches. If it is missing, unverified, malformed or its labels do not match the model, the app uses temperature 1 and a bar of 0.97 for every label.

```
{ "version": 1,
  "tasks": {
    "type":     { "temperature": 1.4, "act": "sigmoid", "labels": ["otp", "expense", ...], "sure": { "promo": 0.98, "alert": 0.95 } },
    "category": { "temperature": 1.1, "labels": ["food", "groceries", ...], "sure": { "food": 0.96 } } } }
```

`act` is optional per task: `softmax` (default) gives `softmax(logits / temperature)`; `sigmoid` gives `sigmoid(logit / temperature)` per label, so labels are independent. With `sigmoid` the primary label is the argmax and every other label at 0.5 or more becomes a secondary tag. A label is Sure only when the primary's calibrated probability is at least its entry in `sure` (0.97 when absent); otherwise the item gets an ASK stamp. `labels` is optional and, when present, must equal the model's label order.

## Labels, tags and categories

`Labels` in `:core` is the one place that maps Decide labels to app kinds: otp to Otp, expense to Debit (CardSpend when the rules found a card), income to Credit, bill to Bill (Statement when the rules found one), delivery, alert, personal, promo, and spam to Spam. Amount, last4, merchant and due always come from the rules; the model only chooses the kind and tags. A label whose fields the rules did not find (for example expense on a message with no amount) cannot settle, so the item stays ASK. The `category` task maps to `Category` (food, groceries, shopping, transport, travel, bills, entertainment, health, transfer, other) and is used only when it is Sure and the rules had no category for the merchant.

Secondary tags are stored on the item and shown as small chips on Inbox and Ledger lines. Spam items are hidden from Today, listed under Inbox, Alerts with a SPAM stamp, and "Not spam" is a correction.

A bills debit whose merchant is a card issuer or CRED is a card payment: it carries a CARD PAYMENT stamp and is left out of spend totals in the Ledger, Today and Ask, because the card spends are already counted.

## Corrections

Answering an ASK item, changing the category of a settled ledger line, and marking "Not an OTP" or "Not spam" each write a `Correction` row (item id, task `type` or `category`, the model's guess and probability if any, the chosen label, and how it was corrected) and settle the item. No message text is copied into the row: the export joins on the item's stored title, note and message text.

After two consistent corrections of the same message template (sender brand plus the text with numbers masked, hashed) a `TemplateRule` auto-files identical messages. Rules are listed, and can be deleted, under Settings, Learned rules. A conflicting correction restarts the count.

Settings, Export corrections writes `companion-corrections.jsonl` (`sender`, `text`, `task`, `model`, `modelProb`, `chosen`, one corrected item per line, oldest first; `text` is the title and the full message text when it is still stored, otherwise the title and note) to the app cache and opens the share sheet to send it to a Mac or save it. Nothing is sent automatically. The cache file is removed shortly after sharing and on the next app start. Each export holds every correction, so later lines for the same message supersede earlier ones.

To pull it with adb, choose Save to Files or Downloads in the share sheet, then:

```
adb pull /sdcard/Download/companion-corrections.jsonl
```

or, while the share sheet is still open:

```
adb exec-out run-as app.companion cat cache/exports/companion-corrections.jsonl > companion-corrections.jsonl
```

The Mac side of the loop is in `tools/retrain/README.md`.

## Message text

Each item stores its message text in the encrypted database and Search matches it (full text index over title, merchant, note, bank, category and message text). Codes are never stored: OTP items keep only service, purpose, code and expiry, the code is deleted at expiry, and any message the model tags otp has no text kept. Digits that look like a code in other messages are masked before storing.

Settings, Privacy, Keep message text: 90 days, 1 year (default) or forever. A daily worker blanks text older than that; amounts, merchants, dates and tags stay. Only corrected items are exported with their text, and only through the share sheet.

## Security

Encrypted database (SQLCipher, passphrase wrapped by an Android Keystore AES-GCM key), `allowBackup=false`, no analytics, no logging of content, Gmail read-only with the token held in memory only, exported components limited to the launcher, SMS receiver, notification listener, Quick Settings tiles and the widget receiver. Corrections are shared only on request through a non-exported FileProvider limited to the cache exports folder.
