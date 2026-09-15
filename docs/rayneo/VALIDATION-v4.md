# Guided pages and full creation-path audit — 2026-09-15

The login endpoint returned a device code on v3, but SignInActivity rendered its
guided page across the physical window with no stereo/input wrapper. SignIn and
AddDevice commit their Fragment asynchronously without setContentView, bypassing
MotherActivity.onContentChanged. Both now install a stable named content container
before the transaction, including restored-Activity creation.

The requested broader audit also found and fixed channel loading spinners attached
to DecorView, unwrapped player/title tooltips and remaining app foreground notices.
See [the complete coverage inventory](WINDOW-COVERAGE.md) for reachable entry points,
noninteractive routes, unreferenced legacy code and system-window boundaries.

## Host verification

- 30 tests, zero failures/errors; signed Release build successful.
- GuidedContent regression verifies async Fragment content at 640 px and exactly one
  restored Fragment after Activity recreation, inside one stereo root.
- Tooltip regression verifies anchor-window ownership, no focus stealing, hide and
  replacement without removing an unrelated notice.
- Overlay-root test verifies loading indicator coordinates in the logical eye.
- Application-context notice test verifies routing into the focused stereo window.
- Existing focus, sidebar, ListView, notice, dialog and Surface tests remain passing.
- Independent read-only audit/review found no additional confirmed visible Activity
  creation bypass beyond the two guided pages.
- No submodule commits changed; app message calls use a local compatibility facade.

## Deployment

Installed `32.47-rayneo.4`, code 2440, package `app.smarttube.rayneo`, arm64-v8a.
Cold startup succeeded in about 1.3 seconds; crash buffer was empty. Existing
account data was retained. At the start of guided-page verification a system launcher
quick-settings panel owned focus; account authorization is not performed by the agent.

## Artifact

`SmartTube_fdroid_32.47-rayneo.4_arm64-v8a.apk` — 28,430,686 bytes.

SHA-256: `0df07a6318905c30818e111cfe66bfd67189d4096113ce89ec16551f9a050c3f`

Certificate SHA-256: `9b8d5d6835cf5601bf77fd982692accaf61e7f5986b755de49fb7783d9a0a507`

APK v2 signature verification and ZIP alignment passed. Local captures including
login codes/account UI are excluded from Git and are not published as artifacts.
