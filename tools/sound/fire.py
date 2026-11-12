"""Fire spell sounds. Shared voice: a pitch-dropping thump, a noise rush that
brightens and fades, a fluttering low roar, and ringing sparks.

Sparks stop while the fire under them is still loud. A spark that outlasts the
body is heard as a stray high tick on the tail."""
from __future__ import annotations

import numpy as np

from synth import (band, bang, clock, crackle, curve, decay, finish, flutter, noise, place, repitch,
                   reverb, svf, sweep, thump, unit, vary)


def roar(rng, seconds: float, amp: np.ndarray, top: float = 250, speed: float = 23, depth: float = 0.5):
    return unit(band(noise(rng, seconds, -3), hi=top) * amp * flutter(rng, seconds, speed, depth))


def inferno_wave_cast(rng):
    d = 0.85
    t = clock(d)
    peak = vary(rng, 0.14, 0.15)
    centre = 480 + vary(rng, 2700) * np.exp(-((t - peak) / 0.13) ** 2) + 300 * t
    rush = unit(svf(noise(rng, d), centre, 1.6) * decay(d, 0.08, 5.8))
    body = roar(rng, d, decay(d, 0.05, 4.2), 240, 23, 0.5)
    hit = thump(d, vary(rng, 150), 45, 18, 14)
    sparks = unit(crackle(rng, d, 0.08, 0.4, 22, 8.0))
    mix = 1.0 * rush + 0.3 * body + 0.65 * hit + 0.6 * sparks
    return finish(reverb(rng, mix, 0.45, 0.17), drive=1.5)


def inferno_wave_hit(rng):
    d = 0.45
    t = clock(d)
    sear = unit(svf(noise(rng, d), vary(rng, 2600) * np.exp(-9 * t) + 500, 1.2) * decay(d, 0.003, 13))
    sizzle = unit(band(noise(rng, d), lo=4500, hi=9500) * decay(d, 0.01, 9) * flutter(rng, d, 40, 0.6))
    hit = thump(d, vary(rng, 130), 55, 30, 22)
    return finish(1.0 * sear + 0.4 * sizzle + 0.6 * hit, drive=1.3)


def dragons_pyre_cast(rng):
    d = 1.75
    t = clock(d)
    swell = curve(d, [(0, 0), (0.28, 1), (0.85, 0.5), (d, 0)], 1.4)
    rush = unit(svf(noise(rng, d, -2), 320 + 1500 * np.exp(-((t - 0.38) / 0.3) ** 2), 1.3) * swell)
    # Two resonant bands and a 31 Hz rasp give the breath a throat.
    breath = noise(rng, d, -3) * (1 + 0.6 * np.sin(2 * np.pi * 31 * t))
    growl = unit((svf(breath, 430, 4.0) + 0.7 * svf(breath, 1050, 5.0))
                 * curve(d, [(0, 0), (0.18, 1), (0.9, 0.45), (1.6, 0), (d, 0)], 1.3))
    body = roar(rng, d, swell, 200, 14, 0.6)
    hit = thump(d, 95, 36, 9, 6, attack=0.02)
    sparks = unit(crackle(rng, d, 0.3, 1.0, 40, 3.5))
    mix = 0.9 * rush + 0.7 * growl + 0.45 * body + 0.7 * hit + 0.55 * sparks
    return finish(reverb(rng, mix, 0.9, 0.22, bright=2600), drive=1.6)


def flamethrower_loop(rng):
    # Retriggered every 0.4 s while spraying; the long fade lets copies overlap.
    d = 0.9
    amp = curve(d, [(0, 0), (0.09, 1), (0.55, 1), (d, 0)])
    jet = unit(svf(noise(rng, d, -1), vary(rng, 1100) * flutter(rng, d, 9, 0.25), 0.9))
    hiss = unit(band(noise(rng, d), lo=3800, hi=8500))
    low = unit(band(noise(rng, d, -3), hi=220))
    mix = (0.8 * jet * flutter(rng, d, 26, 0.35) + 0.22 * hiss * flutter(rng, d, 35, 0.4)
           + 0.5 * low * flutter(rng, d, 17, 0.5)) * amp
    return finish(mix, drive=1.1, tail=0.01)


