package com.anton.elementalwands.util;

/** Transient movement mirrored to the affected client, never persisted. */
public interface StoneMotionAccess {
    void elementalwands$stoneMotion(float yaw, float speed, int mode);
    boolean elementalwands$stoneInterrupted();
}
