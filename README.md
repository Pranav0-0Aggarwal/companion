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

Native build: `:app` compiles one JNI library, `llm_jni`, with the NDK (arm64-v8a only): `NuxJni` runs NuExtract and `ChatJni` runs the conversation model, both on llama.cpp. Install the pinned toolchain once with `sdkmanager "ndk;28.2.13676358" "cmake;3.22.1"`. `fetchLlama` downloads the llama.cpp `b11306` source tarball and the KleidiAI `v1.24.0` source tarball over HTTPS into `app/build/llama/`, checks both against pinned SHA-256s, rejects archive paths that escape the target and fails the build on a mismatch; CMake gets the KleidiAI directory through `FETCHCONTENT_SOURCE_DIR_KLEIDIAI` with `FETCHCONTENT_FULLY_DISCONNECTED`, so configuring never touches the network. Nothing fetched is committed. CI runs both fetch tasks before it blackholes the model hosts; the release workflow runs them as part of the build.

llama.cpp is built as static libraries with `BUILD_SHARED_LIBS=OFF`, `GGML_CPU_ARM_ARCH=armv8.6-a+dotprod+i8mm`, `GGML_CPU_KLEIDIAI=ON`, `GGML_OPENMP=OFF`, `GGML_NATIVE=OFF`, `GGML_LLAMAFILE=OFF`, `LLAMA_CURL=OFF`, `LLAMA_OPENSSL=OFF`, no common library, tests, examples, tools or server, `-O3` and ThinLTO. The build needs a CPU with dot product and i8mm (Snapdragon 8 Gen 1, Exynos 2200, Tensor G3 and newer); `llm_jni` checks both hardware capabilities at load and fails closed on older phones, which then keep the rules-only result.

`local.properties` (gitignored): `sdk.dir=...` and `gmail.webClientId=` (empty disables Gmail). compileSdk is 37 because current AndroidX and SQLCipher releases require it; targetSdk is 35, minSdk 34. `android.uniquePackageNames=false` is set because `litert` and `litert-api` share one manifest namespace.

## Ask

The search field answers data questions locally, for example "how much did I spend on food last month" or "Swiggy this week". `RulePlanner` (English and Hinglish) turns the text into typed queries first and those rule answers are instant. Everything else goes to the Conversation model (Chat, below). If the model is not installed or the phone cannot run it, the screen falls back to the matching messages.

Every answer card starts with the interpreted query, for example "Food · 1 to 30 Sep 2026", and an Edit chip that opens the fields (category, merchant, card, dates, or title and time) so a wrong parse is visible and fixable. Reads run immediately; reminders and events need one tap to confirm.

## Chat

The Conversation model (Qwen3.5-2B Q4_0, or LFM2.5-1.2B-Instruct as the alternative; the spec names are in `Manifest.chat` and `Manifest.chatLfm`) runs only inside `ChatService`, declared `android:isolatedProcess="true"`: no network, no permissions, no access to the app's files. The app opens the GGUF and passes the file descriptor over the binder; `ChatJni` maps it, tokenizes the prompt, reuses the KV cache for the longest common prefix (system prompt and tool schema stay cached), and streams each token back over a Messenger. Generation is constrained by a GBNF grammar built from the tool registry, so the model can only emit `{"tool":..,"args":..}` or `{"say":..}`. Threads are 4 while charging and 2 on battery; background work runs at background priority.

`ChatEngine` stays in the app process. It renders the conversation (last 12 turns, `ChatTemplate.Qwen` or `ChatTemplate.Lfm`), runs up to 4 tool calls per turn against `Repo` (spend, bills, cards, balance, log_meal, food_today, set_kcal, log_weight, start_trip, end_trip, trip_summary, vault_find, vault_add, remind, calendar_add, open), and streams the answer. Tools never run inside the isolated process. `vault_find` returns masked numbers only. The model is unloaded after 60 s idle. Prompts, chat text, food and documents are never logged.

Food logging resolves each item through personal SKUs, brand menus and the bundled food database (`food_db.json`, `brand_menus.json`) and asks a short question when unsure; the answer is saved as a SKU. Meal and weight nudges are notifications with an inline reply that a background worker hands to Chat. Trips, the document vault and Health Connect sit on the same repo.

## On-device models and memory budget

Three components, never resident together: `Governor` holds a mutex so only one model is loaded, unloads Decide after 30 s idle and the Conversation model and NuExtract after 60 s idle, refuses to load below 1.5 GB available or when the system reports low memory, and unloads everything on `onTrimMemory(UI_HIDDEN)` and above. Without any model the app runs on rules alone.

