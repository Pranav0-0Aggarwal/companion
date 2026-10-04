# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Native Android: Kotlin + Jetpack Compose, minSdk 34, target Samsung Galaxy Z Flip 7 (6.9" inner screen, 4.1" cover screen). On-device model: a fine-tuned, calibrated GLiNER2.5-Decide (486M) exported to ONNX and run with ONNX Runtime, loaded on demand. Local encrypted database with full-text search. Personal sideload install, not the Play Store.

## Users

One user, the owner: an engineer in India with many bank, card, UPI, wallet and shopping accounts who gets hundreds of SMS, app notifications and emails a week. The job: never miss a bill, an odd charge or a message that needs a reply; find an OTP instantly; know where the money went, without reading every message.

## Product Purpose

Companion reads SMS, app notifications (CRED, INDmoney, Amazon Pay, banks, UPI apps, WhatsApp and others) and Gmail on the phone, and turns them into structured knowledge: an expiring OTP ledger, a ledger of expenses, credits and credit card spends with categories, bills and dues with reminders, a "worth a look" queue, a WhatsApp digest of what needs the owner, and a searchable store of everything extracted. Success: the owner opens it, sees today's codes and anything that needs action in one glance, and trusts that everything else was filed correctly.

## Positioning

Fully on device and private: no network permission, nothing leaves the phone. It merges the same event seen through several channels (a bank SMS, a CRED notification and an Amazon Pay notification for one payment) into one entry. A calibrated decision model files what it is sure about and asks the owner about the rest, so its confidence is honest.

## Operating Context

- The phone is often folded: the cover screen must show live OTPs and the most urgent item at a glance.
- First open shows the Today digest: live OTPs on top, then anything needing action (bills due, odd charges, messages worth a look), then today's spend.
- India first: INR, Indian banks, UPI, cards, CRED, INDmoney, Amazon Pay. Other currencies are shown as they appear.
- WhatsApp: only what needs the owner (questions, money, dates, plans, or people marked important); other chats are summarised as counts. Only notification text is available, not chat history.
- Gmail: read-only, finance and logistics mail only (receipts, statements, bills, deliveries, travel), synced incrementally.
- Heavy work (first import) runs only while charging and idle; afterwards one new item at a time.

## Capabilities and Constraints

- Sources: SMS (history and new), notification listener, Gmail API (OAuth testing mode, owner as the only user).
- Pipeline: collect, extract with rules (amount, account or card ending, merchant, due date, OTP and expiry), classify with the model only when rules cannot decide, de-duplicate across sources, store with links back to sources.
- OTPs expire and are deleted after their validity (default 10 minutes when not stated).
- Cards: the owner adds each credit card with bank, optional name, last 4 digits, statement day, due day and optional limit. Never the full card number or CVV. Transactions from SMS, notifications and statement emails are matched to a card by bank and last 4 digits and shown per statement cycle, with limit used and the due date.
- Low RAM and battery: no resident model, no polling, event driven, inference only on new items.
- Undecided: exact budget features; widget designs beyond the cover screen.

## Brand Commitments

Name: Companion (working name). Voice: calm, plain, brief. Look: One UI native, refined; it should sit beside Samsung's own One UI apps, with passbook stamps and tabular figures only where they carry state or numbers (owner's standing choice).

## Evidence on Hand

- 1,601 anonymised, labelled real SMS from the owner (local only, never committed).
- SMS Inbox benchmark (ArcadeBench) for measuring the classifier.
- No other assets; do not fabricate screenshots, users or claims.

## Product Principles

1. Glance first: the most important thing is readable in one second, folded or open.
2. Honest confidence: file automatically only when sure; ask once otherwise and learn.
3. Nothing leaves the phone.
4. Quiet by default: notify only for what needs action.
5. One event, one entry: never show the same payment twice.

## Accessibility & Inclusion

Large, legible numerals for OTPs and amounts; supports system font scaling and dark mode.
