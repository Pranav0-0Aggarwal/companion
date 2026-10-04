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

Native build: `:app` compiles a tiny JNI shim for the Needle query model with the NDK (arm64-v8a only). Install the pinned toolchain once with `sdkmanager "ndk;28.2.13676358" "cmake;3.22.1"`. The Gradle task `fetchNeedle` downloads `libneedle.a` and `needle.h` from Hugging Face `Cactus-Compute/needle3` at revision `27c0a9a5b3ca835e0b7dbeaccf555df03dac493d` into `app/build/needle/`, checks both against pinned SHA-256s and fails the build on a mismatch. The binary is never committed. CI and the release workflow install the same NDK and CMake.

`local.properties` (gitignored): `sdk.dir=...` and `gmail.webClientId=` (empty disables Gmail). compileSdk is 37 because current AndroidX and SQLCipher releases require it; targetSdk is 35, minSdk 34. `android.uniquePackageNames=false` is set because `litert` and `litert-api` share one manifest namespace.

## Ask

The search field answers data questions locally, for example "how much did I spend on food last month" or "Swiggy this week". `RulePlanner` (English and Hinglish) turns the text into typed queries first. Only when it finds nothing, and the text has at least three words, the Needle 3 tool-calling model gets a turn: it picks one of six tools (`sum_spend`, `list_transactions`, `list_bills`, `top_merchants`, `create_reminder`, `create_event`) and copies the date phrase word for word; `Phrase` resolves the phrase against today in Asia/Kolkata and `Validator` rejects anything out of range, with an unknown tool, or with a bad argument. If anything is invalid the screen says "Couldn't understand, try 'food last month'".

Every answer card starts with the interpreted query, for example "Food · 1 to 30 Sep 2026", and an Edit chip that opens the fields (category, merchant, card, dates, or title and time) so a wrong parse is visible and fixable. Reads run immediately; reminders and events need one tap to confirm.

Needle runs only inside `NeedleService`, declared `android:isolatedProcess="true"`: no network, no permissions, no access to the app's files. The app opens `needle3.cact` and passes the file descriptor over the binder; the isolated process maps it with `mmap` and hands the buffer to `needle_load`, so the model is never copied. The JNI shim sets `NEEDLE_TELEMETRY=0` and `DO_NOT_TRACK=1` before loading. Queries, prompts and outputs are never logged. The service is bound on demand and unbound after 60 s idle; its process is killed when it is destroyed, which frees the model.

## On-device models and memory budget

Two components, never resident together: `Governor` holds a mutex so only one model is loaded, unloads Decide after 30 s idle and Needle after 60 s idle, refuses to load below 1.5 GB available or when the system reports low memory, and unloads everything on `onTrimMemory(UI_HIDDEN)` and above. Without any model the app runs on rules alone.

| Budget | Limit |
| --- | --- |
| App | 200 MB |
| Decide (LiteRT, GLiNER2.5-Decide int8) | 600 MB |
| Needle 3 (isolated process, CPU) | 150 MB |
| Peak (app + Decide, or app + Needle) | about 0.8 GB |
| Hard ceiling | 2 GB |

Decide runs on LiteRT `CompiledModel` (NPU, then GPU, then CPU) and only when rules are unsure. Its input follows `schema_prefix.json` `steps` exactly: the template "Text message from {sender}:\n{body}", a full stop appended when the text does not end in `.`, `!` or `?`, the schema's word splitter, lowercase, per-word unigram encoding over NFC text with consecutive unknowns fused, then the task prefix, truncation to 384 and padding to bucket 256 or 384. `:core` tests compare every step with ids produced by the Python reference (200 tokenizer vectors and 13 synthetic messages) and check the argmax of the int8 model's logits against the reference labels. It settles a message only when its calibrated probability clears that label's own bar; otherwise the item stays ASK.

### Model files

`Manifest` in `app/src/main/kotlin/app/companion/ai/Models.kt` pins each file's name, size and SHA-256. A file is used only if it matches; an entry with a zero size or an empty or malformed SHA-256 is treated as not pinned and never installs.

| File | Role |
| --- | --- |
| `decide.tflite` | Decide, int8 LiteRT (about 495 MB) |
| `tokenizer.dtk` | SentencePiece unigram vocabulary |
| `schema_prefix.json` | task prefixes, labels and signatures |
| `calibration.json` | temperatures and per-label bars |
| `needle3.cact` | Cactus Needle 3 query model (about 35 MB) |

Settings, On-device AI lists each file as not installed, downloading x%, ready or custom, and has a Download models button (about 520 MB, Wi-Fi only). The download is a WorkManager job with an unmetered-network constraint: HTTPS only, from `https://github.com/Pranav0-0Aggarwal/companion/releases/download/models-v1/<file>`, following redirects only to `release-assets.githubusercontent.com`. It resumes with a `Range` request into `<file>.part` under `noBackupFilesDir`, checks the size and SHA-256, then moves the file atomically into `files/models/`. It can be cancelled and picks up where it stopped. Nothing else in the app uses the network apart from Gmail.

### Private models with adb

Files in `files/models/custom/` win over the downloaded base files. List each file's SHA-256 in `custom.json` next to them; a file whose hash does not match is ignored and the base file is used. Only the five manifest names are accepted, and Settings shows them as custom.

```
echo '{"decide.tflite": "<sha256>", "needle3.cact": "<sha256>"}' > custom.json
adb push decide.tflite needle3.cact custom.json /data/local/tmp/
adb shell run-as app.companion sh -c 'mkdir -p files/models/custom && cp /data/local/tmp/decide.tflite /data/local/tmp/needle3.cact /data/local/tmp/custom.json files/models/custom/'
```

`run-as` only works on a debuggable build (`assembleDebug`).

## Calibration

`calibration.json` is checked against its pinned SHA-256 (`Manifest.calibration`) like the other model files, and loaded only when it matches. If it is missing, unverified, malformed or its labels do not match the model, the app uses temperature 1 and a bar of 0.97 for every label.

```
{ "version": 1,
  "tasks": {
    "type":     { "temperature": 1.4, "act": "sigmoid", "labels": ["otp", "expense", ...], "sure": { "promo": 0.98, "alert": 0.95 } },
    "category": { "temperature": 1.1, "labels": ["food", "groceries", ...], "sure": { "food": 0.96 } } } }
```

`act` is optional per task: `softmax` (default) gives `softmax(logits / temperature)`; `sigmoid` gives `sigmoid(logit / temperature)` per label, so labels are independent. With `sigmoid` the primary label is the argmax and every other label at 0.5 or more becomes a secondary tag. A label is Sure only when the primary's calibrated probability is at least its entry in `sure` (0.97 when absent; an entry of 1.01 means never Sure, which the base calibration uses for most labels); otherwise the item gets an ASK stamp. `labels` is optional and, when present, must equal the model's label order.

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

Encrypted database (SQLCipher, passphrase wrapped by an Android Keystore AES-GCM key), `allowBackup=false`, no analytics, no logging of content, Gmail read-only with the token held in memory only, the only other network use is the user-started model download from GitHub Releases, the Needle model runs in an isolated process with no network or permissions, exported components limited to the launcher, SMS receiver, notification listener, Quick Settings tiles and the widget receiver. Corrections are shared only on request through a non-exported FileProvider limited to the cache exports folder.
