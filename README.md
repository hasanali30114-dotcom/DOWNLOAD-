# ASHRAFUL FAST DOWNLOAD MANAGER

A small, native Android download manager designed for fast startup and reliable background transfers on modern Android/ColorOS.

## Included
- Direct HTTP/HTTPS file downloads
- User-initiated data transfer jobs on Android 14+ (API 34+)
- Foreground-service fallback on older Android
- Resume from partial download when the server supports HTTP Range
- Pause / resume / cancel / remove
- Wi-Fi-only option
- Progress notification
- Public Downloads/Ashraful Download Manager folder on Android 10+
- ColorOS battery-settings shortcut
- No ads, analytics, login, or third-party runtime SDKs

## Important limitation
This app downloads **direct file URLs**. It does not bypass Telegram, YouTube, or another app's private download system. For Telegram specifically, the source URL must be accessible to this app and permitted by the service.

## GitHub mobile build
1. Create a new GitHub repository.
2. Upload the **contents** of this ZIP (not the outer ZIP folder).
3. Open **Actions**.
4. Run **Build APK**.
5. Download the `ashraful-download-manager-debug-apk` artifact.

The workflow installs Java 17, Android SDK platform/build-tools 36, Gradle 9.5 and builds `app-debug.apk`.
