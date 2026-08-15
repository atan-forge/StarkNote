# StarkNote

Local-first Android notes and checklists with locked note content and encrypted backups.

## Features

- Notes and checklists
- Locked note content with PIN and biometric access
- Encrypted backup save and restore

Locked note content is encrypted, but titles, timestamps, note type, and checklist details remain visible locally. See [Privacy](PRIVACY.md) and [Security](SECURITY.md) for limits.

## Build

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

## Release

Before publishing an APK:

- Update the version name and code.
- Sign APKs outside the repository.
- Publish a checksum and release notes.
- Test on a clean Android device or emulator.

Contributions use pull requests; the repository owner decides what is merged.

Licensed under [Apache-2.0](LICENSE).
