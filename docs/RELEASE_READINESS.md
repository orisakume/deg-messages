# Version 1 release readiness

The maintained release checklist is [ROADMAP.md](ROADMAP.md). It covers SMS/default-role integration, OTP management, notifications/privacy, Recycle Bin, spam filtering, accessibility, packaging, and store readiness.

## Current security and privacy review

- No Internet permission, advertising SDK, analytics SDK, account, or backend is present.
- SMS permissions are used for the app's core default-SMS role. `READ_CONTACTS` is optional for contact names/photos and recipient suggestions; `READ_PHONE_STATE` is requested when the user opens SIM selection; notifications are requested on Android 13+.
- Android WAP-push/MMS URI declarations are retained for default-SMS-role eligibility only. Version 1 has no attachment UI and does not process MMS.
- App backup is disabled. OTP records and Recycle Bin message copies are local in app-private Room storage. Recycle Bin copies are not app-layer encrypted, and uninstall may remove recovery data.
- Notification lock-screen visibility is private. Message preview text can be hidden through the Settings screen; this UI behavior still needs a final emulator check.
- Blocked-number rules are local and do not block carrier delivery.

## Release status

- With minSdk 29 and compile/target API 37, debug and signed release builds, unit tests, lint, and bundle tasks pass. Release artifacts use the dedicated certificate recorded in [RELEASE_CERTIFICATE_SHA256.txt](RELEASE_CERTIFICATE_SHA256.txt). The debug APK uses the separate Android debug key.
- Emulator checks have exercised SMS provider paths, OTP notification action visibility/tap, Recycle Bin restore, and blocked-sender spam suppression; see [TEST_MATRIX.md](TEST_MATRIX.md).
- Latest build was installed and launched in place on a OnePlus CPH2447 / Android 16 (API 36), exercising the Room migration. Contact photos appear in the inbox; the composer is hidden for an alphanumeric sender and shown for a numeric contact address. An identified test-only SMS was permanently removed. No app-specific runtime exception appeared in the log scan.
- Saved contact title lookup, live recipient matches, and carrier-named SIM selection are implemented. OTP cards expire after 24 hours or the fourth copy while source SMS remains in chat. These new behaviors have unit coverage where applicable but still need end-to-end physical-device verification.
- Sending over Wi-Fi/mobile data is not available: this app uses Android's SMS service. Data messaging needs RCS or a separate service/backend.
- A cold launch on OnePlus CPH2447 / Android 16 measured 1,009–1,142 ms after deferring worker setup, historical OTP indexing, and contact lookups. This is a small sample, not a broad performance or battery assessment. Physical SIM/carrier, dual-SIM, TalkBack, retention expiry, reboot recovery, and remaining compatibility validation remain open in [ROADMAP.md](ROADMAP.md). The app targets API 37.

Do not market or ship Version 1 as an MMS/photo messaging app. Review [Android default SMS handler requirements](https://developer.android.com/reference/android/provider/Telephony) and the current [Google Play SMS permissions policy](https://support.google.com/googleplay/android-developer/answer/10208820) before submission.
