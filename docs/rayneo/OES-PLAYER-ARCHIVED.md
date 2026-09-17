# OES full-player experiment: stopped, not for daily use

Date: 2026-09-17. User prioritizes their own working X3 Pro configuration and
practical benefit, not broad compatibility or improving a CPU percentage alone.
The existing v6 was explicitly described as fully usable without an obvious weakness.

## Outcome

The component experiment showed roughly 23% relative CPU reduction for video only.
The full-player integration did not preserve equivalent display behavior:

1. Full-width OES video beneath the existing StereoLayout: video appeared in both
   eyes, but playback controls, gradient/scrim and subtitles appeared on the left.
2. One software-rendered UI bitmap, reused in both eyes with root invalidation:
   video remained stereo, but controls and the dynamic in-window notice still
   appeared only on the left in the device screenshot.

These are observed output failures, not a proven diagnosis of damage clipping,
HWUI caching or a particular driver function. A passing host bitmap test did not
predict the device composition result. No whole-device reboot was observed.

Per the user's instruction to avoid repeated tuning/testing when the current
configuration is not worthwhile, stop here. Do not add more copy layers, expand
the test matrix, or promote this renderer. This does **not** prove zero theoretical
OES benefit for all layered players; it rejects this candidate as a replacement
for the user's already usable v6. CPU comparison of unequal one-eye UI output
would be misleading, so no full-player performance improvement is claimed.

## Isolation and cleanup

- Original signed v6 APK was reinstalled with `pm install -r`, preserving data.
  Package `app.smarttube.rayneo`, version `32.47-rayneo.6`, code `2460` was checked
  again after cleanup; stopped, no running process. It was not launched afterward.
- Full candidate package was `app.smarttube.rayneo.oes`; local validation used
  `app.smarttube.rayneo.oes.validation`, with the real PlaybackActivity/Fragment,
  locally injected video, controls, subtitles and transient messages.
- Only the validation package was installed for the new scheme. It was stopped
  and uninstalled; its test media and temporary installation/screenshot files
  were removed. No account or network settings were changed.
- APKs under ignored `releases/rayneo-oes-v1/` are renamed `NOT-FOR-USE-*`.
  `full-ui.png` and `full-ui-replay.png` preserve before/after failure screenshots.
  Do not mistake these files for accepted releases.

## Source state

The archive branch retains the OES surface wrapper, transformed video anchor,
per-instance renderer cleanup and fallback, optional UI bitmap replay, targeted
host regressions and the excluded local full-player test entry. The ordinary
adaptation branch retains v6 and the earlier component benchmark; do not merge
this archived runtime path into it without a separately justified experiment.

Build/host checks succeeded for the candidate and validation builds, including
stereo-root sibling ownership, geometry and single-draw bitmap behavior. Physical
display acceptance failed as above. No claim of complete interaction, optical,
HDR or background-resume validation is made.
