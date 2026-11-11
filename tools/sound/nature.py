"""Nature spell sounds. Shared voice: whimsical fairy magic. Small bright bells,
quick runs of notes up or down a scale, showers of twinkling fairy dust, and a
soft breath of air and leaves underneath.

Nature is the lightest element on purpose, so it cannot be confused with Stone:
almost nothing here sits below 300 Hz, and wood and earth are only hinted at.
Rising runs mean growth and healing; falling runs mean something ending.
Twinkles thin out and fade smoothly inside a bright reverb, so the tail is a
shimmer and not stray ticks."""
from __future__ import annotations

import numpy as np

from synth import band, clock, curve, decay, finish, flutter, noise, reverb, svf, thump, unit, vary

# C major pentatonic from C6 up: the notes every bell in the set is drawn from.
SCALE = (1046.5, 1174.7, 1318.5, 1568.0, 1760.0, 2093.0, 2349.3, 2637.0, 3136.0, 3520.0, 4186.0)


def bump(seconds: float, at: float, rise: float, fall: float) -> np.ndarray:
    """A smooth swell peaking at `at`, with separate rise and fall times."""
    t = clock(seconds) - at
    return np.exp(-(t / np.where(t < 0, rise, fall)) ** 2)


def bell(seconds: float, pitch: float, rate: float = 16.0, start: float = 0.0) -> np.ndarray:
    """One small bell: a clear note with a little glassy overtone, struck at `start`."""
    t = clock(seconds) - start
    after = np.maximum(t, 0)
    ring = sum(gain * np.sin(2 * np.pi * pitch * multiple * after) * np.exp(-rate * (1 + 0.6 * index) * after)
               for index, (multiple, gain) in enumerate(((1.0, 1.0), (2.0, 0.3), (3.01, 0.14), (4.1, 0.06))))
    return ring * (t >= 0) * np.clip(after / 0.002, 0, 1)


def run(seconds: float, notes, start: float, step: float, rate: float = 16.0, swell: float = 0.0) -> np.ndarray:
    """Bells played one after another, `step` seconds apart. `swell` above 0 makes later notes louder."""
    return unit(sum((1 + swell * index) * bell(seconds, pitch, rate, start + index * step)
                    for index, pitch in enumerate(notes)))


def twinkle(rng, seconds: float, start: float, stop: float, count: int, low: int = 3, high: int = 11,
            rate: float = 22.0, fade: float = 3.0) -> np.ndarray:
    """Fairy dust: many tiny bells at random moments and pitches, thinning out as they fade."""
    out = np.zeros(len(clock(seconds)))
    for when in np.sort(rng.uniform(start, stop, count)):
        gain = rng.uniform(0.4, 1.0) * np.exp(-fade * (when - start))
        out += gain * bell(seconds, SCALE[rng.integers(low, high)] * rng.choice((1.0, 1.0, 2.0)) , rate, when)
    return unit(out)


def swish(rng, seconds: float, at: float, rise: float, fall: float, low: float, top: float):
    """A light pass of air: a soft band that rises and falls with its level."""
    shape = bump(seconds, at, rise, fall)
    return unit(band(svf(noise(rng, seconds, -2), low + (top - low) * shape, 1.4), lo=400, hi=6000) * shape)


def rustle(rng, seconds: float, amp, lo: float = 2000, hi: float = 6500, grain: float = 44):
    """A few leaves stirring."""
    return unit(band(noise(rng, seconds, -1), lo=lo, hi=hi) * amp * flutter(rng, seconds, grain, 0.85))


def glow(rng, seconds: float, amp, notes=(523.3, 659.3, 784.0, 1046.5), q: float = 22.0):
    """A warm breathy chord under the bells: noise ringing at a few pitches."""
    air = noise(rng, seconds, -3)
    return unit(sum(svf(air, pitch, q) for pitch in notes) * amp)


def tap(rng, seconds: float, pitch: float, rate: float, start: float = 0.0):
    """A light woody tick, the only wood left in the set."""
    hit = noise(rng, seconds) * decay(seconds, 0.001, 120, start)
    return unit(svf(hit, pitch, 6.0) * decay(seconds, 0.002, rate, start))


def sparkle(rng, mix, tail: float = 0.6, wet: float = 0.28, drive: float = 1.1):
    return finish(reverb(rng, mix, tail, wet, bright=7000), drive=drive, tail=0.06)


def pick(rng, low: int, count: int, step: int = 1):
    """`count` scale notes starting near `low`, moving by `step` (negative to fall)."""
    first = low + int(rng.integers(0, 2))
    return [SCALE[min(max(first + step * index, 0), len(SCALE) - 1)] for index in range(count)]