| Budget | Limit |
| --- | --- |
| App | 200 MB |
| Decide (LiteRT, GLiNER2.5-Decide int8) | 600 MB |
| Conversation, Qwen3.5-2B Q4_0 (llama.cpp, isolated process, CPU, 2 to 4 threads) | 1.6 GB |
| NuExtract-1.5-tiny q4_0 (llama.cpp, isolated process, CPU, 2 threads) | 600 MB |
| Peak (app + Decide, or app + NuExtract, or app + Conversation) | about 1.8 GB |
| Hard ceiling | 2 GB |

### Smart extraction (NuExtract)

When the rules leave a gap (an amount they cannot read, a bill with no due date, a card whose last four digits they missed), NuExtract-1.5-tiny reads the text and proposes values; it is a fallback behind Decide and never a classifier. `Gaps` lists what is missing, `Nux.prompt` builds the exact NuExtract template prompt for `amount`, `due_date` and `card_last4` (text cut to 600 characters), and decoding is constrained by an embedded GBNF grammar so the output is always that JSON shape. `Nux.parse` reads it as untrusted input: only those three keys, only strings of at most 40 characters. Every value must then pass `Verbatim` against the message: an amount must be a standalone number in the text, a date must appear in the text and parse, and a card last4 must be four digits present in the text, bare or as XX1234. OTP codes and merchants are never taken from the model; merchants stay regex only.

A value is filled in automatically only when the classifier's kind is money (expense, income, card spend) or Bill or Statement: the rules event is one of those, or Decide is sure of one of those labels. For any other kind the model is not asked and the item stays ASK. Suggestions are not stored, because the data model has no field for them.

`Pending` runs the background refine in two passes inside one `Governor.hold`: first rules and Decide for every queued item (Decide loads once and its scores are memoised per message), then, only for items that still have a gap and only when the phone is not warm (thermal below MODERATE) and battery saver is off, NuExtract for all of them in one load. Otherwise the items keep their rules and Decide result, stay ASK. Bulk Import and Reprocess never call NuExtract.

Phones without dot product and i8mm (read from `/proc/cpuinfo` once per process) never download the model, show "Not supported on this phone's CPU" in Settings and never bind `NuxService`; a native load failure also disables it until the process restarts. With the ModernBERT classifier, a batch (Pending, Reprocess, Import) runs every type inference with the type model loaded once, then loads the category model once for the expense items only.

NuExtract runs only inside `NuxService`, a sibling of `ChatService` declared `android:isolatedProcess="true"`: no network, no permissions, no access to the app's files. The app opens the GGUF and passes the file descriptor over the binder; the shim duplicates it and lets llama.cpp memory-map the weights from that descriptor. The service thread runs at `THREAD_PRIORITY_BACKGROUND` and the context uses 2 threads. The context is 768 tokens with a 512 token batch and a greedy sampler behind the grammar sampler, which stops at the end of the grammar or end of sequence. The shim keeps the tokens in sequence 0 of the KV cache, so the constant template prefix is evaluated once per load: each call tokenizes the whole prompt in one pass, exactly as the model was evaluated, after `Nux.clip` breaks up any `<|` and `|>` in the message so token-like text inside an SMS stays plain text, keeps the longest common token prefix with the cached tokens, removes the rest with `llama_memory_seq_rm`, decodes only the new tokens and removes the generated tokens afterwards. Output is capped at 4 KB and everything is freed on every error path. Text, prompts and outputs are never logged and the llama.cpp log is silenced. After 60 s idle the service is unbound and its process is killed, which frees the model.

Decide runs on LiteRT `CompiledModel` (NPU, then GPU, then CPU) and only when rules are unsure. Its input follows `schema_prefix.json` `steps` exactly: the template "Text message from {sender}:\n{body}", a full stop appended when the text does not end in `.`, `!` or `?`, the schema's word splitter, lowercase, per-word unigram encoding over NFC text with consecutive unknowns fused, then the task prefix, truncation to 384 and padding to bucket 256 or 384. `:core` tests compare every step with ids produced by the Python reference (200 tokenizer vectors and 13 synthetic messages) and check the argmax of the int8 model's logits against the reference labels. It settles a message only when its calibrated probability clears that label's own bar; otherwise the item stays ASK.

### Model files

`Manifest` in `app/src/main/kotlin/app/companion/ai/Models.kt` pins each file's name, size and SHA-256. A file is used only if it matches; an entry with a zero size or an empty or malformed SHA-256 is treated as not pinned and never installs.

