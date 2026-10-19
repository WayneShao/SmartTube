# SmartTube 32.68 embedded input integration

Baseline: SmartTube `ffa16426ea3c7c82077e0f00228818b85f373588` (32.68).
Module: WayneShao/BBLL-RayNeo `32ac674b46555ccc557887add49a00172c118dd2`.
Exact imported source hashes and local deltas: `embedded-input/module-version.json`.

## Actual entry inventory

- SearchSupportFragment, SearchFieldPresenter and ChannelHeaderPresenter: original Helpers.showKeyboardAlt calls, text watchers and editor actions retained.
- SimpleEditDialog / WebProxyDialog: existing RayNeoWindow attaches the editor bridge; CENTER and real mouse release open the focused original EditText. Original KeyHelpers click path continues through Helpers.
- Leanback SearchBar native keyboard request and GuidedActionAdapterGroup open/close: optional shared presenter, with default IMM fallback on non-RayNeo devices.
- Window content changes: bind current EditTexts after global layout; disable automatic system IME only where window touch/confirm and helper entry are connected. Pre-attachment requests are cancelled by subsequent hide and checked against active window before delivery.
- WebBrowserFragment has a plain WebView, no preexisting WebViewClient. RayNeo uses EmbeddedWebView before loadUrl, retains JS/cache setup and external clicked-navigation behavior, installs DOM bridge after page finished, closes on view destruction.
- No Compose or BBLL T9-specific rules were added.

## Ownership

The independent Android library owns its Dialog and KeyboardSurface. KeyboardWindow attaches input only, never StereoLayout. FocusNavigator/FocusRecovery/FocusTarget accept ViewGroup so the same native navigation works with both surfaces.

Only keyboard windows call EmbeddedInput.motion/key before TempleInput. Consumed multi-touch cancels pending host gestures. Right pad cyttsp5_mt owns voice/clear; left pad cyttsp6_mt is consumed in this window. Ordinary windows retain their existing two-finger MENU. No InputMethodService or secure-settings writes.

The original Editable, filters, text watchers and IME actions remain authoritative. User BACK/collapse routes a distinct cancel callback to original pre-IME handling. Programmatic hide closes the requesting window's input session without submitting it. Password targets disable voice.

## Build and dependencies

`./scripts/build-rayneo.ps1 -JavaHome PATH_TO_JDK17 -AndroidSdk PATH_TO_SDK`

The Gradle library preBuild invokes scripts/prepare_keyboard.py, verifies all pinned config/keyboard-vendor.json and keyboard-voice.json hashes and generates sources/assets/JNI beneath ignored build/. A clean machine downloads the same pinned dependencies; a populated _local/vendor cache is verified before reuse. Only ARM64 APK is produced for -Prayneo. Models/APKs/signing material are not tracked. Build flag does not rename the app: label is SmartTube.

The shared library manifest keeps the host's legacy minimum SDK; EmbeddedInput.show rejects API <23, while this host additionally requires RayNeo API29+. Native engine acceptance targets X3 Pro Android12, not all legacy host devices.

Existing ARM64 APK has no libc++_shared.so; dependency inspection found no duplicate native names. Feelime JNI class/method names are retained. Feelime license/notice and sherpa-onnx Apache2 license are packaged. sherpa license source: https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/v1.13.6/LICENSE (SHA256 cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30).

## Shared defects and portable fixes

See shared-input-report.md and shared-input-32ac674b-fixes.patch. These are module fixes, not hidden host workarounds. The original BBLL reference worktree remains unchanged.

## Acceptance boundary

Desktop tests cover real Android editor buffers, host helper entry, keyboard window ownership, original cancel/submit behavior, late voice/pinyin result rejection, pointer and two-finger state-machine behavior. Native engines are not executed by Robolectric. The DOM-event fixture checks the actual submit branch under Node; it does not prove real WebView/browser compatibility.

Prior instruction is install without launch. No app launch, playback, microphone recording or physical gesture acceptance is implied by build/install. On-device pinyin selection, offline speech, physical hold/up/release, WebView fields and ordinary MENU after keyboard close remain to be accepted.

Rollback: keep signed releases/rayneo-v9 APK. It is versionCode2490; Android may reject release downgrades. Do not uninstall/clear data to force rollback; rebuild the known v9 source with a higher versionCode if needed for data-preserving recovery.
