# Changelog

All notable changes to Companion. Everything runs on your phone; nothing about your messages leaves it.

## 0.5.0 (unreleased)

### Learning that actually learns
- One correction is enough: the rule takes effect immediately.
- "Also filed N similar · Undo": a correction is applied to matching messages already in your history.
- "This is…" picker: re-file any message as delivery, bill, expense, income, alert, promo, spam or personal, and pick a category for spends.
- Sender rules: after 3 matching corrections, Companion always treats that sender the same way (for example ZOOMCR as promo).
- Smarter message fingerprints. Months, links, references and payee names are masked, so rules match about 4 times more of your messages (61% of messages now share a fingerprint, up from 16%). Existing rules are carried over.
- "What I've learned" lists rules in plain words with an example and how many messages each caught, plus merchant categories, sender rules and merchant names, each with delete.

### Money that adds up
- "Spent" counts only real spending. Transfers between your own accounts, credit card bill payments and investments show on a separate "moved" line.
- "Not spending" switch for transfers to a specific person.
- Large amounts shrink to fit and never get cut off. "In" moves under "Spent" when they don't fit side by side.

### Bills and cards that look after themselves
- One bill per card per cycle, showing the newest amount.
- Older cycles close when a newer statement arrives.
- Card payments mark the matching bill as paid automatically.
- Bills long past their due date fold into "Older · probably paid" instead of showing as overdue.
- Cards found from your messages: "Found N cards" with Add or Add all, with statement day, due day and limit filled in.

### Brands and names
- Built-in database of 246 Indian merchants, banks and services, 36 of them with logos (from Simple Icons, CC0). The rest get a monogram in the brand colour. Nothing is fetched from the internet.
- Clean merchant names: "'Swiggy" becomes Swiggy, "Amazon Pay In G" becomes Amazon Pay, and EatClub is filed as food.
- Real sender names in Inbox: Royal Enfield, ACT Fibernet and BPCL instead of JM-ENFILD-S, AD-ACTGRP-S and AX-BPCLIN-S.
- Rename a merchant once and every past and future match updates, with Undo. Companion also learns names by itself over time.

### Notifications
- Quiet spend alerts ("₹261 · Swiggy · Food · HDFC ··4021"), grouped into a daily total.
- New bill alerts with Pay (opens CRED) and Remind me.
- Parcels out for delivery show in the Now Bar as an Android 16 Live Update, with the delivery OTP.
- 8 pm summary: what needs you, bills due tomorrow and today's spend.
- Per-type toggles in Settings. Amounts and codes are hidden on the lock screen unless you turn details on.

### Galaxy Z Flip cover screen
- New "Now" widget for the cover screen and home screen. It shows the delivery OTP, the latest code, the next bill or today's spend. Add it in Settings → Cover screen → Widgets.

### Polish
- Calmer Inbox rows, with one suggestion and "This is…" instead of three chips.
- Friendlier empty states.
- The Ask sheet no longer reopens on its own.

## 0.4.2 (2026-10-05)

### Fixed
- The on-device classifiers now actually run on the phone. A text pattern that Android rejects (but desktop Java accepts) made every GLiNER and ModernBERT prediction fail silently, so only the rules ever filed messages. ModernBERT now reprocesses about 13 messages per second at around 650 MB of RAM.
- "Only while charging" starts as soon as you plug in, instead of waiting for the phone to be idle or for a 15-minute recheck.
- Pauses because the phone is warm resume after 3 minutes.
- A confident "delivery" or "alert" verdict is kept even when the rules missed it.

### Improved
- The category model is skipped while its calibration can never be confident, removing a ~400 MB reload per batch (cooler and faster).
- Smart extraction (NuExtract) now fills missing amounts, due dates and card numbers during reprocess and history import too.

### Added
- Bill cycles: statement and reminder messages for the same card collapse into one bill, and paying marks the whole cycle as paid.
- "Same as…" duplicates with Undo, plus "Not a duplicate" to restore.
- Model errors are logged by type only, never with message text.

## 0.4.1 (2026-10-05)
- Real ModernBERT support. Named signatures, length buckets 64, 96 and 128, and inputs bound by name. The tokenizer matches the reference exactly.
- XNNPACK weight cache brings ModernBERT from about 1.4 GB to about 0.45 GB of RAM.
- Power rules: bulk work with ModernBERT runs only while charging and while the phone is cool. On battery, at most 4 messages are scored right away.
- Importing private models skips extra files instead of failing.

## 0.4.0 (2026-10-05)
- Messages are saved instantly using the rules, then refined in the background by the on-device classifier. Your corrections are never touched.
- Smart extraction with NuExtract-1.5-tiny (4-bit, 352 MB): fills missing amounts, due dates and card endings. Every value must appear word for word in the message. OTP codes never come from a model.
- Import message history, and reprocess with the current model. These run only while charging by default, and pause when the phone is warm, in battery saver, or below 20%.
- Import private model files from Settings, including support for a private ModernBERT classifier.

## 0.3.0 (2026-10-04)
- Full One UI native redesign: collapsing headers, floating cards, a tab pill, light and dark themes.
- Ask Companion sheet with voice input, suggestions and editable understanding.
- Today greeting, live code cards, swipe-to-file "Needs you", and a spend-pacing line in Ledger.
- Flex mode layout for a half-folded phone, spring motion throughout, and a redesigned cover widget and icon.
- Faster, resumable model downloads with progress and Cancel.

## 0.2.0 (2026-10-04)
- On-device AI. GLiNER2.5-Decide sorts messages and Needle 3 answers Ask questions in an isolated process. Models download over Wi-Fi with SHA-256 checks.
- Support for your own fine-tuned models.
- Signed with a new release key, so uninstall 0.1.0 first.

## 0.1.0 (2026-10-04)
- First test build. Ledger, Cards, Bills, Inbox, Today and Ask, built on rule-based parsing of SMS and app notifications.
- OTP cards, reminders, calendar, cover widget and Quick Settings tiles.
- Encrypted database and biometric lock.
