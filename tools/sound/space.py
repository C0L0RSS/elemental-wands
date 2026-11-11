"""Space spell sounds. Shared voice: the void taking a breath and letting go.

Three ingredients, taken from the demo clip the developer approved first: an
inhale (a band of noise rising and tightening toward a moment), a dark tone
that drops in pitch from that moment, slightly out of tune with itself, and a
glassy shimmer on top. Everything sits in a long, dark reverb."""
from __future__ import annotations

import numpy as np

from synth import SR, band, clock, curve, decay, finish, flutter, noise, reverb, svf, thump, unit, vary


def inhale(rng, seconds: float, hit: float, top: float = 3800.0, low: float = 300.0, power: float = 3.0):
    """Air drawn inward, rising and brightening until it cuts off at `hit`."""
    t = clock(seconds)
    u = np.clip(t / hit, 0, 1)
    return unit(svf(noise(rng, seconds), low + (top - low) * u ** power, 3.0) * np.where(t < hit, u ** power, 0))


def drop(seconds: float, hit: float, top: float, floor: float, fall: float, rate: float, wobble: float = 2.2):
    """The void's voice: a tone falling from `top` to `floor` after `hit`, doubled out of tune."""
    t = clock(seconds)
    after = np.maximum(t - hit, 0)
    base = floor + (top - floor) * np.exp(-fall * after)
    phase = 2 * np.pi * np.cumsum(base) / SR
    tone = sum(np.sin(phase * detune + wobble * np.sin(2 * np.pi * 3.1 * t))
               for detune in (1.0, 1.007, 0.5, 1.5))
    return unit(tone * (t >= hit) * np.clip(after / 0.004, 0, 1) * np.exp(-rate * after))


def glass(seconds: float, hit: float, root: float, rate: float = 5.0, tremolo: float = 11.0):
    """Glassy shimmer: four high partials ringing from `hit`."""
    t = clock(seconds)
    after = np.maximum(t - hit, 0)
    ring = sum(np.sin(2 * np.pi * root * ratio * t + index) * np.exp(-rate * (1 + 0.3 * index) * after)
               for index, ratio in enumerate((1.0, 1.498, 2.0, 2.67)))
    return unit(ring * (t >= hit) * np.clip(after / 0.003, 0, 1) * (1 + 0.6 * np.sin(2 * np.pi * tremolo * t)))


def hush(rng, seconds: float, amp, lo: float = 400, hi: float = 3000):
    """Soft dark air around a void sound."""
    return unit(band(noise(rng, seconds, -3), lo=lo, hi=hi) * amp)


def singularity_bolt_cast(rng):
    d = 0.5
    hit = 0.07
    mix = (1.3 * inhale(rng, d, hit, 3200) + 0.6 * drop(d, hit, vary(rng, 240), 80, 12, 9)
           + 0.14 * glass(d, hit, vary(rng, 2100, 0.04), 12))
    return finish(reverb(rng, mix, 0.5, 0.25, bright=5000), drive=1.2)


def singularity_bolt_impact(rng):
    d = 0.6
    hit = 0.02
    pop = thump(d, vary(rng, 170), 55, 26, 14)
    mix = (0.8 * pop + 0.6 * drop(d, hit, vary(rng, 300), 70, 14, 8) + 0.5 * hush(rng, d, decay(d, 0.004, 14), 500, 4000)
           + 0.16 * glass(d, hit, vary(rng, 1900, 0.04), 9))
    return finish(reverb(rng, mix, 0.6, 0.28, bright=5000), drive=1.3)


def blink_rift_cast(rng):
    # Space folds: a fast inhale, a snap, and the shimmer left where the caster was.
    d = 0.7
    hit = 0.13
    mix = (1.4 * inhale(rng, d, hit, 4200) + 0.5 * drop(d, hit, vary(rng, 330), 110, 16, 10)
           + 0.2 * glass(d, hit, vary(rng, 1760, 0.03), 6))
    return finish(reverb(rng, mix, 0.7, 0.3, bright=5000), drive=1.2)


def blink_rift_fail(rng):
    # The fold does not take: a dull drop with no shimmer.
    d = 0.5
    hit = 0.06
    mix = 0.8 * inhale(rng, d, hit, 1400, 250) + 0.9 * drop(d, hit, 150, 60, 10, 9, wobble=1.0) + 0.4 * hush(rng, d, decay(d, 0.02, 12), 250, 1200)
    return finish(reverb(rng, mix, 0.3, 0.15, bright=1500), drive=1.2)


