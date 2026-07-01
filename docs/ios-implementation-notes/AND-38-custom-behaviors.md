# AND-38 — Custom (trainer-defined) behaviors

Notes from the parallel iOS change, captured so the Android side stays in sync. Unlike most of these notes, **Android was already ahead here** — the main takeaway is a backend change that unblocks the Android form, plus one small gap to close.

## Card

- **Android**: [AND-38](https://app.notion.com/p/390d8a41762281c1a9d0d0762300c259) — `Custom (trainer-defined) behaviors` (Backlog)
- **iOS**: shipped first (ad-hoc change, no IOS card)

## What changed (requirement)

Behaviors were previously restricted to a fixed list of 12 standard types. The requirement changed: a **Trainer can now add a custom, free-text behavior** alongside the 12 presets.

## Backend status — DEPLOYED

Migration `allow_custom_behaviors` is **live in production Supabase** (project `ddrkziwwpmqaophocnhb`). It:

- **drops** the old `behaviors_name_valid_type` CHECK (the closed list of 12 labels), and
- **adds** `behaviors_name_nonempty`: `CHECK (char_length(btrim(name)) BETWEEN 1 AND 40)`.

SQL source is checked in at `hound_habit/scripts/sql/allow_custom_behaviors.sql` in the iOS repo. No data migration was needed — existing rows hold valid standard names.

### This fixes a latent Android bug

Android's `BehaviorFormScreen` was **already** a free-text `OutlinedTextField` + "SUGGESTIONS" list (it never had the iOS enum restriction). But against the *old* CHECK constraint, any non-standard name a trainer typed would have been rejected by Postgres with a **`23514` check_violation** on insert. As of this migration, custom names insert successfully. If you saw (or QA saw) "add behavior fails for anything not in the list," that was this — and it's now resolved server-side with no client change.

## Android code status

- `core/models/Behavior.kt` — `name: String`. **No change needed.** (Android stores the raw string; it never modelled a closed enum, so it also can't crash decoding custom names created from iOS.)
- `feature/trainer/plans/BehaviorFormScreen.kt` — free text + `SUGGESTIONS` list already matches the target UX. **One gap:** it does not cap length, but the DB now enforces `≤ 40` chars. Add a length guard so a long name fails in-form instead of round-tripping to a `23514`:
  ```kotlin
  val canSave = name.trim().let { it.isNotEmpty() && it.length <= 40 }
  // and/or trim input in onValueChange to 40 chars
  ```
- `core/services/TrainingPlanService.kt` — `createBehavior(name = ...)` / `updateBehavior` pass the string straight through. No change.

## iOS vs Android divergences (intentional — do NOT "fix" to match)

| Aspect | iOS | Android |
|---|---|---|
| Model | `BehaviorType` = `.standard(StandardBehavior)` \| `.custom(String)` discriminated enum, Codes to/from a bare string | `name: String` (plain) |
| Wire format | bare string in `behaviors.name` | bare string in `behaviors.name` — **identical on the wire**, so they interoperate |
| Custom == standard label | coerced to `.standard` (e.g. typing "Sit" → the preset) | stored as the literal string; harmless |
| Rename after create | **not allowed** (delete + re-add) | **allowed** (`BehaviorFormScreen` has an `editing:` path / "Edit Behavior") |

The rename divergence predates this change and is a genuine product difference; Android's rename is now safe against the backend. Flag it to product if parity is desired, but no code action is required for this change.

## Checklist for the Android card

- [ ] Add the `≤ 40` char guard in `BehaviorFormScreen` (only real code change).
- [ ] Manually verify: trainer types a custom name → behavior saves (no `23514`).
- [ ] Confirm a custom behavior created on iOS renders correctly in the Android guardian/trainer plan views (it will — plain string).
- [ ] Update `docs/prd.md` behavior section on the Android side to match iOS wording ("12 standard **or** a custom trainer-typed name").