def flamethrower_overheat(rng):
    d = 1.15
    choke = thump(d, 120, 44, 22, 16)
    gate = sum(level * decay(d, 0.004, 38, start) for start, level in
               ((0.02, 1.0), (0.11, 0.8), (0.19, 0.6), (0.3, 0.4)))
    sputter = unit(svf(noise(rng, d, -1), 900, 1.0) * gate)
    steam = unit(band(noise(rng, d), lo=2800, hi=11000) * decay(d, 0.06, 3.6, 0.12) * flutter(rng, d, 40, 0.3))
    mix = 0.6 * choke + 0.8 * sputter + 0.9 * steam
    return finish(reverb(rng, mix, 0.3, 0.1), drive=1.2)


def flashover_cast(rng):
    d = 0.34
    t = clock(d)
    flick = unit(svf(noise(rng, d), 800 + vary(rng, 1900) * np.exp(-((t - 0.09) / 0.07) ** 2), 2.2)
                 * decay(d, 0.05, 14))
    spark = unit(band(noise(rng, d), lo=2500, hi=8000) * decay(d, 0.002, 45))
    ember = unit(crackle(rng, d, 0.03, 0.16, 6, 14.0, centre=4600))
    return finish(0.9 * flick + 0.5 * spark + 0.5 * ember, drive=1.1)


def flashover_blast(rng):
    d = 0.85
    t = clock(d)
    crack = bang(rng, d, 6500, 16, 11, 280)
    hit = thump(d, vary(rng, 190), 52, 24, 12)
    burst = unit(svf(noise(rng, d, -1), 500 + 2200 * np.exp(-((t - 0.06) / 0.09) ** 2), 1.4)
                 * decay(d, 0.03, 8))
    sparks = unit(crackle(rng, d, 0.05, 0.3, 22, 12.0))
    mix = 1.0 * crack + 0.9 * hit + 0.6 * burst + 0.6 * sparks
    return finish(reverb(rng, mix, 0.5, 0.18), drive=1.8)


def fire_leap_launch(rng):
    d = 1.05
    t = clock(d)
    hit = thump(d, 170, 44, 18, 11)
    blast = bang(rng, d, 5000, 20, 16, 300)
    # The band climbs as the caster leaves the ground.
    jet = unit(svf(noise(rng, d, -1), 380 + 2400 * (t / d) ** 0.7, 1.8)
               * curve(d, [(0, 0), (0.06, 1), (0.35, 0.7), (d, 0)], 1.6))
    body = roar(rng, d, decay(d, 0.03, 4.0), 250, 22, 0.5)
    sparks = unit(crackle(rng, d, 0.05, 0.45, 18, 6.0))
    mix = 0.9 * hit + 0.8 * blast + 0.9 * jet + 0.35 * body + 0.5 * sparks
    return finish(reverb(rng, mix, 0.5, 0.15), drive=1.5)


def fire_leap_land(rng):
    d = 1.2
    t = clock(d)
    hit = thump(d, 125, 34, 16, 6.5)
    crack = bang(rng, d, 5500, 22, 20, 250)
    # The band falls as the flame ring spreads outward.
    ring = unit(svf(noise(rng, d, -1), vary(rng, 2200) * np.exp(-4.5 * t) + 320, 1.5) * decay(d, 0.04, 4.2))
    body = roar(rng, d, decay(d, 0.03, 3.2), 230, 20, 0.5)
    sparks = unit(crackle(rng, d, 0.06, 0.45, 26, 7.0))
    mix = 1.1 * hit + 0.9 * crack + 0.8 * ring + 0.4 * body + 0.55 * sparks
    return finish(reverb(rng, mix, 0.7, 0.22, bright=2400), drive=1.9)


