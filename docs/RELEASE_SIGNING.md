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
