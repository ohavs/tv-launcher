package com.ohav.tvhome.camera;

/** Player state from the :camera process. state: 1 connecting, 2 playing, 3 error. */
oneway interface ICameraListener {
    void onState(int state, String message);
    void onVideoSize(int width, int height);
}
