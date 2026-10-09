# 32.64 RayNeo v7 upstream update — 2026-10-09

Merged upstream master `f18f785142dd781681bfd613e95c22fd327f27d1` (32.64)
into the daily `rayneo/x3pro` branch. Preserved the accepted TextureView rendering,
all-window stereo integration, generic message presenter and native focus recovery.
No archived OES changes were merged.

Conflicts were confined to MotherActivity, the application build version block,
and the SharedModules pointer. MotherActivity retains upstream back-state and
screensaver resume/suspend/cleanup changes alongside stereo content installation.
AppDialogActivity and PlaybackActivity retain upstream's inherited back handling.
No new upstream manifest/layout/window construction path was introduced in this
update; menu additions continue using the existing wrapped dialog presenter.

Dependencies:
- MediaServiceCore: `240321a55f8bef7e407658949170242318be6731`.
- SharedModules: `e30ada49b4d3467b8b14050d7971675f6c934395`, merging upstream
  `13f5687dd6757b02fbcdf14c5403d0339e377db5` while retaining only the generic
  MessageHelpers presenter delta. Dependency commit published to the fork first.

Validation:
- Full build script succeeded on JDK 17, Gradle 7.5 / AGP 7.4.2.
- 62 host tests across 14 suites, zero failures/errors/skips, including the new
  upstream screensaver and suggestions tests and the existing RayNeo suite.
- Independent read-only merge review found no blocking integration issue.
- APK v1/v2 signature verification and zipalign passed. Signing certificate matches
  the prior daily package. No benchmark Activity or archived OesVideoView in DEX.

Artifact:
- `releases/rayneo-v7/SmartTube_fdroid_32.64-rayneo.7_arm64-v8a.apk`
- Package `app.smarttube.rayneo`, version `32.64-rayneo.7`, code `2470`.
- minSdk 21, targetSdk 34; arm64-v8a; 28,455,118 bytes.
- SHA-256: `1569cc198b43f1e5a0a654a53df2d657f3be727ebb21ce853da6b6c6098116c0`.
- Certificate SHA-256:
  `9b8d5d6835cf5601bf77fd982692accaf61e7f5986b755de49fb7783d9a0a507`.

Deployment: refreshed device identity and current user, reused verified TCP ADB,
then installed with `pm install -r --user 0`. PackageManager returned Success;
read-back confirmed 32.64-rayneo.7/code2470 with the prior data inode retained.
Application stayed stopped with no process. Temporary APK removed. No launch,
physical gesture, login, online playback or optical acceptance claimed this turn.
Previous v6 APK remains local for rollback/reference.
