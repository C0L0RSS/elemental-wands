package com.anton.elementalwands.entity;

/** Per-boss Nature pressure. Absolute times never alter a committed attack's timeline. */
public final class GuardianNatureResponse {
    public static final int EXTRA_RECOVERY = 20, RESTRAINT_COOLDOWN = 200, CLEAR_COOLDOWN = 160;
    private long restraintReady, clearReady, lastThorn = Long.MIN_VALUE, firstThorn;
    private boolean pending;

    public void entangle(int stacks, long now) {
        if (stacks >= 5 && now >= restraintReady) pending = true;
    }
    public int finishAttack(long now) {
        if (!pending) return 0;
        pending = false;
        restraintReady = now + RESTRAINT_COOLDOWN;
        return EXTRA_RECOVERY;
    }
    public void thorn(long now) {
        if (lastThorn == Long.MIN_VALUE || now - lastThorn > 40) firstThorn = now;
        lastThorn = now;
    }
    public boolean wantsClear(long now) {
        return lastThorn != Long.MIN_VALUE && now - lastThorn <= 40
                && now - firstThorn >= 40 && now >= clearReady;
    }
    public void clearing(long now) { clearReady = now + CLEAR_COOLDOWN; }
    public void reset() {
        pending = false; restraintReady = clearReady = 0; lastThorn = Long.MIN_VALUE;
    }
}
