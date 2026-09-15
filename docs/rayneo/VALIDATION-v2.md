# Focus-navigation correction — 2026-09-15

Requirement: temple swipes move the selected item/focus highlight up, down, left,
or right in each application page and dialog. Both eyes show the same selection.

## Changes

- Removed free-pointer state/drawing, coordinate-click injection and edge scrolling.
- A completed contact or hover swipe emits one dominant-axis DPAD direction.
- Single tap confirms the current selection; double tap returns; long press delegates MENU.
- Per-window focus recovery handles touch mode and ListView selection independently.
- Direct callback key dispatch includes normal unhandled-key focus traversal.
- Deferred confirmation checks focused content, adapter identity, selected row and ID.
- Focus loss, content replacement, cancellation and detach invalidate pending work.

## Evidence

- 22 host tests passed, zero failures: actual four-direction selected-control
  movement and confirmation, gesture mapping, list selection recovery/confirmation,
  row/adapter invalidation, cancellation, window/dialog behavior, ordinary View
  stereo rendering and existing Surface lifetime regressions.
- Full signed release build succeeded. APK v2 signature and ZIP alignment verified.
- Installed with data-preserving package replacement; version `32.47-rayneo.2`,
  versionCode 2438, package `app.smarttube.rayneo`, ABI arm64-v8a.
- Cold startup succeeded, BrowseActivity became the focused application window,
  process remained alive and crash buffer was empty.
- Device screenshot after an ADB RIGHT key showed focus move from the first to the
  second card in both eye regions. The free white pointer is absent.
- A system launcher music card initially covered the app; BACK dismissed that
  overlay, and WindowManager then confirmed SmartTube owned input focus.

The ADB direction-key check is not a physical touchpad test. Physical swipe delivery,
all dialog flows and actual video/optical acceptance require separate evidence.

## Artifact

`SmartTube_fdroid_32.47-rayneo.2_arm64-v8a.apk`, 28,428,470 bytes.

SHA-256: `bce06f193ff0a7f13b373012438901f1dd4d3893d63b8026f52d978074895cdf`

Certificate SHA-256: `9b8d5d6835cf5601bf77fd982692accaf61e7f5986b755de49fb7783d9a0a507`

The certificate is unchanged from v1. Local screenshots and APKs remain ignored by Git.

## Subsequent user feedback

The user confirmed physical four-direction temple navigation. They reported two
remaining issues: RIGHT from the sidebar did not return to content, and the exit
Toast was not stereo. These are addressed in v3; see VALIDATION-v3.md.
