# Khmer Kreung

Offline cooking library for Android: 210 recipes from 40+ countries — 31 Cambodian dishes,
115+ from across Asia, plus German, English and French classics — with ingredients, techniques,
food safety and a **Food lab** that explains the science behind each method.
No internet permission: nothing ever leaves the device.

## Get the app
Open **Releases** (right side of this page) → newest **Khmer Kreung build N** → download the APK → install.

## Update the recipes or app
1. Replace `app/src/main/assets/index.html` (Add file → Upload files → Commit).
2. The **Actions** tab builds automatically (about 2–3 minutes).
3. Install the new APK from **Releases** over the old one.

The version number rises automatically with every build.

## Keep safe
`keystore/flavour-atlas.jks` signs every build. Keep it: it lets updates install without losing your saved notes and photos.
(The internal package name stays `com.flavouratlas.app` for the same reason.)

## Project layout
- `app/src/main/assets/index.html` – the whole app page
- `app/src/main/java/com/flavouratlas/app/MainActivity.java` – Android shell (offline WebView, backups, file picker, crash screen)
- `app/src/main/res/mipmap-*` – launcher icon (your kitchen picture)
- `.github/workflows/build.yml` – builds and publishes the APK
