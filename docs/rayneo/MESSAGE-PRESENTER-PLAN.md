# Central message presentation

Approved design: preserve upstream MessageHelpers callers and add a generic,
optional presenter in SharedModules. RayNeo owns presentation and window selection;
the shared library has no dependency on the app or glasses. Device testing remains
paused. Publish the dependency commit before the app's submodule pointer.

Implementation and verification:

- [x] Add regression tests for original callers, background-thread dispatch,
  fallback, cancellation, presenter replacement, and stale Toast cleanup.
- [x] Add MessagePresenter and main-thread registration/show/cancel coordination;
  keep native Toast cleanup separate from public cancellation.
- [x] Register the RayNeo presenter at application startup; restore original
  MessageHelpers imports/calls without reverting unrelated fixes.
- [x] Run the existing stereo/navigation suite and a signed release build.
- [x] Review the change, fork/push SharedModules, update the pinned dependency,
  and push the SmartTube branch. Verify both remote commits are reachable.

Do not change nested MediaServiceCore/SharedModules: Gradle resolves sharedutils
from the root SharedModules checkout in this workspace. A clean checkout must
resolve the published dependency without local sibling repositories.

Acceptance: unchanged business callers reach a stereo window when available;
ordinary devices and unavailable windows retain Toast fallback; message replacement
and cancellation run on the main thread; native cleanup never cancels a newer
presenter message. No claim of hardware acceptance for this refactor.

Completed with 36 passing host tests and a signed v5 release; see VALIDATION-v5.md.
The read-only review found no blocking defect. Its cancellation-exception test
suggestion was implemented and passed.
