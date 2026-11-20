# Messages project roadmap

## Version 1 — SMS-focused release

**Scope:** Native SMS, OTP management, notifications, Recycle Bin, local spam filtering, privacy, accessibility, and the fixed black, white, and green interface.

**Explicitly out of scope:** MMS, photo sending/receiving, and all attachment messaging. These are deferred to Version 2. Version 1 has no photo picker, attachment draft, MMS transport, MMS persistence, or attachment permission request. Minimal WAP-push receiver and MMS URI declarations remain in the manifest to satisfy Android default-SMS-role qualification; they do not implement MMS behavior.

### Release-readiness checklist

#### Build and packaging

- [x] Debug APK builds successfully.
- [x] Unit test suite passes.
- [ ] Create and verify a signed release build (not just the debug APK).
- [ ] Verify install, update, and uninstall/reinstall behavior on supported Android versions.

#### SMS and Android integration

- [x] Request Android's default SMS role before relying on SMS Provider access.
- [x] Implement SMS Provider inbox/read/write paths and SmsManager sending, including multipart text and failure/retry state.
- [ ] Verify incoming and outgoing SMS on physical devices and carrier SIMs.
- [ ] Verify default-role removal and reassignment, SMS permission denial, delivery reports, and dual-SIM selection/routing.
- [ ] Confirm Android system backup/restore and message retention expectations (app backup is disabled).

#### OTP, notifications, and privacy

- [x] Local OTP detection and OTP list actions (copy, correct, hide) are implemented.
- [x] OTP cards expire after 24 hours or after four copies; expiry removes OTP metadata only and preserves the source SMS.
- [x] Notifications use private lock-screen visibility; message previews can be hidden in app settings.
- [ ] Verify OTP copy value and fourth-copy cleanup end-to-end, 24-hour expiry, behavior when default SMS role is lost, and interaction with Recycle Bin.
- [ ] Verify notification actions, Android 13+ permission grant/denial, preview hiding, and behavior across reboot on a working device.
- [ ] Decide whether app-private OTP/Recycle Bin data needs encryption before making security claims; current Recycle Bin message copies are not app-layer encrypted.

#### Recycle Bin and spam

- [x] Recycle Bin capture and restore path exercised on emulator.
- [x] Blocked sender was stored in Spam without an app notification; unblock and “Not spam” were exercised.
- [ ] Verify permanent delete, empty bin, expiration cleanup, restore failures, and reboot recovery end-to-end.
- [ ] Confirm retention behavior when WorkManager is delayed or the app is force-stopped.
- [ ] Keep wording clear that spam blocking is app-local and cannot block carrier delivery.

#### Accessibility, compatibility, and quality

- [x] Fixed palette; no theme selector, ads, analytics, account, or backend.
- [x] No Internet, camera, microphone, or broad storage permission is declared.
- [ ] Verify accessibility labels and full navigation with TalkBack.
- [ ] Verify font/display scaling, pinch zoom and saved per-conversation text size, contrast, and touch targets.
- [ ] Measure cold start, long inbox/search behavior, battery use, and background worker impact on physical devices.
- [ ] Verify Room migrations from every previously released schema and test low-storage/error cases.

#### Store and legal readiness

- [ ] Publish a privacy policy that accurately describes SMS/OTP handling, local storage, retention, and deletion.
- [ ] Complete Google Play SMS and Contacts permission declarations and Data Safety answers for the actual release behavior.
- [ ] Review current Google Play SMS policy and Android default-handler requirements before submission.
- [ ] Provide a clear support contact and release notes; do not advertise MMS/photo messaging in Version 1.

## Version 2.0.0 — development scope

V2 development is tracked on `codex-v2-development`. The goal is a privacy-focused SMS/MMS client using Android telephony and local processing only. No analytics, account, advertising, proprietary cloud, paid API, or developer-operated server is in scope. V1 behavior must remain available throughout development.

### Planned work

- [ ] Messaging: strengthen SMS delivery/retry, drafts, multi-recipient composition, message actions and conversation organization; add carrier-compatible MMS, attachment handling and image preparation where Android/carrier APIs permit.
- [ ] Smart messaging: configurable local OTP retention, user-correctable local categories, richer local search, scheduled SMS with restart recovery, and local templates.
- [ ] Privacy: optional app lock and private conversations using Android authentication; local blocklist and suspicious-link warnings; never claim SMS encryption.
- [ ] Notifications and contacts: notification actions and per-conversation controls; contact discovery/photos and explicit-confirmation QR import/export.
- [ ] Data management: validated user-selected backup/restore, optional encrypted export, duplicate handling, and improved Recycle Bin retention and restore behavior.
- [ ] Experience and quality: accessible adaptive UI, theme options, performance work, Android 10+ compatibility, device/manual test matrix, and release documentation.

These are planned requirements, not claims that the features are already implemented. Carrier/MMS behavior and system-restricted operations will be marked with their tested limitations.

## Current known gaps and unverified behavior

- The app has been installed and visually checked on a OnePlus CPH2447 running Android 16 (API 36). OTP expiry/copy behavior now removes only app metadata and keeps the source SMS visible in chat; the OTP settings explain this fixed policy. The app is the phone's SMS-role holder.
- Emulator SMS, OTP notification action, Recycle Bin restore, and blocked-sender spam suppression were exercised as recorded in [TEST_MATRIX.md](TEST_MATRIX.md). These results are not carrier/physical-device validation.
- OTP clipboard contents were not independently asserted; only the notification action's presence and tap were verified.
- OTP and Recycle Bin expiry deadlines, WorkManager delays, reboot recovery, notification preview privacy, pinch-zoom persistence, TalkBack, role reassignment, dual-SIM behavior, and physical-device battery/performance are not fully verified. No live SMS was sent during this phone check to avoid contacting anyone or changing message data.
- Standard SMS still uses Android's carrier SMS service. Wi-Fi/mobile-data messaging requires RCS or a separate messaging service/backend and is not part of this app.
- MMS/photo messaging is intentionally absent in Version 1, not a release defect. The UI does not offer attachment selection.

## Platform references

- [Android Telephony: default SMS app responsibilities](https://developer.android.com/reference/android/provider/Telephony)
- [Google Play SMS and Call Log permissions policy](https://support.google.com/googleplay/android-developer/answer/18265346)
- [Android default handler guidance](https://developer.android.com/guide/topics/permissions/default-handlers)
