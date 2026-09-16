# RayNeo v5 — centralized message presentation

Date: 2026-09-16. Source/build verification only; device work remains paused.

## Scope and provenance

- Package `app.smarttube.rayneo`, version `32.47-rayneo.5`, versionCode `2450`.
- SharedModules fork: https://github.com/WayneShao/SharedModules
- Dependency branch `rayneo/message-presenter`, commit
  `52c6db36ff261d31499ab03778ad309091b2a4bd`, based on the previous pinned
  `620808aad13f31c4c07b6036bbd78f93558dafdf`.
- One generic MessageHelpers presenter interface replaces the app-wide
  RayNeoMessages facade. MainApplication installs the RayNeo binding once.
- Compared with upstream SmartTube `249bc833f`, 47 pre-existing production Java
  files are restored exactly. Modified pre-existing production Java files fall
  from 63 to 16 in the main repository. The shared-library hook is counted
  separately: one file, 61 insertions and 7 deletions.
- Independent window, login container, focus navigation, tooltip, file picker and
  video changes remain. Seven direct-Toast call sites still route through the
  public MessageHelpers API. System/direct dependency Toast calls are not globally
  intercepted.

## Validation

- Initial regression test compile failed because the new presenter API did not
  exist. After implementation, focused presenter/window tests passed.
- Full common/app host suites and `assembleStfdroidRelease` succeeded with JDK 17,
  Gradle 7.5, AGP 7.4.2 and `-Prayneo`.
- After read-only review, added a cancellation-exception regression and reran the
  full common host suite. Final reports: 36 tests in 10 suites, zero failures,
  errors or skips. No production source changed after the release build.
- Tests cover original message overloads, main-thread worker dispatch/cancel,
  native fallback with no/declining/failing presenter, stale native cleanup,
  presenter replacement, exception-safe cancellation and actual stereo-window
  routing. Existing navigation/window/video lifetime checks also pass.
- APK signature v1/v2 verification and `zipalign -c 4` pass.
- Manifest: minSdk 21, targetSdk 34, arm64-v8a.

Artifact: `releases/rayneo-v5/SmartTube_fdroid_32.47-rayneo.5_arm64-v8a.apk`

- Bytes: `28430511`
- SHA-256: `1d7e60ea7912fc31ef71ab5c06aca14b1c2992ee3d942ab3488cd972aef8b32a`
- Certificate SHA-256:
  `9b8d5d6835cf5601bf77fd982692accaf61e7f5986b755de49fb7783d9a0a507`

No ADB commands, installation, launch or physical tests were performed for v5.
The last installed version from the previous session is v4; it was not queried
again. Login-page physical acceptance and hardware video/optical checks remain
pending as recorded in v4. APKs and signing material remain local and ignored.