def thornbite_cast(rng):
    # A vine flicks out: a light swish with three quick notes riding it.
    d = 0.4
    mix = (0.9 * swish(rng, d, 0.07, 0.03, 0.08, 800, vary(rng, 3600)) + 0.7 * run(d, pick(rng, 2, 3), 0.03, 0.035, 26)
           + 0.25 * rustle(rng, d, bump(d, 0.08, 0.05, 0.06)))
    return sparkle(rng, mix, 0.3, 0.2)


def thornbite_hit(rng):
    d = 0.3
    high = pick(rng, 4, 1)[0]
    mix = (0.9 * unit(bell(d, high, 24) + 0.8 * bell(d, high * 1.335, 28, 0.012)) + 0.6 * tap(rng, d, vary(rng, 1300), 40)
           + 0.4 * twinkle(rng, d, 0.02, 0.14, 4, 6, 11, 34, 9))
    return sparkle(rng, mix, 0.3, 0.2)


def seed_cast(rng):
    # A seed flicked from the wand: one bright "plip" and a breath of air.
    d = 0.34
    note = pick(rng, 1, 1)[0]
    mix = 1.0 * unit(bell(d, note, 26) + 0.5 * bell(d, note * 1.5, 30, 0.03)) + 0.6 * swish(rng, d, 0.06, 0.03, 0.08, 800, vary(rng, 3000))
    return sparkle(rng, mix, 0.3, 0.2)


def seed_fail(rng):
    # The seed finds nowhere to root: two notes drooping down.
    d = 0.45
    mix = 0.9 * run(d, (SCALE[2], SCALE[0] * 0.943), 0.0, 0.11, 18) + 0.4 * tap(rng, d, 700, 30, 0.11) + 0.3 * rustle(rng, d, decay(d, 0.01, 14), 1500, 4000)
    return sparkle(rng, mix, 0.25, 0.18)


def seed_plant(rng):
    # The seed settles and a shoot pops up: a plink, then three notes climbing.
    d = 0.6
    mix = (0.8 * unit(bell(d, SCALE[0], 22)) + 0.9 * run(d, pick(rng, 2, 3), 0.14, 0.06, 20, 0.15)
           + 0.4 * twinkle(rng, d, 0.2, 0.42, 5, 6, 11, 30, 6) + 0.3 * swish(rng, d, 0.2, 0.06, 0.1, 700, 2600)
           + 0.3 * thump(d, 210, 130, 40, 30))
    return sparkle(rng, mix, 0.4, 0.24)


def seed_grow(rng):
    # A quiet pulse of growth, repeated as a seedling or bloom spreads.
    d = 0.5
    mix = (1.0 * twinkle(rng, d, 0.0, 0.26, 6, 3, 10, 24, 5) + 0.35 * glow(rng, d, bump(d, 0.15, 0.1, 0.16))
           + 0.2 * rustle(rng, d, bump(d, 0.12, 0.08, 0.1)))
    return sparkle(rng, mix, 0.4, 0.26)


def seed_harvest(rng):
    # A seedling picked for its healing: a run of notes up and a shower of dust.
    d = 1.0
    mix = (1.0 * run(d, pick(rng, 1, 5), 0.0, 0.055, 14, 0.12) + 0.7 * twinkle(rng, d, 0.2, 0.7, 14, 5, 11, 22, 3.5)
           + 0.45 * glow(rng, d, bump(d, 0.25, 0.14, 0.32)) + 0.2 * swish(rng, d, 0.2, 0.12, 0.25, 900, 3200))
    return sparkle(rng, mix, 0.7, 0.3)


def seed_end(rng):
    # A plant fades: notes stepping down and the last of its dust.
    d = 0.75
    mix = (0.9 * run(d, pick(rng, 6, 4, -1), 0.0, 0.09, 13) + 0.4 * twinkle(rng, d, 0.1, 0.45, 5, 2, 8, 24, 5)
           + 0.3 * rustle(rng, d, decay(d, 0.03, 8), 1600, 4500, 30))
    return sparkle(rng, mix, 0.5, 0.26)


def springbloom_land(rng):
    # The pod lands and its petals fold open: a soft plop, then four notes unfolding.
    d = 0.8
    mix = (0.5 * thump(d, 190, 110, 34, 24) + 0.7 * unit(bell(d, SCALE[0] * 0.5, 20)) + 0.9 * run(d, pick(rng, 0, 4, 2), 0.12, 0.075, 15, 0.1)
           + 0.4 * rustle(rng, d, bump(d, 0.3, 0.12, 0.14)) + 0.4 * twinkle(rng, d, 0.3, 0.6, 7, 5, 11, 26, 5))
    return sparkle(rng, mix, 0.5, 0.26)


