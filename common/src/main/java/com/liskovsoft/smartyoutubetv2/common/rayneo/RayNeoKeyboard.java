package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.content.Context;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import com.liskovsoft.sharedutils.helpers.Helpers;
import io.github.wayneshao.rayneo.ime.EmbeddedInput;
import io.github.wayneshao.rayneo.ime.NativeEditor;

/** SmartTube's actual editor buffers stay in their original windows. */
public final class RayNeoKeyboard {
    private RayNeoKeyboard() { }
    private static final java.util.WeakHashMap<EditText, PendingShow> pending = new java.util.WeakHashMap<>();
    private static final class PendingShow implements Runnable {
        final java.lang.ref.WeakReference<EditText> owner;
        final long deadline = android.os.SystemClock.uptimeMillis() + 1500;
        PendingShow(EditText editor) { owner = new java.lang.ref.WeakReference<>(editor); }
        @Override public void run() {
            EditText editor = owner.get();
            if (editor == null || pending.get(editor) != this) return;
            if (android.os.SystemClock.uptimeMillis() >= deadline || !editor.isEnabled()) {
                pending.remove(editor); return;
            }
            if (!editor.isAttachedToWindow() || !editor.hasWindowFocus()) {
                editor.postDelayed(this, 50); return;
            }
            pending.remove(editor);
            if (editor.isShown() && editor.hasFocus()) open(editor);
        }
    }
    public static void install(Context context) {
        if (!RayNeo.isEnabled(context)) return;
        Helpers.setKeyboardPresenter(new Helpers.KeyboardPresenter() {
            public boolean show(View view) { return open(view); }
            public boolean hide(View view) {
                if (view == null || !RayNeo.isEnabled(view.getContext())) return false;
                View root = view.getRootView();
                java.util.Iterator<EditText> requests = pending.keySet().iterator();
                while (requests.hasNext()) {
                    EditText editor = requests.next();
                    if (editor != null && (editor == view || editor.getRootView() == root)) requests.remove();
                }
                EmbeddedInput.closeWindow(view);
                return true;
            }
        });
    }
    public static EmbeddedInput.Host host() { return KeyboardWindow::install; }
    public static boolean open(View view) {
        if (!(view instanceof EditText) || !RayNeo.isEnabled(view.getContext())) return false;
        EditText editor = (EditText) view;
        editor.setShowSoftInputOnFocus(false);
        if (EmbeddedInput.isShowing(editor)) return true;
        if (!editor.isAttachedToWindow() || !editor.hasWindowFocus()) {
            if (!pending.containsKey(editor)) {
                PendingShow request = new PendingShow(editor);
                pending.put(editor, request);
                editor.post(request);
            }
            return true;
        }
        if (!editor.isShown() || !editor.isEnabled()) return false;
        editor.requestFocusFromTouch();
        boolean shown = EmbeddedInput.show(new NativeEditor(editor), host());
        android.util.Log.d("SmartTubeRayNeoInput", "keyboard request editor=" + editor.getClass().getSimpleName() + " shown=" + shown);
        return shown;
    }
    static void bind(View view) {
        if (view instanceof EditText) ((EditText) view).setShowSoftInputOnFocus(false);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) bind(group.getChildAt(i));
        }
    }
    static boolean key(View root, KeyEvent event) {
        View focus = root.findFocus();
        if (!(focus instanceof EditText) || event.getKeyCode() != KeyEvent.KEYCODE_DPAD_CENTER) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) open(focus);
        return true;
    }
    static void touch(View root, MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_UP || event.getPointerCount() != 1) return;
        View focus = root.findFocus();
        if (!(focus instanceof EditText)) return;
        Rect rect = new Rect();
        float x = event.getRawX();
        Rect host = new Rect();
        if (root instanceof StereoLayout && root.getGlobalVisibleRect(host) && x >= host.left + root.getWidth() / 2f)
            x -= root.getWidth() / 2f;
        if (focus.getGlobalVisibleRect(rect) && rect.contains((int) x, (int) event.getRawY())) open(focus);
    }
}
