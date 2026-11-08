# Contributing

Contributions to DEG Messages are welcome. Please open an issue to discuss a
substantial change before starting work.

## Development

- Use the existing Kotlin, Jetpack Compose, Room, and Android platform
  architecture.
- Keep SMS content processing on device and avoid analytics, tracking,
  external message APIs, and unnecessary dependencies.
- Do not add permissions without explaining the user-facing need.
- Preserve the Android default SMS handler behavior and the documented SMS-only
  Version 1 scope.
- Run `gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  before submitting a change.

Please include a concise description, steps to reproduce for bug fixes, and the
devices or Android versions affected. Never include private SMS content,
credentials, signing keys, or personal data in issues or patches.
