# TextureView versus OES: measured component experiment

## Decision

The OES renderer shows a measurable CPU benefit and is worth retaining for further
integration experiments. Do not replace the production renderer on these results
alone: this test excludes the complete playback UI, captions, online transport,
focus interactions and background/resume behavior. The normal application remains
v6 with its existing rendering path. No full-app or long-term heat benefit is claimed.

## Results

Same device/boot session, USB power, fixed local H.264 1920x1080 30 fps yuv420p,
40-second repeating dynamic testsrc2 file. Both paths use the same bundled ExoPlayer
factory and display one full source in each 640x360 eye rectangle at y=60 within
1280x480. Warmup precedes each 80-second sample. Each sample covers two full source
cycles, though starting phase was not precisely synchronized. There is no audio,
subtitle, interactive overlay or network traffic in the test source.

| Run | Rendering | CPU, percent of one core | End PSS, MiB | Battery sensor, C |
|---|---|---:|---:|---|
| A1 | Existing TextureViewWrapper + StereoLayout | 33.00 | 154.62 | 37.0 -> 37.0 |
| B | Moonlight-derived external-OES renderer | 26.08 | 149.20 | 37.0 -> 37.5 |
| A2 | Existing TextureViewWrapper + StereoLayout | 34.76 | 156.18 | 37.5 -> 38.0 |

CPU mean of A1/A2 is 33.88%; B saves 7.80 percentage points, a **23.04% relative
reduction in this component benchmark**. Each run has eight valid tick/uptime
intervals. CPU formula: `100 * delta(utime + stime) / CLK_TCK / delta(uptime)`;
CLK_TCK was read as 100, and PID/boot identity checked throughout each run. PID
changes between runs are permitted; no measurement interval spans processes.

OES consumption counters progressed 1441 -> 2634 frames over 40.063 seconds,
or 29.78 consumed frames/s. Decoder logs identify 1920x1080 30 fps AVC and READY
state. A1's later decoder count was 3294 rendered / 4 dropped, B's was 2690 / 2,
and A2's was 1790 / 4 at their respective logged times. These are cumulative,
unequal observation periods, not a comparable drop-rate table. Decoder delivery
and OES texture consumption are not measurements of optical presentation cadence.

Captured TextureView and OES screenshots each had zero RGB difference between the
two eye crops. The samples contain moving markers; images were inspected for fit,
orientation and black bars. Screenshots do not prove optical synchronization.
The same boot ID was observed before/after; no reboot was observed. Current crash
buffer entries inspected were older than this benchmark, not new test crashes.

Battery sensor readings are not temple skin temperature. Ordered A/B/A, one repeat,
USB charging, changing temperature and system activity limit causal claims about
power or heat. System gpubusy pairs are stored raw and are not app-specific.
Startup/cache/GC effects also limit interpretation of the small PSS difference.

## Why this is a component experiment

Two ordinary v6 online videos remained in buffering; the first logged repeated
SABR SocketTimeoutException. These are not valid decoder/rendering baselines.
The exported normal launch path does not accept arbitrary local media files.
Instead, an opt-in separate-package Activity uses the actual existing
TextureViewWrapper/StereoLayout implementation and the same bundled player library,
then switches only the renderer to an experimental Moonlight OES implementation.
It does not claim to exercise the full production PlaybackFragment or network flow.

Separate home diagnostics measured 39.57% single-core CPU over 20 seconds on a
selected card, and 33.15% after focus moved to the search icon. RenderThread dominated
a later instantaneous thread sample. Those short, partly startup-affected samples
establish an investigation target, not a diagnosed bug or a video-OES benefit.

## Reproduction

Generate the same deterministic host-side sample (not on-device transcoding):

```powershell
ffmpeg -f lavfi -i 'testsrc2=size=1920x1080:rate=30' -t 40 -c:v libx264 -preset ultrafast -crf 22 -pix_fmt yuv420p -an -movflags +faststart motion-1080p30.mp4
./gradlew.bat :smarttubetv:assembleStfdroidRelease -Prayneo -PrayneoBenchmark
```

The opt-in APK package is `app.smarttube.rayneo.benchmark`. Push the file to its
external-files directory, then launch
`com.liskovsoft.smartyoutubetv2.tv.benchmark.VideoBenchmarkActivity` with string
extra `mode=texture` or `mode=oes`. Wait for valid moving output/READY and warmup;
run `scripts/monitor-rayneo.py --help` for the explicit serial/package/output
arguments. Use unique output directories. Exiting the Activity releases the
player; the OES renderer additionally releases GL/Surface resources. This prototype
ends the Activity on EGL/Surface loss; that policy is not a production resume design.

The normal build excludes the entire benchmark source set and manifest. Only the
opt-in Gradle block touches an existing production file. Renderer code derives from
WayneShao/moonlight-android `d01a5a8b`, with namespace/logging/fit adaptations.

Sample SHA-256:
`38913f260122fea38346ed2629aa72b869eb18f22a42e4010176fca98d2f3e6d`

Measured APK SHA-256:
`a88d7c6f2af1337fbfaa7db517ed9289f55e36bd43f86ca57cfcd6c72dfd4f69`

Raw samples, screenshots, system snapshots and attempt logs remain ignored under
`releases/performance-v6/`; they may contain unrelated device/media metadata and
are not published. The temporary benchmark package and device-side media/temp
files were removed after the experiment. Production package was rechecked as
`32.47-rayneo.6`, versionCode 2460; no production replacement was installed.

Normal-build verification after the experiment: 51 host tests passed with zero
failures/errors/skips; release assembly succeeded. Every DEX in the normal arm64
APK was checked and contains no VideoBenchmarkActivity. Bounded independent
source review found no blocker in the component comparison or cleanup path.