def astral_double_cast(rng):
    # A second self steps out: a soft breath outward and a glass tone.
    d = 0.8
    hit = 0.1
    mix = (1.0 * inhale(rng, d, hit, 2600) + 0.4 * drop(d, hit, 260, 130, 6, 7, wobble=1.2)
           + 0.5 * hush(rng, d, decay(d, 0.03, 6), 600, 3500) + 0.22 * glass(d, hit, vary(rng, 1568, 0.02), 4.5, 7))
    return finish(reverb(rng, mix, 0.8, 0.32, bright=5500), drive=1.1)


def astral_double_chime(rng):
    d = 0.7
    mix = 1.0 * glass(d, 0.0, vary(rng, 1760, 0.03), 6, 9) + 0.25 * hush(rng, d, decay(d, 0.01, 9), 1500, 6000)
    return finish(reverb(rng, 0.9 * mix, 0.7, 0.35, bright=6000), drive=1.0)


def gravity_well_cast(rng):
    # The bomb is thrown: a dark lob with a low voice inside it.
    d = 0.6
    hit = 0.09
    mix = (1.0 * inhale(rng, d, hit, 2000, 250) + 0.8 * drop(d, hit, 190, 75, 9, 8)
           + 0.5 * hush(rng, d, decay(d, 0.03, 8), 300, 1800))
    return finish(reverb(rng, mix, 0.5, 0.22, bright=3500), drive=1.2)


def gravity_well_land(rng):
    # It lands, the ground gives a glassy crack, and the pull begins.
    d = 1.4
    t = clock(d)
    crack = unit(band(noise(rng, d), lo=1500, hi=7000) * decay(d, 0.001, 38))
    pull = unit(np.tanh(0.7 * sum(np.sin(2 * np.pi * f * t + i) for i, f in enumerate((55.0, 55.6, 82.7))))
                * curve(d, [(0, 0), (0.12, 1), (0.8, 0.7), (d, 0)]) * (1 + 0.3 * np.sin(2 * np.pi * 6 * t)))
    mix = (0.7 * crack + 0.9 * drop(d, 0.0, 260, 60, 8, 3.5) + 0.7 * pull + 0.2 * glass(d, 0.0, vary(rng, 2300, 0.04), 8)
           + 0.4 * hush(rng, d, decay(d, 0.05, 3.5), 300, 2000))
    return finish(reverb(rng, mix, 1.0, 0.3, bright=4500), drive=1.4, tail=0.08)


def gravity_well_collapse_charge(rng):
    # 0.4 s of everything being drawn in before the burst.
    d = 0.42
    t = clock(d)
    rise = unit(np.sin(2 * np.pi * np.cumsum(70 + 260 * (t / d) ** 2) / SR) * (t / d) ** 2)
    return finish(1.3 * inhale(rng, d, 0.4, 4200) + 0.6 * rise, drive=1.2, tail=0.01)


def gravity_well_collapse_burst(rng):
    d = 1.4
    boom = thump(d, 120, 36, 9, 4.2)
    mix = (1.0 * boom + 0.7 * drop(d, 0.0, 300, 55, 7, 3.2) + 0.6 * hush(rng, d, decay(d, 0.006, 6), 300, 4500)
           + 0.18 * glass(d, 0.0, vary(rng, 1760, 0.03), 4.5))
    return finish(reverb(rng, mix, 1.2, 0.35, bright=5000), drive=1.6, tail=0.08)


def hollow_purple_charge(rng):
    # Three seconds of two stars closing: a drone that rises and tightens.
    d = 3.1
    t = clock(d)
    u = t / d
    pitch = 48 * 2 ** (1.4 * u ** 1.5)
    phase = 2 * np.pi * np.cumsum(pitch) / SR
    drone = unit(np.tanh(0.7 * sum(np.sin(phase * m + 1.4 * np.sin(2 * np.pi * (3 + 9 * u) * t)) for m in (1.0, 1.006, 1.5, 2.0)))
                 * curve(d, [(0, 0), (0.3, 0.5), (2.9, 1.0), (3.02, 0.9), (d, 0)]) * (1 + 0.25 * np.sin(2 * np.pi * (3 + 9 * u) * t)))
    mix = 0.8 * drone + 1.0 * inhale(rng, d, 3.02, 3600, 250, 2.2) + 0.3 * hush(rng, d, curve(d, [(0, 0), (2.9, 1), (d, 0)], 2), 500, 3500)
    return finish(reverb(rng, mix, 1.0, 0.3, bright=4500), drive=1.4, tail=0.05)


