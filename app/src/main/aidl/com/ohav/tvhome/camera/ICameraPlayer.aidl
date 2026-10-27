package com.ohav.tvhome.camera;

import android.view.Surface;
import com.ohav.tvhome.camera.ICameraListener;

/** Renders an RTSP stream into a surface owned by the launcher process. */
interface ICameraPlayer {
    void play(in Surface surface, String url, ICameraListener listener);
    /** Returns once the surface is no longer used. */
    void stop();
}
