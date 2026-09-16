# SmartTube RayNeo X3 Pro — first port

Based on SmartTube 32.47 (`249bc833f`). Branch: `rayneo/x3pro`.
Build with `-Prayneo` for package `app.smarttube.rayneo`, label `SmartTube RayNeo`,
version `32.47-rayneo.5`. This package can coexist with official SmartTube.

## Display and input

The port activates only on RayNeo ARGF20 / MercuryLiteXR with a 1280x480 physical
display. Application content is laid out in one 640x480 eye region and drawn in
both eyes. A single decoder feeds the existing TextureView path; ordinary video,
subtitles, playback controls, and app UI share the content tree. TextureView
surface lifetime uses one owned Surface/holder and invalidates the stereo parent
on new frames. Tunneling is disabled on this path.

- Cursor mode follows the app's selected item/focus highlight, drawn in both eyes.
- Each completed X/Y touchpad swipe moves selection left/right/up/down using the
  dominant delivered coordinate delta. System direction signs are preserved.
- The existing list/grid focus system scrolls off-screen items into view.
- Single tap confirms the focused control after the double-tap interval.
- Double tap returns within the active window without a preceding confirmation.
- Long press delegates MENU to the current focused item.
- Sidebar-to-content navigation accepts Leanback fragment containers and preserves
  their normal descendant-focus/header-transition behavior.
- The exit confirmation is a nonfocusable, two-second message inside the stereo
  content tree; it appears in both eyes and resets its timer when repeated.
- A window-local navigator restores focus and ListView selection after touch mode
  clears them, and supplies focus traversal when direct key dispatch is unhandled.
- Pending confirmation is bound to the original focus, selected row and adapter.
  Focus/content/adapter changes, focus loss and detach invalidate pending input.


## Window coverage

| Entry | Implementation |
|---|---|
| Browse, search, channel/uploads, web, player | `MotherActivity.onContentChanged` |
| Sign-in and add-device guided pages | Stable `GuidedContent` container before async Fragment transaction |
| Settings, playback options, comments/chat, list/radio preferences, errors | Existing Activity/Fragment content, same stereo root |
| Text-edit AlertDialog and proxy AlertDialog | Complete dialog content, including title/buttons; public Window APIs |
| Local file selection | `RayNeoFilePickerActivity` adapter |

SharedModules' generic selector and YesNoDialog classes have no callers in this
checkout's application code. Future new Dialog/Popup creation sites must be added
to this inventory and adapted; wrapping an Activity does not wrap another Window.
System-owned permission dialogs, input method, voice-input and external activities
are outside this app's window ownership.

## Build

Use JDK 17, Android SDK platform 34 and build tools 30.0.3, and the pinned submodules.
The Gradle wrapper uses Gradle 7.5 / AGP 7.4.2.

```powershell
git submodule update --init --recursive
./scripts/build-rayneo.ps1 -JavaHome 'PATH/TO/JDK17' -AndroidSdk 'PATH/TO/SDK' -DebugApk
```

For a release build, create your own keystore and local `keystore.properties`:

```properties
storeFile=../releases/signing/rayneo.jks
storePassword=YOUR_LOCAL_PASSWORD
keyAlias=rayneo
keyPassword=YOUR_LOCAL_PASSWORD
```

`storeFile` is resolved relative to `smarttubetv/`. Keystores, properties, APKs and
local artifacts are ignored by Git. Keep the signing material for future updates.
Omit `-DebugApk` for release. Output is copied into `releases/rayneo-v5/`.

## Verification boundary

Version 1 was built without device testing and then installed on request. Version 2 corrects the input semantics to focused-item navigation. Host tests
exercise ordinary View double rendering, logical sizing, pointer mapping, cursor
direction mapping, actual selected-control movement, ListView recovery and delayed confirmation cancellation. These tests
do not prove TextureView hardware replay, firmware event delivery, optical
synchronization, actual online playback or comfort/performance on the glasses.

Pending device acceptance: moving video with controls hidden; subtitles and
animated controls; all listed pages/dialogs; cursor four-direction motion and
scrolling; tap/double-tap/long-press; cancel/reopen dialogs; background/return;
video replacement, resize/rotation/zoom; search/text entry. No device installation
or runtime acceptance is implied by the first code push.

Full creation-path and overlay inventory: [WINDOW-COVERAGE.md](WINDOW-COVERAGE.md).

## Keeping the message adaptation isolated

Business code retains the upstream MessageHelpers API. SharedModules adds only a
generic MessagePresenter extension; RayNeoMessagePresenter binds it to the active
stereo window at application startup. Native Toast fallback and cleanup remain in
the helper, independently of custom-message timers.

The root SharedModules submodule is pinned to the WayneShao fork. For an existing
checkout after pulling this change, run `git submodule sync -- SharedModules`
before `git submodule update --init --recursive`. Keep the submodule commit
published before pushing the main repository pointer. Future upstream dependency
updates must carry the small presenter hook forward; do not reset the pointer to
an unmodified upstream revision. MediaServiceCore and its nested dependency are
unchanged.

Host API regression tests live in common under MessagePresenterTest, using the
Java 17-compatible Robolectric harness already used by this port. Physical
acceptance remains separate: see [v5 validation](VALIDATION-v5.md).