def springbloom_launch(rng):
    # The pad throws the player: a fast run of notes straight up with air behind it.
    d = 0.65
    mix = (1.0 * run(d, SCALE[1:9], 0.0, 0.022, 20, 0.08) + 0.8 * swish(rng, d, 0.12, 0.07, 0.2, 700, vary(rng, 3400))
           + 0.5 * twinkle(rng, d, 0.18, 0.48, 9, 6, 11, 26, 5) + 0.35 * thump(d, 150, 260, 26, 18))
    return sparkle(rng, mix, 0.5, 0.26)


def tendril_bloom_cast(rng):
    # Vines stir at the caster's call.
    d = 0.6
    stir = bump(d, 0.16, 0.1, 0.16)
    mix = (0.6 * rustle(rng, d, stir) + 0.8 * run(d, pick(rng, 1, 3, 2), 0.04, 0.07, 18, 0.1) + 0.4 * swish(rng, d, 0.15, 0.08, 0.18, 600, 2400)
           + 0.4 * twinkle(rng, d, 0.15, 0.4, 5, 5, 11, 28, 6))
    return sparkle(rng, mix, 0.4, 0.24)


def tendril_bloom_launch(rng):
    # A tendril shoots out of a seedling.
    d = 0.5
    mix = (0.9 * swish(rng, d, 0.1, 0.04, 0.14, 700, vary(rng, 3400)) + 0.9 * run(d, pick(rng, 2, 5), 0.0, 0.028, 24, 0.1)
           + 0.3 * rustle(rng, d, bump(d, 0.12, 0.06, 0.08)))
    return sparkle(rng, mix, 0.35, 0.22)


def tendril_bloom_bloom(rng):
    # The knot of vines opens into flower: the full run up and a long shower of dust.
    d = 1.4
    mix = (1.0 * run(d, SCALE[0:9], 0.0, 0.05, 12, 0.1) + 0.8 * twinkle(rng, d, 0.3, 1.0, 24, 4, 11, 20, 2.6)
           + 0.5 * glow(rng, d, bump(d, 0.35, 0.2, 0.4)) + 0.3 * rustle(rng, d, bump(d, 0.25, 0.15, 0.25))
           + 0.3 * swish(rng, d, 0.25, 0.15, 0.35, 800, 3200))
    return sparkle(rng, mix, 0.9, 0.32)


def overgrowth_awaken(rng):
    # The great tree rises: a warm swell from below, a grand run upward, and dust raining from the crown.
    d = 2.2
    t = clock(d)
    swell = bump(d, 0.7, 0.45, 0.7)
    root = glow(rng, d, swell, (196.0, 261.6, 392.0, 523.3), 18)
    trunk = unit(svf(noise(rng, d, -3), 260 + 500 * np.clip(t / 1.0, 0, 1), 4.0) * curve(d, [(0, 0), (0.2, 1), (1.0, 0.6), (1.4, 0), (d, 0)], 1.3)
                 * flutter(rng, d, 16, 0.5))
    climb = run(d, [n * 0.5 for n in SCALE[0:6]] + list(SCALE[1:9]), 0.15, 0.06, 9, 0.06)
    mix = (0.8 * root + 0.5 * trunk + 1.0 * climb + 0.8 * twinkle(rng, d, 0.9, 1.8, 34, 4, 11, 18, 2.2)
           + 0.4 * rustle(rng, d, bump(d, 1.0, 0.4, 0.4), 1500, 6000, 30) + 0.5 * thump(d, 140, 70, 10, 6, attack=0.02))
    return sparkle(rng, mix, 1.2, 0.34, 1.3)


def overgrowth_grow_stage(rng):
    # The trunk stretches a stage taller: a climbing run over a soft creak.
    d = 0.9
    t = clock(d)
    stretch = unit(svf(noise(rng, d, -3), 320 + 420 * np.clip(t / 0.5, 0, 1), 5.0) * bump(d, 0.25, 0.15, 0.2) * flutter(rng, d, 20, 0.6))
    mix = (0.45 * stretch + 1.0 * run(d, pick(rng, 0, 5), 0.08, 0.07, 14, 0.1) + 0.5 * twinkle(rng, d, 0.35, 0.7, 8, 5, 11, 24, 4)
           + 0.3 * rustle(rng, d, bump(d, 0.4, 0.18, 0.13)))
    return sparkle(rng, mix, 0.5, 0.26)


