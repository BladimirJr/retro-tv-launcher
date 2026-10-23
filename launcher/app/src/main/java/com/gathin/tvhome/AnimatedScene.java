package com.gathin.tvhome;

/** An animated Home backdrop (a theme's "scene"): runs only between start and stop, while Home is visible. */
interface AnimatedScene {
    void start();

    void stop();
}
