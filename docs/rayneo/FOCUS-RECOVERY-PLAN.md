# Focus recovery after UI changes

Implement the approved skill behavior in the shared RayNeo navigation layer.
Keep business pages unchanged and do not perform device operations.

- Add failing host regressions for lost focus after a consumed direction,
  replacement content, live list/grid selection, stable item identity, normal
  boundaries, and cancellation when a window loses ownership.
- Separate current-tree target discovery from historical focus hints. Prefer
  valid live selection; constrain fallback to the surviving active region.
- Observe direction results and coalesce layout/focus-change recovery. Wait for
  layout/scroll completion within a bounded observation window. Restore focus
  only; never replay a consumed direction or obsolete confirmation.
- Retain existing unhandled direction traversal, including nonfocusable sidebar
  containers with focusable children. Prevent traversal after logical selection
  or scroll already advanced.
- Review, run the full host suite and release build, verify artifact, commit and
  push. Device testing remains paused; the installed v5 is not replaced here.

Completed: 51 host tests passed, release/signature/alignment verified and reviewed.
See VALIDATION-v6.md for artifact identity and the untested device boundary.
