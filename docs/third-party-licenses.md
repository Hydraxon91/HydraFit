# Third-party licenses and attribution

Living record of what HydraFit bundles, what licence each component carries, and what a
distribution must preserve. It was last swept for the `0.13.2` candidate (2026-11-04).
HydraFit-authored source is MIT (root `LICENSE`).

## Method

The bundled inventory comes from the resolved Android runtime classpath, not from the version
catalogue alone:

```sh
./gradlew :androidApp:dependencies --configuration debugRuntimeClasspath
```

Test-only tooling was identified from the test configurations. Licences were confirmed against each
project's published terms; the dependency metadata in the Gradle cache does not carry licence names,
so this is an upstream-terms sweep rather than an automated scan.

## Bundled runtime components

| Group | Components | Licence |
| --- | --- | --- |
| `org.jetbrains.kotlin`, `org.jetbrains.kotlinx` | Kotlin standard library, coroutines, serialization JSON | Apache-2.0 |
| `org.jetbrains.compose.*`, `org.jetbrains.androidx.*`, `org.jetbrains` | Compose Multiplatform (runtime, foundation, material3, ui, resources), lifecycle and navigation KMP ports | Apache-2.0 |
| `androidx.*` | Activity, appcompat, core, lifecycle, navigation, compose interop, collection, window and transitive AndroidX libraries | Apache-2.0 |
| `app.cash.sqldelight` | SQLDelight runtime, coroutines extensions, Android driver | Apache-2.0 |
| `io.insert-koin` | Koin core, Compose and ViewModel integration | Apache-2.0 |
| `io.ktor` | Client core, content negotiation, OkHttp engine, JSON serialization | Apache-2.0 |
| `com.squareup.okhttp3`, `com.squareup.okio` | OkHttp and Okio (Ktor engine and transitive dependencies) | Apache-2.0 |
| `co.touchlab` | Stately concurrency and concurrent collections (SQLDelight/KMP) | Apache-2.0 |
| `com.charleskorn.kaml` | YAML reader used by the plan-tooling path | Apache-2.0 |
| `it.krzeminski` | SnakeYAML Engine KMP (KAML dependency) | Apache-2.0 |
| `com.google.guava` | Guava listenable-future types pulled by AndroidX | Apache-2.0 |
| `org.jspecify` | JSpecify annotations | Apache-2.0 |
| `net.thauvin.erik.urlencoder` | URL encoder used by the PDF/document path | Apache-2.0 |
| `org.slf4j` | `slf4j-api` logging facade | MIT |

No bundled component is copyleft. No cloud planner credential, model weights or on-device model is
bundled; the Groq key is user-entered and stored on the device.

## Test-only tooling (not shipped in the APK)

| Group | Licence |
| --- | --- |
| Robolectric | MIT |
| MockK | Apache-2.0 |
| JUnit 4 | EPL-1.0 |
| `kotlin-test`, `kotlinx-coroutines-test`, Compose UI test | Apache-2.0 |
| ktlint (Pinterest) and the ktlint Gradle plugin | Apache-2.0 |

## Data, artwork and references

The in-app acknowledgments screen (`feature/settings/.../strings.xml`, `acknowledgments_*`) carries
the reader-facing credits. The sweep confirmed they match the repository's claims:

- `yuhonas/free-exercise-db` — Unlicense: exercise names, muscles and equipment facts only.
- Melih Colpan (MuscleMap) and ELABBASSI Hicham (react-native-body-highlighter) — MIT: body-map
  artwork and paths, adapted.
- Unicode Character Database case folding — Unicode License v3.
- ExRx.net — facts cross-check only; no text or media is copied.
- Exercise instruction text and cautions are independently authored, with provenance recorded in
  `docs/exercise-catalog-sources.md`; no third-party description or media is bundled.

## Compliance notes

- Apache-2.0 components require preserving their notices and providing the licence terms to
  recipients. The APK does not currently bundle a third-party notices asset or an in-app licence
  list; this is a known distribution gap and needs a decision (in-app licence screen versus a
  notices asset) before a store-wide distribution is treated as complete. The in-app acknowledgments
  screen covers data and artwork credits only.
- `org.slf4j:slf4j-api` (MIT) and the Apache-2.0 components keep their own copyright notices; the
  root MIT licence applies to HydraFit-authored source, not to those dependencies.
- No dependency was added, removed or upgraded for `0.13.2`; this sweep documents the existing set.
- Dependency changes remain approval-gated, and any new dependency must repeat this sweep with its
  licence and transitive hygiene recorded.
