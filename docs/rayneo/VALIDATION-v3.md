# Sidebar traversal and stereo exit notice — 2026-09-15

The user confirmed basic physical four-direction navigation in v2 and reported
that RIGHT could not leave the category sidebar, and the exit Toast was single-screen.

## Fixes

Leanback's BrowseSupportFragment focus search returns the main fragment container
when moving from headers toward content. That container may be nonfocusable while
its children are focusable. FocusNavigator previously rejected it with isFocusable().
Traversal now validates window ownership/visibility and hasFocusable(), then calls
the container's normal requestFocus(direction). This preserves descendant selection
and Leanback's header-transition callbacks.

The two exit-back helpers route their prompt into the active StereoLayout. A
nonfocusable transient TextView is drawn in each eye, above elevated content. It
does not consume clicks or navigation. Repeated messages reset the two-second
timeout; focus loss and detach hide it. Ordinary windows retain the original Toast
fallback. Other system/background Toast producers are outside this scoped change.

## Validation

- Reproduced the container-traversal failure in a host regression before fixing it.
- 26 tests passed, zero failures/errors, including container-to-child focus transfer,
  identical left/right notice pixels, retained focus, timeout replacement and detach.
- Signed release build, APK v2 signature verification and ZIP alignment passed.
- Installed `32.47-rayneo.3` / versionCode 2439 with the same signing certificate.
- Cold startup completed in about 1.3 seconds; BrowseActivity owned window focus,
  process remained alive and crash buffer was empty.
- On-device BACK-key checks opened the sidebar and displayed the exit prompt.
  Screenshot confirmed “再按一次退出” in both eye regions, at matching positions.
- Physical RIGHT swipe from the sidebar is awaiting the user's current check.
  A native ADB RIGHT key alone would not prove the synthetic-key traversal fix.

## Artifact

`SmartTube_fdroid_32.47-rayneo.3_arm64-v8a.apk` — 28,429,076 bytes.

SHA-256: `917d91927a5cf2b5cebede2fe7a655577b8299ff41284e8ac7ff7c8c9f3b3d29`

Certificate SHA-256: `9b8d5d6835cf5601bf77fd982692accaf61e7f5986b755de49fb7783d9a0a507`

Local device captures are retained under `releases/rayneo-v3/` and excluded from Git.
