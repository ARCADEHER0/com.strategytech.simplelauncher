# Senior Simple Launcher (Android)

A home-screen replacement for older adults: big clock, four giant buttons (Phone, Messages,
Grandkids Photos, Emergency Help), a "My Apps" shelf, and a caregiver settings screen.
Native Kotlin, no third-party libraries, no internet permission, no tracking.

## 1. Run it on a phone (about 10 minutes)

1. Install **Android Studio** (current stable version): https://developer.android.com/studio
2. **File > Open** and choose this `SimpleLauncher` folder. Let Gradle sync.
   The first sync downloads Android SDK 36 and build tools. Accept any licence prompts
   ("Install missing SDK package(s)").
3. Plug in a phone (Settings > Developer options > USB debugging) or create an emulator
   (Device Manager > Create device). Press the green **Run** button.
4. Press the phone's **Home** button. Android asks which home app to use, or tap the green
   "Make this my home screen" button on the launcher's own screen.

No Android Studio? Push this folder to a GitHub repository. `.github/workflows/build.yml`
builds a test APK in the cloud. Open the repo's **Actions** tab, then the latest run, and
download `SimpleLauncher-debug-apk`.

## 2. Before you publish (do these first)

- **Choose your package name.** In `app/build.gradle.kts` change `namespace` and
  `applicationId` (currently `com.strategytech.simplelauncher`). If you change the namespace,
  also change the `package` line in each `.kt` file and move the folder to match, or use
  Android Studio's *Refactor > Rename > package*. **The applicationId can never be changed
  after the first upload to Google Play.**
- **Build the signed bundle.** Android Studio: *Build > Generate Signed App Bundle / APK >
  Android App Bundle*. Create a new keystore when asked and keep it, with its passwords,
  somewhere safe (a password manager plus an offline copy). Enrol in **Play App Signing**
  when you upload. Or from a terminal: copy `keystore.properties.example` to
  `keystore.properties`, fill it in, then run `./gradlew bundleRelease`. The file ends up in
  `app/build/outputs/bundle/release/app-release.aab`.
- **Every upload needs a higher `versionCode`** in `app/build.gradle.kts`.
- Fill in `store/PRIVACY_POLICY.md` (your email), host it at a public web address
  (GitHub Pages works), and use that address in Play Console.
- Everything for the Play Console form is in `store/PLAY_LISTING.md`.

## 3. Where things are

| Want to change | Edit |
|---|---|
| Button names, wording | `app/src/main/res/values/strings.xml` |
| Colors | `app/src/main/res/values/colors.xml` |
| Home screen layout | `app/src/main/res/layout/activity_home.xml` |
| What the four buttons do | `HomeActivity.kt` (`buildTiles`, `openDialer`, `openMessages`, `openPhotos`) |
| Caregiver settings | `SettingsActivity.kt`, `Prefs.kt` |
| App icon | `res/drawable/ic_launcher_foreground.xml` |

## 4. Design decisions worth knowing

- **Emergency opens the dialer** with the number typed in; the user presses the green call
  button. This avoids the `CALL_PHONE` permission, which Google Play restricts and which
  would make review harder. The app says plainly that it is not a monitored alert service.
- **Settings need a press-and-hold** on the gear, so a shaky tap cannot change anything.
- **No `QUERY_ALL_PACKAGES`** (restricted by Google Play). A narrow `<queries>` block lets
  the app list launchable apps instead.
- Back button / gesture does nothing on the home screen, as a home screen should behave.
- Targets **Android 16 (API 36)**, as Google Play requires for new apps; supports Android 8.0+.

## 5. If Gradle sync complains

This project was written and desk-checked but had not been compiled when it was handed to
you. If sync fails, paste the error to Claude. Version pins live in the root
`build.gradle.kts` (Android Gradle Plugin, Kotlin) and `gradle/wrapper/gradle-wrapper.properties`.
Android Studio's "Upgrade" suggestion for the Android Gradle Plugin is safe to accept.
