# Security Policy

PDF Helper processes documents that may contain confidential information. If
you find a vulnerability that could expose a user's files, corrupt their
documents, or bypass the privacy guarantees, please report it privately.

## Supported versions

Only the latest release receives security fixes.

| Version | Supported |
| :--- | :--- |
| Latest `v*` tag | ✅ |
| Older releases | ❌ |

If you're on an older version and a vulnerability affects it, update to the
latest APK from the [Releases page](https://github.com/realximanta/PDF-helper/releases).

## Reporting a vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Use one of these private channels:

1. **GitHub Security Advisories** — go to the
   [Security tab](https://github.com/realximanta/PDF-helper/security),
   click **"Report a vulnerability"**. This creates a private thread visible
   only to the maintainer.
2. **Telegram** — [@b4nzw](https://t.me/b4nzw), if you cannot use GitHub.

Please include:

- A description of the issue and its impact
- Steps to reproduce (device, Android version, app version, exact file used)
- A proof-of-concept PDF if the vulnerability depends on file content
  (**only if the file contains no personal data**)
- Any suggested fix, if you have one

## What to expect

- **Acknowledgment** within 7 days of your report.
- **Assessment** within 14 days — severity, scope, whether a fix is feasible.
- **Fix + coordinated disclosure** — I'll agree a disclosure date with you
  before publishing details. Typical turnaround is 30–90 days depending on
  severity.
- **Credit** in the release notes for the fix, if you'd like.

This is a one-person project run in spare time. I will not promise a
same-day response, but I will not ignore a report either.

## Scope

### In scope

- **File handling** — any way the app could read, write, or leak files it
  shouldn't have access to
- **PDF parsing** — crashes, memory corruption, or code execution triggered by
  a maliciously crafted PDF
- **Password handling** — timing attacks, weak encryption, password leakage
  via logs or the UI
- **SAF bypass** — any path where the app accesses a file without an explicit
  user pick
- **FileProvider** — any path traversal or URI permission escalation that
  exposes files outside `cache/shared/` or `files/outputs/`
- **Supply chain** — compromised or typo-squatted dependencies
- **Build pipeline** — any way the GitHub Actions workflow could produce a
  release that differs from the tagged source

### Out of scope

- **Absence of the INTERNET permission.** That's a feature, not a bug.
- **"The app could add tracking in a future update."** Only released code is
  in scope, not hypothetical changes.
- **Rate limiting or DDoS on the website.** Static HTML on GitHub Pages.
- **Third-party app / OS bugs.** If Android itself has a vulnerability,
  report it to Google.
- **Social engineering.** If someone tricks you into installing a fake APK,
  that's not a vulnerability in this project.

## Known limitations

These are documented design decisions, not security bugs:

- **Digitally signed PDFs lose their signature** after any modification. This
  is a side effect of re-saving and is warned about in the UI.
- **Password removal requires the correct password.** There is no brute-force
  or bypass path, and none will be added.
- **Older, weaker PDF encryption** (RC4, 40-bit) may be readable by the
  current engine. This is inherited from PDFBox and reflects the PDF spec, not
  a project choice.
- **Memory usage scales with file size.** Very large PDFs may be rejected by
  the OS on low-RAM devices. Not a crash to be exploited.

## Security-relevant dependencies

| Dependency | Purpose | Tracked |
| :--- | :--- | :--- |
| `com.tom-roush:pdfbox-android` | PDF parsing + manipulation | Dependabot |
| `org.bouncycastle:*` (transitive) | Password encryption primitives | Dependabot |
| `androidx.*` | Compose, Activity, Lifecycle | Dependabot |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | Async work | Dependabot |

Dependabot is configured to open PRs for security advisories and version
updates. Maintainer reviews before merging.

## Recognition

Reporters who follow this policy and give reasonable time for a fix before
public disclosure will be credited in the release notes. If you prefer to
remain anonymous, that's fine too — just say so.

## A note on the privacy model

The security posture of this app rests on one core property: **it has no
network permission.** If you find a way to exfiltrate data without that
permission, that is by far the highest-severity bug possible and I want to
hear about it immediately.

Thank you for helping keep PDF Helper safe.
