# Third-Party Notices

ExecBin's open source code
Maven POM / npm `package.json` on 2026-10-25.

**Before publishing the app:** the Android binary bundles these libraries. Show their notices in
the app or store listing (for example with Google's `oss-licenses-plugin`) — the SQLCipher
license in particular requires its copyright notice in documentation or other materials
distributed with the binary. The app does not currently include an open-source licenses screen.

## Android app (bundled into the APK/AAB)

| Component | Version | License |
|---|---|---|
| AndroidX: core-ktx, appcompat, recyclerview, room (runtime/ktx/compiler), sqlite-ktx, lifecycle (viewmodel/livedata/runtime), biometric, exifinterface, swiperefreshlayout, security-crypto | see `app/build.gradle.kts` | Apache License 2.0 |
| Material Components for Android | 1.9.0 | Apache License 2.0 |
| Firebase BoM 33.7.0: firebase-firestore 25.1.1, firebase-messaging 24.1.0, firebase-appcheck-playintegrity 18.0.0, firebase-appcheck-debug 18.0.0 (debug builds only), firebase-crashlytics 19.3.0 | — | Apache License 2.0 |
| Firebase Authentication (firebase-auth) | 23.1.0 | **Android Software Development Kit License** (Google, proprietary) |
| Google Play services Auth (play-services-auth) | 20.7.0 | **Android Software Development Kit License** (Google, proprietary) — declared but not used by the code |
| Play Integrity (com.google.android.play:integrity) | 1.4.0 | **Play Integrity API Terms of Service** (Google, proprietary) |
| SQLCipher for Android (net.zetetic:sqlcipher-android) | 4.6.1 | BSD-style SQLCipher Community Edition license (full text below) |
| ZXing core | 3.5.3 | Apache License 2.0 |
| Kotlin standard library / kotlinx | 2.0.21 | Apache License 2.0 |

Google's proprietary SDKs may be used free of charge but under Google's terms; whoever
publishes the app accepts those terms with their own Google/Firebase account.

## Build and test tools (not shipped in the app)

| Component | License |
|---|---|
| Gradle Wrapper (`gradle/wrapper/gradle-wrapper.jar`) | Apache License 2.0 |
| Android Gradle Plugin, Google Services and Crashlytics Gradle plugins | Apache License 2.0 |
| detekt | Apache License 2.0 |
| ktlint 1.3.1 | MIT |
| ktlint-gradle plugin 12.1.1 | MIT according to its GitHub repository (its POM declares no license) |
| JUnit 4.13.2 (unit tests) | Eclipse Public License 1.0 |
| AndroidX Test / Espresso (instrumented tests) | Apache License 2.0 |

## Cloud Functions and rules tests (Node.js, installed by `npm install`, not included in the ZIP)

| Package | Version (lockfile) | License |
|---|---|---|
| firebase-admin | 12.7.0 | Apache-2.0 |
| firebase-functions | 5.1.1 | MIT |
| @anthropic-ai/sdk | 0.39.0 | MIT |
| typescript | 5.9.3 | Apache-2.0 |
| firebase (JS SDK, tests only) | 11.10.0 | Apache-2.0 |
| @firebase/rules-unit-testing (tests only) | 4.0.1 | Apache-2.0 |

All transitive npm dependencies in both lockfiles were checked: MIT, Apache-2.0, BSD-2/3-Clause,
ISC and 0BSD only (one package is dual-licensed BSD-3-Clause OR GPL-2.0 and is used under
BSD-3-Clause).

## Services (accounts, not code)

Firebase / Google Cloud, Google Play and the Anthropic API are online services. Using them
requires the buyer's own accounts and acceptance of each provider's terms. No account, API key
or quota is transferred with this package.

## Assets

The app uses no third-party fonts or image packs: icons are drawn in code
(`LineIconDrawable.kt`) or use system emoji, and the launcher icon files are in `app/src/main/res`
and `app/src/main/ic_launcher-playstore.png`. See `HANDOVER.md` → "Rights and ownership" for the
icon's provenance.

---

## SQLCipher license (required notice)

```
Copyright (c) 2008-2023, ZETETIC LLC
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the ZETETIC LLC nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY ZETETIC LLC ''AS IS'' AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL ZETETIC LLC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

The Apache License 2.0 text is available at https://www.apache.org/licenses/LICENSE-2.0.
