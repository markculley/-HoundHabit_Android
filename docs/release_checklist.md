# Play Console Release Checklist

End-to-end steps to ship Hound Habit to the Play Console internal testing track
and from there to closed / open testing and production. Captures the
Android-specific gotchas that aren't obvious from Google's docs.

Reference state for this checklist:

| | |
|---|---|
| Package id | `com.cometncloud.houndhabit` |
| `versionCode` | 1 (bump for every Play Console upload — must be monotonically increasing) |
| `versionName` | 1.0 (user-visible — bump on feature releases) |
| `minSdk` / `targetSdk` | 26 / 36 |
| Privacy policy URL | `https://www.cometncloud.com/houndhabitprivacypolicy` (already in Settings) |
| Permissions | `INTERNET`, `POST_NOTIFICATIONS`, `VIBRATE` |

---

## 1. In the repo — signing + release build

### 1a. Generate a release keystore (one time, **back this up**)

```bash
keytool -genkey -v \
  -keystore ~/keystores/hound-habit-release.jks \
  -alias hound-habit \
  -keyalg RSA -keysize 4096 \
  -validity 25000
```

Store the password somewhere safe (1Password, etc.). **If you lose this key
you cannot ship updates to the same Play Store listing** — you'd have to
publish a brand-new app with a different package id.

Then opt into [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)
when you first upload the AAB — Play holds the production key, you keep the
upload key locally. This is the modern default; just accept the prompt.

### 1b. Wire the keystore into `app/build.gradle.kts`

Add a `signingConfigs { release { … } }` block and reference it from
`buildTypes.release.signingConfig`. Read passwords from
`~/.gradle/gradle.properties` so they never enter the repo:

```kotlin
val releaseStoreFile: String? by project
val releaseStorePassword: String? by project
val releaseKeyAlias: String? by project
val releaseKeyPassword: String? by project

signingConfigs {
    if (releaseStoreFile != null) {
        create("release") {
            storeFile = file(releaseStoreFile!!)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }
}

buildTypes {
    release {
        if (releaseStoreFile != null) {
            signingConfig = signingConfigs.getByName("release")
        }
        isMinifyEnabled = false  // see 1d below before flipping this
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
}
```

In `~/.gradle/gradle.properties` (NOT in the repo):

```properties
releaseStoreFile=/Users/mark/keystores/hound-habit-release.jks
releaseStorePassword=...
releaseKeyAlias=hound-habit
releaseKeyPassword=...
```

### 1c. Build the AAB (app bundle — what Play actually wants)

```bash
./gradlew :app:bundleRelease
# output: app/build/outputs/bundle/release/app-release.aab
```

If you ever need an installable APK (e.g. for sideloading the same signed
build):

```bash
./gradlew :app:assembleRelease
```

### 1d. Optional but recommended: enable R8 minification

`isMinifyEnabled = true` shrinks the AAB and obfuscates. Compose, Supabase
Kotlin, and kotlinx.serialization all need a few `keep` rules; without them
the release build crashes at runtime in subtle ways (often: serializers
become "unable to find class"). Plan:

- Flip the flag.
- Build a signed release APK.
- Install via `adb install -r app-release.apk`.
- Smoke-test every screen.
- Fix any `NoSuchMethodError` / `SerializationException` by adding the
  relevant `-keep` rule to `app/proguard-rules.pro`.

If short on time, ship with `isMinifyEnabled = false` for v1 and tackle R8
in a later release.

### 1e. Verify the AAB before upload

```bash
# Confirm signing identity matches expectations.
$ANDROID_HOME/build-tools/<latest>/apksigner verify --print-certs \
  app/build/outputs/bundle/release/app-release.aab
```

(Or use `bundletool build-apks` + `install-apks` to do a dry-run install
from the AAB itself, which mirrors what Play does.)

---

## 2. Play Console — one-time account setup

1. Sign up for a Google Play Developer account at
   `https://play.google.com/console/`. **$25 one-time fee.**
2. Identity verification — Google now requires a government ID and a
   short personal-info form. Can take 1–3 days.
3. Create a new app.
   - App name: `Hound Habit`
   - Default language: English (US)
   - App or game: **App**
   - Free or paid: **Free**
   - Declarations: ad-free, complies with policies.

---

## 3. App content (the long form)

Play Console → **App content** sidebar. Every section here must be green
before you can publish to any track.

| Section | What this app needs to say |
|---|---|
| **Privacy policy** | The URL already in Settings: `https://www.cometncloud.com/houndhabitprivacypolicy`. |
| **App access** | Provide a test account (email + password) — Play reviewers sign in with this. Use a dedicated `playreview@…` Guardian account, not your personal account. Note that some features require linking to a trainer; provide either both accounts or a note that the guardian flow can be exercised standalone. |
| **Ads** | No. |
| **Content rating** | Run the questionnaire. Hound Habit is a pet-training app — no violence, no UGC by default (training notes are private to the guardian). Expected rating: **Everyone**. |
| **Target audience** | **18+** (adults), since this is a productivity / pet-care tool. Avoid declaring "designed for kids" — that triggers Families policy and the COPPA workflow. |
| **News app** | No. |
| **COVID-19 contact tracing & status app** | No. |
| **Data safety** | See §4 below — most attention goes here. |
| **Government apps** | No. |
| **Financial features** | No. |
| **Health declaration** | No (training, not medical). |
| **Advertising ID** | App does NOT use the advertising ID. |

---

## 4. Data safety form (most detailed)

Be honest — Play randomly audits and a wrong declaration is grounds for
removal. For this app the answers are:

**Does your app collect or share any of the required user data types?** Yes.

