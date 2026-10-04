---
name: Companion
description: A first-party-feeling Galaxy app that knows your money; One UI native, refined.
colors:
  bg: "#F4F5F7"
  card: "#FFFFFF"
  raised: "#F0F1F4"
  ink: "#111317"
  ink2: "#5D626B"
  ink3: "#8A8F98"
  line: "#E6E8EC"
  accent: "#2A62DB"
  on-accent: "#FFFFFF"
  accent-box: "#E4ECFD"
  on-accent-box: "#1846B0"
  red: "#C0302A"
  red-box: "#FCE9E7"
  green: "#17784A"
  green-box: "#E2F3EA"
  amber: "#B25E00"
  bar: "#FFFFFF"
  bg-night: "#000000"
  card-night: "#17171A"
  raised-night: "#232327"
  ink-night: "#F2F3F5"
  ink2-night: "#A3A8B0"
  ink3-night: "#6E737B"
  line-night: "#2A2B30"
  accent-night: "#7EA6FF"
  on-accent-night: "#0B1A3A"
  accent-box-night: "#1D2A47"
  on-accent-box-night: "#C9D8FF"
  red-night: "#FF6B61"
  red-box-night: "#3A1614"
  green-night: "#4CC38A"
  green-box-night: "#10301F"
  amber-night: "#FFB547"
  bar-night: "#1E1E22"
typography:
  header:
    fontFamily: "SamsungOne, system-ui, sans-serif"
    fontSize: "32sp"
    fontWeight: 700
    lineHeight: "38sp"
    letterSpacing: "-0.4sp"
  title:
    fontFamily: "SamsungOne, system-ui, sans-serif"
    fontSize: "22sp"
    fontWeight: 700
  title-bar:
    fontFamily: "SamsungOne, system-ui, sans-serif"
    fontSize: "20sp"
    fontWeight: 700
  body:
    fontFamily: "SamsungOne, system-ui, sans-serif"
    fontSize: "16sp"
    fontWeight: 500
  support:
    fontFamily: "SamsungOne, system-ui, sans-serif"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "20sp"
  meta:
    fontFamily: "SamsungOne, system-ui, sans-serif"
    fontSize: "13sp"
    fontWeight: 400
  tab-label:
    fontFamily: "SamsungOne, system-ui, sans-serif"
    fontSize: "11sp"
    fontWeight: 600
  stamp:
    fontFamily: "SamsungOne, system-ui, sans-serif"
    fontSize: "11sp"
    fontWeight: 800
    letterSpacing: "1.2sp"
  figure-hero:
    fontFamily: "SamsungOne, system-ui, sans-serif"
    fontSize: "40sp"
    fontWeight: 700
    fontFeature: "tnum, lnum"
  code:
    fontFamily: "SamsungOne, system-ui, sans-serif"
    fontSize: "36sp"
    fontWeight: 700
    letterSpacing: "2sp"
    fontFeature: "tnum, lnum"
  code-big:
    fontFamily: "SamsungOne, system-ui, sans-serif"
    fontSize: "52sp"
    fontWeight: 700
    letterSpacing: "2sp"
    fontFeature: "tnum, lnum"
  figure-row:
    fontFamily: "SamsungOne, system-ui, sans-serif"
    fontSize: "16sp"
    fontWeight: 600
    fontFeature: "tnum, lnum"
rounded:
  stamp: "6dp"
  tag: "10dp"
  small: "12dp"
  field: "16dp"
  medium: "18dp"
  card: "26dp"
  sheet: "28dp"
  pill: "9999px"
spacing:
  gutter: "16dp"
  row-x: "18dp"
  header-x: "24dp"
  section-x: "28dp"
  row-min: "64dp"
  touch: "48dp"
  divider-inset: "72dp"