| File | Role |
| --- | --- |
| `decide.tflite` | Decide, int8 LiteRT (about 495 MB) |
| `tokenizer.dtk` | SentencePiece unigram vocabulary |
| `schema_prefix.json` | task prefixes, labels and signatures |
| `calibration.json` | temperatures and per-label bars |
| `qwen3.5-2b-q4_0.gguf` | Conversation model (about 1.3 GB; size and SHA-256 are filled in when published) |
| `nuextract-tiny-q4_0.gguf` | NuExtract-1.5-tiny q4_0, Smart extraction (release `models-v2`, about 352 MB) |

Settings, On-device AI lists each file (the NuExtract row is labelled Smart extraction) as not installed, queued, downloading x%, verifying, paused, waiting for Wi-Fi, ready or custom, with one progress bar for the whole set (MB done of total, speed, time left) and Download, Pause, Resume and Cancel buttons. It refreshes live. Files go in this order: `qwen3.5-2b-q4_0.gguf`, the small tokenizer, schema and calibration files, `decide.tflite`, then `nuextract-tiny-q4_0.gguf`, so Ask works first and the extraction model, which needs Decide to be used, comes last.

The download is an Android 14 user-initiated data transfer job (`ModelJob`, `JobInfo.setUserInitiated(true)`, unmetered network, `RUN_USER_INITIATED_JOBS`, service bound only by `BIND_JOB_SERVICE`, not exported). It is scheduled from the Download or Resume tap and shows an ongoing progress notification (percent, MB, speed, time left) with a Cancel action; every `PendingIntent` is `FLAG_IMMUTABLE`. User-initiated jobs have no 10-minute cutoff. If the system stops the job (Wi-Fi lost, timeout) it is rescheduled, and a WorkManager job with the same unmetered constraint is queued as a fallback, which also runs after a reboot while a download is wanted. Only one runner is active at a time. A full disk is not retried: Settings and the notification say "Not enough storage: need X MB free".

Transport rules are unchanged: HTTPS only, from `https://github.com/Pranav0-0Aggarwal/companion/releases/download/<tag>/<file>` where each manifest entry names its release tag (`models-v1` for every file except NuExtract, `models-v2`), redirects only to `release-assets.githubusercontent.com` (at most 4), no credentials or ports. Files over 32 MB use 4 parallel `Range` connections that write 8 MB pieces at their offsets into a preallocated `<file>.part` under `noBackupFilesDir`; `<file>.part.ranges` records the completed byte ranges (with the pinned size and SHA-256 in its header, so a mismatching sidecar is ignored), so a resume fetches only the missing ranges. The signed redirect URL is resolved once per file with a one-byte `Range` probe (which also checks the total size), reused for every piece and resolved again on 403 or 410. Smaller files use one stream. Whatever the path, the whole file's size and SHA-256 are checked before the atomic move into `files/models/`; a mismatch deletes the part and sidecar, and an entry that is not pinned never downloads. Nothing else in the app uses the network apart from Gmail.

### Private models

Files in `files/models/custom/` win over the downloaded base files. List each file's SHA-256 in `custom.json` next to them; a file whose hash does not match is ignored and the base file is used. Only the six manifest names, `model_spec.json` and the private ModernBERT files below are accepted, and Settings shows them as custom. `TOKENIZER.md`, `tokenizer.mbpe`, `tokenizer_vectors.json` and `SHA256SUMS` are recognised and skipped (never copied), so picking a whole model folder works.

**Release build (no adb needed).** Copy `custom.json` and the model files to the phone's Downloads folder (USB file transfer, Quick Share or `adb push`), then open Settings, On-device AI, Import private model files and select them all at once. The app copies them into private staging storage, checks every SHA-256 against the picked `custom.json`, and only if all of them match replaces the files in `files/models/custom/`. Any rejected name, mismatch or unlisted file aborts the whole import and nothing changes (the four skipped names above are not rejected); the result list shows which file failed and why. No storage permission is used. Afterwards the classifier is re-detected and the reprocess banner appears if the model changed. Remove private models in the same place wipes `files/models/custom/` and returns to the base models.

Rules for the import: names must be plain file names (no separators or `..`) from the list above; `custom.json` must be picked and must list every other picked file except `model_spec.json`; a file is at most 2 GB; the picked `custom.json` replaces the old one, so it must list every private file that should stay active.

