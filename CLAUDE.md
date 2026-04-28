# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

Android port of **Hound Habit**, a pet-training tracker. The iOS original is the source of truth for product behavior; this repo is a **feature-parity, not visual-parity** rewrite in Kotlin. Same Supabase project, same schema, same RLS — no backend forks.

iOS Swift sources to port from live at `/Users/mark/dev/hound_habit/HoundHabit/` (outside this repo). `docs/porting_map.md` maps every Swift file to its Kotlin counterpart and is the primary reference when adding a feature.

## Authoritative docs (read before non-trivial work)

- `docs/prd.md` — product spec; canonical source for enum values (Three D's, status colors) and UX decisions. Copied from the iOS repo; sync manually.
- `docs/mvp_plan.md` — architecture, schema, 12-phase plan. Phase order is the same as iOS.
- `docs/porting_map.md` — file-by-file iOS → Android mapping with stack equivalences and gotchas. **Use this before creating new files** so structure stays consistent.
- `docs/bootstrap.md` — bootstrap checklist; reflects intended dependency set (Supabase Kotlin, Compose, Navigation, Credential Manager, WorkManager). The current `app/build.gradle.kts` has not yet been upgraded to this set.
- `docs/use_cases/` — one Mermaid-diagrammed file per use case. Update or create one when implementing a use case.

## Current state

This is an Android Studio scaffold; **no app code has been ported yet.** `app/src/main/java/com/example/houndhabit/` is empty. The scaffold currently declares `appcompat` + `material` (Views toolkit), but the chosen stack per `docs/bootstrap.md` and `docs/porting_map.md` is **Jetpack Compose + Material 3**. Expect to swap the dependencies in `gradle/libs.versions.toml` and `app/build.gradle.kts` when starting Phase 1 work.

Package id is `com.example.houndhabit`. `bootstrap.md` proposes `com.markculley.houndhabit` to match the iOS bundle id; either rename early or accept the mismatch — don't half-rename.

`local.properties` (gitignored) holds `SUPABASE_URL` / `SUPABASE_ANON_KEY` and points at the same Supabase project as iOS.

## Common commands

```bash
./gradlew :app:assembleDebug          # build debug APK
./gradlew :app:test                   # JVM unit tests (src/test)
./gradlew :app:connectedAndroidTest   # instrumented tests on a running emulator/device
./gradlew :app:lint                   # Android Lint
./gradlew :app:test --tests com.example.houndhabit.SomeTest.someMethod   # single test
```

## Architecture & conventions

**Pattern:** MVVM + Service layer, mirroring iOS.
- Models: pure `@Serializable` data classes (`kotlinx.serialization`) with `@SerialName("snake_case")` mapping for the Supabase JSON wire format. This replaces the iOS `Codable` + `CodingKeys`.
- Services: stateless classes with `suspend fun` methods wrapping the Supabase client. Errors propagate as exceptions (or `Result<T>` where the call site needs both branches).
- ViewModels: `androidx.lifecycle.ViewModel` exposing `StateFlow` (or Compose `mutableStateOf`). Coroutines run in `viewModelScope`.
- Routing: a single `AppRouter` observes `supabase.auth.sessionStatus` and switches between Auth / Guardian / Trainer nav graphs.

**Folder layout:** feature-grouped, parallels iOS — `core/{models,services}`, `feature/{auth,guardian,trainer}/...`, `shared/{components,util,notifications}`. See `docs/porting_map.md` for exact file targets.

**Stack equivalences** (also in porting_map):
- SwiftUI `View` → `@Composable fun`
- `@Observable` VM → `ViewModel` + `StateFlow`
- `NavigationStack` + value links → `NavHost` with typed routes (Navigation Compose 2.8+)
- `.sheet` → `ModalBottomSheet`
- `UNUserNotificationCenter` → `WorkManager` + `NotificationCompat`
- `PhotosPicker` → `ActivityResultContracts.PickVisualMedia`
- Sign in with Apple → Google Sign-In via Credential Manager (primary auth provider on Android)

## Gotchas (carry over from iOS — these will bite)

1. **UUID lowercasing.** Supabase RLS compares against `auth.uid()::text`, which is lowercase. Any UUID used in a storage path or string-compared identifier must be `uuid.toString().lowercase()`. The iOS app got bitten by this.
2. **Don't nest `NavHost`s inside the same `NavController`.** Use nested graphs (`navigation { ... }`) inside one `NavHost`. Analogous to the iOS rule against nested `NavigationStack`s.
3. **Account deletion flow.** Call the existing `delete_my_account` RPC, then show the "Account Deleted" confirmation, **then** sign out on dismiss. Do not sign out inside the service method — the iOS code was fixed to remove that and the bug shouldn't reappear here.
4. **Profile auto-creation.** Handled by a Postgres trigger on `auth.users`. Pass `full_name` and `role` in user metadata at sign-up time, exactly as the iOS `signUp` does — don't insert into `profiles` from the client.
5. **Storage buckets:** `pet-photos`, `resources`, `avatars`. Path conventions are documented in `docs/mvp_plan.md`.

## Testing rules (same shape as iOS)

Write tests for:
- **Each `@Serializable` enum** — one test per enum verifying the exact wire string (e.g., `"arms_length"`) decodes to the correct case. Lives in `app/src/test/.../models/`.
- **Pure ViewModel logic** — streaks, filters, aggregations, anything that computes derived state without hitting the network. Extract to a top-level / companion function if needed.

Skip:
- Composable UI tests (no equivalent of iOS `#Preview` enforcement; previews are encouraged but not required).
- Service-layer tests (require a live Supabase connection — defer).
- Postgres trigger / badge logic (test directly via Supabase SQL).