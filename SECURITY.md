# Security and sensitive reports

LensGuard is evaluation software. The current 1.0.0 code line is the version under active development; there is no guaranteed security response time or production assurance.

Please do not disclose signing keys, personal photos, raw room reports, network identifiers or exploit details in a public issue.

For a sensitive vulnerability, check the repository's **Security → Advisories → Report a vulnerability** control. If private reporting is available, use it. If it is not available, open a minimal issue asking the maintainer for a private reporting channel, without describing the vulnerability or sharing sensitive attachments. Do not assume a public issue is confidential.

Include the release tag/commit, Android version, affected behavior and a minimal reproduction once a private channel is established. Ordinary reproducible bugs that contain no sensitive information can use the public bug template.

Release APKs are development-signed. Signing identity can change between evaluation builds; APK checksum/certificate files are published with each release. A checksum proves that a file matches the published asset, not independent trust in the publisher. Private production signing keys must never be committed or included in release artifacts.
