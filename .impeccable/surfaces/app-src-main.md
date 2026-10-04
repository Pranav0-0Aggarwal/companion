---
version: 1
slug: "app-src-main"
primary_target: "app/src/main"
related_targets: []
---

# Companion app surface

Scope: the whole Android app (inner screen and cover screen). Mode: Operate.
Audience and job: the owner, glancing at OTPs and anything needing action, reviewing money in the evening. Constraints: on device only, low RAM and battery, Material 3 structure, dark theme first class.

## Direction contract

THESIS: Companion is a passbook that prints itself. Every money event becomes a printed passbook line with a running balance, settled lines carry the bank's stamp, and everything else (OTPs, bills, messages) lives on passbook pages of its own. It refuses the category default of soft cards, donut charts and gradient summary tiles.

OWN-WORLD: Passbook blue cover #1F3A68 owns the app bar, cover screen and dark ground; ledger white #FAFAF7 pages ruled in cyan #9CC7D6; printed ink #2A2A2A set in a monospaced kiosk face with tabular figures for every date and amount; stamp red #B3261E only for actions, overdue and the "needs you" stamp; rotated rubber-stamp outlines (SETTLED, DUE, REFUND, ASK) as the state vocabulary. Material 3 components themed into this, never replaced.

STORY: The owner opens Companion and sees today's page: OTP coupons to copy, a few lines stamped ASK or DUE that need one tap, then today's printed entries and the running total. They trust it because every line shows where it came from and anything unsure is stamped ASK instead of guessed.

FIRST VIEWPORT: Inner screen, portrait: a passbook blue header with guide words naming the page ("Sat 4 Oct · Today"); a row of perforated OTP coupons with the code at Display size and seconds left printed along the perforation; up to three stamped lines that need action; then the ruled ledger table (date, particulars, debit, credit, balance) with today's lines. Cover screen: one OTP coupon full width plus the next due bill. No FAB; search lives in the app bar.

FORM: Bank Passbook, position 1 of my ordered list (passbook, railway departure board, bahi-khata, dak sorting rack, flight strips, transit signage, receipt roll); seed key 90a5f2af, chosen as IMPECCABLE'S PICK. Signature interaction: new lines print into the ledger character by character and filing an ASK line stamps it SETTLED (both replaced by a crossfade when animations are off).

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
