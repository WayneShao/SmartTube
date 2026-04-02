package com.liskovsoft.smartyoutubetv2.tv.ui.rayneo;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.app.HeadersSupportFragment;
import androidx.leanback.widget.VerticalGridView;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Maps RayNeo X3 Pro temple-touchpad gestures to Android D-pad KeyEvents.
 *
 * <h3>How it works (no permissions needed, no loops)</h3>
 * The handler is called from {@code Activity.dispatchTouchEvent(MotionEvent)}.
 * When a gesture is recognised it calls {@code activity.dispatchKeyEvent(KeyEvent)}
 * directly. Because {@code dispatchTouchEvent} and {@code dispatchKeyEvent} are
 * independent dispatch chains, there is no risk of re-entry or infinite loops –
 * the synthesised KeyEvent never routes back into this handler.
 *
 * <h3>Gesture map (right temple = cyttsp5_mt)</h3>
 * <ul>
 *   <li>Single tap           → DPAD_CENTER (confirm / play-pause)</li>
 *   <li>Double tap           → BACK</li>
 *   <li>Two-finger single tap→ MENU (open player options)</li>
 *   <li>Swipe up            → DPAD_UP</li>
 *   <li>Swipe down          → DPAD_DOWN</li>
 *   <li>Swipe left          → DPAD_LEFT</li>
 *   <li>Swipe right         → DPAD_RIGHT</li>
 * </ul>
 *
 * <h3>Tuning constants</h3>
 * Adjust {@link #SWIPE_THRESHOLD}, {@link #DOUBLE_TAP_MS}, and
 * {@link #TAP_SLOP} if the gesture recognition feels off after testing.
 */
public class RayNeoGestureHandler {

    private static final String TAG = "RayNeoGesture";

    /** Minimum displacement (px in touchpad-coordinate space) to count as a swipe. */
    private static final float SWIPE_THRESHOLD = 20f;
    /** Maximum displacement (px) to still count as a tap rather than a swipe. */
    private static final float TAP_SLOP = 12f;
    /** Maximum interval (ms) between two taps to recognise a double-tap. */
    private static final long  DOUBLE_TAP_MS = 350L;
    /**
     * Maximum interval (ms) between two consecutive ACTION_DOWN events to be
     * treated as a two-finger tap.  Mercury does not send ACTION_POINTER_DOWN;
     * instead it fires two separate ACTION_DOWN events in rapid succession.
     */
    private static final long  TWO_FINGER_DOWN_MS = 180L;

    private final Activity mActivity;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // --- state per touch sequence ---
    private float mDownX, mDownY;
    private long  mDownTime;
    private int   mPointerCount;       // max pointers seen in this sequence
    private boolean mDidSwipe;         // moved far enough to be a swipe

    // --- double-tap state ---
    // mLastTapUpTime: timestamp of the most recent tap-up, or -1 if none pending.
    // mPendingSingleTap: delayed runnable for the single-tap action (DPAD_CENTER).
    //   On second tap within DOUBLE_TAP_MS the runnable is cancelled and BACK is fired instead.
    //   This prevents the first tap from triggering an unintended action before the double-tap
    //   is recognised, which made "double-tap = back" unreliable.
    private long     mLastTapUpTime    = -1;
    private Runnable mPendingSingleTap = null;

    // --- two-finger detection ---
    // Mercury does not send ACTION_POINTER_DOWN; it sends two separate ACTION_DOWN events.
    // We track the timestamp of the previous ACTION_DOWN; if a new ACTION_DOWN arrives
    // within TWO_FINGER_DOWN_MS we treat the pair as a two-finger tap.
    private long mLastDownTime = -1;

    public RayNeoGestureHandler(Activity activity) {
        mActivity = activity;
        // MOD diagnostics: log every focus change so auto-flash root cause can be identified.
        // Runs after onCreate so the decor view is already attached.
        mHandler.post(() -> {
            View decorView = mActivity.getWindow().getDecorView();
            decorView.getViewTreeObserver().addOnGlobalFocusChangeListener(
                    (oldFocus, newFocus) -> Log.d(TAG, "FOCUS_CHANGE "
                            + viewDesc(oldFocus) + " → " + viewDesc(newFocus)));
        });
    }

    /**
     * Call this from {@code Activity.dispatchTouchEvent(MotionEvent event)} BEFORE
     * calling {@code super.dispatchTouchEvent(event)}.
     *
     * @return {@code true} if the event was consumed (caller should skip super).
     */
    public boolean handleMotionEvent(MotionEvent event) {
        if (!isTempleDevice(event)) return false;

        // ALL events from the temple touchpad are consumed here and never forwarded
        // to the View hierarchy. This prevents RecyclerView/ScrollView from also
        // interpreting the same touch sequence as a fling/scroll.
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                long nowDown = SystemClock.uptimeMillis();
                InputDevice dev = event.getDevice();
                Log.d(TAG, "temple DOWN src=0x" + Integer.toHexString(event.getSource())
                        + " dev=" + (dev != null ? dev.getName() : "null")
                        + " x=" + event.getX() + " y=" + event.getY()
                        + " sincePrevDown=" + (mLastDownTime >= 0 ? (nowDown - mLastDownTime) : "n/a"));
                // Mercury sends two rapid ACTION_DOWN events instead of ACTION_POINTER_DOWN.
                // Detect this: if a second DOWN arrives within TWO_FINGER_DOWN_MS, treat it
                // as a two-finger gesture (mPointerCount = 2) and discard the second DOWN's
                // coordinates so the pending gesture still resolves as a tap.
                if (mLastDownTime >= 0 && (nowDown - mLastDownTime) < TWO_FINGER_DOWN_MS) {
                    mPointerCount = 2;
                    Log.d(TAG, "temple DOWN → two-finger detected (gap=" + (nowDown - mLastDownTime) + "ms)");
                    // Do NOT reset mDownX/Y: keep first finger's position so the gesture
                    // is evaluated as a tap at the original down point.
                } else {
                    mDownX        = event.getX();
                    mDownY        = event.getY();
                    mDownTime     = nowDown;
                    mPointerCount = 1;
                    mDidSwipe     = false;
                }
                mLastDownTime = nowDown;
                break;
            }

            case MotionEvent.ACTION_POINTER_DOWN:
                mPointerCount = Math.max(mPointerCount, event.getPointerCount());
                break;

            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - mDownX;
                float dy = event.getY() - mDownY;
                if (Math.abs(dx) > TAP_SLOP || Math.abs(dy) > TAP_SLOP) {
                    mDidSwipe = true;
                }
                break;
            }

            case MotionEvent.ACTION_UP: {
                float totalDx = event.getX() - mDownX;
                float totalDy = event.getY() - mDownY;
                float dist    = (float) Math.hypot(totalDx, totalDy);

                if (dist >= SWIPE_THRESHOLD) {
                    if (Math.abs(totalDx) >= Math.abs(totalDy)) {
                        fireKey(totalDx > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT);
                    } else {
                        fireKey(totalDy > 0 ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP);
                    }
                } else if (!mDidSwipe) {
                    if (mPointerCount >= 2) {
                        fireKey(KeyEvent.KEYCODE_MENU);
                    } else {
                        long now = SystemClock.uptimeMillis();
                        if (mLastTapUpTime >= 0 && (now - mLastTapUpTime) < DOUBLE_TAP_MS) {
                            // Second tap within window: cancel pending single-tap, fire BACK.
                            if (mPendingSingleTap != null) {
                                mHandler.removeCallbacks(mPendingSingleTap);
                                mPendingSingleTap = null;
                            }
                            mLastTapUpTime = -1;
                            fireKey(KeyEvent.KEYCODE_BACK);
                        } else {
                            // First tap: delay the DPAD_CENTER action so a quick second tap
                            // can cancel it and trigger BACK instead.
                            mLastTapUpTime = now;
                            mPendingSingleTap = () -> {
                                mPendingSingleTap = null;
                                fireKey(KeyEvent.KEYCODE_DPAD_CENTER);
                            };
                            mHandler.postDelayed(mPendingSingleTap, DOUBLE_TAP_MS);
                        }
                    }
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL:
                mDidSwipe = true; // suppress pending tap
                break;
        }
        return true; // always consume temple events — never let views see them
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Synthesise a DOWN+UP KeyEvent pair and dispatch it through the Activity's
     * normal key-dispatch chain.  This reaches Leanback's focus/navigation logic
     * without any special permissions and without looping back into this handler.
     *
     * <p>Strategy A – direct focus traversal: {@link View#focusSearch} + {@link View#requestFocus}.
     * Bypasses touch-mode / key-dispatch issues.  Used for all directional keys.</p>
     *
     * <p>Strategy B – sidebar open (DPAD_LEFT only): when the Leanback headers dock is
     * {@code INVISIBLE} (sidebar hidden), {@code requestFocus} on the headers grid fails
     * because {@link View#isShown()} returns false through the invisible ancestor.  We
     * detect this case and call {@link BrowseSupportFragment#startHeadersTransition(boolean)}
     * directly to make the dock visible, then focus the headers grid after the animation.</p>
     *
     * <p>Strategy C – key-event injection: last resort fallback.</p>
     */
    private void fireKey(int keyCode) {
        Log.d(TAG, "fireKey keyCode=" + keyCode + " (" + KeyEvent.keyCodeToString(keyCode) + ")");
        mHandler.post(() -> {
            // --- strategy A11y: AccessibilityService navigation (bypasses touch mode entirely) ---
            RayNeoA11yService a11y = RayNeoA11yService.getInstance();
            if (a11y != null && tryA11yNavigation(a11y, keyCode)) {
                return;
            }

            // --- diagnostic: log current focus state (only when A11y not handling) ---
            View decorView = mActivity.getWindow().getDecorView();
            View focused = decorView.findFocus();
            Log.d(TAG, "fireKey[post] a11y=" + (a11y != null ? "connected" : "null")
                    + " focused=" + viewDesc(focused));

            // --- strategy A: direct focus traversal ---
            if (tryDirectFocusTraversal(keyCode, focused)) {
                return;
            }

            // --- strategy B: DPAD_LEFT → open Leanback browse sidebar ---
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && tryOpenBrowseSidebar()) {
                return;
            }

            // --- strategy C: key-event injection (last resort; also used for MENU) ---
            long now = SystemClock.uptimeMillis();
            boolean downHandled = mActivity.dispatchKeyEvent(
                    new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
            boolean upHandled   = mActivity.dispatchKeyEvent(
                    new KeyEvent(now, now, KeyEvent.ACTION_UP,   keyCode, 0));
            Log.d(TAG, "fireKey[inject] down=" + downHandled + " up=" + upHandled);
        });
    }

    /**
     * Handle the key via {@link RayNeoA11yService}, which uses the Accessibility Node API
     * and therefore bypasses Android touch-mode restrictions on {@code requestFocus()}.
     *
     * <p>Returns {@code false} for keys with no A11y equivalent (e.g. MENU) so that the
     * caller falls through to Strategy C (key injection).</p>
     *
     * <p>Also returns {@code false} when A11y navigation finds no next node (e.g. when
     * the Leanback sidebar is hidden and DPAD_LEFT needs Strategy B to open it).</p>
     */
    private boolean tryA11yNavigation(RayNeoA11yService a11y, int keyCode) {
        boolean ok;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:     ok = a11y.navigate(View.FOCUS_UP);    break;
            case KeyEvent.KEYCODE_DPAD_DOWN:   ok = a11y.navigate(View.FOCUS_DOWN);  break;
            case KeyEvent.KEYCODE_DPAD_LEFT:   ok = a11y.navigate(View.FOCUS_LEFT);  break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:  ok = a11y.navigate(View.FOCUS_RIGHT); break;
            case KeyEvent.KEYCODE_DPAD_CENTER: ok = a11y.click();                    break;
            case KeyEvent.KEYCODE_BACK:        ok = a11y.back();                     break;
            default: return false; // MENU etc. — fall through to key injection
        }
        Log.d(TAG, "tryA11y keyCode=" + KeyEvent.keyCodeToString(keyCode) + " ok=" + ok);
        return ok;
    }

    /**
     * Strategy B for DPAD_LEFT: find the {@link BrowseSupportFragment} in the current
     * activity and open its side-navigation panel (headers).
     *
     * <p>The Leanback headers dock is {@code INVISIBLE} while the sidebar is hidden;
     * a plain {@code requestFocus} on the headers grid fails because {@code isShown()}
     * returns false through an invisible ancestor.  We call
     * {@link BrowseSupportFragment#startHeadersTransition(boolean)} to make the dock
     * visible and start the slide-in animation, then post a 250 ms delayed runnable
     * to request focus on the headers grid once the transition is underway.</p>
     *
     * <p>If the sidebar is already visible, {@code startHeadersTransition(true)} is a
     * no-op (Leanback guards against double-transitions) and the delayed focus simply
     * moves the cursor into the already-visible sidebar – which is correct behaviour
     * for a left swipe when the sidebar is already open.</p>
     *
     * @return {@code true} if we initiated the transition (caller should skip Strategy C)
     */
    private boolean tryOpenBrowseSidebar() {
        if (!(mActivity instanceof FragmentActivity)) return false;
        Fragment f = ((FragmentActivity) mActivity)
                .getSupportFragmentManager()
                .findFragmentById(R.id.main_frame);
        if (!(f instanceof BrowseSupportFragment)) {
            Log.d(TAG, "tryOpenBrowseSidebar: no BrowseSupportFragment at main_frame");
            return false;
        }
        BrowseSupportFragment browse = (BrowseSupportFragment) f;
        HeadersSupportFragment headers = browse.getHeadersSupportFragment();
        VerticalGridView grid = headers != null ? headers.getVerticalGridView() : null;

        // Capture visibility BEFORE the transition so we know whether the sidebar was already
        // open.  If it was already open, Strategy A has already handled focus correctly and we
        // must NOT post a delayed focus request — that would cause a "flash" (sidebar closes
        // and reopens) if the user navigates away within the 400 ms window.
        boolean sidebarWasShowing = grid != null && grid.isShown();

        try {
            browse.startHeadersTransition(true);
            Log.d(TAG, "tryOpenBrowseSidebar: startHeadersTransition(true) called"
                    + " wasShowing=" + sidebarWasShowing);
        } catch (Exception e) {
            Log.d(TAG, "tryOpenBrowseSidebar: startHeadersTransition failed: " + e);
            return false;
        }

        if (sidebarWasShowing || grid == null) {
            // Already visible — Strategy A already placed focus correctly; nothing more to do.
            return true;
        }

        // Sidebar was hidden: wait for the full transition (150 ms start-delay + 250 ms
        // duration = 400 ms) then focus the first child item.  Guard against the user having
        // navigated away by checking isShown() before focusing.
        final VerticalGridView gridRef = grid;
        mHandler.postDelayed(() -> {
            if (!gridRef.isShown()) {
                Log.d(TAG, "tryOpenBrowseSidebar[delayed]: grid no longer shown, skip focus");
                return;
            }
            View firstChild = gridRef.getChildAt(0);
            if (firstChild != null) {
                boolean ok = firstChild.requestFocus();
                if (!ok) ok = firstChild.requestFocusFromTouch();
                Log.d(TAG, "tryOpenBrowseSidebar[delayed]: firstChild="
                        + firstChild.getClass().getSimpleName() + " ok=" + ok);
            } else {
                boolean ok = gridRef.requestFocus();
                Log.d(TAG, "tryOpenBrowseSidebar[delayed]: no child; grid ok=" + ok);
            }
        }, 400);
        return true;
    }

    /**
     * For directional key codes, attempts to move focus directly via
     * {@link View#focusSearch}/{@link View#requestFocus} without going through
     * the key-event dispatch pipeline.
     *
     * @param keyCode  one of KEYCODE_DPAD_UP/DOWN/LEFT/RIGHT (others are ignored)
     * @param focused  the currently focused view, or {@code null}
     * @return {@code true} if focus was successfully moved (caller should skip injection)
     */
    private boolean tryDirectFocusTraversal(int keyCode, View focused) {
        int direction;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:    direction = View.FOCUS_UP;    break;
            case KeyEvent.KEYCODE_DPAD_DOWN:  direction = View.FOCUS_DOWN;  break;
            case KeyEvent.KEYCODE_DPAD_LEFT:  direction = View.FOCUS_LEFT;  break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: direction = View.FOCUS_RIGHT; break;
            default: return false; // not a directional key; caller will inject
        }

        if (focused == null) {
            Log.d(TAG, "tryDirectFocus: no focused view → falling back to injection");
            return false;
        }

        View next = focused.focusSearch(direction);
        Log.d(TAG, "tryDirectFocus: from=" + focused.getClass().getSimpleName()
                + " next=" + (next == null ? "null" : next.getClass().getSimpleName()
                              + "#" + Integer.toHexString(next.getId())));

        if (next != null && next != focused) {
            boolean ok = next.requestFocus(direction);
            // NOTE: requestFocusFromTouch() is intentionally NOT used here.
            // It calls ViewRootImpl.leaveTouchMode() globally, which fires onTouchModeChanged
            // across the entire view tree. That side-effect triggers Leanback's internal focus
            // listeners and causes the browse sidebar to open/close spontaneously (the "flash"
            // bug). Views that must receive focus from the temple touchpad must instead be made
            // focusableInTouchMode=true explicitly (e.g. IconHeaderItemPresenter, ErrorDialogFragment).
            Log.d(TAG, "tryDirectFocus: requestFocus=" + ok + " next=" + viewDesc(next));
            return ok;
        }

        Log.d(TAG, "tryDirectFocus: focusSearch found nothing → falling back to injection");
        return false;
    }

    /** Short description of a view for log messages. */
    private static String viewDesc(View v) {
        if (v == null) return "null";
        return v.getClass().getSimpleName() + "#" + Integer.toHexString(v.getId());
    }

    /**
     * Returns true if {@code event} originates from the RayNeo temple touchpad.
     * Mercury re-sources touchpad events as MOUSE before forwarding to the app,
     * so we detect by source first and fall back to device-name matching.
     */
    private static boolean isTempleDevice(MotionEvent event) {
        // Mercury maps temple events to SOURCE_MOUSE (0x2002) or SOURCE_TOUCHSCREEN|SOURCE_MOUSE (0x5002)
        int src = event.getSource();
        if ((src & InputDevice.SOURCE_MOUSE) != 0) {
            return true;
        }
        // Fallback: match raw device name for cyttsp / capsense
        InputDevice device = event.getDevice();
        if (device != null) {
            String name = device.getName().toLowerCase();
            return name.contains("cyttsp") || name.contains("capsense");
        }
        return false;
    }
}
