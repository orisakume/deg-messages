# DEG Messages

DEG Messages is a native Android SMS client built with Kotlin, Jetpack Compose,
Android's SMS Provider, and local Room metadata. Version 1 focuses on SMS,
locally detected OTPs, notification actions, message recovery, and local spam
filtering. MMS, photos, and attachments are outside the current release scope.

## Features

- Chats, OTPs, and Settings with manual bottom navigation.
- SMS conversations, search, archive, pin, mute, and read status.
- Local OTP detection and copy, with expiring OTP cards; the original SMS stays
  in the conversation.
- Incoming message notifications with configurable message previews, Mark as
  read, and context-aware Copy OTP, Copy number, or Delete actions.
- Local Recycle Bin with restore, permanent deletion, and configurable cleanup.
- Optional contact names/photos, SIM selection, local spam filtering, and
  blocked-number rules.
- Fixed black, white, and green theme. No account, advertising, analytics, or
  backend is included in the app module.

## Screenshots

Screenshots are not included in this release. Captures from the connected
phone may contain private SMS or contact data; sanitized app screenshots can
be added after review.

## Requirements

- Android 10 (API 29) or newer. The build currently compiles against and targets
  Android 17 (API 37); see the compatibility matrix for versions actually tested.
- Android SDK 37 and Java 17 for building.
- A telephony-capable device and active SIM for carrier SMS. Android may run
  the interface on devices without telephony, but sending/receiving SMS is not
  available there.
- DEG Messages must be selected by the user as the device's default SMS app to
  access the shared SMS Provider and perform core SMS handler functions.
- Compact through large phones, tablets, and foldables use the same adaptive
  Compose layouts. Only the form factors listed in the test matrix are claimed
  as tested.

## Permissions

The app declares SMS send/read/receive permissions for its core default SMS
handler function. Contacts access is optional and supports saved contact names,
photos, and recipient suggestions. Phone state access supports SIM selection.
Notification permission is used on Android versions that require it. MMS/WAP
Push declarations are retained for default SMS role eligibility; MMS content
is not processed in Version 1.

The app manifest does not declare `INTERNET`. SMS is transmitted through Android
and the carrier in the normal way. Ordinary SMS is not end-to-end encrypted.
See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for the on-device data handling
details.

## Build and install

Open the project in Android Studio (Android Gradle Plugin 9.1.1) or run:

```powershell
./gradlew.bat :app:assembleDebug
```

Install the debug build from `app/build/outputs/apk/debug/app-debug.apk`. On a
telephony-capable device, open DEG Messages and follow Android's system prompt
to set it as the default SMS handler.

To run the existing unit tests and lint checks:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

The release build uses the existing application ID `com.example.messages`,
version name `1.0.0`, and version code `2`. Release signing is configured from
the ignored local `keystore.properties` file; see
[docs/RELEASE_SIGNING.md](docs/RELEASE_SIGNING.md). Never commit the keystore or
that local file.

## Install a release

Download the universal APK for most devices, or choose the APK matching the
device CPU: ARM64 (`arm64-v8a`), 32-bit ARM (`armeabi-v7a`), 64-bit Intel/AMD
(`x86_64`), or 32-bit Intel/AMD (`x86`). Android 10 (API 29) or newer is
required. On the phone, open the downloaded APK and allow installation from
that source if Android asks. Alternatively, with USB debugging enabled:

```powershell
adb install DEG-Messages-v1.0.0-universal.apk
```

After installation, open DEG Messages and follow Android's prompt to make it
the default SMS app. Changing the default SMS app can affect which app handles
new messages.

### Verify download checksums

Download `SHA256SUMS.txt` alongside the APK. In PowerShell, verify a file with:

```powershell
(Get-FileHash .\DEG-Messages-v1.0.0-universal.apk -Algorithm SHA256).Hash.ToLower()
```

Compare the result with the line for that filename in `SHA256SUMS.txt`. The
same command works for each architecture-specific APK.

## Compatibility updates

Keep `minSdk` at API 29 unless an Android 10 dependency blocker is demonstrated.
Update `targetSdk` separately when adopting a newer stable Android release,
then review its behavior changes and test notifications, role handling,
background work, and adaptive layouts. See
[docs/ANDROID_COMPATIBILITY.md](docs/ANDROID_COMPATIBILITY.md).

## Open source

The source is available for public review, modification, and local builds under
the Apache License 2.0. Third-party dependencies retain their own licenses;
see [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).

## Project layout

- `app/src/main/java/.../feature`: Compose screens and view models.
- `app/src/main/java/.../data`: SMS Provider, OTP, and Recycle Bin logic.
- `app/src/main/java/.../core`: Room database, DI, and theme.
- `app/src/main/java/.../platform`: Android receivers, notifications, and
  background cleanup.
- `docs/`: roadmap, release readiness, and test matrix.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
before opening a change. Security reports should follow [SECURITY.md](SECURITY.md).

## Releases

The published [v1.0.0 GitHub release](https://github.com/orisakume/deg-messages/releases/tag/v1.0.0)
includes signed APK downloads and `SHA256SUMS.txt`:

- [Universal APK](https://github.com/orisakume/deg-messages/releases/download/v1.0.0/DEG-Messages-v1.0.0-universal.apk)
- [ARM64 APK](https://github.com/orisakume/deg-messages/releases/download/v1.0.0/DEG-Messages-v1.0.0-arm64-v8a.apk)
- [ARMv7 APK](https://github.com/orisakume/deg-messages/releases/download/v1.0.0/DEG-Messages-v1.0.0-armeabi-v7a.apk)
- [x86_64 APK](https://github.com/orisakume/deg-messages/releases/download/v1.0.0/DEG-Messages-v1.0.0-x86_64.apk)
- [x86 APK](https://github.com/orisakume/deg-messages/releases/download/v1.0.0/DEG-Messages-v1.0.0-x86.apk)
- [SHA-256 checksums](https://github.com/orisakume/deg-messages/releases/download/v1.0.0/SHA256SUMS.txt)

The signed [AAB build artifact](https://github.com/orisakume/deg-messages/releases/download/v1.0.0/DEG-Messages-v1.0.0.aab)
is for integrators and is not directly installable; most users should download
the universal APK above.

## License

Original project code is offered under the Apache License 2.0; see [LICENSE](LICENSE).
Third-party dependencies retain their own licenses; see
[THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