components:
  button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.on-accent}"
    typography: "{typography.support}"
    rounded: "{rounded.pill}"
    padding: "0 18dp"
    height: "40dp"
  button-tonal:
    backgroundColor: "{colors.accent-box}"
    textColor: "{colors.on-accent-box}"
    typography: "{typography.support}"
    rounded: "{rounded.pill}"
    padding: "0 18dp"
    height: "40dp"
  button-text:
    textColor: "{colors.accent}"
    typography: "{typography.support}"
    rounded: "{rounded.pill}"
    padding: "8dp 12dp"
  chip:
    backgroundColor: "{colors.card}"
    textColor: "{colors.ink}"
    rounded: "{rounded.pill}"
    padding: "0 14dp"
    height: "36dp"
  chip-selected:
    backgroundColor: "{colors.accent-box}"
    textColor: "{colors.on-accent-box}"
    rounded: "{rounded.pill}"
    padding: "0 14dp"
    height: "36dp"
  tag:
    backgroundColor: "{colors.raised}"
    textColor: "{colors.ink2}"
    rounded: "{rounded.tag}"
    padding: "3dp 8dp"
  field:
    backgroundColor: "{colors.raised}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.field}"
    padding: "0 16dp"
    height: "60dp"
  field-focused:
    backgroundColor: "{colors.card}"
    textColor: "{colors.ink}"
    rounded: "{rounded.field}"
  card-group:
    backgroundColor: "{colors.card}"
    rounded: "{rounded.card}"
  list-row:
    backgroundColor: "{colors.card}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    padding: "12dp 18dp"
    height: "64dp"
  code-card:
    backgroundColor: "{colors.card}"
    textColor: "{colors.ink}"
    typography: "{typography.code}"
    rounded: "{rounded.card}"
  ask-pill:
    backgroundColor: "{colors.card}"
    textColor: "{colors.ink2}"
    typography: "{typography.body}"
    rounded: "{rounded.pill}"
    height: "56dp"
  nav-bar:
    backgroundColor: "{colors.bar}"
    textColor: "{colors.ink2}"
    typography: "{typography.tab-label}"
    rounded: "32dp"
    height: "64dp"
  nav-indicator:
    backgroundColor: "{colors.accent-box}"
    textColor: "{colors.on-accent-box}"
    rounded: "{rounded.pill}"
    size: "52dp x 30dp"
  ask-button:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.on-accent}"
    rounded: "{rounded.pill}"
    size: "64dp"
  stamp-red:
    textColor: "{colors.red}"
    typography: "{typography.stamp}"
    rounded: "{rounded.stamp}"
    padding: "2dp 6dp"
  stamp-green:
    textColor: "{colors.green}"
    typography: "{typography.stamp}"
    rounded: "{rounded.stamp}"
    padding: "2dp 6dp"
  stamp-quiet:
    textColor: "{colors.ink2}"
    typography: "{typography.stamp}"
    rounded: "{rounded.stamp}"
    padding: "2dp 6dp"
  ask-sheet:
    backgroundColor: "{colors.bg}"
    rounded: "{rounded.sheet}"
---

# Design System: Companion

## Overview

**Creative North Star: "The Galaxy Native"**

Companion should read as one of Samsung's own One UI apps (Settings, Wallet, My Files) that happens to know your money. Every screen opens on One UI's calm viewing-area header: a tall empty top region, a 32sp title sitting low, actions up in the toolbar, and content that starts within thumb reach. Content lives in rounded white cards on a cool grey ground (pure black at night). A floating translucent pill of five tabs sits above the gesture bar with a detached round Ask button beside it.

The old Bank Passbook world survives only where it carries meaning: small outlined caps stamps for state (ASK, DUE, SETTLED, PAID, CHECK) and tabular figures for every amount, date, time and code. There is no ruled paper, no ledger lines, no faux-print texture. Density is relaxed: 64dp minimum rows, 16dp gutters, one accent. Glance surfaces (code cards, Flex top half, the cover widget) show only the next decision, with figures at monumental scale.

