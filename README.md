# Universal Anti-Split 📦

A fast, modern Android utility & companion tool designed to merge Split APKs into a single, monolithic, self-contained standalone APK.

---

## 🌟 Highlights

- **Dual-Engine Power:** High-performance **Rust Native NDK Core** + **Pure Kotlin JVM Engine**.
- **Split Neutralization:** Cleans binary `AndroidManifest.xml` and merges `resources.arsc` chunks seamlessly.
- **On-Device Signing:** Unique per-device Keystore generation with official Google `apksig` supporting Signature Scheme v1, v2, and v3.
- **PairIP & Integrity Awareness:** Detects Google Play Automatic Integrity Protection and warns about signature mismatch issues upfront.
- **Companion to Universal Installer:** Built to work seamlessly alongside [Universal Installer](https://github.com/pass-with-high-score/universal-installer).

---

## 🗺️ Architectural Plan

See full architectural specification, components, and phased roadmap in [PLAN.md](PLAN.md).

---

## 📜 License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
