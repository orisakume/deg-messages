# Android compatibility maintenance

The project keeps `minSdk = 29` (Android 10) to define the oldest installable
OS. `targetSdk` is an independent compatibility setting and should move to the
latest stable API supported by the pinned Android Gradle Plugin and SDK tools.
Current build values are API 29 minimum and API 37 compile/target.

When updating the target:

1. Read Android behavior changes for every API level since the current target.
2. Confirm plugin, Gradle, JDK, compile SDK, and dependency compatibility.
3. Build debug and release variants; run unit tests and lint.
4. Test SMS role/default-handler flows, runtime permissions, notification
   channels/actions, PendingIntent mutability, and background workers.
5. Exercise compact, standard, large, tablet, and foldable layouts at multiple
   densities and font scales, including landscape and keyboard-visible states.
6. Record device/API/form-factor coverage and unresolved issues in
   `docs/TEST_MATRIX.md`. Do not describe untested Android versions as tested.

GitHub Actions runs build, unit-test, and lint checks only. It has no release
signing secrets and does not publish artifacts.
