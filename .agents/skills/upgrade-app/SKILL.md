---
name: upgrade-app
description: Automates the process of upgrading the app version. Bumps versionCode and versionName in build.gradle.kts for changed modules, updates changelogs, verifies the build, commits, pushes, and creates a GitHub release. Use when the user says "upgrade app", "release new version", or "bump version".
---

# Upgrade App Workflow

This skill automates the end-to-end process of releasing a new version for `:app`.

## Architecture & Versioning
- **Package name:** `app.pwhs.universalantisplit`
- **Module:** `:app`
- **Version configuration:** `app/build.gradle.kts` (`versionCode`, `versionName`)

## Workflow Steps

### 1. Preparation
- Ensure the git working tree is clean (`git status`).
- Check `gh auth status` to ensure GitHub CLI is authenticated.
- Identify the last git tag:
  ```bash
  LAST_TAG=$(git describe --tags --abbrev=0)
  ```

### 2. Version Bump
- **versionCode:** Increment `versionCode` by 1 in `app/build.gradle.kts`.
- **versionName:** Suggest a new semantic version (e.g., if current is `1.0`, suggest `1.1` or `1.0.1`).
- Set the new `versionName` in `app/build.gradle.kts`.

### 3. Update Changelogs
- Fetch user-facing commits since the last tag:
  ```bash
  git log $LAST_TAG..HEAD --oneline
  ```
- **Filter commits:**
  - Exclude commits that are only about translations or language updates (e.g., `i18n`, `translation`, `locale`, `strings.xml`).
  - **Strip issue references:** Remove all GitHub issue references (e.g., `#70`, `(#72)`, `#121`) from the changelog text. The changelog is for end users, not issue tracking.
- Generate concise changelogs in:
  - English: `fastlane/metadata/android/en-US/changelogs/<new-app-versionCode>.txt`
  - Vietnamese (optional): `fastlane/metadata/android/vi-VN/changelogs/<new-app-versionCode>.txt`
- **MANDATORY:** Check the character count of each changelog file (`wc -c <file_path>`). They **MUST NOT exceed 500 characters** (Google Play limit). Shorten if necessary.

### 4. User Confirmation
- **MANDATORY:** Present the detected changes, version bump plan, and generated changelogs to the user.
- **WAIT** for the user to confirm or edit before proceeding to build and release.

### 5. Build Verification
- Run build verification on `:app` before committing or tagging:
  ```bash
  ./gradlew assembleDebug
  ```
- If any build task fails, stop, diagnose, and fix before committing.

### 6. Git & GitHub Operations (MUST BE SEQUENTIAL)
- Execute commit, tag, push, and release:
  ```bash
  git add app/build.gradle.kts fastlane/ && \
  git commit -m "chore: bump version to v<versionName> (<details>)" && \
  git tag v<versionName> && \
  git push origin main && \
  git push origin v<versionName> && \
  gh release create v<versionName> --title "v<versionName>" --notes-file fastlane/metadata/android/en-US/changelogs/<new-app-versionCode>.txt
  ```

## Guardrails
- **Build Verification:** Always run `./gradlew assembleDebug` before pushing.
- **Race Condition Prevention:** Never separate `git commit` and `git tag` into different tool calls without ensuring the commit succeeded.
- **Confirmation:** Always wait for user confirmation on the changelog and version bump plan.
- **Changelog Limit:** All changelogs in `fastlane/metadata/` MUST be strictly under 500 characters (Play Store limit). Always verify with `wc -c`.
- **No Issue References:** Never include GitHub issue numbers in user-facing changelogs.
- **No Translation Commits:** Never include routine i18n/translation commits in the changelog.

