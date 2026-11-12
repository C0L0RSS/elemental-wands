"""Wind spell sounds. Shared voice: round, controlled, slightly supernatural air.

Three layers make it. A hollow low push of pressure gives weight. A breathy
pitched tone, noise ringing in a narrow band between about 250 and 1200 Hz,
glides to show which way the air moves. Soft mid air washes over both. There is
very little above 5 kHz. Only the daggers are bright, because they are metal.

The developer accepted this version as good enough for now and may revisit it.
Directions already tried and rejected, so they are not repeated:
- narrow bands sweeping fast above 3 kHz: "electric";
- deep, buffeted noise: "a microphone in a wind storm";
- bright hissy whooshes: thin;
- clean oscillator tones carrying the sound: "8-bit video game".
Of three balances of this recipe, the roundest and most tonal was preferred."""
from __future__ import annotations

import numpy as np

from synth import band, clock, curve, decay, finish, noise, place, repitch, reverb, svf, thump, unit, vary


def bump(seconds: float, at: float, rise: float, fall: float) -> np.ndarray:
    """A smooth swell peaking at `at`, with separate rise and fall times."""
    t = clock(seconds) - at
    return np.exp(-(t / np.where(t < 0, rise, fall)) ** 2)


def glide(seconds: float, start: float, stop: float, begin: float, length: float) -> np.ndarray:
    """A pitch that holds `start`, slides to `stop` over `length` from `begin`, then holds."""
    return start * (stop / start) ** np.clip((clock(seconds) - begin) / length, 0, 1)


def push(rng, seconds: float, amp, lo: float = 90, hi: float = 450) -> np.ndarray:
    """Hollow low air: the weight of a pressure release."""
    return unit(band(noise(rng, seconds, -3), lo=lo, hi=hi) * amp)


def hoo(rng, seconds: float, pitch, amp, q: float = 16.0) -> np.ndarray:
    """A breathy pitched tone: noise ringing in a narrow band that follows `pitch`."""
    return unit(svf(noise(rng, seconds, -3), pitch, q) * amp)


def wash(rng, seconds: float, centre, amp, q: float = 1.4, top: float = 5000) -> np.ndarray:
    """Soft mid air following `centre`."""
    return unit(band(svf(noise(rng, seconds, -3), centre, q), lo=250, hi=top) * amp)


def glint(rng, seconds: float, root: float, rate: float) -> np.ndarray:
    """A thin blade ringing: uneven high partials in beating pairs."""
    t = clock(seconds)
    out = np.zeros(len(t))
    for ratio, gain in ((1.0, 1.0), (1.21, 0.8), (1.37, 0.65), (1.58, 0.5), (1.83, 0.35)):
        for detune in (0.998, 1.002):
            out += (gain * np.sin(2 * np.pi * root * ratio * detune * t + rng.uniform(0, 2 * np.pi))
                    * np.exp(-rate * np.sqrt(ratio) * t))
    return unit(out * np.clip(t / 0.002, 0, 1))


def waylay_dash_cast(rng, airy: float = 0.5, tone: float = 0.8):
    # "Fwoom-psh": pressure lets go at once, then a puff of air closes behind the caster.
    d = 0.5
    t = clock(d)
    root = vary(rng, 285, 0.05)
    low = push(rng, d, curve(d, [(0, 0), (0.012, 0.65), (0.13, 1.0), (0.21, 0.25), (0.3, 0), (d, 0)], 1.2), 60, 400)
    note = hoo(rng, d, root * (1 - 0.12 * np.clip((t - 0.12) / 0.15, 0, 1)), bump(d, 0.14, 0.09, 0.07))
    puff = wash(rng, d, 1150, bump(d, 0.32, 0.04, 0.07), 2.2, 4000)
    over = wash(rng, d, 900 + 1200 * bump(d, 0.16, 0.1, 0.15), bump(d, 0.15, 0.12, 0.16), 1.2)
    mix = 1.0 * low + tone * note + 1.0 * puff + airy * over
    return finish(reverb(rng, mix, 0.2, 0.08), drive=1.1)


def updraft_cast(rng, airy: float = 0.6, tone: float = 1.1):
    # A push at the feet, the tone leaps upward, and low air lingers below.
    d = 0.78
    rise = glide(d, vary(rng, 300, 0.05), 1300, 0.15, 0.2)
    low = push(rng, d, curve(d, [(0, 0), (0.015, 0.6), (0.1, 0.75), (0.2, 0.9), (0.3, 0.35), (0.5, 0.3),
                                 (0.7, 0.05), (d, 0)]), 60, 380)
    note = hoo(rng, d, rise, bump(d, 0.24, 0.08, 0.09), 18)
    lift = wash(rng, d, rise * 1.5, bump(d, 0.26, 0.09, 0.13))
    thin = wash(rng, d, 800 + 700 * clock(d) / d, bump(d, 0.55, 0.15, 0.12), 1.2)
    mix = 1.0 * low + tone * note + airy * lift + 0.3 * thin
    return finish(reverb(rng, mix, 0.25, 0.08), drive=1.1)


