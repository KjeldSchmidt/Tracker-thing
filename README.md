# Pain & Mental State Tracker (Android)

Android app to record pain alongside mental state and recent activities.

## Features
- Create entries with:
  - Time (automatic, current time)
  - Pain 1: none / light / medium / strong
  - Pain 2: continuous / intermittent
  - Mental state (free text)
  - Activities of previous hours (free text)
  - Comments (free text)
- Configurable daily reminder times directly in the app (no code changes needed)
- History screen showing all previous entries in a table-like layout
- Data export:
  - CSV export with `;` separator (share/send the generated file, Excel-friendly)
  - SQLite export (share/send the DB copy)
- Reminder scheduling restored on reboot/time change

## Tech stack
- Kotlin
- Jetpack Compose (UI)
- SQLiteOpenHelper (local storage)
- DataStore Preferences (reminder time configuration)
- AlarmManager + BroadcastReceiver (local notifications)

## Build locally
```bash
./gradlew assembleDebug
```

Output APK:
`app/build/outputs/apk/debug/app-debug.apk`

## CI / GitHub pipeline
A GitHub Actions workflow is included at:

`.github/workflows/android-apk.yml`

It builds debug + release APKs on pushes/PRs and uploads both as artifacts.
On `push` runs it also creates a GitHub **prerelease** and attaches both APKs,
which gives a public download link for each run.

## In-place app updates (keep local history)
- The app is signed with a stable project keystore configuration so Android can install new APKs as updates.
- CI sets `versionCode` from `github.run_number` (`CI_VERSION_CODE`), so each build is upgradeable.
- As long as installs come from this pipeline/build chain, users should not need uninstall/reinstall.