**Data collected, per category:**

| Category | Items | Collected | Shared | Optional? | Purpose | Encrypted in transit | Can users delete? |
|---|---|---|---|---|---|---|---|
| **Personal info** | Email address | Yes | No | No (required for account) | Account management | Yes | Yes (Account → Delete Account) |
| **Personal info** | Name | Yes | No | Yes (full_name is optional in signup) | Account management | Yes | Yes |
| **Photos and videos** | Photos | Yes | No | Yes (pet photos, resource photos — user-supplied) | App functionality | Yes | Yes |
| **App activity** | App interactions | Yes | No | No | Analytics (training streaks, etc., stored as user content) | Yes | Yes |
| **App info and performance** | Crash logs | No | — | — | — | — | — |

Other answers:
- **All user data is encrypted in transit** → Yes (Supabase is HTTPS-only).
- **Users can request data deletion** → Yes; in-app at Settings → Account → Delete Account (Phase 12 work).
- **Security review** — declare "Yes, my app has been independently security reviewed" only if true; otherwise leave unchecked.

---

## 5. Store listing

Play Console → **Main store listing**.

| Field | Notes |
|---|---|
| **App name** | `Hound Habit` (30 char max) |
| **Short description** | 80 chars. Suggestion: `A training tracker for guardians and their dog trainers.` |
| **Full description** | 4000 chars. Cover: log sessions, the Three D's, training plans, trainer linking, badges/streaks, daily reminders. |
| **App icon** | 512×512 PNG, no transparency required. Use the master art at `app/cnc_app_icon.png` downsampled to 512. **Do not** upload the foreground PNG (transparent) — that has the safe-zone padding baked in. |
| **Feature graphic** | 1024×500 PNG. Required. Often the icon + tagline on a brand-color background. |
| **Phone screenshots** | 2 minimum, 8 maximum. **min 320 px, max 3840 px on the long side, 16:9 or 9:16 ratio.** Capture from a phone-form-factor emulator (Pixel 8 is fine) via `adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png`. Capture at least: signup, pet list, log session form (showing the Three D's), plan detail, trainer dashboard, achievements. |
| **Tablet screenshots** | Optional. Skip unless you've tested on tablet form factors — the bottom-bar nav and modal sheets may need work for tablets first. |
| **Promo video** | Optional YouTube link. Skip for v1. |
| **App category** | Lifestyle (closest fit). Alternative: Health & Fitness. |
| **Tags** | Pet care, productivity. |
| **Contact email** | A monitored address. |

---

## 6. Release tracks

Promote up through tracks before going public.

### 6a. Internal testing

- **Up to 100 testers** by email or via a Google Group.
- No review wait time — usually live within minutes of upload.
- Right place to validate the signed build, the runtime permission flow,
  Google Sign-In (Credential Manager needs the production SHA-1 fingerprint
  registered in GCP — see the memory note about Android OAuth clients).
- **Critical:** add the production SHA-1 from Play App Signing to your
  Google Cloud OAuth client *before* releasing to internal testing, or
  Google Sign-In will silently fail in the released build even though it
  worked in debug.
  ```bash
  # After uploading the AAB and opting into Play App Signing, Play shows
  # the production signing certificate. Copy the SHA-1 fingerprint and add
  # it to GCP → APIs & Services → Credentials → your Android OAuth 2.0
  # Client ID. Match package = com.cometncloud.houndhabit.
  ```

### 6b. Closed testing

- Invite a wider tester group (e.g. real users with their own dogs).
- Triggers Google's first manual policy review — **plan for 1–7 days**.
- Common rejection causes for an app like this: missing privacy policy URL
  in-app, data-safety form contradicting actual permissions, account
  deletion not reachable in 3 taps from main.

### 6c. Open testing → Production

- Public, but still flagged as Open Test if you want.
- Production promotion gets a final policy review.

---

## 7. Pre-flight before each upload

1. Bump `versionCode` in `app/build.gradle.kts`. Play rejects duplicates.
2. Bump `versionName` if it's a user-visible change.
3. `./gradlew :app:bundleRelease`
4. Install the AAB locally (via `bundletool` or push the matching APK) and
   smoke-test:
   - Sign up a fresh account → triggers the profile trigger.
   - Sign in with Google → verifies the SHA-1 + Web Client ID setup.
   - Toggle daily reminder → grants notification permission.
   - Settings → Account → Delete Account → confirm full cleanup.
5. Upload to internal track → wait for "Available to testers" → install on
   a physical device via the opt-in link.
6. Promote up.

---

## 8. Things that will surprise you

- **Play App Signing is irreversible.** Once you opt in, you can't go back
  to managing your own signing key for that listing. That's fine; just
  know.
- **`POST_NOTIFICATIONS` permission must be requested at runtime** on
  Android 13+. We do this on first toggle of the daily reminder. If Play
  reviewers grant the permission, they'll see a "Daily Training Reminder"
  notification 24h later — make sure the test account in §3 is set up so
  this looks reasonable.
- **Account deletion path is explicitly tested by Play reviewers.** They
  need to reach the destructive button in under 3 taps from app open.
  Current path: bottom-bar Settings → scroll to Account → tap. ✅
- **Google Sign-In failure mode is silent on Android** — if the SHA-1
  isn't registered, the credential picker just dismisses without an error
  toast. Always verify by signing out + signing back in via Google on
  the internal test build.
- **`bundleRelease` will reuse cached debug code if Gradle's incremental
  build is stale.** When in doubt, `./gradlew clean :app:bundleRelease`.
- **Don't bundle `local.properties` or `gradle.properties` with secrets.**
  Confirm `git status` is clean before tagging.
