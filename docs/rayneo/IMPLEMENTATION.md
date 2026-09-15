# SmartTube RayNeo implementation

Baseline: 249bc833f, SmartTube 32.47. Branch: rayneo/x3pro.

## Required interaction

Cursor mode means the selected control/focus highlight. A temple swipe moves the
selected item left, right, up or down; a single tap confirms it, a double tap goes
back, and a long press opens its menu. Activity pages and independent dialogs use
one per-window content and input owner. Both eyes show the same selected item.
The first version's free-pointer interpretation was rejected and removed in v2.

## Display

Keep the single content tree measured to 640x480, drawn across the 1280x480 X3 Pro
window. Preserve the TextureView video path, Surface ownership repair and subtitle/
controls integration. Do not change system display/direction settings.

## v2 verification

- [x] Remove free-pointer drawing, coordinate-click injection and edge mouse scrolling.
- [x] Map completed touch/hover swipes to one directional navigation action.
- [x] Restore per-window focus and missing ListView selection.
- [x] Supply unhandled-key focus traversal for direct Window callback dispatch.
- [x] Bind delayed confirmation to original focus, row and adapter.
- [x] Test real selected controls through a four-direction swipe cycle and confirm.
- [x] Test list recovery, row/adapter changes and cancellation before layout.
- [x] Complete final signed build, deploy/start, and push the input correction.

Physical temple event delivery and full video/optical acceptance remain distinct
from host widget tests and ADB-injected direction-key checks.

## v3 correction

The user confirmed basic physical four-direction navigation. Sidebar traversal now
accepts Leanback's nonfocusable fragment container, and exit confirmation is rendered
inside the stereo window. See VALIDATION-v3.md for host and device evidence.
