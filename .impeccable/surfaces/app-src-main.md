---
version: 1
slug: "app-src-main"
primary_target: "app/src/main"
related_targets: []
---

# Companion app surface

Scope: the whole Android app (inner screen, Flex tabletop and cover screen widget). Mode: Operate.
Audience and job: the owner, glancing at codes and anything needing action, asking plain questions about money, reviewing spend in the evening. Constraints: on device only, light runtime (RAM, battery, jank) with no feature cuts, Material 3 structure themed as One UI, dark and light first class, system font scale and reduced motion honoured.

## Direction contract

THESIS: Companion is a first-party Galaxy app that happens to know your money. One UI's calm viewing-area header, rounded cards and floating pill bar carry every screen; the passbook survives only as stamped state and tabular figures. It refuses the vibecoded finance default: gradient tiles, donuts, ruled-ledger pastiche, decorative labels.

OWN-WORLD: cool grey ground #F4F5F7, true black #000000 at night; cards #FFFFFF / #17171A at 26dp radius with inset hairlines; ink #111317 / #F2F3F5. One accent, Companion blue #2A62DB / #7EA6FF, for actions, selection and the Ask sparkle. Stamp red #D2372D only for ASK, DUE, overdue; settled green #17784A for SETTLED and money in. System sans (SamsungOne on Galaxy), tabular figures for every amount, date and code. Stamps: small outlined caps labels, upright at rest.

STORY: The owner opens Companion, is greeted by name, copies a live code from a big card, clears each needs-you item with one tap, sees today's spend, and asks a question in plain words from anywhere; the answer says how it understood the question and can be corrected.

FIRST VIEWPORT: Today, portrait: expanded header about a third of the height, actions top right, greeting at 32sp with one quiet status line; an "Ask Companion" pill; full-width code cards, code at 36sp tabular over a draining hairline; the Needs you card with stamps and one-tap actions. Floating translucent pill of five tabs with a detached round Ask button. Flex: top half glance (code, next bill, today's spend), bottom half Ask, needs-you and destinations. Cover widget: one code and the next bill.

FORM: Pinned by the owner: One UI native, refined, the category canon played straight; the bar is Samsung's own One UI apps (Settings, Wallet, My Files). Roll seed 1f157805 assigned grounded index 7; the pinned direction beats the roll. Raise from Terminal Yellow Wayfinding: glance surfaces show only the next decision, figures at monumental scale. Raise from the catalog sleeve: nothing is labelled twice. Signature interaction: filing an item thumps a SETTLED stamp in and the row folds away (a crossfade when animations are off).

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Unresolved

- Exact budget features; widget designs beyond the cover screen.
