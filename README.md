# Hound Habit — Android

Android port of Hound Habit, a pet-training tracker. Kotlin + Jetpack Compose,
backed by the same Supabase project as the iOS original.

> ### ⚠️ Pre-release software
>
> **This is a pre-release version and is not yet generally available.** It has
> not shipped to the Play Store — the current release is the `v1.0.0-alpha.1`
> alpha tag (`versionCode 1` / `versionName 1.0.0-alpha.1`). Builds are for
> development and internal testing only.
>
> Expect incomplete features, unstable behavior, and breaking changes without
> notice. Do not rely on it for real training records — data written by a
> pre-release build may be lost or migrated destructively. APIs, schema
> assumptions, and UI are all subject to change.

## Docs

- **[docs/prd.md](docs/prd.md)** — product spec; canonical source for enum values and UX decisions.
- **[docs/mvp_plan.md](docs/mvp_plan.md)** — architecture, schema, and the 12-phase build plan.
- **[docs/porting_map.md](docs/porting_map.md)** — file-by-file iOS Swift → Android Kotlin mapping, stack equivalences, and gotchas.
- **[docs/bootstrap.md](docs/bootstrap.md)** — original repo/project bootstrap checklist.
- **[docs/release_checklist.md](docs/release_checklist.md)** — end-to-end Play Console release steps.
- **[docs/use_cases/](docs/use_cases/)** — one Mermaid-diagrammed file per use case.

## Getting started

Requires a `local.properties` (gitignored, never committed) with
`SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `GOOGLE_WEB_CLIENT_ID`.

```bash
make help      # list all targets
make build     # assemble debug APK
make run       # install + launch on a connected device/emulator
make check     # lint + unit tests
```

The `Makefile` is the canonical command surface — see it for the full target list.

## Ground rules for the Android port

1. **Same Supabase project, same schema.** No backend forks. RLS, triggers, and the `delete_my_account` RPC are reused as-is.
2. **`docs/prd.md` and `docs/use_cases/` remain the canonical spec.** Copied from the iOS repo with periodic manual sync.
3. **Feature parity, not visual parity.** Use Material 3 idioms on Android, not a SwiftUI lookalike.
