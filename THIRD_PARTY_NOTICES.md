# Third-Party Notices and Dependency Inventory

> **Disclaimer:** this is a technical inventory prepared from published package metadata. It is
> not a legal opinion. Licenses below are as *declared* by each component's publisher (Maven POM
> or npm lockfile). A software license does not grant rights to the publisher's trademarks,
> logos, artwork or services. Have counsel review before commercial distribution.

Inventory date: 2026-10-25. Method:
- **Android:** `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` on the
  extracted `FINAL_EXECBIN_SOURCE.zip` → 158 unique resolved artifacts (direct + transitive). For
  each, the published POM was fetched from Google Maven or Maven Central and its `<licenses>`
  read, following parent POMs where the license is inherited (6 cases). All 158 resolved.
  Full list: `Evidence/android_release_dependencies.csv`; raw tree: `Evidence/android_release_dependency_tree.txt`.
- **npm:** `functions/package-lock.json` and `firestore-tests/package-lock.json` (337 entries,
  license field per package). Full list: `Evidence/npm_dependencies.csv`.
- **Assets:** archive contents inspected (§5).

ExecBin's own code is transferred under the sale agreement; no open-source license is granted by
the package. `ExecBin/THIRD_PARTY_NOTICES.md` inside the archive is a shorter version of this file.

## 1. Android app — bundled into the APK/AAB (158 artifacts)

| Declared license | Count | Examples |
|---|---:|---|
| Apache License 2.0 (all spellings) | 137 | AndroidX (core, appcompat, room, lifecycle, biometric, security-crypto, …), Material Components, Firebase Firestore / Messaging / App Check / Crashlytics / common, Kotlin stdlib & coroutines, gRPC (incl. grpc-okhttp), Guava, ZXing |
| Android Software Development Kit License (Google, **proprietary**) | 15 | `firebase-auth` 23.1.0, `firebase-auth-interop`, `firebase-iid-interop`, `firebase-measurement-connector`, `play-services-auth` 20.7.0, `play-services-auth-api-phone`, `play-services-auth-base`, `play-services-base`, `play-services-basement`, `play-services-cloud-messaging`, `play-services-fido`, `play-services-stats`, `play-services-tasks`, `recaptcha` 18.5.1, `googleid` 1.1.0 |
| Play Core Software Development Kit Terms of Service (**proprietary**) | 1 | `com.google.android.play:core-common` 2.0.4 |
| Play Integrity API Terms of Service (**proprietary**) | 1 | `com.google.android.play:integrity` 1.4.0 |
| BSD-3-Clause | 1 | `com.google.protobuf:protobuf-javalite` 3.25.1 |
| MIT | 2 | `org.checkerframework:checker-qual` 3.37.0, `org.codehaus.mojo:animal-sniffer-annotations` 1.23 |
| SQLCipher Community Edition license (BSD-style) | 1 | `net.zetetic:sqlcipher-android` 4.6.1 — POM only links to the license page; text taken from the project's repository (§4) |

### Obligations / flags for manual review

| Item | Obligation or concern |
|---|---|
| Apache 2.0 components | Keep license/NOTICE information when redistributing; the app currently has **no open-source notices screen** — add one before publishing (e.g. Google `oss-licenses-plugin`) |
| SQLCipher | **Binary redistribution must reproduce the copyright notice and disclaimer** in documentation or other materials distributed with the app (§4). Commercial SQLCipher editions exist; the Community Edition license is what this project uses — confirm it suits the buyer |
| BSD-3-Clause / MIT | Retain copyright notices in distributed notices |
| Google proprietary SDKs (17) | Free to use but governed by Google's terms; the publisher accepts them with their own account. Not open source; cannot be modified or redistributed outside the app |
| `play-services-auth`, `googleid`, `recaptcha` | Pulled in by `play-services-auth` / `firebase-auth`; `play-services-auth` itself is unused by the code — removing it is possible but would change the code (not done) |
| Firebase / Google Play services | Use of the *services* (not just libraries) requires accepting Firebase, Google Cloud and Google Play terms with the buyer's own accounts |

## 2. Build and test tooling (not shipped in the app)

| Component | Declared license |
|---|---|
| Gradle Wrapper | Apache 2.0 |
| Android Gradle Plugin 9.0.1, Google Services plugin 4.4.2, Crashlytics Gradle plugin 3.0.3 | Apache 2.0 (POMs checked) |
| detekt 1.23.7 | Apache 2.0 (POM checked) |
| ktlint 1.3.1 | MIT (POM checked) |
| ktlint-gradle 12.1.1 | **No license in POM** — MIT according to its GitHub repository; flag for review if relevant |
| JUnit 4.13.2 | Eclipse Public License 1.0 (test only) |
| AndroidX Test / Espresso | Apache 2.0 |

## 3. Node.js packages (installed with `npm install`; not included in the ZIP)

| Project | Scope | Licenses (count) |
|---|---|---|
| `functions/` | Runtime (deployed with the Cloud Function) | MIT 192, Apache-2.0 31, BSD-3-Clause 12, ISC 11, 0BSD 1, BSD-2-Clause 1, `(BSD-3-Clause OR GPL-2.0)` 1, **UNKNOWN 1** |
| `functions/` | Dev only | Apache-2.0 1 (`typescript`) |
| `firestore-tests/` | Dev/test only | Apache-2.0 53, MIT 17, BSD-3-Clause 10, ISC 5, 0BSD 1 |

Key direct packages: `firebase-admin` 12.7.0 (Apache-2.0), `firebase-functions` 5.1.1 (MIT),
`@anthropic-ai/sdk` 0.39.0 (MIT), `typescript` 5.9.3 (Apache-2.0), `firebase` 11.10.0 (Apache-2.0),
`@firebase/rules-unit-testing` 4.0.1 (Apache-2.0).

Flags:
- `limiter` 1.1.5 — lockfile has no license field; its own `package.json` declares MIT (legacy
  `licenses` array). Manual check recommended.
- `node-forge` 1.4.0 — dual licensed `BSD-3-Clause OR GPL-2.0`; can be used under BSD-3-Clause.

## 4. SQLCipher license (required notice)

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

Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0 — MIT and BSD texts accompany the
respective packages.

## 5. Assets, fonts, icons

| Asset | Finding |
|---|---|
| Fonts | None bundled (system fonts) |
| UI icons | Drawn in code (`LineIconDrawable.kt`) or system emoji; no icon packs |
| Launcher icon (`res/mipmap-*`, `res/drawable*/ic_launcher_*`, `ic_launcher-playstore.png`) | **Provenance not documented** — seller to confirm (see `IP_AND_TRANSFER_CHECKLIST.md`) |
| App name "ExecBin" | No trademark search performed |
| Notification icons | Android system drawables (`android.R.drawable.*`) |
| Strings / translations (10 languages) | Authored for the project; recent additions not reviewed by native speakers |
