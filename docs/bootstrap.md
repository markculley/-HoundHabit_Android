# Android Bootstrap Checklist

## 1. Create the repo

- [ ] `gh repo create hound_habit_android --private`
- [ ] Local clone, add `.gitignore` (Android Studio template)
- [ ] Copy `docs/prd.md`, `docs/mvp_plan.md`, `docs/use_cases/`, `docs/privacy_policy.md` into the new repo's `docs/` folder. (Or `git submodule add` a shared spec repo if you decide to extract one.)

## 2. Create the Android Studio project

- [ ] Android Studio → New Project → **Empty Activity (Compose)**
- [ ] Package: `com.markculley.houndhabit` (match the iOS bundle identifier base)
- [ ] Min SDK: **API 26 (Android 8.0)** — needed for `java.time` without desugaring headaches and modern notification APIs
- [ ] Target SDK: latest stable
- [ ] Language: **Kotlin**, build system: **Gradle (Kotlin DSL)**

## 3. Core dependencies

Add to `app/build.gradle.kts`:

```kotlin
dependencies {
    // Supabase (BOM keeps modules in sync)
    implementation(platform("io.github.jan-tennert.supabase:bom:<latest>"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt") // only if needed
    implementation("io.ktor:ktor-client-okhttp:<latest>")

    // Compose
    implementation(platform("androidx.compose:compose-bom:<latest>"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navigation, lifecycle, image loading
    implementation("androidx.navigation:navigation-compose:<latest>")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:<latest>")
    implementation("io.coil-kt.coil3:coil-compose:<latest>")

    // Auth
    implementation("androidx.credentials:credentials:<latest>")
    implementation("androidx.credentials:credentials-play-services-auth:<latest>")
    implementation("com.google.android.libraries.identity.googleid:googleid:<latest>")

    // Notifications + alarms
    implementation("androidx.work:work-runtime-ktx:<latest>")

    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("io.mockk:mockk:<latest>")
}
```

## 4. Supabase wiring

- [ ] Create `local.properties` entries (NOT committed):
  ```properties
  SUPABASE_URL=https://<project>.supabase.co
  SUPABASE_ANON_KEY=<anon key>
  ```
- [ ] Expose them in `build.gradle.kts` via `buildConfigField`
- [ ] Create `core/SupabaseClient.kt` — singleton equivalent to [HoundHabit/Core/Services/SupabaseClient.swift](../../HoundHabit/Core/Services/SupabaseClient.swift)

## 5. Auth providers

| iOS  | Android |
|------|---------|
| Sign in with Apple | **Google Sign-In** via Credential Manager (primary) |
| Email + password | Email + password (works as-is via `auth-kt`) |
| —    | (Optional) Apple via web OAuth flow if cross-platform parity needed |

Configure Google OAuth in Supabase dashboard → Authentication → Providers, then add the SHA-1 fingerprint of your debug + release keystores.

## 6. Notifications

- [ ] Request `POST_NOTIFICATIONS` runtime permission (API 33+)
- [ ] Use `WorkManager` + `NotificationCompat` to replicate the daily training reminder logic from [NotificationManager.swift](../../HoundHabit/Shared/Utilities/NotificationManager.swift)
- [ ] Boot receiver to reschedule after device reboot

## 7. CI / release

- [ ] GitHub Actions workflow: `./gradlew :app:assembleDebug test` on PR
- [ ] Set up Play Console internal testing track before writing release-signing config
- [ ] App version: read from `BuildConfig.VERSION_NAME` — and surface it in the Account screen footer (the iOS app does not currently display version; Android port can add it from day one)

## 8. First milestone

Don't try to ship all 12 phases at once. Match the iOS phase order from `docs/mvp_plan.md`:

1. Project scaffold + Supabase client
2. Auth (email/password first; Google second)
3. Pet profiles (Guardian)
4. Training record logging

Stop and ship an internal test build at this point before continuing.
