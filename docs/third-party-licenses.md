# Third-party licenses and attribution

Living record of what HydraFit bundles, what licence each component carries, and what a
distribution must preserve. Updated for the folded `0.13.4` candidate (2026-11-05).
HydraFit-authored source is MIT (root `LICENSE`).

This document records licences and provenance. It is not a legal clearance, a trademark
opinion, or a claim that every component's copyright status has been exhaustively audited.

## Method

The runtime inventory was resolved from both Android runtime configurations, not from the
version catalogue:

```sh
./gradlew :androidApp:dependencies --configuration debugRuntimeClasspath
./gradlew :androidApp:dependencies --configuration releaseRuntimeClasspath
```

Both configurations resolve the same 266 module coordinates (verified by set comparison).
Coordinates may include both an Android AAR and its KMP/JVM counterpart where the graph
contains both; that is the resolved graph, not duplicate shipped code. Test-only tooling is
not in the runtime inventory. Licence families were confirmed against published upstream
terms; Gradle cache metadata does not carry licence names.

The bundled, offline notice asset is
`feature/settings/src/commonMain/composeResources/files/licenses/THIRD-PARTY-NOTICES.txt`
(SHA256 `016b16cebf2657f1e7ebf6109baac2894fa6daf9f0a250c6dfe468c19a1eada2`,
382219 bytes). It is generated from the resolved coordinate list and verified upstream
texts, then read from app resources by Settings → About → Open-source licenses. It includes the
exact HydraFit MIT text (Copyright (c) 2026 Hydraxon91) as its own labelled section. No notice
or font is fetched at runtime.

## Bundled runtime components

| Group | Components | Licence |
| --- | --- | --- |
| `org.jetbrains.kotlin` | Kotlin standard library 2.4.20 (includes ThreeTen BSD-3-Clause and Boost Math BSL-1.0 object code) | Apache-2.0 |
| `org.jetbrains.kotlinx` | Coroutines 1.11.0, serialization 1.11.0, IO 0.9.1, datetime 0.8.0-0.6.x-compat | Apache-2.0 |
| `org.jetbrains.compose.*`, `org.jetbrains.androidx.*` | Compose Multiplatform 1.12.1 family, lifecycle/navigation KMP ports | Apache-2.0 |
| `androidx.*` | Activity, appcompat, core, lifecycle, navigation, compose interop, collection, savedstate, sqlite, window and transitive AndroidX libraries | Apache-2.0 |
| `app.cash.sqldelight` | Runtime, coroutines extensions, Android driver 2.4.0 | Apache-2.0 |
| `io.insert-koin` | Koin core, Compose and ViewModel integration 4.2.2 | Apache-2.0 |
| `io.ktor` | Client core, content negotiation, OkHttp engine, JSON serialization 3.6.0 | Apache-2.0 |
| `com.squareup.okhttp3`, `com.squareup.okio` | OkHttp 5.5.0 and Okio 3.18.2 | Apache-2.0 |
| `co.touchlab` | Stately concurrency and concurrent collections 2.1.0 | Apache-2.0 |
| `com.charleskorn.kaml` | YAML reader 0.79.0 | Apache-2.0 |
| `it.krzeminski` | SnakeYAML Engine KMP 3.1.1 | Apache-2.0 |
| `com.google.guava` | Guava listenable-future types pulled by AndroidX | Apache-2.0 |
| `org.jspecify` | JSpecify annotations | Apache-2.0 |
| `net.thauvin.erik.urlencoder` | URL encoder 1.6.0 | Apache-2.0 |
| `org.slf4j` | `slf4j-api` 2.0.19 logging facade | MIT |

### Non-Apache runtime terms

