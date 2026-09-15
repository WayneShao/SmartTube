# Window and content audit — 2026-09-15

This inventory traces content installation and visible child layers, not merely
Activity inheritance. Source coverage, host tests and physical-device acceptance
are separate evidence levels.

| Reachable entry/layer | Installation | Audit disposition |
|---|---|---|
| Browse, search tags, channel, channel uploads, playback, web browser | setContentView, MotherActivity.onContentChanged | Stereo root and per-window navigation installed |
| AppDialogActivity and opaque variant: settings, comments/chat, preferences | setContentView plus contained fragments | Stereo root covers content; independent child windows require separate entries below |
| SignInActivity | Previously async GuidedStep fragment directly into android.R.id.content | **Miss fixed in v4:** install stable guided_content container before transaction, including restoration |
| AddDeviceActivity | Same async GuidedStep entry | **Same miss fixed in v4** |
| File picker | RayNeoFilePickerActivity | Dedicated wrapper and single-eye density |
| Text-edit/proxy AlertDialogs | explicit RayNeoWindow.install after show | Full title/content/buttons wrapped; cancel and focus lifecycle retained |
| Channel/channel-upload delayed loading spinners | Previously added to DecorView | **Miss fixed in v4:** overlayRoot selects StereoLayout |
| Player and title-bar tooltips | Previously WindowManager TYPE_APPLICATION_SUB_PANEL | **Miss fixed in v4:** anchored overlay in the anchor's own stereo root; hide removes only its own view |
| Foreground app notices/errors/proxy results/exit prompts | MessageHelpers and direct Toast sites | **Expanded in v4:** RayNeoMessages routes app calls to focused stereo window; non-RayNeo/background fallback retained |
| Screensaver dim layer | Uniform full-DecorView color | Both eye regions receive the same dimming; not a content-copy omission |
| Splash, section launchers, backup receiver | Route/import without interactive page | No independent visual content to duplicate |

## Reachability checks

- The two GuidedStep addAsRoot Activity sites are SignIn and AddDevice. Both now
  create content before committing the fragment, with stable IDs on recreation.
- Generic selector/YesNo dialog implementations in SharedModules have no discovered
  application callers in this checkout. They are not claimed as adapted reachable UI.
- Legacy LoopingVideoView/PreviewCardView/VideoCardView has no discovered final-card
  caller or resource use; it is not counted as a tested player path.
- ToastFactory has no callers. A Toast example inside TextViewLinkHandler is a comment.
- Dependencies can still generate their own messages; routing app call sites does not
  intercept every third-party/system Toast globally.

## Boundaries requiring separate acceptance

Android IME, permission/settings screens, external browser/login confirmation,
notifications and system PiP are system-owned windows. The app wrapper does not
establish their stereo behavior. PiP invocation remains conditional on platform
support and is not a validated RayNeo display mode. Main video hardware replay,
all online content variants and optical comfort also need physical verification.

## Regression targets

Host: asynchronous fragment containment and recreation; exactly one content tree;
tooltip show/hide/replacement ownership; logical loading-overlay placement;
application-context message routing; existing four-direction focus, sidebar,
ListView recovery, dialogs, notices and Surface ownership tests.

Device: reopen login and confirm code plus both action buttons in each eye; navigate
between guided actions; inspect an anchored title tooltip; retain previously user-
confirmed four-direction navigation and sidebar RIGHT behavior. Displaying a code
is not proof that the user has completed account authorization.
