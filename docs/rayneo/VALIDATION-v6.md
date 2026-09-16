# RayNeo v6 — recover navigation from the current UI

Date: 2026-09-16. Host/build validation; no device commands in this change.

## Changes

Recovery remains in the shared RayNeo adaptation layer. FocusRecovery reads live
ListView/Leanback selection, validates current-tree ownership and keeps weak
ancestor-region hints. Whole-list replacement stays in the surviving content
panel; nested-grid recovery follows the current outer-row/inner-card selection,
not the previously focused but still attached row. Valid native focus takes
precedence over history.

Invalid ListView selection can restore a moved stable-ID item in the same adapter.
Changed non-stable data invalidates historical row reuse. Empty/disabled content
does not receive confirmation. Global layout/focus changes coalesce recovery at
pre-draw; delayed direction observation waits at most 400 ms and is window-gated.
Blocked Leanback descendants, pending adapter/layout work and an offscreen selected
holder retain framework ownership instead of selecting another visible item.

Direction fallback checks original target/adapter/selection and scroll state, so
logical row movement with an unchanged focused View does not produce a second
movement. Recovery never replays a consumed key or an obsolete confirmation.
Existing guarded confirmation and sidebar traversal remain. Observer cleanup uses
finally around temporary dispatch ownership and explicit navigator shutdown.

The existing leanback project is now an explicit common-module dependency so the
adapter can use native grid-selection APIs. No new external runtime library or
business-page patch is introduced. SharedModules stays pinned to v5's generic
message-presenter commit `52c6db36ff261d31499ab03778ad309091b2a4bd`.

## Evidence

- Initial six recovery regressions: five failed on v5; the normal boundary case
  passed. They exposed content replacement, live-selection and stable-ID gaps.
- Added real Leanback grid tests and list/ownership/no-double-move regressions.
- Review identified whole-list ancestor loss and stale nested-row ownership;
  both were fixed with targeted tests. Nested-row reproduction failed with
  expected row 2 / actual row 1 before the selected-path guard was added.
- Final common and app host reports: **51 tests, 12 suites, zero failures,
  errors or skips**. Existing stereo, messages, guided-page, tooltip, input,
  selected-row confirmation and TextureView lifetime tests are included.
- `:common:testStfdroidDebugUnitTest :smarttubetv:testStfdroidDebugUnitTest
  :smarttubetv:assembleStfdroidRelease -Prayneo --console=plain` succeeded with
  JDK 17, Gradle 7.5 and AGP 7.4.2.
- Read-only follow-up review found no additional blocking regression.
- APK v1/v2 signature verification and `zipalign -c 4` passed.

## Artifact and deployment boundary

- Package: `app.smarttube.rayneo`
- Version: `32.47-rayneo.6`, versionCode `2460`
- minSdk 21, targetSdk 34, arm64-v8a
- File: `releases/rayneo-v6/SmartTube_fdroid_32.47-rayneo.6_arm64-v8a.apk`
- Bytes: `28431579`
- SHA-256: `3548bc0549e6f11d1c3653f2710dd470263f5e61483908ec87d3c8c982228c1a`
- Certificate SHA-256:
  `9b8d5d6835cf5601bf77fd982692accaf61e7f5986b755de49fb7783d9a0a507`

v6 was not installed or launched. The previous user-directed deployment installed
v5 without launch; the device was not queried again for this work. Actual temple
gestures, optical/highlight behavior, online UI variations and video hardware
output remain separate device acceptance tasks. The recovery tests do not prove
that every possible business interception of a direction key should be overridden;
valid current selection at a boundary or under an intentional handler is retained.
