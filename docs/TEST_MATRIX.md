# DEG Messages v2.3.0 verification

The release supports installation beginning with Android 10 (API 29). Runtime checks were performed on a OnePlus 11 running Android 16 and, for the SMS regression below, an Android 17 emulator.

| Area | Coverage |
| --- | --- |
| OnePlus in-place upgrade | Version 2.3.0 installed and launched over the existing test installation using its signing identity. |
| Reply keyboard | On the OnePlus, the gap above the keyboard fell from 813 px to 24 px. The reply field accepted test input and returned to the bottom after the keyboard closed. |
| Single-message deletion | Sample conversations verified that Delete from a chat or the OTP tab preserves the other SMS. The previous build reproduced disappearance from a filtered list after deleting the latest OTP. The updated build brought the surviving chat into view on Back. The three existing Zepto SMS rows remained unchanged. |
| Recycle Bin | Emulator checks captured only the selected sample SMS. On the OnePlus, a sample OTP was restored to the conversation and deleted again through the OTP tab. |
| Offline sender logos | Unit tests covered carrier prefixes and category suffixes, recognized headers, and look-alike rejection. The Zepto sample showed its brand logo and original sender ID in the inbox. |
| OTPs, highlights, search, categorization, conversation mapping, and MMS encoding | Covered by the JVM suite. Tests for MMS encoding verify the constructed payload and do not establish delivery over a carrier. |
| Unverified in this release pass | Complete settings toggle combinations, real carrier delivery and reports, both SIMs, live photo/video MMS, fingerprint on different devices, scheduling through reboot and idle modes, full backup and restore combinations, tablet/foldable layouts, every supported Android version, battery, and TalkBack. |

No live SMS or MMS was sent for these checks. Incoming MMS downloads are outside the current implementation.