```
cd ~/models/modernbert   # holds model_spec.json type.tflite category.tflite tokenizer.json calibration.json
{ echo '{'; for f in type.tflite category.tflite tokenizer.json calibration.json; do printf '  "%s": "%s",\n' "$f" "$(shasum -a 256 "$f" | cut -d' ' -f1)"; done | sed '$ s/,$//'; echo '}'; } > custom.json
adb shell mkdir -p /sdcard/Download/companion-models
adb push model_spec.json type.tflite category.tflite tokenizer.json calibration.json custom.json /sdcard/Download/companion-models/
```

On the phone, tap Import private model files, open Downloads, then companion-models, select `model_spec.json`, `tokenizer.json`, `type.tflite`, `category.tflite`, `calibration.json` and `custom.json` (or the whole folder; pushing `TOKENIZER.md`, `tokenizer.mbpe`, `tokenizer_vectors.json` or `SHA256SUMS` along is harmless) and confirm. The first message scored after an import packs the weights once (a few seconds of extra load and about 350 MB of cache per task), later loads are fast. Add `"decide.tflite": "<sha256>"` or `"qwen3.5-2b-q4_0.gguf": "<sha256>"` to `custom.json` (and push those files too) to install them in the same import.

**Debug build (adb).** `run-as` only works on a debuggable build (`assembleDebug`):

```
echo '{"decide.tflite": "<sha256>", "qwen3.5-2b-q4_0.gguf": "<sha256>"}' > custom.json
adb push decide.tflite qwen3.5-2b-q4_0.gguf custom.json /data/local/tmp/
adb shell run-as app.companion sh -c 'mkdir -p files/models/custom && cp /data/local/tmp/decide.tflite /data/local/tmp/qwen3.5-2b-q4_0.gguf /data/local/tmp/custom.json files/models/custom/'
```

### Private ModernBERT classifier

An optional backend replaces Decide for message type and category. Its weights are fine-tuned on private messages, so they are never bundled, downloaded or published: they reach the phone only through `files/models/custom/`, like the other private models.

| File | Role |
| --- | --- |
| `model_spec.json` | `"arch": "modernbert-classifier"`, template, buckets, pad id, per task file, labels and signatures |
| `type.tflite` | int8 LiteRT classifier, 9 labels: otp, expense, income, bill, delivery, alert, personal, promo, spam |
| `category.tflite` | int8 LiteRT classifier, 10 labels: food, groceries, shopping, transport, travel, bills, entertainment, health, transfer, other |
| `tokenizer.json` | the model's Hugging Face `tokenizers` file: the `answerdotai/ModernBERT-large` vocabulary and merges, the NFC normalizer and the 116 added tokens (merges may be written as strings or pairs; a `truncation` block is ignored) |
| `calibration.json` | version 2 (same shape as version 1 plus `"model"`), optional; the spec's `calibration` entry may name another plain file |
| `custom.json` | file name to SHA-256 for every file above except `model_spec.json` |

```
{ "arch": "modernbert-classifier", "model": "modernbert-large-sms-v1",
  "template": "{sender}: {text}", "max_len": 128, "buckets": [64, 96, 128],
  "pad_id": 50283, "cls_id": 50281, "sep_id": 50282, "truncation": "right",
  "tokenizer": { "json": "tokenizer.json" }, "calibration": "calibration.json",
  "tasks": {
    "type": { "file": "type.tflite", "labels": ["otp", "expense", ...],
      "signatures": { "s64":  { "bucket": 64,  "inputs": { "input_ids": [1, 64],  "attention_mask": [1, 64] },  "dtype": "int32", "output": "logits" },
                      "s96":  { "bucket": 96,  ... }, "s128": { "bucket": 128, ... } } },
    "category": { "file": "category.tflite", "labels": ["food", "groceries", ...], "signatures": { "...": "..." } } } }
```

Only `arch`, `buckets` and `tasks.type` are required. `template` defaults to `{sender}: {text}`, `max_len` to the largest bucket, `tokenizer` (a file name, or an object with `json`) to `tokenizer.json`, `calibration` to `calibration.json`, and `pad_id`, `cls_id` and `sep_id` to the ids of `[PAD]`, `[CLS]` and `[SEP]` in the tokenizer. `signatures` is optional per task. In the current form it maps a signature name to its `bucket`, input names (the one containing `mask` is the attention mask, the other the ids), `dtype` (only `int32`) and `output`; the app binds each input buffer by name, because the signatures list `attention_mask` before `input_ids`. The older form maps a bucket to a signature key and uses positional buffers (a top-level `inputs` list that starts with the mask swaps the two). Every other block (`expected`, `runtime`, `provenance`, `quantization`, `sha256`, `bucket_rule`, `output`, `inputs_note`) is ignored. File names in the spec must be plain names. A spec without a `category` task never runs one.

