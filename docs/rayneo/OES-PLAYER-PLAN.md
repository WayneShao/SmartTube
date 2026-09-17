# Separate OES player candidate

User authorized restoring normal v6, then making a new adaptation APK. Normal
v6 was restored without launch/data clearing. Build the full app as a separate
package/label using -PrayneoOes; preserve the normal -Prayneo output.

Design: one ExoPlayer decoder surface backed by OES, a full-window GL video layer
under the existing stereo UI, and one transparent logical video anchor retaining
the upstream layout/input contract. Map anchor corners into the output layer to
retain aspect, zoom, rotation, flip and gravity. Keep SurfaceHolder callbacks and
per-owner cleanup, including asynchronous readiness and Surface recreation.

Changes stay in the common surface factory/transform predicates, RayNeo window
sibling discovery, independent OES classes, and opt-in build configuration.
No copies of business pages, caption state, focus engine or dialogs.

Verification: regression tests for window sibling idempotence and coordinate
mapping; existing 51 host checks; full separate release identity/signature; an
opt-in full-player local-file harness for actual fragment/control/subtitle/overlay
checks if device testing is available. No production package replacement for tests.
Renderer initialization failure must unbind cleanly and restore TextureView.
Never claim complete optical/physical-input or online acceptance from host tests.

STOPPED after the full-player device display check. See OES-PLAYER-ARCHIVED.md.
The candidate failed stereo UI acceptance; no additional performance tuning or
production rollout is intended.
