# SmartTube RayNeo X3 Pro implementation

User approved implementation on 2026-09-15. Baseline: 249bc833f, SmartTube 32.47.

## Design

Use the existing single-player TextureView path on RayNeo X3 Pro (ARGF20 /
MercuryLiteXR, API 32, physical 1280x480). Keep one business View tree measured
to 640x480, drawn into both eye regions. Derive eye width from the actual
window; adapt SmartTube's existing density calculation to the eye width.
No CPU bitmap video copying or second player. Independently wrap application
dialogs using public Window/content APIs. Keep existing DPAD/business actions;
scope temple gesture handling to identified touchpads and the current window.
User clarified that cursor mode is mandatory: both eyes display the same visible
cursor, touchpad X/Y deltas move it, confirmed single tap clicks at its location,
double tap returns within the active window, long press opens the existing menu.
Keep hardware DPAD navigation intact. Cancel pending clicks on focus loss/detach.

Normal devices retain upstream behavior. System-owned IME/permission windows
are outside the application content wrapper. Full composition through hardware
layers and physical touchpad behavior require runtime validation.

## Implementation plan

- [x] Preserve baseline on a dedicated branch; initialize pinned submodules.
- [x] Build upstream stfdroid debug with JDK 17 and local SDK (BUILD SUCCESSFUL).
- [x] Add behavioral tests for half-width layout, identical eye drawing,
  right-eye coordinate mapping without event mutation, and window ownership.
- [x] Add common RayNeo device/window/layout helpers; hook MotherActivity
  content installation, density, fullscreen, and disable edge-slide conflicts.
- [x] Use TextureView in SurfacePlaybackFragment on supported hardware;
  verify frame invalidation and stable Surface holder lifetime.
- [x] Adapt independently created AlertDialogs and preserve cancel/dismiss.
- [x] Add temple cursor movement, edge scrolling and existing back/menu dispatch; cancel pending
  actions on window focus loss/detach and preserve mouse/controller input.
- [x] Build signed APK, run focused tests and check manifest/signature/hash.
- [x] Record pending device smoke validation; user explicitly deferred all
  device testing. Do not install/start/probe devices during implementation.
- [x] Push the first implementation branch to the user's existing fork
  WayneShao/SmartTube after host checks, as explicitly requested by user.

## Acceptance

Homepage, settings, search, player, subtitles/control overlays, and editable
dialogs must display complete content in both eyes. Check pause/resume,
seek, background/return, dialog close, and surface recreation. Screen captures
can prove logical composition, not optical synchronization or wearing comfort.
No performance improvement claim without comparable baseline/candidate media.
