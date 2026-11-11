"""Stone spell sounds. Shared voice: deep earth. Boulders knocking together with
a low hollow clonk, sub-heavy thuds, slow grinding, and rock crumbling in the
low mids. Nothing bright: cracks are dull and close at once.

Stone is the deepest element on purpose, so it cannot be confused with Nature.
The clonk and the crumble sit between 150 and 900 Hz so the set still reads on
small speakers that cannot play the sub."""
from __future__ import annotations

import numpy as np

import synth
from synth import (band, bang, clock, crackle, curve, decay, flutter, noise, place, reverb, svf, sweep, thump, unit,
                   vary)


def finish(x: np.ndarray, **options) -> np.ndarray:
    """Stone's master balance, then the shared finish.

    The thuds carry far more energy than the clonks and crumbling, and a mix
    left like that is nearly all sub: silent on small speakers and shapeless on
    large ones. This pulls the sub down and lifts the 170-950 Hz body.
    """
    return synth.finish(x - 0.55 * band(x, hi=120) + 1.6 * band(x, lo=170, hi=950), **options)


def crack(rng, seconds: float, bright: float, close: float, rate: float, floor: float = 130):
    """Rock splitting: a dull break with no sparkle."""
    return bang(rng, seconds, bright, close, rate, floor)


def grind(rng, seconds: float, amp, lo: float = 60, hi: float = 520, rough: float = 0.7, speed: float = 24):
    """A great weight dragged over rock: low noise with a coarse, uneven grain."""
    return unit(band(noise(rng, seconds, -3), lo=lo, hi=hi) * amp * flutter(rng, seconds, speed, rough))


def crumble(rng, seconds: float, amp, lo: float = 110, hi: float = 850):
    """Rock breaking down into gravel: dense low-mid grit."""
    return unit(band(noise(rng, seconds, -3), lo=lo, hi=hi) * amp * flutter(rng, seconds, 58, 0.9))


