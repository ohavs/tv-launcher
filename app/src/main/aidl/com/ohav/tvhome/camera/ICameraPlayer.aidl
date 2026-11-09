package com.ohav.tvhome.camera;

import android.view.Surface;
import com.ohav.tvhome.camera.ICameraListener;
import com.ohav.tvhome.camera.IMotionListener;

/** Renders RTSP streams into surfaces owned by the launcher process; one player per slot. */
interface ICameraPlayer {
    void play(int slot, in Surface surface, String url, ICameraListener listener);
    /** Returns once the slot's surface is no longer used. */
    void stop(int slot);
    /** Watches these streams for motion (no decoding) until stopMotion or unbind. */
    void watchMotion(in String[] urls, in int[] channels, IMotionListener listener);
    void stopMotion();
}
