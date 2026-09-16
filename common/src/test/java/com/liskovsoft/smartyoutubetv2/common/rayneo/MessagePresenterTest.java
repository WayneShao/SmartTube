package com.liskovsoft.smartyoutubetv2.common.rayneo;

import android.content.Context;
import android.os.Looper;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Public upstream message API, including ordering across native/presenter paths. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE)
public class MessagePresenterTest {
    private final Context context = RuntimeEnvironment.getApplication();

    private static class Presenter implements MessageHelpers.MessagePresenter {
        String message;
        boolean isLong;
        boolean active;
        boolean onMain;
        boolean consume = true;
        boolean fail;
        Context receivedContext;
        @Override public boolean show(Context context, String text, boolean longer) {
            onMain = Looper.myLooper() == Looper.getMainLooper();
            receivedContext = context;
            message = text;
            isLong = longer;
            if (fail) throw new IllegalStateException("presenter failure");
            active = consume;
            return consume;
        }
        @Override public void cancel() {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            active = false;
        }
    }

    @Before public void reset() {
        MessageHelpers.setMessagePresenter(null);
        ShadowToast.reset();
    }

    @After public void cleanup() { MessageHelpers.setMessagePresenter(null); }

    @Test public void originalOverloadsRouteToPresenterWithoutNativeToast() {
        Presenter presenter = new Presenter();
        MessageHelpers.setMessagePresenter(presenter);
        MessageHelpers.showLongMessage(context, "value %s", "42");
        assertEquals("value 42", presenter.message);
        assertTrue(presenter.isLong);
        assertTrue(presenter.onMain);
        assertSame(context, presenter.receivedContext);
        assertEquals(0, ShadowToast.shownToastCount());
        MessageHelpers.showMessage(context, android.R.string.ok);
        assertEquals(context.getString(android.R.string.ok), presenter.message);
        assertFalse(presenter.isLong);
        MessageHelpers.cancelToasts();
        assertFalse(presenter.active);
    }

    @Test public void workerShowAndCancelAreOrderedOnMainThread() throws Exception {
        Presenter presenter = new Presenter();
        MessageHelpers.setMessagePresenter(presenter);
        Thread worker = new Thread(() -> MessageHelpers.showMessage(context, "worker"));
        worker.start(); worker.join();
        assertNull(presenter.message);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("worker", presenter.message);
        assertTrue(presenter.onMain);
        Thread cancel = new Thread(MessageHelpers::cancelToasts);
        cancel.start(); cancel.join();
        assertTrue(presenter.active);
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(presenter.active);
    }

    @Test public void missingDecliningAndFailingPresentersKeepNativeFallback() {
        MessageHelpers.showMessage(context, "ordinary");
        assertEquals("ordinary", ShadowToast.getTextOfLatestToast());
        Presenter presenter = new Presenter();
        presenter.consume = false;
        MessageHelpers.setMessagePresenter(presenter);
        MessageHelpers.showMessage(context, "no foreground window");
        assertEquals("no foreground window", ShadowToast.getTextOfLatestToast());
        presenter.fail = true;
        MessageHelpers.showMessage(context, "failed renderer");
        assertEquals("failed renderer", ShadowToast.getTextOfLatestToast());
    }

    @Test public void oldNativeCleanupCannotCancelNewPresenterMessage() {
        Presenter presenter = new Presenter();
        presenter.consume = false;
        MessageHelpers.setMessagePresenter(presenter);
        MessageHelpers.showMessage(context, "native");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4));
        presenter.consume = true;
        MessageHelpers.showMessage(context, "stereo");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertTrue(presenter.active);
        assertEquals(1, ShadowToast.shownToastCount());
    }

    @Test public void replacementAndFallbackDismissPreviousPresentation() {
        Presenter first = new Presenter();
        Presenter second = new Presenter();
        MessageHelpers.setMessagePresenter(first);
        MessageHelpers.showMessage(context, "first");
        MessageHelpers.setMessagePresenter(second);
        assertFalse(first.active);
        MessageHelpers.showMessage(context, "second");
        assertTrue(second.active);
        second.consume = false;
        MessageHelpers.showMessage(context, "fallback");
        assertFalse(second.active);
        assertEquals("fallback", ShadowToast.getTextOfLatestToast());
        MessageHelpers.setMessagePresenter(null);
        MessageHelpers.showMessage(context, "unregistered");
        assertEquals("unregistered", ShadowToast.getTextOfLatestToast());
    }

    @Test public void throwingCancellationCannotBlockReplacementOrNativeFallback() {
        Presenter broken = new Presenter() {
            @Override public void cancel() {
                super.cancel();
                throw new IllegalStateException("cleanup failure");
            }
        };
        MessageHelpers.setMessagePresenter(broken);
        MessageHelpers.showMessage(context, "custom");
        assertTrue(broken.active);
        MessageHelpers.cancelToasts();
        assertFalse(broken.active);
        broken.consume = false;
        MessageHelpers.showMessage(context, "fallback after cleanup failure");
        assertEquals("fallback after cleanup failure", ShadowToast.getTextOfLatestToast());
        Presenter replacement = new Presenter();
        MessageHelpers.setMessagePresenter(replacement);
        MessageHelpers.showMessage(context, "replacement");
        assertTrue(replacement.active);
        assertEquals("replacement", replacement.message);
    }
}