Install them with the release flow above (the example there pushes exactly these files). On a debuggable build the same files can be copied with `adb shell run-as app.companion` into `files/models/custom/`.

If an earlier `custom.json` listed `decide.tflite` or `qwen3.5-2b-q4_0.gguf`, keep those entries in the new one.

ModernBERT is used for classification (live refine, import, reprocess) only when `model_spec.json` has the right `arch` and every file it names (the tokenizer and each task file) is listed in `custom.json` with a matching SHA-256. Otherwise the app keeps the GLiNER Decide path, and removing `model_spec.json` switches back. Settings, On-device AI shows "Message classifier: ModernBERT (custom)" or "GLiNER (base)". The processing key is a hash of the model file hashes and the active custom calibration hash, so swapping a model or its calibration shows the reprocess banner, as does switching backend.

The input is `"{sender}: {text}"` (raw text, codes not redacted, matching the training data), byte-level BPE encoded as in Hugging Face `tokenizers`: raw added tokens first (a literal `[CLS]`, `[SEP]`, `[MASK]`, `[PAD]`, `[UNK]` or `<|endoftext|>` is one token, and `[MASK]` also takes the whitespace before it), then NFC, then the normalized added tokens (a run of 2 to 24 spaces is one token, matched longest first, so 30 spaces are a 24-space token and a 6-space token), then the pre-tokenizer regex, merge ranks, and `[CLS]`, up to 126 ids, `[SEP]`, padded on the right with the pad id (mask 1 for real tokens, 0 for padding) to the smallest bucket that fits. `:core` tests compare the ids of 215 synthetic messages with the `tokenizers` output for the public `answerdotai/ModernBERT-large` `tokenizer.json` at revision `45bb4654a4d5aaff24dd11d4781fa46d39bf8c13` (`tools/bert/vectors.py` regenerates them; the committed file's SHA-256 is checked), and the 200 synthetic vectors that ship with the private model (`bert/vectors_v2.json`, no message text from real SMS) against the same public file, also after rewriting its merges as pairs and adding a `truncation` block; both must match 100%. The private `tokenizer.json` is not committed. `PrivateModelTest` runs the same vectors against the real private folder when `COMPANION_PRIVATE_DIR` points at it, and is skipped otherwise (CI).

`type.tflite` loads on demand and `category.tflite` only when the type is expense, never both at once: the Governor's one-model rule applies and each unloads after 30 s idle. They run on the CPU (XNNPACK) or the NPU where it compiles the graph, never the GPU, at background thread priority, with 2 threads (4 while charging, decided at load). The int8 weights are repacked by XNNPACK on load, which costs about 1.4 GB of RAM (figures from the model's own spec, measured by its exporter, not on a phone); the app therefore sets the LiteRT CPU option `xnnpack_weight_cache_path` (`CompiledModel.Options.cpuOptions = CpuOptions(numThreads, null, path)`) to `bert-type-<sha8>.xnn` or `bert-category-<sha8>.xnn` in `noBackupFilesDir`, so the first load writes the packed weights (about 350 MB per task) and later loads map the file (about 0.45 GB resident). The GLiNER Decide model uses `decide-<sha8>.xnn` the same way. Cache files whose hash prefix does not match the active model are deleted at start, after every import and on Remove private models. Input buffers are bound by name per signature, and a batch (Pending with more than 4 queued messages, Reprocess, Import) with ModernBERT runs only while charging and below thermal status MODERATE: the jobs wait for the charger and idle even if "Only while charging" is off, and a larger live burst scores 4 messages now and the rest when the phone is charging. A single live message is scored on battery. A load per message type is paid when type and category alternate, so a reprocess with many expense messages is slower than with Decide.

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

Encrypted database (SQLCipher, passphrase wrapped by an Android Keystore AES-GCM key), `allowBackup=false`, no analytics, no logging of content, Gmail read-only with the token held in memory only, the only other network use is the user-started model download from GitHub Releases (a non-exported job service and cancel receiver), the Conversation and NuExtract models run in isolated processes with no network or permissions, exported components limited to the launcher, SMS receiver, notification listener, Quick Settings tiles and the widget receiver. Corrections are shared only on request through a non-exported FileProvider limited to the cache exports folder.
