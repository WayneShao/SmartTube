# Shared embedded-input defect report — 2026-10-19

Reference repository WayneShao/BBLL-RayNeo, branch rayneo/in-app-keyboard, commit 32ac674b46555ccc557887add49a00172c118dd2. Reference worktree was read-only and clean.

## User close does not notify the original editor

KeyboardDialog BACK/collapse only dismissed the dialog. Native Leanback editors rely on pre-IME BACK UP to save the edited GuidedAction, leave editing mode and restore row focus. The original buffer changing does not replace that callback.

Fix: optional EditorTarget.cancel(), NativeEditor delegates original pre-IME BACK DOWN/UP, KeyboardDialog invokes it only for user close after dismissal. Programmatic hide/session replacement/detach do not submit or cancel another session.

## Web completion never submits

Original EmbeddedWebView mutate('submit') only dispatched change and blur. An actual-source Node fixture fails against 32ac674b with expected submit1/actual0.

Fix: dispatch cancellable Enter keydown/keyup and use form.requestSubmit only when not cancelled and not a textarea, preserving form validation. Existing pages that implement Enter handling must cancel default behavior as with normal browser input. Real page compatibility remains unverified.

Reproduce: `python scripts/test_keyboard_web_submit.py PATH_TO_REFERENCE_EmbeddedWebView.java`; fixed: `python scripts/test_keyboard_web_submit.py` from SmartTube.

## Window creation can throw with a finishing or invalid host

EmbeddedInput.show previously registered the dialog then called show without checking Activity lifecycle or cleaning up on token/display failure.

Fix: unwrap Activity, reject finishing/destroyed hosts; catch BadTokenException/InvalidDisplayException, remove only the failed registration, dismiss resources and return false. Regression checks finishing host rejection and successful reopening after simulated token failure.

## Window-scoped hide contract

Add EmbeddedInput.closeWindow(View) so host hide requests from a different control in the same window can close the active keyboard. Keep close(EditorTarget) identity-specific to protect newer sessions.

## Host-only findings

SmartTube Leanback show/hide calls needed both directions wired; pending pre-attachment show needed cancellation and window-focus checks; right-eye touch hit testing needed coordinate folding. These are not added as assumptions to the shared module. Right-pad-only voice is intentional; left pad is not a second voice source.

## Transfer

`shared-input-32ac674b-fixes.patch` applies to the reference repository source layout; for CRLF working trees use `git apply --ignore-space-change`. The window API also exposes isShowing(owner), so a host can deduplicate automatic requests while its underlying window lacks focus. Android regression tests live in SmartTube common/src/test/java/io/github/wayneshao/rayneo/ime/KeyboardWindowTest.java; source-only module tests are in embedded-input/src/test. Shared source changes retain no SmartTube/BBLL class or resource dependency. Physical glasses and native speech results remain pending; do not infer acceptance from these desktop tests.
