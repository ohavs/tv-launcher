package com.ohav.tvhome.camera;

/** Motion on a camera channel started (true) or settled (false). */
oneway interface IMotionListener {
    void onMotion(int channel, boolean moving);
}
