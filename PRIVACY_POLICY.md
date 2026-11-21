# DEG Messages Privacy Policy

Last updated November 21, 2026

DEG Messages is an Android messaging app. It does not use a developer-operated backend, an account system, advertising, analytics, or tracking. The app does not send message content or contacts to the project maintainers.

## What the app handles

- **Messages and numbers.** As your default SMS app, DEG Messages uses Android’s telephony provider to read and manage message history. Android and your carrier transmit SMS and outgoing MMS to the intended recipients. Standard carrier SMS and MMS are not end-to-end encrypted.
- **Local processing.** Search, message categories, OTP detection, spam filtering, and message highlights are processed on your device. Quick replies, schedules, preferences, and recoverable deleted messages are stored locally. Recycle Bin entries are removed when you delete them permanently or when the configured retention period expires.
- **Sender logos.** An offline directory bundled with the app matches supported SMS sender IDs to brand logos. This lookup uses the sender ID on the device and sends no information to a third party.
- **Contacts and SIMs.** Optional contacts access shows saved names and photos and helps you choose recipients. Phone state access provides SIM choices.
- **Notifications and clipboard.** Notification previews follow the app and Android settings. Copying content puts it on Android’s clipboard. When you choose Share or open a link, Android passes the data to the app you select.
- **App lock.** The PIN or password is verified locally using a verifier backed by Android Keystore. When enabled, fingerprint verification uses Android’s biometric APIs. DEG Messages does not receive or store fingerprint templates.

## Backups and deletion

Automatic app backup is disabled. You can manually export SMS and outgoing photo history to a readable JSON file. This file contains message content, phone numbers, dates, and supported attachments and is unencrypted. The Android file picker saves it to the location you choose. A cloud-backed location is subject to that provider’s practices. If app lock is enabled, the app verifies your credential before opening the save dialog. You can delete exported files from their storage location.

Uninstalling removes app-private data such as saved quick replies and local preferences. Messages in the shared Android telephony provider and manually exported backup files may remain. You can manage those messages through your default SMS app and delete backup files separately.

## Permissions

SMS and MMS permissions let the app act as Android’s default SMS handler. The app also uses notification permission to display message alerts, optional contacts access to show recipient information, phone state access to present available SIMs, and biometric permission for optional fingerprint verification.

## Contact

For privacy questions, contact the project maintainer through the [DEG Messages repository](https://github.com/orisakume/deg-messages). Use the private reporting channel described in [SECURITY.md](SECURITY.md) for security issues. Keep private messages, backup files, and credentials out of public reports.