def rubble(rng, seconds: float, start: float, stop: float, count: int, fade: float, centre: float = 240):
    """Loose rocks knocking together as they fall. Low-passed so none is heard as a tick."""
    rocks = (crackle(rng, seconds, start, stop, count, fade, centre=centre, q=4.0)
             + 0.6 * crackle(rng, seconds, start, stop, max(count // 2, 1), fade, centre=centre * 0.55, q=4.0))
    return unit(band(rocks, hi=800))


def boulder(rng, seconds: float, pitch: float, rate: float, start: float = 0.0):
    """One boulder striking another: a deep hollow clonk with a short ring."""
    hit = noise(rng, seconds) * decay(seconds, 0.001, 60, start)
    ring = svf(hit, pitch, 5.0) + 0.6 * svf(hit, pitch * 1.9, 4.0) + 0.3 * svf(hit, pitch * 3.3, 3.0)
    body = place(seconds, (start, 1.0, thump(seconds - start, pitch * 0.9, pitch * 0.42, 30, rate)))
    return unit(unit(ring * decay(seconds, 0.002, rate, start)) + 0.7 * body)


def faultline_cast(rng):
    # The ground splits: a dull break, then the rumble running forward.
    d = 1.0
    hit = thump(d, vary(rng, 85), 32, 12, 6.5)
    run = grind(rng, d, curve(d, [(0, 0), (0.08, 1), (0.55, 0.6), (d, 0)], 1.3), 50, 380, 0.6, 18)
    mix = (0.7 * crack(rng, d, 1600, 18, 12) + 1.1 * hit + 0.9 * run + 0.5 * rubble(rng, d, 0.08, 0.55, 14, 4.5)
           + 0.5 * crumble(rng, d, decay(d, 0.04, 5)))
    return finish(reverb(rng, mix, 0.6, 0.16, bright=1400), drive=1.9)


def faultline_erupt(rng):
    # Spikes punch out of the ground, several times along the line.
    d = 0.5
    hit = thump(d, vary(rng, 105), 42, 22, 12)
    mix = (0.6 * crack(rng, d, vary(rng, 1500), 26, 16) + 1.0 * hit + 0.8 * boulder(rng, d, vary(rng, 150), 20, 0.012)
           + 0.45 * rubble(rng, d, 0.04, 0.26, 7, 8) + 0.4 * crumble(rng, d, decay(d, 0.01, 12)))
    return finish(reverb(rng, mix, 0.3, 0.1, bright=1400), drive=1.8)


def gathered_mass_gather(rng):
    # A chunk tears free of the ground and is pulled in.
    d = 0.55
    tear = grind(rng, d, decay(d, 0.03, 8), 80, 600, 0.7, 30)
    pop = thump(d, vary(rng, 120), 50, 24, 16)
    mix = (0.9 * tear + 0.9 * pop + 0.8 * boulder(rng, d, vary(rng, 170), 18, 0.16) + 0.4 * rubble(rng, d, 0.03, 0.28, 8, 7)
           + 0.4 * crumble(rng, d, decay(d, 0.02, 10)))
    return finish(mix, drive=1.6)


def gathered_mass_launch(rng):
    # A heavy throw: the heave of the rock leaving, and gravel trailing off it.
    d = 0.5
    t = clock(d)
    heave = unit(svf(noise(rng, d, -3), 140 + vary(rng, 450) * np.exp(-((t - 0.08) / 0.08) ** 2), 1.2)
                 * decay(d, 0.03, 8))
    push = thump(d, vary(rng, 110), 45, 18, 13)
    mix = 1.0 * heave + 1.0 * push + 0.5 * crumble(rng, d, decay(d, 0.01, 13), 120, 700)
    return finish(mix, drive=1.5)


def gathered_mass_impact(rng):
    d = 1.0
    hit = thump(d, vary(rng, 95), 34, 14, 7)
    mix = (0.7 * crack(rng, d, vary(rng, 1900), 20, 11) + 1.1 * hit + 0.9 * boulder(rng, d, vary(rng, 130), 12, 0.01)
           + 0.5 * rubble(rng, d, 0.05, 0.5, 16, 4) + 0.5 * crumble(rng, d, decay(d, 0.01, 6)))
    return finish(reverb(rng, mix, 0.5, 0.15, bright=1500), drive=2.0)


def juggernaut_cast(rng):
    # Stone closes over the caster and the first stride lands.
    d = 0.9
    armour = grind(rng, d, curve(d, [(0, 0), (0.06, 1), (0.3, 0.5), (0.45, 0), (d, 0)], 1.2), 70, 600, 0.7, 28)
    stomp = place(d, (0.3, 1.0, thump(0.6, 90, 34, 14, 7)), (0.3, 0.5, crack(rng, 0.6, 1300, 24, 14)))
    mix = 0.8 * armour + 0.8 * boulder(rng, d, vary(rng, 140), 11, 0.3) + 1.1 * stomp + 0.4 * crumble(rng, d, decay(d, 0.02, 7, 0.3))
    return finish(reverb(rng, mix, 0.4, 0.12, bright=1400), drive=1.8)


def juggernaut_step(rng):
    d = 0.36
    step = thump(d, vary(rng, 92), 36, 22, 11)
    mix = 1.1 * step + 0.4 * crack(rng, d, vary(rng, 1000), 28, 20) + 0.5 * boulder(rng, d, vary(rng, 120), 20) + 0.3 * rubble(rng, d, 0.03, 0.16, 4, 11)
    return finish(mix, drive=1.7)


def juggernaut_full_speed(rng):
    # The charge reaches full speed: stone plates lock with two deep clonks and a low surge.
    d = 0.55
    surge = unit(band(noise(rng, d, -3), lo=60, hi=320) * curve(d, [(0, 0), (0.1, 1), (d, 0)], 1.5))
    mix = 1.0 * boulder(rng, d, 180, 14) + 0.7 * boulder(rng, d, 120, 12, 0.05) + 0.7 * surge + 0.7 * thump(d, 105, 45, 20, 11)
    return finish(mix, drive=1.6)


def juggernaut_impact(rng):
    d = 1.7
    hit = thump(d, vary(rng, 82), 26, 9, 3.8) + 0.5 * thump(d, 52, 30, 5, 4.2)
    roll = unit(band(noise(rng, d, -5), hi=200) * decay(d, 0.02, 3.0) * flutter(rng, d, 8, 0.5))
    mix = (0.7 * crack(rng, d, 2000, 14, 8) + 1.2 * hit + 0.8 * boulder(rng, d, 110, 8, 0.01) + 0.7 * roll
           + 0.55 * rubble(rng, d, 0.08, 0.9, 30, 3) + 0.5 * crumble(rng, d, decay(d, 0.02, 3.5)))
    return finish(reverb(rng, mix, 1.0, 0.22, bright=1500), drive=2.2, tail=0.08)


def stone_wall_cast(rng):
    # The wall grinds up out of the ground and locks.
    d = 1.1
    rise = grind(rng, d, curve(d, [(0, 0), (0.05, 1), (0.5, 0.8), (0.62, 0.1), (d, 0)], 1.2), 55, 480, 0.7, 22)
    lift = unit(svf(noise(rng, d, -3), 100 + 250 * np.clip(clock(d) / 0.55, 0, 1), 1.5)
                * curve(d, [(0, 0), (0.1, 1), (0.55, 0.7), (0.65, 0), (d, 0)]))
    lock = place(d, (0.57, 1.0, thump(0.53, 88, 32, 12, 6.5)), (0.57, 0.45, crack(rng, 0.53, 1400, 22, 14)),
                 (0.58, 0.8, boulder(rng, 0.52, vary(rng, 120), 9)))
    mix = 0.9 * rise + 0.5 * lift + 1.1 * lock + 0.4 * rubble(rng, d, 0.1, 0.55, 12, 2.6) + 0.4 * crumble(rng, d, decay(d, 0.05, 3))
    return finish(reverb(rng, mix, 0.6, 0.16, bright=1400), drive=1.9)


def stone_wall_settle(rng):
    d = 0.5
    mix = (1.1 * thump(d, vary(rng, 75), 36, 16, 10) + 0.8 * boulder(rng, d, vary(rng, 110), 13, 0.01)
           + 0.5 * crumble(rng, d, decay(d, 0.02, 10)))
    return finish(mix, drive=1.5)


def stone_wall_stress(rng):
    # Stone complaining under load: a dull split and a deep shifting.
    d = 0.5
    shift = grind(rng, d, decay(d, 0.02, 9), 120, 700, 0.8, 38)
    mix = (0.6 * crack(rng, d, vary(rng, 1500), 30, 20, 140) + 0.8 * shift + 0.8 * boulder(rng, d, vary(rng, 230), 22, 0.02)
           + 0.7 * thump(d, 95, 45, 26, 16))
    return finish(mix, drive=1.6)


def stone_wall_shatter(rng):
    d = 1.8
    hit = thump(d, vary(rng, 80), 25, 8, 3.6)
    groan = grind(rng, d, decay(d, 0.03, 2.8), 40, 260, 0.6, 11)
    mix = (0.7 * crack(rng, d, 2200, 13, 7.5) + 1.2 * hit + 0.8 * boulder(rng, d, 105, 7, 0.01) + 0.7 * groan
           + 0.6 * rubble(rng, d, 0.08, 1.0, 36, 2.8) + 0.6 * crumble(rng, d, decay(d, 0.02, 3.0)))
    return finish(reverb(rng, mix, 1.0, 0.22, bright=1500), drive=2.3, tail=0.08)


def titan_dome_cast(rng):
    # The ultimate begins: the ground breaks in a ring and a deep rumble gathers.
    d = 2.0
    t = clock(d)
    hit = thump(d, 75, 26, 7, 3.0, attack=0.006)
    rumble = unit(band(noise(rng, d, -5), hi=150) * curve(d, [(0, 0.3), (0.5, 1), (1.4, 0.7), (d, 0)], 1.3)
                  * flutter(rng, d, 8, 0.55))
    drone = unit(np.tanh(0.6 * (sweep(np.full(len(t), 41.0)) + sweep(np.full(len(t), 61.6), 1.1)))
                 * curve(d, [(0, 0), (0.4, 1), (1.3, 0.7), (d, 0)]))
    mix = (0.6 * crack(rng, d, 1800, 14, 8) + 1.2 * hit + 0.9 * rumble + 0.5 * drone
           + 0.7 * grind(rng, d, curve(d, [(0, 0), (0.2, 1), (1.1, 0.5), (d, 0)], 1.4), 55, 420, 0.7, 18)
           + 0.5 * rubble(rng, d, 0.1, 1.0, 24, 2.6) + 0.4 * crumble(rng, d, decay(d, 0.05, 2.4)))
    return finish(reverb(rng, mix, 1.2, 0.25, bright=1300), drive=2.1, tail=0.1)


def titan_dome_form(rng):
    # One course of stone slamming into place; repeated as the dome rises.
    d = 0.4
    mix = (1.0 * thump(d, vary(rng, 100), 40, 22, 12) + 1.0 * boulder(rng, d, vary(rng, 135), 16)
           + 0.35 * crack(rng, d, vary(rng, 1100), 32, 22) + 0.4 * crumble(rng, d, decay(d, 0.01, 14)))
    return finish(mix, drive=1.7)


def titan_dome_formed(rng):
    # The last stone seals the dome: a heavy close and a low hum that hangs inside it.
    d = 1.9
    t = clock(d)
    hum = unit(np.tanh(0.6 * (sweep(np.full(len(t), 49.0)) + sweep(np.full(len(t), 73.4), 0.7)
                              + 0.5 * sweep(np.full(len(t), 98.2), 1.9)))
               * curve(d, [(0, 0), (0.15, 1), (1.0, 0.6), (d, 0)]) * (1 + 0.2 * np.sin(2 * np.pi * 4.0 * t)))
    mix = (1.1 * thump(d, 82, 28, 8, 3.8) + 0.6 * crack(rng, d, 1500, 18, 10) + 1.0 * boulder(rng, d, 100, 6, 0.01)
           + 0.6 * hum + 0.3 * crumble(rng, d, decay(d, 0.02, 5)))
    return finish(reverb(rng, mix, 1.3, 0.3, bright=1200), drive=1.9, tail=0.1)


def titan_dome_repair(rng):
    d = 0.55
    knit = grind(rng, d, curve(d, [(0, 0), (0.05, 1), (0.27, 0.5), (0.34, 0), (d, 0)], 1.2), 100, 650, 0.7, 34)
    mix = (0.8 * knit + 1.0 * boulder(rng, d, vary(rng, 160), 15, 0.29) + 0.7 * place(d, (0.29, 1.0, thump(0.26, 100, 46, 24, 15)))
           + 0.4 * crumble(rng, d, decay(d, 0.02, 9)))
    return finish(mix, drive=1.5)


def titan_dome_collapse_crack(rng):
    # The dome fails: breaks race across it and the stone groans.
    d = 1.6
    breaks = place(d, (0.0, 1.0, crack(rng, 0.6, 1900, 24, 14)), (0.27, 0.8, crack(rng, 0.6, 1600, 24, 14)),
                   (0.49, 0.9, crack(rng, 0.6, 2100, 24, 14)), (0.8, 0.7, crack(rng, 0.6, 1400, 24, 14)))
    knocks = place(d, (0.0, 1.0, boulder(rng, 0.6, 150, 14)), (0.27, 0.8, boulder(rng, 0.6, 120, 14)),
                   (0.49, 0.9, boulder(rng, 0.6, 170, 14)), (0.8, 0.8, boulder(rng, 0.6, 105, 12)))
    groan = grind(rng, d, curve(d, [(0, 0), (0.3, 0.6), (1.1, 1), (d, 0)], 1.3), 35, 260, 0.7, 10)
    low = unit(band(noise(rng, d, -5), hi=140) * curve(d, [(0, 0.2), (1.2, 1), (d, 0)], 1.4) * flutter(rng, d, 7, 0.5))
    mix = (0.6 * unit(breaks) + 0.9 * unit(knocks) + 1.0 * groan + 0.8 * low + 0.5 * rubble(rng, d, 0.1, 1.1, 26, 1.4)
           + 0.5 * crumble(rng, d, curve(d, [(0, 0.3), (1.1, 1), (d, 0)], 1.3)))
    return finish(reverb(rng, mix, 1.0, 0.22, bright=1400), drive=2.0, tail=0.08)


def titan_dome_collapse_boom(rng):
    d = 2.6
    hit = thump(d, 80, 23, 6, 2.5, attack=0.003) + 0.5 * thump(d, 50, 28, 4, 3.0)
    roll = unit(band(noise(rng, d, -4), hi=220) * decay(d, 0.02, 1.9) * flutter(rng, d, 7, 0.6))
    mix = (0.7 * crack(rng, d, 2400, 10, 6) + 1.3 * hit + 0.9 * boulder(rng, d, 95, 5, 0.01) + 0.9 * roll
           + 0.6 * rubble(rng, d, 0.1, 1.4, 60, 2.3) + 0.5 * rubble(rng, d, 0.2, 1.3, 16, 2.0, centre=130)
           + 0.6 * crumble(rng, d, decay(d, 0.03, 2.2)))
    return finish(reverb(rng, mix, 1.6, 0.28, bright=1500, predelay=0.02), drive=2.4, tail=0.1)


# name under assets/elementalwands/sounds -> (recipe, number of variants)
SOUNDS = {
    "spell/stone/faultline_cast": (faultline_cast, 2),
    "spell/stone/faultline_erupt": (faultline_erupt, 3),
    "spell/stone/gathered_mass_gather": (gathered_mass_gather, 3),
    "spell/stone/gathered_mass_launch": (gathered_mass_launch, 2),
    "spell/stone/gathered_mass_impact": (gathered_mass_impact, 3),
    "spell/stone/juggernaut_cast": (juggernaut_cast, 1),
    "spell/stone/juggernaut_step": (juggernaut_step, 3),
    "spell/stone/juggernaut_full_speed": (juggernaut_full_speed, 1),
    "spell/stone/juggernaut_impact": (juggernaut_impact, 2),
    "spell/stone/stone_wall_cast": (stone_wall_cast, 2),
    "spell/stone/stone_wall_settle": (stone_wall_settle, 2),
    "spell/stone/stone_wall_stress": (stone_wall_stress, 3),
    "spell/stone/stone_wall_shatter": (stone_wall_shatter, 2),
    "spell/stone/titan_dome_cast": (titan_dome_cast, 1),
    "spell/stone/titan_dome_form": (titan_dome_form, 3),
    "spell/stone/titan_dome_formed": (titan_dome_formed, 1),
    "spell/stone/titan_dome_repair": (titan_dome_repair, 2),
    "spell/stone/titan_dome_collapse_crack": (titan_dome_collapse_crack, 1),
    "spell/stone/titan_dome_collapse_boom": (titan_dome_collapse_boom, 1),
}
