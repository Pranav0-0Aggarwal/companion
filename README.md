# Companion

A private, on-device Android companion that reads SMS, app notifications and Gmail, and turns them into a ledger, bills, reminders and an expiring OTP list. Sideloaded, single user, nothing leaves the phone.

## Modules

- `:core` pure Kotlin/JVM. Extraction, dedup, classifier slot, date and time slots, suggestions, query planner and validator, SentencePiece tokenizer, byte-level BPE tokenizer, Decide and ModernBERT input builders, calibration, template hashing and learned-rule logic. Tested without the Android SDK.
- `:app` Android app: Compose UI, encrypted Room database, ingest, widget, tiles, reminders, calendar.

## Build

```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :core:test
./gradlew :app:assembleDebug
```

Native build: `:app` compiles two tiny JNI shims with the NDK (arm64-v8a only): `needle_jni` for the Needle query model and `nux_jni` for NuExtract on llama.cpp. Install the pinned toolchain once with `sdkmanager "ndk;28.2.13676358" "cmake;3.22.1"`. The Gradle task `fetchNeedle` downloads `libneedle.a` and `needle.h` from Hugging Face `Cactus-Compute/needle3` at revision `27c0a9a5b3ca835e0b7dbeaccf555df03dac493d` into `app/build/needle/`, checks both against pinned SHA-256s and fails the build on a mismatch. `fetchLlama` downloads the llama.cpp `b11306` source tarball and the KleidiAI `v1.24.0` source tarball over HTTPS into `app/build/llama/`, checks both against pinned SHA-256s, rejects archive paths that escape the target and fails the build on a mismatch; CMake gets the KleidiAI directory through `FETCHCONTENT_SOURCE_DIR_KLEIDIAI` with `FETCHCONTENT_FULLY_DISCONNECTED`, so configuring never touches the network. Nothing fetched is committed. CI runs both fetch tasks before it blackholes the model hosts; the release workflow runs them as part of the build.

llama.cpp is built as static libraries with `BUILD_SHARED_LIBS=OFF`, `GGML_CPU_ARM_ARCH=armv8.6-a+dotprod+i8mm`, `GGML_CPU_KLEIDIAI=ON`, `GGML_OPENMP=OFF`, `GGML_NATIVE=OFF`, `GGML_LLAMAFILE=OFF`, `LLAMA_CURL=OFF`, `LLAMA_OPENSSL=OFF`, no common library, tests, examples, tools or server, `-O3` and ThinLTO. The build needs a CPU with dot product and i8mm (Snapdragon 8 Gen 1, Exynos 2200, Tensor G2 and newer); `nux_jni` checks both hardware capabilities at load and fails closed on older phones, which then keep the rules-only result.

`local.properties` (gitignored): `sdk.dir=...` and `gmail.webClientId=` (empty disables Gmail). compileSdk is 37 because current AndroidX and SQLCipher releases require it; targetSdk is 35, minSdk 34. `android.uniquePackageNames=false` is set because `litert` and `litert-api` share one manifest namespace.

## Ask

The search field answers data questions locally, for example "how much did I spend on food last month" or "Swiggy this week". `RulePlanner` (English and Hinglish) turns the text into typed queries first. Only when it finds nothing, and the text has at least three words, the Needle 3 tool-calling model gets a turn: it picks one of six tools (`sum_spend`, `list_transactions`, `list_bills`, `top_merchants`, `create_reminder`, `create_event`) and copies the date phrase word for word; `Phrase` resolves the phrase against today in Asia/Kolkata and `Validator` rejects anything out of range, with an unknown tool, or with a bad argument. If anything is invalid the screen says "Couldn't understand, try 'food last month'".

Every answer card starts with the interpreted query, for example "Food · 1 to 30 Sep 2026", and an Edit chip that opens the fields (category, merchant, card, dates, or title and time) so a wrong parse is visible and fixable. Reads run immediately; reminders and events need one tap to confirm.

Needle runs only inside `NeedleService`, declared `android:isolatedProcess="true"`: no network, no permissions, no access to the app's files. The app opens `needle3.cact` and passes the file descriptor over the binder; the isolated process maps it with `mmap` and hands the buffer to `needle_load`, so the model is never copied. The JNI shim sets `NEEDLE_TELEMETRY=0` and `DO_NOT_TRACK=1` before loading. Queries, prompts and outputs are never logged. The service is bound on demand and unbound after 60 s idle; its process is killed when it is destroyed, which frees the model.

