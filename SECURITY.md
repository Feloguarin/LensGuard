# Security and sensitive reports

LensGuard is evaluation software. The current 2.0.0 code line is the version under active development; there is no guaranteed security response time or production assurance.

Please do not disclose signing keys, personal photos, raw room reports, network identifiers or exploit details in a public issue.

For a sensitive vulnerability, check the repository's **Security → Advisories → Report a vulnerability** control. If private reporting is available, use it. If it is not available, open a minimal issue asking the maintainer for a private reporting channel, without describing the vulnerability or sharing sensitive attachments. Do not assume a public issue is confidential.

Include the release tag/commit, Android version, affected behavior and a minimal reproduction once a private channel is established. Ordinary reproducible bugs that contain no sensitive information can use the public bug template.

From 2.0.0, release APKs are signed with a permanent release key held in repository secrets (certificate SHA-256 `FF:BD:95:84:8D:D9:A6:E6:15:97:0A:26:22:E6:7A:BD:A0:D6:8F:89:8B:EF:55:E9:CE:55:15:D2:A5:6A:A6:3D`); earlier evaluation builds used temporary keys. Each release's notes say which key signed it, and APK checksum/certificate files are published with each release. A checksum proves that a file matches the published asset, not independent trust in the publisher. Private production signing keys must never be committed or included in release artifacts.