Motion is spring-based, short and physical, routed through one helper that honours the system animator scale. The one signature moment: filing an item thumps a stamp in and the row folds away.

**Key Characteristics:**
- One UI collapsing header, rounded 26dp cards, segmented list groups, floating pill nav.
- One accent (Companion blue) for actions, selection and the Ask sparkle; red, green and amber are state colours only.
- System sans throughout; tabular lining figures stand in for any monospace face.
- Soft neutral lift only on hero objects in light mode; lists sit flat; dark mode has no shadows.
- Stamps for state, never for decoration.
- Springs everywhere, crossfades or instant states when animations are off.

## Colors

A cool neutral ground with one saturated blue, plus three state hues that only appear when something is owed, settled or expiring. Night is true black with charcoal cards, matching One UI's dark mode. Each role has a light and a `-night` token; `Pal` in `ui/Theme.kt` is the source of truth and is mapped onto the Material 3 scheme. The cover widget repeats the same values in `OtpWidget.kt`.

### Primary
- **Companion Blue** (accent / accent-night): primary buttons, switches, focused field edges and labels, text buttons, the Ask button and sparkle, the code drain bar, selected tab label, the active pacing line.
- **Blue Wash** (accent-box, with on-accent-box text): tonal buttons, the selected chip, the nav selection indicator, accent lead discs.

### Tertiary (state)
- **Stamp Red** (red / red-night, on red-box): only ASK, DUE, overdue and errors. Darkened from #D2372D to #C0302A so 11sp stamp text clears WCAG AA on its own red tint (about 4.9:1 on red-box, 5.7:1 on white; #D2372D managed only about 4.2:1 on the tint).
- **Settled Green** (green / green-night, on green-box): SETTLED and PAID stamps, money in (credits are prefixed "+"), the swipe-to-file reveal, confirmations such as "Copied".
- **Expiry Amber** (amber / amber-night): code countdown in its last 15 seconds only.

### Neutral
- **Cool Grey Ground** (bg / bg-night): every screen background and the collapsed toolbar fill.
- **Card White / Charcoal** (card / card-night): cards, list groups, chips at rest, the Ask pill.
- **Raised Grey** (raised / raised-night): resting fields, tags, skeleton bars, plain lead discs, unchecked switch track.
- **Ink** (ink, ink2, ink3 and night pairs): primary text and figures; ink2 for secondary lines, section titles, unselected tabs and quiet stamps; ink3 only for outlines.
- **Hairline** (line / line-night): segmented row dividers, chip outlines, nav pill border, empty drain-bar track.
- **Bar** (bar / bar-night): the floating nav pill surface.

### Named Rules
**The One Voice Rule.** Companion Blue is the only accent. Red, green and amber are never used decoratively; each appears only when its state is true.

**The Stamp Red Rule.** Stamp red is #C0302A in light mode, not #D2372D. Any new red text on a red tint must clear 4.5:1.

## Typography

**Display Font:** System default sans (SamsungOne on Galaxy)
**Body Font:** System default sans
**Label/Mono Font:** None. Figures use the same face with `tnum, lnum` (the `Ty.mono` helper is named for its role, not its face).

**Character:** Native and unbranded on purpose; the system face makes Companion sit beside Samsung's apps. Weight and scale do the work, never a second family.

### Hierarchy
- **Header** (Bold, 32sp, 38sp line, -0.4sp): the viewing-area title on every tab, pushed screen and onboarding step. Its subline is 14sp regular in ink2.
- **Title** (Bold, 22sp / 20sp): sheet and dialog titles (Ask sheet, Lock, card dialogs) at 22; collapsed toolbar title and month headers at 20.
- **Body** (Medium, 16sp): list row titles, field text, Ask pill hint, toggles.
- **Support** (SemiBold 14sp for buttons, chips and section titles; Regular 14sp, 20sp line for quiet copy).
- **Meta** (Regular, 13sp): row sublines, times, provenance. The most used size in the build.
- **Figures** (Bold, tabular): hero figures 40sp (spent today, Flex tiles with no code); secondary totals 32 to 36sp (card cycle, ledger month, answer totals); codes 36sp, 52sp on Flex, 40sp in the cover widget, all with 2sp tracking; row amounts SemiBold 16sp.
- **Tab label** (SemiBold, Bold when selected, 11sp): capped at 1.3x font scale so five tabs never wrap.
- **Stamp** (ExtraBold, 11sp, 1.2sp tracking, caps): state labels only.