## On-device models and memory budget

Three components, never resident together: `Governor` holds a mutex so only one model is loaded, unloads Decide after 30 s idle and Needle and NuExtract after 60 s idle, refuses to load below 1.5 GB available or when the system reports low memory, and unloads everything on `onTrimMemory(UI_HIDDEN)` and above. Without any model the app runs on rules alone.

| Budget | Limit |
| --- | --- |
| App | 200 MB |
| Decide (LiteRT, GLiNER2.5-Decide int8) | 600 MB |
| Needle 3 (isolated process, CPU) | 150 MB |
| NuExtract-1.5-tiny q4_0 (llama.cpp, isolated process, CPU, 2 threads) | 600 MB |
| Peak (app + Decide, or app + Needle, or app + NuExtract) | about 0.8 GB |
| Hard ceiling | 2 GB |

### Smart extraction (NuExtract)

When the rules leave a gap (an amount they cannot read, a bill with no due date, a card whose last four digits they missed), NuExtract-1.5-tiny reads the text and proposes values; it is a fallback behind Decide and never a classifier. `Gaps` lists what is missing, `Nux.prompt` builds the exact NuExtract template prompt for `amount`, `due_date` and `card_last4` (text cut to 600 characters), and decoding is constrained by an embedded GBNF grammar so the output is always that JSON shape. `Nux.parse` reads it as untrusted input: only those three keys, only strings of at most 40 characters. Every value must then pass `Verbatim` against the message: an amount must be a standalone number in the text, a date must appear in the text and parse, and a card last4 must be four digits present in the text, bare or as XX1234. OTP codes and merchants are never taken from the model; merchants stay regex only.

A value is filled in automatically only when the classifier's kind is money (expense, income, card spend) or Bill or Statement: the rules event is one of those, or Decide is sure of one of those labels. For any other kind the model is not asked and the item stays ASK. Suggestions are not stored, because the data model has no field for them.

`Pending` runs the background refine in two passes inside one `Governor.hold`: first rules and Decide for every queued item (Decide loads once and its scores are memoised per message), then, only for items that still have a gap and only when the phone is not warm (thermal below MODERATE) and battery saver is off, NuExtract for all of them in one load. Otherwise the items keep their rules and Decide result, stay ASK. Bulk Import and Reprocess never call NuExtract.

NuExtract runs only inside `NuxService`, a sibling of `NeedleService` declared `android:isolatedProcess="true"`: no network, no permissions, no access to the app's files. The app opens the GGUF and passes the file descriptor over the binder; the shim duplicates it and lets llama.cpp memory-map the weights from that descriptor. The service thread runs at `THREAD_PRIORITY_BACKGROUND` and the context uses 2 threads. The context is 768 tokens with a 512 token batch and a greedy sampler behind the grammar sampler, which stops at the end of the grammar or end of sequence. The shim keeps the tokens in sequence 0 of the KV cache, so the constant template prefix is evaluated once per load: each call tokenizes the whole prompt, keeps the longest common token prefix with the cached tokens, removes the rest with `llama_memory_seq_rm`, decodes only the new tokens and removes the generated tokens afterwards. Output is capped at 4 KB and everything is freed on every error path. Text, prompts and outputs are never logged and the llama.cpp log is silenced. After 60 s idle the service is unbound and its process is killed, which frees the model.

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
| `nuextract-tiny-q4_0.gguf` | NuExtract-1.5-tiny q4_0, Smart extraction (release `models-v2`; not pinned yet, so it never installs) |

Settings, On-device AI lists each file (the NuExtract row is labelled Smart extraction) as not installed, queued, downloading x%, verifying, paused, waiting for Wi-Fi, ready or custom, with one progress bar for the whole set (MB done of total, speed, time left) and Download, Pause, Resume and Cancel buttons. It refreshes live. Files go in this order: `needle3.cact`, the small tokenizer, schema and calibration files, `decide.tflite`, then `nuextract-tiny-q4_0.gguf`, so Ask works first and the extraction model, which needs Decide to be used, comes last.

