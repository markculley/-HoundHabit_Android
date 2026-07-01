# AND-16 — Build Account screen (name, email, identity provider, dates)

The matching iOS card is **IOS-13**, but the scope is asymmetric: iOS already had an Account screen, so IOS-13 was just the IDP row addition. Android still has to build the whole screen. These notes cover the IDP-row implementation in depth and stub-link the rest.

## Cards

- **Android**: [AND-16](https://www.notion.so/35ed8a41762281d19e42cd110be5ffe4) — full Account screen
- **iOS**: [IOS-13](https://www.notion.so/35ed8a41762281c686d6f699b70b0b5b) — IDP row only (**shipped**)

## What iOS did (IDP row)

Single file: [`HoundHabit/Guardian/Settings/AccountView.swift`](https://github.com). Two pieces:

### 1. New `LabeledContent` row in the Account section

```swift
Section("Account") {
    LabeledContent("Role", value: profile?.role.rawValue.capitalized ?? "—")
    LabeledContent("Name", value: profile?.fullName ?? "—")
    LabeledContent("Email", value: user?.email ?? "—")
    LabeledContent("Signed in with", value: signedInWithLabel)   // NEW
}
```

### 2. Provider-label helpers

```swift
/// Comma-separated list of provider display labels (e.g. "Apple, Email & Password").
/// Falls back to "—" when Supabase hasn't populated `identities`.
private var signedInWithLabel: String {
    let providers = user?.identities?.map(\.provider) ?? []
    guard !providers.isEmpty else { return "—" }
    return providers.map(Self.displayLabel(forProvider:)).joined(separator: ", ")
}

private static func displayLabel(forProvider provider: String) -> String {
    switch provider.lowercased() {
    case "apple":  return "Apple"
    case "google": return "Google"
    case "email":  return "Email & Password"
    default:       return provider.prefix(1).uppercased() + provider.dropFirst()
    }
}
```

`user?.identities` is `[UserIdentity]?` on `Supabase.User`; each `UserIdentity` has `.provider: String` ("apple", "google", "email", etc.). The supabase-kt equivalent is identical in shape.

## Android implementation (when you build the screen)

Mirror the iOS helper. In Kotlin:

```kotlin
@Composable
private fun signedInWithLabel(user: UserInfo?): String {
    val providers = user?.identities?.map { it.provider } ?: emptyList()
    if (providers.isEmpty()) return "—"
    return providers.joinToString(", ") { providerDisplayLabel(it) }
}

private fun providerDisplayLabel(provider: String): String = when (provider.lowercase()) {
    "apple"  -> "Apple"
    "google" -> "Google"
    "email"  -> "Email & Password"
    else     -> provider.replaceFirstChar { it.uppercase() }
}
```

On supabase-kt, identities live on `UserInfo.identities` (a `List<Identity>?`). Each `Identity` has a `provider: String`. Same provider tokens as iOS.

### Row order in the Account section

Match iOS for consistency:

1. Role
2. Name
3. Email
4. **Signed in with**

Created / Last Login go in a separate "Dates" section (also matching iOS).

## Acceptance criteria specific to the IDP row

These are a subset of AND-16's full criteria:

- [ ] Account screen shows a **Signed in with** row with the user's provider(s)
- [ ] Apple-Sign-In users see "Apple" (relevant once Apple-on-Android is wired up; for now mostly hypothetical)
- [ ] Google-Sign-In users see "Google"
- [ ] Email/password users see "Email & Password"
- [ ] Unknown provider falls back to capitalized version of the raw token
- [ ] Multiple linked identities → comma-separated list

## Test cases

Same tokens as iOS:

| User signed in with | Expected display |
|---|---|
| Email + password only | `Email & Password` |
| Google only | `Google` |
| Apple only | `Apple` |
| Google + Email linked | `Google, Email & Password` |
| No identities populated yet | `—` |

## Reminder about the broader scope

AND-16 is still mostly unbuilt — Android's Settings screen doesn't have an Account row at all. Build the screen first; the IDP row is just one of six rows. The "Delete Account" implementation note in AND-16 still stands: pick whether to keep that button on Settings or move it onto the new Account screen.