### Named Rules
**The Tabular Figures Rule.** Every amount, date, time and code is set with `tnum, lnum` in the system face. A monospace family is never used.

**The Said Once Rule.** Nothing is labelled twice. Section titles are sentence case 14sp ink2; there are no eyebrows or kickers above titles.

## Layout

Portrait single column at 360dp wide (Z Flip 7 inner, 360x840dp). Cards and groups inset 16dp from the edges; row content pads 18dp; header text 24dp; section titles 28dp. Rows are at least 64dp tall; every touch target is at least 48dp (tool buttons, chevrons, mic, checkboxes). List content reserves bottom space for the floating nav (nav inset + 112dp).

The header occupies 26% of screen height (20% for short screens), clamped to 150 to 300dp, with the title at its bottom edge and a 56dp toolbar above. Lead discs are 40dp with a 14dp gap, so segmented dividers start 72dp in, aligned with row text.

**Flex (tabletop):** when the fold is half-open and horizontal on a top-level tab, the app swaps to a split: the top pane above the crease shows the glance (greeting, one code at 52sp, spent-today and next-bill tiles); the bottom pane holds Ask, needs-you and destinations. Pane heights come from the fold bounds converted out of window coordinates. Nothing interactive sits near the crease.

**Cover widget:** one code (or a quiet "Nothing needs you") and the next bill, 28dp outer corners, 20dp inner cards.

## Elevation & Depth

Depth is tonal first: grey ground, white cards, raised grey for inputs. Light mode adds one soft neutral shadow, and only to hero objects you act on: code cards, the Ask pill, the nav pill and Ask button, answer cards, Flex tiles. Lists and groups are flat. Dark mode has no shadows at all; the charcoal-on-black step carries depth.

### Shadow Vocabulary
- **Lift** (10dp elevation, ambient black 5%, spot black 10%): code card, Ask pill, raised groups (answer cards), Flex tiles.
- **Float** (14dp elevation, ambient black 8%, spot black 16%): the nav pill and round Ask button.

### Named Rules
**The Hero Lift Rule.** A shadow means "this is the object to act on". Lists never lift, and nothing lifts at night.

## Shapes

Generous continuous rounding in One UI's language: 26dp cards and list groups, 28dp for the Ask sheet top and widget, full pills for buttons, chips, the Ask pill and nav selection, and a 32dp nav pill. Small radii are reserved: 16dp fields, 10dp tags, 6dp stamps. Segmented groups round only their outer corners (first row top, last row bottom). Issuer card faces on the Cards tab use 22dp, a deliberate step down from the container so the plastic card reads as an object inside a card.

## Components

### Buttons
- **Shape:** full pill, at least 40dp tall inside a 48dp touch area.
- **Primary:** Companion Blue with white text, 18dp side padding (10dp dense), optional 18dp leading icon. Press scales to 0.94.
- **Tonal:** Blue Wash with on-accent-box text; press scales to 0.97. The default for secondary actions (filing choices, confirmations).
- **Text button:** accent text on nothing, pill ripple; used for section actions and "Not a code".
- **Disabled:** 40% alpha.

### Chips
- **Style:** 36dp pill, card background with a 1dp hairline at rest; selected fills Blue Wash, drops the outline and goes SemiBold. Background crossfades on the soft spring.

