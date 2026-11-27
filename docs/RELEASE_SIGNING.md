# Release signing and key backup

Release builds use a dedicated release certificate loaded from the ignored
`keystore.properties` file. The keystore must remain outside this repository;
the properties file and every password are local secrets and must never be
committed or uploaded.

## Backup

Keep at least two encrypted offline backups of the release JKS in separate
secure locations. Back up `keystore.properties` separately in a password
manager or encrypted vault. Verify the keystore and alias after copying. Never
email the files, commit them, or store them in public cloud folders. Losing the
keystore or its password prevents signing future APK updates with the same
identity. If the key is exposed, stop release signing and assess recovery
options before publishing another build.

The SHA-256 fingerprint of the public certificate is recorded in
`docs/RELEASE_CERTIFICATE_SHA256.txt`; it is safe to share for identity checks.
The private key and password are not included in that record.

## Version numbering

For future releases, skip any version name or version code that contains
both digits `4` and `5`, in either order. The rule applies across version
components and release lines, even when the digits are separated by dots
or other digits. Examples to skip include `4.5`, `5.4`, `2.4.5`,
`2.5.4`, and `23.4.5`. Keep version codes increasing when skipping a number.

## Local setup

Copy `keystore.properties.example` to `keystore.properties`, set the absolute
path to the external JKS and local passwords, then run
`gradlew.bat :app:assembleRelease`. Release builds stay unsigned when the local
properties file is absent. Do not add signing secrets to CI or GitHub Actions.

Build the Google Play bundle separately with
`gradlew.bat :app:bundleRelease -PplayBundle=true`. This disables APK splits for
the bundle build. Google Play generates device-specific APKs from the bundle.
The Play bundle uses `io.github.orisakume.degmessages`. The GitHub APKs keep
`com.example.messages` so they can upgrade v1 installs. Android treats the
Play and GitHub packages as separate apps with separate app-private data.

## Native crash symbols

Release builds require Android NDK `28.2.13676358` and CMake `3.22.1`.
The app builds the Apache-licensed JNI sources for [AndroidX Graphics Path 1.0.1](https://android.googlesource.com/platform/frameworks/support/+/8a05a22af450d589ef911d772a001a49dcb05b71/graphics/graphics-path/src/main/cpp/)
and [AndroidX DataStore 1.1.7](https://android.googlesource.com/platform/frameworks/support/+/420da5f291449f11801e378c6789a328a06ec6cb/datastore/datastore-core/src/androidMain/cpp/).
Update the vendored sources and matching dependency constraints together when upgrading either library.
Before uploading, check that the bundle includes the matching native symbol files under `BUNDLE-METADATA/com.android.tools.build.debugsymbols/`.
