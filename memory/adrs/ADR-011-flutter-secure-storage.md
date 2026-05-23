# ADR-011: flutter_secure_storage for JWT on Mobile (Not SharedPreferences)

**Status:** Accepted
**Date:** 2026-05
**Deciders:** Project architect + Security

## Context

The Flutter mobile app receives a JWT after authenticating via the Telegram Login Widget WebView. It must persist this token across app restarts. The two common Flutter storage options:

- **`SharedPreferences`** — simple key-value store, backed by unencrypted XML on Android, `NSUserDefaults` on iOS (also unencrypted by default)
- **`flutter_secure_storage`** — backed by Android Keystore on Android, Keychain on iOS — encrypted at the OS level

## Decision

Use **`flutter_secure_storage`** with platform-specific options for maximum encryption.

```dart
static const _storage = FlutterSecureStorage(
  aOptions: AndroidOptions(encryptedSharedPreferences: true),
  iOptions: IOSOptions(accessibility: KeychainAccessibility.first_unlock),
);
```

Key reasons:

1. **Android unencrypted by default**: `SharedPreferences` on Android is stored as plaintext XML in `/data/data/<package>/shared_prefs/`. On a rooted device, ADB backup, or Android backup extraction, this file is readable. A JWT grants full account access.

2. **Android Keystore hardware binding**: `flutter_secure_storage` with `encryptedSharedPreferences: true` uses `EncryptedSharedPreferences` backed by the Android Keystore. Keys are hardware-bound on devices with a secure element — they cannot be extracted even on rooted devices.

3. **iOS Keychain protection**: The iOS Keychain with `KeychainAccessibility.first_unlock` means the secret is encrypted at rest and only accessible after the device is first unlocked after a reboot. It is not backed up to iCloud by default.

4. **Consistent security model**: Both platforms provide hardware-backed encrypted storage. The code is identical regardless of platform — no `Platform.isAndroid` branching.

## Consequences

- **Never use `SharedPreferences` for any security-sensitive value** — token, user ID, session identifier. `SharedPreferences` is fine for UI preferences (theme, language).
- All JWT read/write/delete goes through `TokenStorage` in `lib/core/auth/token_storage.dart` — no direct `FlutterSecureStorage` calls outside this class
- On app uninstall on iOS, Keychain entries **persist** (unlike Android) — the `TokenStorage.delete()` method must be called on logout, not just on uninstall
- First-launch after install on a device that previously had the app will find a stale token in the iOS Keychain — always validate the token with the server on app start, don't assume a stored token is still valid

## Failure mode if violated

`SharedPreferences` on Android: any app with `READ_EXTERNAL_STORAGE` permission (pre-Android 10) or a device backup can read the JWT. The attacker gains full account access without needing the user's Telegram credentials.

## Related

- ADR-007 — JWT issuance on the backend
- ADR-009 — Flutter mobile app decision
- `flutter-mobile.md` agent — full `TokenStorage` implementation