def sky_shear_cast(rng):
    # Three crescents leave a moment apart: three quick flicks of the same air.
    d = 0.45
    mix = np.zeros(len(clock(d)))
    for at, gain in ((0.05, 1.0), (0.1, 0.85), (0.15, 0.7)):
        shape = bump(d, at, 0.022, 0.07)
        mix += gain * (wash(rng, d, 700 + vary(rng, 1500) * shape, shape, 1.6, 4500)
                       + 0.4 * hoo(rng, d, 430 + 380 * shape, shape, 10))
    mix = unit(mix) + 0.45 * push(rng, d, decay(d, 0.01, 14), 110, 420)
    return finish(reverb(rng, mix, 0.2, 0.08), drive=1.1)


def sky_shear_hit(rng):
    d = 0.22
    flick = wash(rng, d, vary(rng, 1500), bump(d, 0.025, 0.012, 0.05), 1.5, 4500)
    return finish(1.0 * push(rng, d, decay(d, 0.005, 28), 120, 500) + 0.6 * flick, drive=1.1)


def gale_daggers_prepare(rng):
    # Air gathers and lifts three blades into place: three thin rings over a rising breath.
    d = 0.95
    rise = glide(d, 320, 760, 0.0, 0.5)
    swell = bump(d, 0.3, 0.2, 0.22)
    blades = unit(place(d, (0.18, 1.0, glint(rng, 0.6, vary(rng, 4700, 0.04), 9)),
                        (0.27, 0.85, glint(rng, 0.6, vary(rng, 5300, 0.04), 9)),
                        (0.36, 0.75, glint(rng, 0.6, vary(rng, 5900, 0.04), 9))))
    shimmer = unit(band(noise(rng, d), lo=6000, hi=12000) * bump(d, 0.35, 0.15, 0.25))
    mix = (0.7 * push(rng, d, bump(d, 0.2, 0.15, 0.2)) + 0.6 * hoo(rng, d, rise, swell, 12)
           + 0.4 * wash(rng, d, rise * 1.4, swell) + 0.5 * blades + 0.12 * shimmer)
    return finish(reverb(rng, mix, 0.3, 0.1, bright=6000), drive=1.1)


def gale_daggers_launch(rng):
    # A thrown blade: mostly bright metal and a hiss of cut air, with a small push behind it.
    # Three of these fire 0.15 s apart, so each is short and leaves room.
    d = 0.26
    zip_ = unit(band(noise(rng, d), lo=4000, hi=12000) * bump(d, 0.035, 0.012, 0.07))
    cut = wash(rng, d, 1800 + 2200 * bump(d, 0.03, 0.012, 0.05), bump(d, 0.03, 0.012, 0.06), 1.4, 6000)
    edge = glint(rng, d, vary(rng, 5200, 0.07), 22)
    mix = 0.8 * zip_ + 0.5 * cut + 0.6 * edge + 0.5 * push(rng, d, decay(d, 0.006, 30), 150, 420)
    return finish(mix, drive=1.1)


def zephyr_strike_cast(rng):
    # Wings beat twice, then the tone climbs and carries the caster up.
    d = 1.7
    beats = bump(d, 0.1, 0.06, 0.13) + 0.8 * bump(d, 0.42, 0.06, 0.14)
    rise = glide(d, 280, 1180, 0.6, 0.7)
    swell = bump(d, 1.0, 0.35, 0.3)
    mix = (1.0 * push(rng, d, beats + 0.6 * bump(d, 0.9, 0.4, 0.4)) + 0.6 * hoo(rng, d, 230 * (1 + 0.25 * beats), beats, 12)
           + 0.8 * hoo(rng, d, rise, swell, 14) + 0.4 * wash(rng, d, rise * 1.3, swell))
    return finish(reverb(rng, mix, 0.6, 0.15, bright=3500), drive=1.2, tail=0.06)


def zephyr_strike_land(rng):
    """The landing's explosion sound: a hollow release of pressure and air rolling outward.

    Minecraft plays explosion sounds at about 0.7 pitch, so this is pre-shifted.
    """
    d = 1.3
    t = clock(d)
    hit = thump(d, 120, 45, 12, 7.0)
    note = hoo(rng, d, 150 + vary(rng, 130) * np.exp(-5 * t), decay(d, 0.02, 5.0), 10)
    wall = wash(rng, d, 350 + vary(rng, 1500) * np.exp(-3.0 * t), decay(d, 0.03, 3.5), 1.3)
    mix = 0.6 * hit + 1.0 * push(rng, d, decay(d, 0.01, 5.0)) + 0.7 * note + 0.5 * wall
    return repitch(finish(reverb(rng, mix, 0.7, 0.18, bright=3000), drive=1.3, tail=0.08), 0.7)


# name under assets/elementalwands/sounds -> (recipe, number of variants)
SOUNDS = {
    "spell/wind/sky_shear_cast": (sky_shear_cast, 3),
    "spell/wind/sky_shear_hit": (sky_shear_hit, 3),
    "spell/wind/waylay_dash_cast": (waylay_dash_cast, 3),
    "spell/wind/updraft_cast": (updraft_cast, 2),
    "spell/wind/gale_daggers_prepare": (gale_daggers_prepare, 2),
    "spell/wind/gale_daggers_launch": (gale_daggers_launch, 3),
    "spell/wind/zephyr_strike_cast": (zephyr_strike_cast, 2),
    "spell/wind/zephyr_strike_land": (zephyr_strike_land, 2),
}