- **Public Suffix List data (MPL-2.0).** OkHttp 5.5.0 packages a generated
  `assets/PublicSuffixDatabase.list` (132737 bytes, SHA256
  `c75ab827afd7ffff59a51b14729c25dbfc3b45237eb40ccf02bec7b7d44304a4`).
  It is generated from the exact PSL source recorded in the notice asset:
  VERSION `2024-11-26_09-06-27_UTC`, COMMIT
  `bf32ba1ae6228a185ebf297d636f1c6541149c45`, source SHA256
  `986879fc41f0a5cc65f5e7d88fa68f6f0d9c366bb620418b8b8045f61a134077`.
  Regeneration matched 9764 normal and 8 exception rules; source comments are removed by
  OkHttp's producer. The complete MPL-2.0 text, OkHttp NOTICE, and the exact labelled source
  are bundled in the notice asset, so the MPL-covered source is available without external
  hosting (MPL-2.0 §3.2(a)/§3.4). HydraFit remains a larger work under MIT; this data file
  keeps MPL-2.0 terms. **The earlier "no copyleft" summary was wrong** and has been removed.
- **ThreeTen backport (BSD-3-Clause).** Copyright 2007-present Stephen Colebourne and
  Michael Nascimento Santos. The code ships inside Kotlin stdlib 2.4.20 `kotlin.time.Instant`
  and kotlinx-datetime date calculations; the full text and three conditions are in the
  notice asset.
- **Boost Math (BSL-1.0).** Eric Ford and Hubert Holin, 2001; embedded in the Kotlin/JVM
  standard library. BSL's object-code exception means the text is not mandatory for an
  object-code-only distribution; it is still included faithfully and is not described as
  mandatory.
- **Unicode License V3.** Applies to the Unicode 17.0.0 case-folding data used by
  `UnicodeCaseFold.kt`. The notice asset carries the current upstream `unicode.org/license.txt`
  text; no matching-year proof for the data snapshot is invented.
- **AndroidX graphics-path native binary.** The native APK contains
  `libandroidx.graphics.path.so` for four ABIs from `androidx.graphics:graphics-path:1.0.1`.
  Headers are Apache-2.0 AOSP (Copyright 2006, 2013, 2017, 2022). Release-associated source:
  `https://android.googlesource.com/platform/frameworks/support/+/8a05a22af450d589ef911d772a001a49dcb05b71/graphics/graphics-path/src/main/cpp/`.
  No extra NOTICE file was found; the complete Apache-2.0 text is bundled. This is a
  release-associated source audit, not a reproducible native toolchain/binary audit.

No cloud planner credential, model weights or on-device model is bundled; the Groq key is
user-entered and stored on the device.

## Bundled notices text provenance

| Block | Exact source | SHA256 |
| --- | --- | --- |
| Apache-2.0 | Kotlin `v2.4.20/license/third_party/guava_license.txt` | `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` |
| SLF4J MIT | `slf4j-api-2.0.19.jar` `META-INF/LICENSE.txt` | `6632f550c207be8d7d6b5e87f5d7b905bee218d18392d531df457bf60bec1447` |
| kotlinx.coroutines NOTICE | `Kotlin/kotlinx.coroutines/1.11.0/license/NOTICE.txt` | `1ab6de67ef738bffae63f9aa6a8798a35e87341c83ea28d09c8dc8ca6993232a` |
| kotlinx.serialization NOTICE | `Kotlin/kotlinx.serialization/v1.11.0/license/NOTICE.txt` | `ec77bba5c852830abfdefcdefbf4a22577ff4df79ec323a898d5100ca1d6f8d6` |
| kotlinx-io NOTICE | `Kotlin/kotlinx-io/0.9.1/NOTICE` | `a126623d2c0c1ca21814828749243869ae2c44c7f6a56ef6074bb87f430b659e` |
| ThreeTen BSD-3 | Kotlin `v2.4.20/license/third_party/threetenbp_license.txt` | `d1bc53b493a3ab387b42717ed5c4b1976a5048996f81154278100bff86d39331` |
| Boost BSL-1.0 | Kotlin `v2.4.20/license/third_party/boost_LICENSE.txt` | `8d8291caf1cee26d23acf3eb67c9f9a2d58f1c681b16a4fbe8cbfb9e3c0b5a9b` |
| Inter OFL-1.1 | bundled `Inter-OFL.txt` (unmodified archive license) | `262481e844521b326f5ecd053e59b98c8b2da78c8ee1bdbb6e8174305e54935a` |
| Body map MIT | bundled `feature/fatigueheatmap/.../bodymap/LICENSES.txt` | `b6ab46b14a216b8c4fd52f0079e92009a9b2820053e72a44f39697fb508dd77f` |

