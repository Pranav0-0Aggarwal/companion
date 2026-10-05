# Changelog

All notable changes to Companion. Everything runs on your phone; nothing about your messages leaves it.

## 0.6.0 (2026-10-05)

### A new app
- Redesigned from scratch around five tabs: Today, Money, Food, Inbox and You. Today is a timeline of your day with what needs you, bills, spend and meals, and a day strip to look back. Money holds the ledger, bills, cards and trips. Food has a calorie ring, macros and meals by slot. You holds the vault, what Companion has learned, weight, privacy and settings.
- An Ask bar on every screen opens chat as a sheet. Answers come back as cards you can tap through.
- The cover screen gets the same care: Today, codes and Ask work on the Flex Window.

### Chat that answers
- Common questions are answered instantly without the chat model: what needs me today, meal ideas ("a high protein vegetarian dinner under 600 calories"), "Swiggy vs last month", which card to use, meetings, weight trend, trips, today's meals and calories, and plain meal logs such as "had 2 idli and chai for breakfast" or "kal raat 2 plate momos aur ek roll khaya".
- When the chat model is needed it only decides what to look up; the answer is Companion's own card and sentence, so numbers always come from your data. A request for ideas never logs a meal.
- Meal ideas are built from the food table, under your calorie limit and vegetarian on request, with the most protein first.
- Faster and lighter on the Galaxy Z Flip 7: the chat model now uses about 1.4 GB instead of 2.6 GB, prepares itself when you open chat, streams its reply, never repeats itself and stops when you send a new message.

### Everything else
- Chat. A small conversational model (Qwen3.5-2B) replaces Needle. It runs in an isolated process, streams its answer and calls tools on your data: spend, bills, cards, meals, weight, trips, documents, reminders and calendar. Rule answers still come first. Old Needle files are deleted on upgrade.
- Chat that looks things up and acts. Chat can look things up: your messages and notifications (sender, date and a short snippet), a spending ledger you can filter by merchant, category, card, amount and period, top merchants, a comparison of two periods, what needs you in the Inbox, bill cycles with due date, minimum and paid status, which card gives the longest interest free window, meals for a day, your weight trend, meetings on a day (when calendar access is allowed) and trips. It can also act for you: mark a bill paid, file a payment under a category, change what an item is, mark a duplicate, rename a merchant, stop counting a merchant as spending, add a card found in your messages, set your monthly budget and dismiss an Inbox item. Nothing changes until you tap Confirm on the card that appears, and most actions offer Undo afterwards. The screen stays on while Companion is thinking.
- Food log. Say what you ate, now or in the past ("yesterday dinner"), and calories are worked out from your own foods, brand menus and a bundled food table, with a short question when unsure. Food orders create a pending meal. Health Connect sync is optional.
- Meal and weight reminders with an inline reply, based on your usual meal times and food payments.
- Trips. Spends in a trip's dates are tagged, forex card charges are converted at the rate in the message, with a summary.
- Document vault. Insurance, registration, pollution certificate, FASTag, loans and warranties are read from messages or photos (offline text recognition), stored encrypted with a hardware-backed key, and remind you 30, 7 and 1 day before expiry.
- Database version 9.
- New message classifier. ModernBERT is now the only classifier that sorts your messages by type; spend categories come from the rules, the brand list and your corrections. It is downloaded once over Wi-Fi from the project's models-v4 release (about 413 MB, every file checked against a pinned checksum) and runs only on your phone. Until it is downloaded, messages are filed by the rules alone, and Settings, On-device AI shows a download prompt. When it finishes, Companion offers to reprocess your messages.
- Removed GLiNER, the older classifier, and the private model import. Old GLiNER files and any imported private models are deleted from the phone on upgrade, which frees up to about 520 MB.

## 0.5.0 (2026-10-05)

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

### Meetings and your day
- Meeting reminders: a heads-up before your next call or meeting, read from the calendars you pick in Settings, Meetings. It is off until you turn it on, and calendar access is only requested then.
- Lead time is 5 minutes for video calls and 15 minutes for in person meetings. Change either to 5, 10, 15 or 30 minutes.
- Join opens the Meet, Zoom, Teams or Webex link. Directions opens your maps app at the location. Running late opens the share sheet with "Running about 10 minutes late, sorry!" so you decide who gets it and when to send it.
- No double pings: if the event already has a calendar reminder at the same time or closer to the start, Companion stays quiet.
- In the last 10 minutes an ongoing countdown stays in your notifications. On Android 16 it also appears as a Live Update in the status bar. It goes away when you join or 5 minutes after the start.
- Today shows a "Next up" card when a meeting starts within 12 hours, with Join or Directions. A line warns you when two meetings overlap, for example "Two meetings overlap at 3:00 pm".
- Choose which calendars count, and optionally only meetings that have a link or a location.
- The cover screen widget shows a meeting starting within the hour, after codes and before bills. Titles stay hidden unless lock screen details are on.
- Companion calendar: turn on "Show bills and trips in your calendar" and Companion adds a separate local calendar with bill due dates, trips and suggested plans. Samsung Calendar and Now Brief can show them. Events are added, updated and removed as your bills and trips change, and turning it off deletes the whole calendar. Your other calendars are only read, never changed.
- Morning brief: a quiet note at 8:00 (you can change the time) with your first meeting, bills due soon and orders on the way, only the lines that apply. Tap it to open Today.
- Between 6:00 and 10:30 the cover screen shows a compact brief with your first meeting and bills due today when nothing more urgent needs you.
- Everything stays on your phone. Notifications hide meeting names on the lock screen unless you turn on details there.

### WhatsApp and chats
- Fixed: a new WhatsApp message that Companion judged unimportant used to be counted and thrown away, so it looked like it was never pulled. Now every chat is kept for a couple of days.
- Inbox has a calm "Chats" section ("12 chats today · not important"), collapsed by default. Tap it to see who wrote, and tap "Mark important" next to a person so their messages show under Needs you from then on.
- Chats that are not important never count in Needs you, never notify you and are removed after 2 days.
- WhatsApp Business and Instagram Lite are now read too.
- Business chats are understood like their SMS. A WhatsApp or Instagram message from Swiggy, HDFC Bank, IndiGo, Shipway or Amazon becomes a delivery, order, bill, payment or code in the right place, with the brand's name and logo.
- A banner on Today and Inbox says when Companion can't see WhatsApp and app notifications, with a Turn on button that opens notification access. Companion also asks Android to reconnect by itself if the connection drops.
- Turning on WhatsApp or Instagram in Settings opens notification access if it is still off. WhatsApp is on by default.

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