The download is an Android 14 user-initiated data transfer job (`ModelJob`, `JobInfo.setUserInitiated(true)`, unmetered network, `RUN_USER_INITIATED_JOBS`, service bound only by `BIND_JOB_SERVICE`, not exported). It is scheduled from the Download or Resume tap and shows an ongoing progress notification (percent, MB, speed, time left) with a Cancel action; every `PendingIntent` is `FLAG_IMMUTABLE`. User-initiated jobs have no 10-minute cutoff. If the system stops the job (Wi-Fi lost, timeout) it is rescheduled, and a WorkManager job with the same unmetered constraint is queued as a fallback, which also runs after a reboot while a download is wanted. Only one runner is active at a time. A full disk is not retried: Settings and the notification say "Not enough storage: need X MB free".

Transport rules are unchanged: HTTPS only, from `https://github.com/Pranav0-0Aggarwal/companion/releases/download/<tag>/<file>` where each manifest entry names its release tag (`models-v1` for every file except NuExtract, `models-v2`), redirects only to `release-assets.githubusercontent.com` (at most 4), no credentials or ports. Files over 32 MB use 4 parallel `Range` connections that write 8 MB pieces at their offsets into a preallocated `<file>.part` under `noBackupFilesDir`; `<file>.part.ranges` records the completed byte ranges (with the pinned size and SHA-256 in its header, so a mismatching sidecar is ignored), so a resume fetches only the missing ranges. The signed redirect URL is resolved once per file with a one-byte `Range` probe (which also checks the total size), reused for every piece and resolved again on 403 or 410. Smaller files use one stream. Whatever the path, the whole file's size and SHA-256 are checked before the atomic move into `files/models/`; a mismatch deletes the part and sidecar, and an entry that is not pinned never downloads. Nothing else in the app uses the network apart from Gmail.

### Private models

Files in `files/models/custom/` win over the downloaded base files. List each file's SHA-256 in `custom.json` next to them; a file whose hash does not match is ignored and the base file is used. Only the six manifest names, `model_spec.json` and the private ModernBERT files below are accepted, and Settings shows them as custom.

**Release build (no adb needed).** Copy `custom.json` and the model files to the phone's Downloads folder (USB file transfer, Quick Share or `adb push`), then open Settings, On-device AI, Import private model files and select them all at once. The app copies them into private staging storage, checks every SHA-256 against the picked `custom.json`, and only if all of them match replaces the files in `files/models/custom/`. Any rejected name, mismatch or unlisted file aborts the whole import and nothing changes; the result list shows which file failed and why. No storage permission is used. Afterwards the classifier is re-detected and the reprocess banner appears if the model changed. Remove private models in the same place wipes `files/models/custom/` and returns to the base models.

Rules for the import: names must be plain file names (no separators or `..`) from the list above; `custom.json` must be picked and must list every other picked file except `model_spec.json`; a file is at most 2 GB; the picked `custom.json` replaces the old one, so it must list every private file that should stay active.

```
cd ~/models/modernbert   # holds model_spec.json type.tflite category.tflite tokenizer.json calibration.json
{ echo '{'; for f in type.tflite category.tflite tokenizer.json calibration.json; do printf '  "%s": "%s",\n' "$f" "$(shasum -a 256 "$f" | cut -d' ' -f1)"; done | sed '$ s/,$//'; echo '}'; } > custom.json
adb shell mkdir -p /sdcard/Download/companion-models
adb push model_spec.json type.tflite category.tflite tokenizer.json calibration.json custom.json /sdcard/Download/companion-models/
```

On the phone, tap Import private model files, open Downloads, then companion-models, select all six files and confirm. Add `"decide.tflite": "<sha256>"` or `"needle3.cact": "<sha256>"` to `custom.json` (and push those files too) to install them in the same import.

**Debug build (adb).** `run-as` only works on a debuggable build (`assembleDebug`):

```
echo '{"decide.tflite": "<sha256>", "needle3.cact": "<sha256>"}' > custom.json
adb push decide.tflite needle3.cact custom.json /data/local/tmp/
adb shell run-as app.companion sh -c 'mkdir -p files/models/custom && cp /data/local/tmp/decide.tflite /data/local/tmp/needle3.cact /data/local/tmp/custom.json files/models/custom/'
```

### Private ModernBERT classifier

An optional backend replaces Decide for message type and category. Its weights are fine-tuned on private messages, so they are never bundled, downloaded or published: they reach the phone only through `files/models/custom/`, like the other private models.