## Fonts

The app bundles unmodified Inter 4.1 static faces (Regular, SemiBold, Bold) from the
official `Inter-4.1.zip` (SHA256
`9883fdd4a49d4fb66bd8177ba6625ef9a64aa45899767dde3d36aa425756b11e`), `extras/ttf`.
Copyright (c) 2016 The Inter Project Authors. Fonts remain under SIL OFL 1.1, not MIT.
Per-file hashes are recorded with the visual/font chunk evidence; the full licence is
bundled both with the font resources and in the notice asset.

## Data, artwork and generated logo provenance

- `yuhonas/free-exercise-db` — Unlicense: exercise names, muscles and equipment facts only;
  its photos are not bundled.
- Melih Colpan (MuscleMap, 2026) and ELABBASSI Hicham (react-native-body-highlighter, 2022) —
  MIT: body-map artwork/paths, adapted. Full MIT notices are in the notice asset.
- Unicode Character Database case folding — Unicode License V3.
- ExRx.net — facts cross-check only; no text or media copied.
- Exercise instruction/caution/technique text is independently authored; provenance is in
  `docs/exercise-catalog-sources.md`.
- The HydraFit 02B tapered-H logo and mark were generated with ChatGPT across roughly ten
  user-directed iterations and selected by the project owner. The selected SVGs are the
  canonical sources (Logo SHA256 `37f1b3b82c25c8bf3ab3baa7ba42c421e4a458df914fdaca0d798f6af7276ffa`,
  Mark SHA256 `264cde497c265019442b9bb25d0028792684b4d8ac34a1706f89673b7adef44b`).
  OpenAI ROW Terms of Use (effective 2026-01-01) state that, as between the user and OpenAI,
  the user owns output to the extent permitted by law; output may not be unique and
  third-party rights are not cleared. This records provenance and owner selection, not
  exclusive copyright, trademark clearance, or a claim that the mark is copyright-free.

## Test-only tooling (not shipped in the APK)

| Group | Licence |
| --- | --- |
| Robolectric | MIT |
| MockK | Apache-2.0 |
| JUnit 4 | EPL-1.0 |
| `kotlin-test`, `kotlinx-coroutines-test`, Compose UI test | Apache-2.0 |
| ktlint (Pinterest) and the ktlint Gradle plugin | Apache-2.0 |

## Compliance notes and limits

- Apache-2.0 components require preserving notices and providing the licence terms. The APK
  now bundles the complete notice asset and exposes it offline; this closes the previous
  "no notices asset" gap but is not a certification of full distribution compliance.
- The notice asset indexes the resolved runtime coordinates and reproduces the applicable
  full texts/NOTICES. It does not reproduce every transitive project's separate NOTICE file
  where the upstream artifact carries none; absence of an embedded NOTICE is not treated as
  proof that none exists.
- MPL-2.0 applies to the PSL data file; the exact source is bundled in the same notice asset.
  HydraFit-authored source remains MIT and is a larger work.
- Native source auditing is release-associated, not a reproducible toolchain audit. Device
  launcher/icon rendering and iOS runtime display remain separately verified by the release
  checks.
- No dependency was added, removed or upgraded for this documentation step.
- Dependency changes remain approval-gated, and any new dependency must repeat this sweep
  with its licence and transitive hygiene recorded.
