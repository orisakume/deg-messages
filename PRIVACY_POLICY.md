# DEG Messages Privacy Policy

Last updated: 8 November 2026

DEG Messages is an Android SMS application. This project does not include an
Internet permission, account service, advertising, analytics, tracking, or a
backend. The app does not send SMS content to a developer-operated server.

## Information handled on the device

- Android's SMS Provider supplies the conversations and messages displayed by
  the app. As the default SMS handler, the app can send and receive SMS through
  Android and the carrier.
- OTP detection and notification action detection run locally on the device.
- Optional contacts permission is used to show saved names/photos and match
  recipients. Optional phone state access is used to show SIM choices.
- App metadata, such as pin/archive/mute preferences, OTP records and temporary
  deleted-message recovery copies, is stored in app-private local storage.
- Notification previews follow the setting in the app. Android may also apply
  the device's lock-screen privacy settings.
- When the user chooses Copy, the selected code or number is placed on the
  system clipboard.

## Sharing and retention

Normal SMS is transmitted by Android and the user's carrier to the recipient.
The app does not provide end-to-end encryption for SMS. Recovery copies remain
local until permanently deleted or removed by the configured Recycle Bin
retention cleanup. App data may be removed when the app is uninstalled. Device
backup is disabled by the app.

## Permissions

SMS permissions support the app's core default SMS handler functionality.
Contacts are optional. Phone state access is used for SIM selection. Android
notification permission is requested on supported versions so incoming SMS
notifications can be shown.

## Changes and contact

Policy changes will be recorded in the project repository. Contact the project
maintainers through the repository's issue or contact channels after the
public repository is available. Do not send private SMS content in a report.
