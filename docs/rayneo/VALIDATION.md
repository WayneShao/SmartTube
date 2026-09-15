# First-version host validation — 2026-09-15

Upstream source: `249bc833f` (SmartTube 32.47).
Pinned submodules: SharedModules `620808aad13f31c4c07b6036bbd78f93558dafdf`,
MediaServiceCore `7133502c576f47e226f726efec7abd6c242ce8ae`.

## Results

- Unmodified upstream `assembleStfdroidDebug`: successful; arm64 baseline retained locally.
- Port: `:common:testStfdroidDebugUnitTest :smarttubetv:testStfdroidDebugUnitTest
  :smarttubetv:assembleStfdroidRelease -Prayneo --console=plain`: successful.
- 22 tests across 6 suites, 0 failures, 0 errors.
- Release APK manifest: `app.smarttube.rayneo`, version `32.47-rayneo.1`, code 2437,
  label `SmartTube RayNeo`, minSdk 21, targetSdk 34, ABI `arm64-v8a`.
- RayNeo Mercury launcher metadata present and true; no debuggable flag.
- APK signature verification successful (v1 + v2), RSA 3072, one signer.
- `zipalign -c 4`: successful.
- Independent bounded code review: four cursor/dialog defects fixed and re-reviewed;
  no remaining confirmed critical/important finding in those corrected paths.

## Artifact identity

File: `SmartTube_fdroid_32.47-rayneo.1_arm64-v8a.apk` (28,428,340 bytes).

SHA-256:
`42ca0fd616545c119561d4e3682012dac5bb0ca461f5ddd15918e3fece3d2249`

Signing certificate SHA-256:
`9b8d5d6835cf5601bf77fd982692accaf61e7f5986b755de49fb7783d9a0a507`

Original arm64 debug baseline SHA-256:
`6dc8e61e6eb183dd15f80bbe8d470787761c95bb09fea0aaac2b6096e7583c68`

Signing material and APKs remain local and excluded from Git. This first push
contains source, tests and reproduction instructions, not a published binary release.

## Scope of the host evidence

Tests cover one-tree half-width measurement, ordinary View/bitmap eye equality,
right-eye pointer mapping and drag continuity, cursor bounds and per-window state,
tap/double-tap/long-press/hover, cancellation, original target/position retention,
replacement/rebound text invalidation, pointer-targeted long click, off-screen
scroll reachability, idempotent window installation, dialog button/listener retention,
outside cancellation and its disabled variant, Surface holder identity, late callback
registration, ownership replacement, and recreation.

The Surface suite disables Robolectric's host Conscrypt initialization because the
app's Android JNI library is not a Windows TLS provider. The suite exercises no TLS
or network behavior; production crypto dependencies are unchanged.

Upstream compilation/resource deprecation and formatting warnings remain. Signature
verification reports the usual v1 META-INF warnings; APK v2 verification succeeds.

## Pending physical acceptance

The user explicitly deferred device testing. No device installation or app startup
was performed during implementation. Moving decoded video in both hardware eye
regions, physical touchpad delivery/direction, online playback, all screen flows,
IME/system-owned windows, background/resume and optical comfort remain unverified.
The ordinary View rendering test is not hardware TextureView/video proof.