def hollow_purple_charge_peak(rng):
    # The stars meet: a bright bloom over a deep drop.
    d = 1.1
    mix = (0.9 * drop(d, 0.0, 220, 50, 6, 3.5) + 0.3 * glass(d, 0.0, 1760, 3.5, 13) + 0.2 * glass(d, 0.0, 2349, 4.5, 9)
           + 0.5 * hush(rng, d, decay(d, 0.004, 8), 800, 6000) + 0.6 * thump(d, 110, 40, 12, 6))
    return finish(reverb(rng, mix, 1.1, 0.4, bright=6000), drive=1.3)


def hollow_purple_launch(rng):
    d = 1.8
    boom = thump(d, 105, 28, 7, 3.0, attack=0.004)
    mix = (1.1 * boom + 0.8 * drop(d, 0.0, 330, 45, 6, 2.6) + 0.7 * hush(rng, d, decay(d, 0.004, 5), 250, 5000)
           + 0.16 * glass(d, 0.0, 1568, 3.5))
    return finish(reverb(rng, mix, 1.5, 0.38, bright=4500, predelay=0.02), drive=1.9, tail=0.1)


def hollow_purple_loop(rng):
    # Retriggered every 0.5 s while the orb flies; the long fade lets copies overlap.
    d = 1.0
    t = clock(d)
    amp = curve(d, [(0, 0), (0.2, 1), (0.55, 1), (d, 0)])
    root = vary(rng, 46, 0.03)
    throb = unit(np.tanh(0.8 * sum(np.sin(2 * np.pi * root * m * t + 1.6 * np.sin(2 * np.pi * 5.5 * t) + i)
                                   for i, m in enumerate((1.0, 1.008, 2.0, 3.0)))))
    mix = (0.9 * throb * (1 + 0.3 * np.sin(2 * np.pi * 5.5 * t)) + 0.35 * hush(rng, d, flutter(rng, d, 7, 0.4), 300, 1800)) * amp
    return finish(mix, drive=1.3, tail=0.01)


def hollow_purple_collapse_implode(rng):
    # 0.35 s before the boom: the orb folds in on itself.
    d = 0.37
    t = clock(d)
    fold = unit(np.sin(2 * np.pi * np.cumsum(60 + 420 * (t / d) ** 2.5) / SR) * (t / d) ** 2)
    return finish(1.4 * inhale(rng, d, 0.35, 4600, 250, 2.5) + 0.7 * fold, drive=1.3, tail=0.01)


def hollow_purple_collapse_boom(rng):
    d = 2.8
    boom = thump(d, 100, 26, 6, 2.4, attack=0.003) + 0.4 * thump(d, 58, 31, 4, 3.0)
    roll = unit(band(noise(rng, d, -5), hi=220) * decay(d, 0.02, 2.0) * flutter(rng, d, 7, 0.6))
    mix = (1.2 * boom + 0.8 * drop(d, 0.0, 380, 40, 5, 2.0) + 0.7 * roll + 0.7 * hush(rng, d, decay(d, 0.003, 4), 250, 6000)
           + 0.2 * glass(d, 0.0, 1318, 2.6, 7))
    return finish(reverb(rng, mix, 1.8, 0.4, bright=4500, predelay=0.02), drive=2.2, tail=0.1)


# name under assets/elementalwands/sounds -> (recipe, number of variants)
SOUNDS = {
    "spell/space/singularity_bolt_cast": (singularity_bolt_cast, 3),
    "spell/space/singularity_bolt_impact": (singularity_bolt_impact, 3),
    "spell/space/blink_rift_cast": (blink_rift_cast, 2),
    "spell/space/blink_rift_fail": (blink_rift_fail, 1),
    "spell/space/astral_double_cast": (astral_double_cast, 1),
    "spell/space/astral_double_chime": (astral_double_chime, 3),
    "spell/space/gravity_well_cast": (gravity_well_cast, 1),
    "spell/space/gravity_well_land": (gravity_well_land, 1),
    "spell/space/gravity_well_collapse_charge": (gravity_well_collapse_charge, 1),
    "spell/space/gravity_well_collapse_burst": (gravity_well_collapse_burst, 1),
    "spell/space/hollow_purple_charge": (hollow_purple_charge, 1),
    "spell/space/hollow_purple_charge_peak": (hollow_purple_charge_peak, 1),
    "spell/space/hollow_purple_launch": (hollow_purple_launch, 1),
    "spell/space/hollow_purple_loop": (hollow_purple_loop, 2),
    "spell/space/hollow_purple_collapse_implode": (hollow_purple_collapse_implode, 1),
    "spell/space/hollow_purple_collapse_boom": (hollow_purple_collapse_boom, 1),
}
