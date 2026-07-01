# AND — Dashboard cleanup + app-branding headers; remove badges & streak

Notes from the iOS implementation. Two things: (1) clean up the guardian dashboard + add app-branding headers, and (2) remove the badges & streak feature entirely, front-to-back.

## Cards

- **Android**: [Dashboard cleanup + app-branding headers](https://www.notion.so/360d8a4176228144bdc0eb7e47d8284b) — Backlog
- **iOS**: "Dashboard cleanup + app-branding headers" — Done

## Shared backend — already applied

Android needs **no DB work**. As part of the iOS change, on the shared Supabase project:

- Dropped both badge-awarding triggers on `training_records` (`award_badges_on_insert` and `on_training_record_insert` — two triggers, same function).
- Dropped the `award_badges()` function.
- Dropped the `badges` table (it was empty; nothing FKs to it).

Only `training_records_set_updated_at` remains on `training_records`. Just make sure the Android app stops querying the `badges` table.

## 1. App-branding header

New shared component — the app icon + the name **"Hound Habit"**, with an optional subtitle:

- iOS uses the `SplashLogo` asset (the existing transparent app-icon image, also used on the splash screen). Android: use the equivalent in-app logo drawable — **not** the launcher icon directly; a transparent-background logo asset.
- No subtitle on the guardian dashboard. Subtitle `"Guardians"` on the trainer page, rendered smaller (iOS: `.title2`, secondary color) beneath the brand line.
- Both screens **hide the nav bar** so the branding header is the screen header (replacing the old large titles).

iOS: `Shared/Components/AppBrandingHeader.swift`, a `VStack` of `[icon + "Hound Habit"]` (large/bold) and an optional secondary subtitle. Placed as a fixed header above the screen's `List`.

## 2. Guardian dashboard

`DashboardView` becomes: `AppBrandingHeader` (no subtitle) over a `List` with just two sections:

- **Training Plans** — "N plans assigned" tappable row → switches to the Plans tab; "No plans assigned yet." when zero.
- **Your Trainer** — linked trainer name + link date, or "No trainer linked yet."

Removed from the dashboard: the streak row and the achievements section. The old "Home" title is gone (the branding header replaces it). The tab-bar item stays "Home".

## 3. Trainer Guardians page

`GuardianListView` becomes a `VStack` of `AppBrandingHeader(subtitle: "Guardians")` over the existing loading / empty / list states. Nav bar hidden; `navigationDestination` for the guardian detail stays registered on the outer container.

## 4. Full removal of badges & streak

Delete, don't hide:

| iOS file | Android equivalent |
|---|---|
| `Guardian/Achievements/AchievementsView.swift` (+ its group) | the achievements screen |
| `Core/Models/Badge.swift` | the `Badge` model |
| `Core/Services/BadgeService.swift` | the badge service / repository |
| `BadgeChipView` (was private in `DashboardView`) | the badge-chip composable |
| `HoundHabitTests/Logic/StreakTests.swift` | streak unit tests |
| streak + badge state in `DashboardViewModel` (`badges`, `currentStreak`, `recentBadges`, `computeStreak`, the badge service) | the same in the dashboard view model |

After this, the dashboard view model just loads pets, the linked trainer, and the assigned-plan count.

## Manual test plan

1. Guardian Home tab → header is the app icon + "Hound Habit"; no streak, no achievements; only Training Plans + Your Trainer sections.
2. "N plans assigned" row → switches to the Plans tab.
3. Trainer Guardians tab → app icon + "Hound Habit" header with a smaller "Guardians" beneath it; tapping a guardian still opens the detail.
4. Nothing in the app references badges/streak anymore; no calls to the (now-dropped) `badges` table.

## Notes

- iOS kept the tab-bar item labeled "Home" — only the *screen title* changed to the branding header. Do the same on Android (bottom-nav label unchanged).
- The `SplashLogo` / in-app logo asset is distinct from the launcher/app icon — reuse whatever the splash screen uses.