### Cards / Containers
- **Corner Style:** 26dp.
- **Background:** card.
- **Shadow Strategy:** see the Hero Lift Rule.
- **Border:** none; dividers are 1dp hairlines inset 72dp (18dp when there is no lead).
- **Segmented rows:** each row is its own lazy item with `part()` rounding, so rows can animate in and out on their own.

### Inputs / Fields
- **Style:** 60dp minimum, raised fill, 16dp radius, floating label at 16sp.
- **Focus:** fill switches to card, a 2dp accent edge fades in, label turns accent, scales to 0.75 and lifts 11dp.

### Navigation
- **Floating pill:** 64dp pill of five tabs (Today, Ledger, Cards, Bills, Inbox) on the bar colour with a hairline border, 12dp from the edges above the gesture bar. A 52x30dp Blue Wash indicator sits behind the selected icon.
- **Ask button:** detached 64dp accent circle with the sparkle icon, presses to 0.92, and is the origin of the Ask container transform.
- **Toolbar:** 56dp; back and tool buttons are 48dp circles with 24dp icons.

### Stamps (signature)
Small outlined caps labels with a 1.5dp border and 6dp radius, upright at rest. Red for ASK and DUE, green for SETTLED and PAID, quiet ink2 for CHECK and card-payment markers. A changing stamp thumps in (see Motion).

### Code Card (signature)
Lifted card: issuer title (15sp) and note (13sp), a chevron that reveals provenance ("Received 10:42 by SMS" plus "Not a code"), the code at 36sp tabular, "0:40 left" with a Copy action, and a 3dp draining bar. Tap anywhere copies. While any live code card is composed, the window carries FLAG_SECURE, reference-counted across cards and cleared when the last one leaves.

### Ask
The "Ask Companion" pill (56dp, sparkle plus hint plus mic) and the nav Ask button both open a full-height sheet: a 22sp title, a 64dp, 24dp-radius input at 20sp, a mic and a send circle, suggestion chips and recent questions before asking, answer cards after. Each answer card says how it understood the question and can be edited.

### Lists, Empty and Loading
- **List rows:** optional 40dp tone disc (accent, red, green or plain), 16sp title, 13sp subline, tags and a stamp below, a trailing tabular amount.
- **Empty:** a 72dp card-coloured disc with a 30dp ink2 icon, 18sp bold title, 14sp body, optional action.
- **Skeleton:** raised-grey bars at 40%, 60% and alternating 90/70% widths.

### Icons
A custom stroke set (`Ic`) at 20 to 24dp, tinted by role. No glyph or emoji icons.

### Motion
One helper governs everything: `LocalMotion` / `motion()` (kit/Motion.kt), provided by `CompanionTheme` from the system animator duration scale. When animations are off, spatial moves become crossfades or instant states (Compose also zeroes durations).

