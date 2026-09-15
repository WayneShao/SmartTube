package com.liskovsoft.smartyoutubetv2.common.rayneo;

import org.junit.Test;
import static org.junit.Assert.*;

public class CursorStateTest {
    @Test public void movesInBothAxesAndStopsAtEyeEdges() {
        CursorState cursor = new CursorState();
        cursor.resize(640, 480);
        assertEquals(320, cursor.x(), 0);
        assertEquals(240, cursor.y(), 0);
        cursor.move(100, -100);
        assertEquals(420, cursor.x(), 0);
        assertEquals(140, cursor.y(), 0);
        cursor.move(1000, -1000);
        assertEquals(639, cursor.x(), 0);
        assertEquals(0, cursor.y(), 0);
        cursor.move(-1000, 1000);
        assertEquals(0, cursor.x(), 0);
        assertEquals(479, cursor.y(), 0);
    }

    @Test public void preservesWindowLocalPositionAndClampsOnResize() {
        CursorState cursor = new CursorState();
        cursor.resize(640, 480);
        cursor.move(200, 100);
        cursor.resize(640, 480);
        assertEquals(520, cursor.x(), 0);
        cursor.resize(400, 300);
        assertEquals(399, cursor.x(), 0);
        assertEquals(299, cursor.y(), 0);
        CursorState otherWindow = new CursorState();
        otherWindow.resize(640, 480);
        assertEquals(320, otherWindow.x(), 0);
    }
}
