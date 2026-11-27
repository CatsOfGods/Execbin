# Android Lint Review

Command: `./gradlew :app:lintDebug`. Reviewed 2026-10-25 against the run that reported
**0 errors, 509 warnings** (report: `app/build/reports/lint-results-debug.html`). Every one of
the 509 warnings falls into exactly one row below.

Earlier in the same review, lint also reported 15 **errors**. All were real crash risks on
Android 6–8 (minSdk is 23) and are fixed: `java.util.Base64` in `CryptoManager` (needs API 26)
and `Activity.mainExecutor` in the biometric prompt (needs API 28).

## Summary

| Group | Warnings |
|---|---:|
| Potentially important — needs a decision or device test | 9 |
| Real bug — fixed during this review | 1 |
| Harmless / intentional | 499 |
| **Total** | **509** |

## Potentially important (9)

None is a confirmed bug in the tested code paths, but each can affect security, compatibility
or behaviour on some devices.

| Count | Lint ID | Where | Why it matters | Recommendation |
|---:|---|---|---|---|
| 1 | SelectedPhotoAccess | `AndroidManifest.xml:7` | On Android 14+ users can grant access to *selected* photos only. The gallery picker (`InlineGalleryPicker.kt:65`) checks only `READ_MEDIA_IMAGES`, so partial access may be treated as "denied". | **Test on Android 14+** (device checklist P3a). If broken, handle `READ_MEDIA_VISUAL_USER_SELECTED` or switch to the system Photo Picker. |
| 3 | GradleDependency | Room `2.7.0-alpha12` (runtime, compiler, ktx) | A pre-release library in production. | Upgrade to Room 2.8.x stable in a separate change; re-test the encrypted cache. |
| 1 | GradleDependency | `androidx.security:security-crypto 1.1.0-alpha06` | Pre-release, and the library is deprecated upstream. It protects the private keys. | Move to `1.1.0` stable now; plan a replacement (Keystore-backed AES key + own storage). Test key migration carefully. |
| 1 | GradleDependency | Firebase BoM 33.7.0 (35.x available) | BoM 34+ removed the `-ktx` modules this code imports, so upgrading is a code change, not a version bump. Older SDKs eventually lose support. | Upgrade with the KTX import migration in its own tested change. |
| 1 | NewerVersionAvailable | `sqlcipher-android` 4.6.1 (4.19.1 available) | SQLCipher bundles SQLite; newer releases include upstream SQLite security fixes. | Upgrade and re-test opening an existing encrypted cache. |
| 1 | HardwareIds | `DeviceUtils.kt:11` | `ANDROID_ID` identifies device records. It's scoped per app-signing key and user since Android 8 and isn't used for ads, but it is a device identifier for privacy / Play Data-safety. | Declare it in the Play Data-safety form, or replace it with a random per-install id (changes existing device records). |
| 1 | UnusedAttribute | `AndroidManifest.xml:19` | `networkSecurityConfig` is ignored on Android 6.0 (API 23), so **certificate pinning doesn't apply there**. Cleartext traffic is still blocked by `usesCleartextTraffic=false`. | Accept, or raise `minSdk` to 24. |

## Fixed during this review (1)

| Count | Lint ID | Where | Fix |
|---:|---|---|---|
| 1 | DefaultLocale | `CryptoManager.computeSafetyNumber` | `String.format("%05d")` produced Arabic-Indic / Persian / Devanagari / Thai digits on phones set to those locales, so two users comparing safety numbers (or the QR payload) saw different characters for the same value. Now uses `Locale.ROOT`; regression test added. After this fix lint reports 508 warnings. |

## Harmless / intentional (499)

| Count | Lint ID | Assessment |
|---:|---|---|
| 375 | UseKtx | Style: suggests Kotlin extensions (`edit {}`, `toUri()`, `toColorInt()`…). No behaviour change. |
| 32 | SetTextI18n | Every instance reviewed: they combine already-translated `S.get(...)` strings with numbers, usernames, emoji or separators (e.g. `"${i + 1}/${n}"`, `"@$username"`). Translation goes through `StringsManager`, not Android resources. |
| 29 | UseTomlInstead | Style: dependencies declared inline instead of in `gradle/libs.versions.toml`. |
| 20 | NotifyDataSetChanged | Performance: full list refreshes. Fine at current sizes; `DiffUtil` would help very long chats. |
| 17 | GradleDependency | Newer stable versions of AndroidX/Play libraries (core, appcompat, material, recyclerview, lifecycle, sqlite, exifinterface, swiperefreshlayout, play-services-auth, integrity), test libraries, and 3 entries in `libs.versions.toml` that the build doesn't use. Routine updates. |
| 10 | UnusedResources | Template leftovers (`purple_200`, `Theme_Execbintest`, …). Safe to delete. |
| 4 | ApplySharedPref | Intentional `commit()` in the secure-storage / DB-key code: the value must be on disk before continuing (e.g. before deleting the legacy plaintext file). |
| 2 | AndroidGradlePluginVersion | Newer Gradle (9.8) / AGP (9.4) exist; the current pair builds correctly. |
| 2 | DefaultLocale | Voice-message duration `"%02d"`; showing the device's digits is fine for display. |
| 2 | ClickableViewAccessibility | A touch listener (`MainActivity.kt:9210`) doesn't call `performClick()`; minor TalkBack issue. |
| 1 | NewerVersionAvailable | ZXing 3.5.3 → 3.5.4 (QR generation only). |
| 1 | SuspiciousImport | `import android.R` in `MyFirebaseMessagingService.kt` for system notification icons. Works; style only. |
| 1 | ScopedStorage | `READ_EXTERNAL_STORAGE` is ignored on Android 13+ (the app also requests `READ_MEDIA_IMAGES`). Could add `android:maxSdkVersion="32"`. |
| 1 | AppBundleLocaleChanges | In-app language switching uses code (`StringsManager`), not resource folders, so Play's per-language splits don't affect it. |
| 1 | KaptUsageInsteadOfKsp | Build speed only. |
| 1 | MonochromeLauncherIcon | Android 13 themed icons won't tint the launcher icon. Cosmetic. |
