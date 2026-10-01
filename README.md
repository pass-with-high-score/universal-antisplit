<div align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" width="128" height="128">
  <h1>Universal Anti-Split</h1>
  <p><strong>Universal Anti-Split</strong> is a fast, modern Android tool that merges Split APKs
  (XAPK, APKM, APKS, AAB) into a single, self-contained standalone APK.</p>
  <p>It cleans the binary manifest, merges resources, re-signs on device, and warns about
  Play Integrity / PairIP protection — built as a companion to
  <a href="https://github.com/pass-with-high-score/universal-installer">Universal Installer</a>.</p>
  <br><br>
  <a href="https://antisplit.pwhs.app">
    <img src="https://img.shields.io/badge/Website-antisplit.pwhs.app-00DC82?logo=googlechrome&logoColor=white">
  </a>
  <a href="https://github.com/pass-with-high-score/universal-antisplit/releases">
    <img src="https://img.shields.io/github/v/release/pass-with-high-score/universal-antisplit">
  </a>
  <a href="https://github.com/pass-with-high-score/universal-antisplit/releases">
    <img src="https://img.shields.io/github/downloads/pass-with-high-score/universal-antisplit/total">
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/github/license/pass-with-high-score/universal-antisplit">
  </a>
  <br><br>
  <h4>Download</h4>
  <a href="https://github.com/pass-with-high-score/universal-antisplit/releases">
    <img src="https://raw.githubusercontent.com/NeoApplications/Neo-Backup/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" height="80">
  </a>
</div>

---

## Features

* Merges Split APKs (XAPK, APKM, APKS, AAB) into one standalone APK
* **Dual engine:** high-performance Rust NDK core + pure Kotlin JVM fallback
* Cleans the binary `AndroidManifest.xml` and merges `resources.arsc` seamlessly
* On-device signing with Google `apksig` (Signature Scheme v1 / v2 / v3)
* Detects Google PairIP / Play Integrity and offers **Kill Signature** (PMS hook) to run standalone
* Batch merge — convert multiple apps in one queue
* Install merged APKs in-app via `PackageInstaller`, share, or open the output folder
* Conversion history with APK details (version, size, SDK range, ABIs)
* 16 KB page-size alignment for full Android 15+ compatibility
* Material You styling with dynamic color, AMOLED and light/dark themes
* Localized into 10 languages

---

## Community

Join the community on Telegram:
[![Telegram](https://img.shields.io/badge/Telegram-Join%20Group-blue?logo=telegram)](https://t.me/blockads_android)

---

## Pass With High Score ecosystem

Explore the other open-source Android tools from the same developer:

* [Universal Installer](https://universal-installer.pwhs.app) ([GitHub](https://github.com/pass-with-high-score/universal-installer)) — install Split APKs and bundles
* [BlockAds](https://github.com/pass-with-high-score/blockads-android) — on-device ad blocking

---

## Build Instructions

### Requirements

* [Android Studio](https://developer.android.com/studio)
* JDK 17

### Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/pass-with-high-score/universal-antisplit.git
   cd universal-antisplit
   ```
2. Open the project in Android Studio
3. Sync Gradle and run the app on a device or emulator

> The Rust NDK core ships as prebuilt `.so` libraries under `rust-antisplit/jniLibs/`, so a normal
> build needs no Rust toolchain. To rebuild the native engine, install the Rust toolchain plus
> [`cargo-ndk`](https://github.com/bbqsrc/cargo-ndk) and the Android NDK, then rebuild from
> `rust-antisplit/`.

---

## Architecture

See the full architectural specification, components, and phased roadmap in [PLAN.md](PLAN.md).

---

## License

This project is licensed under the **GNU General Public License v3.0**.
You are free to use, modify, and distribute it under the terms of the license.
See the full [LICENSE](LICENSE) file for details.

---

## Credits

* Developed and maintained by [Nguyen Quang Minh](https://github.com/nqmgaming) under
  [Pass With High Score](https://github.com/pass-with-high-score)

---

## Contributing

Pull requests and issue reports are welcome — help us improve Universal Anti-Split!

### Help us translate

Want to see the app in your language? Open a pull request with your translation under
`app/src/main/res/values-<locale>/strings.xml`.

---

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=pass-with-high-score/universal-antisplit&type=Date)](https://www.star-history.com/#pass-with-high-score/universal-antisplit&Date)
