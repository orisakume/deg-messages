# Feature verification matrix

Verification includes prior Medium Phone emulator checks and debug builds on a OnePlus CPH2447 / Android 16 (API 36). This release pass also builds against Android 17 (API 37); API 37 runtime checks are tracked below. UI checks do not replace carrier testing or broader physical-device coverage.

| Milestone / feature | Result | Verification and limits |
| --- | --- | --- |
| 1. Foundation and default SMS role | Pass on emulator and phone | App launches as the selected SMS role holder. On OnePlus CPH2447 / Android 16 (API 36), role holder is `com.example.messages` and role-granted SMS permissions are present. Updating the existing install from the prior database schema opened successfully after the Room schema migration. |
| 2. SMS | Pass on emulator | SMS send/receive and provider persistence were exercised in prior milestone checks. This turn also confirmed an inbound SMS appears in the provider. Carrier delivery and real delivery reports still need a physical SIM. |
| 3. Inbox and conversation UX | Pass for exercised flows | Inbox/search/conversation and selection actions were exercised in prior checks. On the OnePlus, a numeric contact thread showed the reply editor while an alphanumeric sender thread did not. A saved contact's profile photo appeared in the inbox row. Conversation title now resolves saved contact names and shows the number as a subtitle; SIM selection uses carrier labels. |
| 4. MMS / photo messaging | Deferred to Version 2 | Photo/attachment UI and MMS processing are intentionally excluded from Version 1. Android WAP-push/MMS URI declarations remain only for SMS-role qualification; no MMS transport is implemented. |
| 5. OTP management | Partial | Detection and OTP views are implemented. A test OTP notification showed the Copy OTP action and the action was tapped. Android's clipboard dump did not expose clipboard contents, so the copied value was not independently asserted. Unit tests cover expiry visibility after 24 hours and removal at the fourth copy. Physical clipboard action/count behavior and worker timing were not waited out. The source SMS is preserved by design. |
| 6. Recycle Bin and spam controls | Pass for exercised flows | Deleted SMS was captured with body/sender and 29 days remaining; restore returned it to the SMS Provider with its body and thread. A blocked sender's inbound SMS was stored, surfaced in Spam, and produced no app notification. Unblock plus “Not spam” was exercised. Retention expiry, permanent deletion, empty-bin, and reboot recovery were not all manually exercised end-to-end. |
| 7. Polish and release readiness | Partial | The OTP settings now explain fixed expiry and copy limits. Accessibility tree contains Chats, OTPs, and Settings. Per-thread pinch zoom/reset and persistence are implemented but not exercised on this phone. Physical-device performance/battery, TalkBack, font scaling, reboot, role reassignment, and Play Console declarations remain unverified. |

## Build verification

- `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:bundleRelease :app:lintDebug` passed after the latest changes; all 7 unit tests passed and lint reported no errors.
- Release APKs and the AAB are signed with the dedicated DEG Messages release certificate. The debug APK remains signed with the separate Android debug certificate.
- The resulting APK was installed and launched on the emulator before the final privacy-preview check.
- Installed and launched the latest debug APK on a USB-connected OnePlus CPH2447 running Android 16 (API 36); the app was updated in place to exercise the Room migration. The contact avatar, reply editor visibility for numeric versus alphanumeric senders, and permanent deletion of the identified test-only message were checked. No SMS was sent. The recent AndroidRuntime log contained no exception for `com.example.messages`; logged exceptions were from an OEM system package.
- The final debug APK was updated in place after confirming its debug signing certificate matched the installed app. Cold `am start -W` measurements were 1,009–1,142 ms. The latest launch completed successfully, with no app-specific AndroidRuntime fatal exception in the checked log window. Notification settings/actions and the new navigation/FAB were not manually exercised on this device.
- Four ABI-specific APKs and one universal APK are generated because native libraries are present for arm64-v8a, armeabi-v7a, x86, and x86_64. The release certificate has not been installed over the phone's debug-signed default-SMS app.
- Live contact suggestions, carrier SIM choice, the 24-hour expiry deadline, and four-copy cleanup still need direct device verification.

## Permission review

The app manifest declares core SMS permissions, `READ_PHONE_STATE`, `READ_CONTACTS`, and `POST_NOTIFICATIONS`. WAP-push/MMS declarations and URI schemes remain only because Android's default-SMS-role qualification contract requires them; Version 1 does not implement MMS. Protected broadcast permissions are required on the receivers/services rather than requested as app permissions. The app does not declare Internet, camera, microphone, or broad external-storage permissions.

## Release blockers

- Verify notification privacy previews, OTP copy count/expiration behavior, and remaining Android runtime-permission flows on a working emulator/device.
- Complete the physical-device, carrier, dual-SIM, performance, battery, accessibility, reboot, and recovery checks listed in [RELEASE_READINESS.md](RELEASE_READINESS.md).
- Complete privacy policy and Play Console declarations for restricted SMS and broad contact access before a Play release.

## Android 10+ compatibility coverage

- Build values are minSdk 29, compileSdk 37, and targetSdk 37. Build compatibility alone does not establish runtime compatibility on every API.
- Android 16/API 36 phone launch has been checked. The Android 17/API 37 Google APIs Play Store image is installed, but its clean emulator could not boot: the image requires 4 GB RAM while the host had about 3.3 GB available. No API 37 runtime result is claimed.
- API 29–35 emulator images are not installed. Compact/tablet/foldable, landscape, large font-scale, and keyboard-visible validation remain incomplete.