| File | Role |
| --- | --- |
| `model_spec.json` | `"arch": "modernbert-classifier"`, template, buckets, pad id, per task file, labels and signatures |
| `type.tflite` | int8 LiteRT classifier, 9 labels: otp, expense, income, bill, delivery, alert, personal, promo, spam |
| `category.tflite` | int8 LiteRT classifier, 10 labels: food, groceries, shopping, transport, travel, bills, entertainment, health, transfer, other |
| `tokenizer.json` | the public `answerdotai/ModernBERT-large` tokenizer |
| `calibration.json` | version 2 (same shape as version 1 plus `"model"`), optional |
| `custom.json` | file name to SHA-256 for every file above except `model_spec.json` |

```
{ "arch": "modernbert-classifier", "model": "modernbert-large-sms-v1",
  "template": "{sender}: {text}", "max_len": 128, "buckets": [32, 64, 128], "pad_id": 50283,
  "cls_id": 50281, "sep_id": 50282, "tokenizer": "tokenizer.json",
  "inputs": ["input_ids", "attention_mask"],
  "tasks": {
    "type":     { "file": "type.tflite",     "labels": ["otp", "expense", ...], "signatures": { "32": "serving_default_32", "64": "...", "128": "..." } },
    "category": { "file": "category.tflite", "labels": ["food", "groceries", ...], "signatures": { "...": "..." } } } }
```

Only `arch`, `buckets` and `tasks.type` are required. `template` defaults to `{sender}: {text}`, `max_len` to the largest bucket, `tokenizer` to `tokenizer.json`, `inputs` to `["input_ids", "attention_mask"]` (a list that starts with the mask swaps the two buffers), and `pad_id`, `cls_id` and `sep_id` to the ids of `[PAD]`, `[CLS]` and `[SEP]` in the tokenizer. `signatures` is optional per task and maps a bucket to a LiteRT signature key. A spec without a `category` task never runs one.

Install them with the release flow above (the example there pushes exactly these files). On a debuggable build the same files can be copied with `adb shell run-as app.companion` into `files/models/custom/`.

If an earlier `custom.json` listed `decide.tflite` or `needle3.cact`, keep those entries in the new one.

ModernBERT is used for classification (live refine, import, reprocess) only when `model_spec.json` has the right `arch` and every file it names (the tokenizer and each task file) is listed in `custom.json` with a matching SHA-256. Otherwise the app keeps the GLiNER Decide path, and removing `model_spec.json` switches back. Settings, On-device AI shows "Message classifier: ModernBERT (custom)" or "GLiNER (base)". The processing key is a hash of the model file hashes and the active custom calibration hash, so swapping a model or its calibration shows the reprocess banner, as does switching backend.

The input is `"{sender}: {text}"` (codes redacted as for Decide), byte-level BPE encoded with the pre-tokenizer regex, merge ranks and added tokens from `tokenizer.json`, then `[CLS]`, up to 126 ids, `[SEP]`, padded to the smallest bucket that fits. `:core` tests compare the ids of 215 synthetic messages with the Hugging Face `tokenizers` output for the `tokenizer.json` of `answerdotai/ModernBERT-large` at revision `45bb4654a4d5aaff24dd11d4781fa46d39bf8c13` (`tools/bert/vectors.py` regenerates them) and check its SHA-256. `type.tflite` loads on demand and `category.tflite` only when the type is expense, never both at once: the Governor's one-model rule applies, each unloads after 30 s idle, and inference runs at background thread priority on the same NPU, GPU, CPU order as Decide. A load per message type is paid when type and category alternate, so a reprocess with many expense messages is slower than with Decide.

The calibration applies only if it is listed in `custom.json`, its labels equal the spec's, and its `model` equals the spec's `model` (when the spec has one); otherwise temperature 1 and a 0.97 bar apply. The model's label is Sure only when `softmax(logits / temperature)` reaches that label's `sure` bar.

## Calibration

`calibration.json` (version 1, or version 2 with a `"model"` name for a private model) is checked against its pinned SHA-256 (`Manifest.calibration`) like the other model files, and loaded only when it matches. If it is missing, unverified, malformed or its labels do not match the model, the app uses temperature 1 and a bar of 0.97 for every label.

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

Encrypted database (SQLCipher, passphrase wrapped by an Android Keystore AES-GCM key), `allowBackup=false`, no analytics, no logging of content, Gmail read-only with the token held in memory only, the only other network use is the user-started model download from GitHub Releases (a non-exported job service and cancel receiver), the Needle and NuExtract models run in isolated processes with no network or permissions, exported components limited to the launcher, SMS receiver, notification listener, Quick Settings tiles and the widget receiver. Corrections are shared only on request through a non-exported FileProvider limited to the cache exports folder.