- **Springs:** `Motion.soft` (damping 0.9, StiffnessMediumLow) for reveals and layout; `Motion.snappy` (0.8, Medium) for state; `Motion.bouncy` (0.55, Medium) for press and stamps.
- **Tabs:** fade-through: enter fades over 210ms after 90ms and scales from 0.97; exit fades over 90ms. With animations off, a 120ms fade.
- **Pushed screens (Settings, Plan):** shared-axis X. The new screen slides in from 1/6 of the width on the soft spring and fades in over 200ms while the tab underneath fades out over 150ms. Pop reverses it: the screen slides back out by 1/6 and fades over 150ms while the tab fades in over 200ms.
- **Collapsing header:** the big title fades and scales to 0.88 from its bottom-left over the header's scroll span (header height minus the 56dp toolbar). The toolbar title fades in over the last 45% and the toolbar fills with bg. All of it runs in graphicsLayer with no recomposition.
- **Ask container transform:** an animated rounded clip morphs from the Ask pill's or the Ask button's bounds (or the bottom edge if unknown) to the full-height sheet with 28dp top corners; the fill blends from card to bg. It opens on spring(0.86, 420) and closes on spring(1.0, 700). The scrim rises to 45% black; sheet content fades in after 30% progress. Dragging the handle past 120dp dismisses, otherwise it springs back. Chips and answers rise in (20dp travel, soft spring) staggered 45ms apiece, capped at 6. A skeleton pulses (0.45 to 1 alpha, 700ms reverse) while the planner runs. While listening, a mic ring breathes (1 to 1.35 scale, 900ms reverse).
- **Code card:** the drain bar moves linearly per 1s tick (1000ms linear tween). In the last 15s the bar and countdown turn amber and pulse (1 to 0.55 alpha, 650ms reverse). Copy fires a Confirm haptic and morphs Copy to Check with a scale-in from 0.6 on the bouncy spring, reverting after 1.8s. The chevron rotates 180 degrees and details expand with expandVertically on the soft spring.
- **Filing:** `SwipeAccept` reveals a green-box strip with a check that grows from 0.6 scale. Release past 30% of the width to accept; beyond that the drag rubber-bands at 0.3. Crossing the threshold fires GestureThresholdActivate; backing off fires SegmentTick; the row springs home on bouncy. ASK→SETTLED and DUE→PAID swap through `StateStamp`: a thump from 1.35 to 1 scale and from -8 to 0 degrees on the bouncy spring. After a dwell (560ms on Today and Bills, 480ms in Inbox; 100 to 120ms with animations off) the write lands and the row leaves via animateItem while its neighbours slide up.
- **Numbers:** `Roll` rolls each digit vertically on the snappy spring when it changes, starting from zeros on first show. The pacing line draws in over 700ms (FastOutSlowIn) once, and its end dot appears at the end.
- **Nav:** the indicator slides between tab slots on the snappy spring, drawn in the draw phase only; tab tints crossfade on soft. Press depth is 0.94 primary, 0.97 tonal, 0.92 Ask button (0.96 default `press`).
- **Flex:** posture changes crossfade in over 220ms and out over 160ms. The top pane slides from -48dp and the bottom from +48dp with a fade, on the soft spring.
- **Onboarding:** steps move on shared-axis X (1/5 width) with a fade on the soft spring, reversed going back. Progress segments fill on soft; floating labels scale to 0.75 and lift 11dp on focus or fill.
- **Empty states** rise in once. Loops (code pulse, mic ring, skeleton) run only while their condition holds and stop when they leave composition.

## Do's and Don'ts

### Do:
- **Do** open every primary screen with the collapsing viewing-area header (32sp title, 26% height, 150 to 300dp).
- **Do** put content in 26dp cards on the grey ground, with segmented rows separated by 72dp-inset hairlines.
- **Do** keep every touch target at least 48dp and every row at least 64dp.
- **Do** set every amount, date, time and code with tabular lining figures in the system face.
- **Do** use stamps only for real state: red ASK/DUE, green SETTLED/PAID, quiet CHECK.
- **Do** lift only hero objects (code card, Ask pill, nav pill and Ask button, answer cards, Flex tiles), and only in light mode.
- **Do** route every animation through `motion()` and the three springs, with a crossfade or instant fallback.
- **Do** hold FLAG_SECURE, reference-counted, only while a live code is on screen.
- **Do** cap tab labels at 1.3x font scale.

### Don't:
- **Don't** use gradients anywhere.
- **Don't** bring back ruled-ledger or passbook pastiche: no ruled lines, paper textures or faux print.
- **Don't** put kickers or eyebrows above titles, or label anything twice.
- **Don't** add decorative chrome: badges, ornaments or tinted panels that carry no state.
- **Don't** use a monospace face; use tabular figures instead.
- **Don't** use red, green or amber unless the state they mean is true.
- **Don't** add shadows to lists, or any shadow in dark mode.
- **Don't** loop an animation without a live condition behind it.
- **Don't** place controls near the Flex crease.
