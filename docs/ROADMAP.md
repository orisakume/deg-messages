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

## Version 2 — deferred media messaging

- MMS/photo send and receive, carrier/APN handling, attachment download and persistence, and related UX/security tests are future-version work. No Version 2 code is included in the Version 1 release scope.
- Sender identity icons: prefer a saved contact photo, then show a locally bundled category icon for confidently recognized senders (for example, a gas-cylinder icon for Indane); use initials/generic icons when identity is uncertain. Use company logos only when their source and sender matching are verified. Keep recognition on-device and avoid adding network access or permissions just to fetch logos.

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