def meteor_cast(rng):
    d = 3.4
    t = clock(d)
    crack = unit(band(noise(rng, d, -2), hi=1100) * decay(d, 0.004, 5))
    voices = sum(sweep(np.full(len(t), f) * (1 + 0.004 * np.sin(2 * np.pi * 0.7 * t + i)))
                 for i, f in enumerate((55.0, 55.7, 82.6, 110.4)))
    drone = unit(np.tanh(voices * 0.4) * curve(d, [(0, 0), (0.7, 1), (2.4, 0.8), (d, 0)])
                 * (1 + 0.25 * np.sin(2 * np.pi * 5.5 * t)))
    # A narrow band sliding down the sky: the meteor's approach.
    howl = unit(svf(noise(rng, d, -1), 2600 * np.exp(-1.0 * t) + 260, 5.0)
                * curve(d, [(0, 0), (0.4, 0.25), (2.6, 1.0), (3.2, 0.6), (d, 0)], 1.4))
    rumble = unit(band(noise(rng, d, -5), hi=160) * curve(d, [(0, 0.1), (2.8, 1), (d, 0)], 1.5)
                  * flutter(rng, d, 11, 0.6))
    mix = 0.8 * crack + 0.6 * drone + 0.8 * howl + 0.7 * rumble
    return finish(reverb(rng, mix, 1.6, 0.35, bright=3500), drive=1.3, tail=0.08)


def meteor_impact_boom(rng):
    """The close, detailed impact."""
    d = 3.0
    t = clock(d)
    crack = bang(rng, d, 6800, 9, 5.5, 180)
    sub = thump(d, 100, 27, 7, 2.6, attack=0.003) + 0.4 * thump(d, 60, 33, 5, 3.4)
    rumble = roar(rng, d, decay(d, 0.02, 2.0), 300, 9, 0.6)
    fire = unit(svf(noise(rng, d, -2), 1800 * np.exp(-2.2 * t) + 350, 1.2) * decay(d, 0.05, 2.6)
                * flutter(rng, d, 20, 0.4))
    debris = unit(crackle(rng, d, 0.15, 0.85, 70, 4.5, centre=2600, q=3.0))
    # Low-passed: each thud's click edge would otherwise tick in the quiet tail.
    thuds = unit(band(crackle(rng, d, 0.2, 2.2, 22, 1.2, centre=420, q=4.0), hi=1100))
    mix = 1.0 * crack + 1.2 * sub + 0.8 * rumble + 0.7 * fire + 0.5 * debris + 0.5 * thuds
    return finish(reverb(rng, mix, 1.8, 0.3, bright=2800, predelay=0.02), drive=2.4, tail=0.1)


def meteor_impact_blast(rng):
    """The explosion's own sound: a low roll that carries 64 blocks.

    Minecraft plays explosion sounds at about 0.7 pitch, so this is pre-shifted.
    """
    d = 3.2
    sub = thump(d, 70, 26, 5, 2.2, attack=0.01)
    roll = unit(band(noise(rng, d, -6), hi=220) * decay(d, 0.03, 1.6) * flutter(rng, d, 5, 0.8))
    rolling = place(d, (0, 1.0, roll), (0.35, 0.5, roll), (0.8, 0.3, roll))
    mid = unit(band(noise(rng, d, -3), lo=150, hi=700) * decay(d, 0.01, 5))
    mix = 1.0 * sub + 0.9 * rolling + 0.35 * mid
    return repitch(finish(reverb(rng, mix, 1.5, 0.4, bright=900), drive=1.8, tail=0.1), 0.7)


# name under assets/elementalwands/sounds -> (recipe, number of variants)
SOUNDS = {
    "spell/fire/inferno_wave_cast": (inferno_wave_cast, 3),
    "spell/fire/inferno_wave_hit": (inferno_wave_hit, 3),
    "spell/fire/dragons_pyre_cast": (dragons_pyre_cast, 2),
    "spell/fire/flamethrower_loop": (flamethrower_loop, 3),
    "spell/fire/flamethrower_overheat": (flamethrower_overheat, 1),
    "spell/fire/flashover_cast": (flashover_cast, 3),
    "spell/fire/flashover_blast": (flashover_blast, 3),
    "spell/fire/fire_leap_launch": (fire_leap_launch, 2),
    "spell/fire/fire_leap_land": (fire_leap_land, 2),
    "spell/fire/meteor_cast": (meteor_cast, 1),
    "spell/fire/meteor_impact_boom": (meteor_impact_boom, 2),
    "spell/fire/meteor_impact_blast": (meteor_impact_blast, 2),
}