def overgrowth_grow_canopy(rng):
    # The crown bursts open: leaves and a wide shower of dust.
    d = 1.4
    mix = (0.6 * rustle(rng, d, curve(d, [(0, 0), (0.1, 1), (0.5, 0.5), (0.9, 0), (d, 0)], 1.3), 1500, 6000, 34)
           + 1.0 * run(d, SCALE[2:10], 0.0, 0.04, 13, 0.08) + 0.9 * twinkle(rng, d, 0.25, 1.0, 28, 4, 11, 20, 2.6)
           + 0.4 * glow(rng, d, bump(d, 0.35, 0.2, 0.4)) + 0.4 * swish(rng, d, 0.16, 0.1, 0.3, 700, 3000))
    return sparkle(rng, mix, 0.9, 0.32)


def overgrowth_hurt(rng):
    # The tree is struck: a woody tick and two notes knocked sour.
    d = 0.42
    note = pick(rng, 0, 1)[0]
    mix = (0.8 * tap(rng, d, vary(rng, 620), 22) + 0.9 * unit(bell(d, note, 20) + 0.9 * bell(d, note * 1.06, 20, 0.008))
           + 0.4 * thump(d, 170, 95, 30, 22) + 0.25 * rustle(rng, d, decay(d, 0.01, 18)))
    return sparkle(rng, mix, 0.3, 0.2, 1.2)


def overgrowth_empower(rng):
    # The tree feeds the seedlings around it: a warm chord with dust spiralling up.
    d = 1.2
    mix = (0.7 * glow(rng, d, bump(d, 0.28, 0.16, 0.4)) + 0.9 * run(d, (SCALE[0], SCALE[2], SCALE[3], SCALE[5], SCALE[7], SCALE[8]), 0.0, 0.07, 12, 0.1)
           + 0.8 * twinkle(rng, d, 0.25, 0.9, 20, 5, 11, 22, 3))
    return sparkle(rng, mix, 0.8, 0.32)


def overgrowth_break(rng):
    # The tree is destroyed: a snap, notes tumbling all the way down, dust scattering.
    d = 1.7
    mix = (0.7 * tap(rng, d, 520, 14) + 0.5 * unit(band(noise(rng, d), lo=800, hi=4500) * decay(d, 0.001, 22))
           + 1.0 * run(d, list(SCALE[9::-1]) + [SCALE[0] * 0.75, SCALE[0] * 0.5], 0.04, 0.065, 11)
           + 0.6 * twinkle(rng, d, 0.1, 1.0, 20, 2, 10, 22, 2.6) + 0.4 * rustle(rng, d, decay(d, 0.05, 3.5), 1500, 5500, 30)
           + 0.5 * thump(d, 150, 70, 12, 8))
    return sparkle(rng, mix, 0.9, 0.3, 1.3)


def overgrowth_end(rng):
    # The tree's time runs out: a slow run down and the last dust drifting off.
    d = 1.5
    mix = (0.9 * run(d, pick(rng, 7, 6, -1), 0.0, 0.14, 8) + 0.6 * twinkle(rng, d, 0.2, 1.0, 12, 3, 10, 18, 2.4)
           + 0.4 * glow(rng, d, bump(d, 0.3, 0.2, 0.6)) + 0.3 * rustle(rng, d, curve(d, [(0, 0), (0.15, 1), (0.7, 0.4), (1.0, 0), (d, 0)], 1.3), 1600, 5000, 26))
    return sparkle(rng, mix, 0.9, 0.32)


# name under assets/elementalwands/sounds -> (recipe, number of variants)
SOUNDS = {
    "spell/nature/thornbite_cast": (thornbite_cast, 3),
    "spell/nature/thornbite_hit": (thornbite_hit, 3),
    "spell/nature/seed_cast": (seed_cast, 3),
    "spell/nature/seed_fail": (seed_fail, 1),
    "spell/nature/seed_plant": (seed_plant, 2),
    "spell/nature/seed_grow": (seed_grow, 3),
    "spell/nature/seed_harvest": (seed_harvest, 2),
    "spell/nature/seed_end": (seed_end, 2),
    "spell/nature/springbloom_land": (springbloom_land, 1),
    "spell/nature/springbloom_launch": (springbloom_launch, 2),
    "spell/nature/tendril_bloom_cast": (tendril_bloom_cast, 2),
    "spell/nature/tendril_bloom_launch": (tendril_bloom_launch, 2),
    "spell/nature/tendril_bloom_bloom": (tendril_bloom_bloom, 1),
    "spell/nature/overgrowth_awaken": (overgrowth_awaken, 1),
    "spell/nature/overgrowth_grow_stage": (overgrowth_grow_stage, 2),
    "spell/nature/overgrowth_grow_canopy": (overgrowth_grow_canopy, 1),
    "spell/nature/overgrowth_hurt": (overgrowth_hurt, 3),
    "spell/nature/overgrowth_empower": (overgrowth_empower, 1),
    "spell/nature/overgrowth_break": (overgrowth_break, 1),
    "spell/nature/overgrowth_end": (overgrowth_end, 1),
}
