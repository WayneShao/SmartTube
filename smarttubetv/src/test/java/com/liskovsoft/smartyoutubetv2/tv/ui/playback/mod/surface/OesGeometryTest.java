package com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface;

import org.junit.Test;
import static org.junit.Assert.*;

public class OesGeometryTest {
    @Test public void centeredWideVideoUsesWholeSourceInEachEye() {
        float[] result = OesGeometry.vertices(new float[]{0,420,640,420,0,60,640,60},640,480);
        assertArrayEquals(new float[]{-1,-.75f,0,0, 1,-.75f,1,0, -1,.75f,0,1, 1,.75f,1,1},result,.0001f);
    }
    @Test public void mirroredAndRotatedLayoutPreservesTextureCorners() {
        float[] result=OesGeometry.vertices(new float[]{500,80,500,400,140,80,140,400},640,480);
        assertEquals(.5625f,result[0],.0001f);
        assertEquals(2f/3,result[1],.0001f);
        assertEquals(-2f/3,result[5],.0001f);
        assertEquals(-.5625f,result[8],.0001f);
        assertEquals(0,result[2],0); assertEquals(1,result[6],0);
    }
    @Test public void invalidSizesProduceNoGeometry() {
        assertNull(OesGeometry.vertices(new float[8],0,480));
        assertNull(OesGeometry.vertices(new float[8],640,0));
    }
}
