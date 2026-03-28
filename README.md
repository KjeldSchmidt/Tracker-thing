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
  - CSV export (share/send the generated file)
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

It builds the debug APK on pushes/PRs and uploads it as an artifact.
On `push` runs it also creates a GitHub **prerelease** and attaches the APK,
which gives a public download link for each run.
