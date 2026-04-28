# Hound Habit — Android Port

Planning docs for the Android version of Hound Habit, intended to live in a separate repo (suggested: `hound_habit_android`). These files stay here only until that repo is bootstrapped — copy them over and delete this folder once it exists.

## Files

- **[bootstrap.md](bootstrap.md)** — Step-by-step checklist for creating the new repo, project, and connecting it to the existing Supabase backend.
- **[porting_map.md](porting_map.md)** — File-by-file mapping from the iOS Swift source to the Android Kotlin equivalent, including stack choices and gotchas.

## Ground rules for the Android port

1. **Same Supabase project, same schema.** No backend forks. RLS, triggers, and the `delete_my_account` RPC are reused as-is.
2. **`docs/prd.md` and `docs/use_cases/` remain the canonical spec.** Either submodule them in or copy them with periodic manual sync.
3. **Feature parity, not visual parity.** Use Material 3 idioms on Android, not a SwiftUI lookalike.
