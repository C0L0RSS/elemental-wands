"""Shared synthesis helpers for the mod's generated sound effects.

Everything is mono float audio at 44.1 kHz built from noise, sine sweeps and
filters. Recipes take a seeded generator so each variant is repeatable.
"""
from __future__ import annotations

import zlib
from pathlib import Path

import numpy as np

SR = 44100


def rng_for(name: str, variant: int = 0) -> np.random.Generator:
    return np.random.default_rng(zlib.crc32(f"{name}#{variant}".encode()))


def samples(seconds: float) -> int:
    return int(round(SR * seconds))


def clock(seconds: float) -> np.ndarray:
    return np.arange(samples(seconds)) / SR


def curve(seconds: float, points, power: float = 1.0) -> np.ndarray:
    """Piecewise-linear envelope through (time, value) points."""
    times, values = zip(*points)
    return np.interp(clock(seconds), times, values) ** power


def decay(seconds: float, attack: float, rate: float, start: float = 0.0) -> np.ndarray:
    """Silence until start, a linear attack, then an exponential fall."""
    t = clock(seconds) - start
    rise = np.clip(t / max(attack, 1e-5), 0, 1)
    return rise * np.exp(-rate * np.maximum(t - attack, 0))


def unit(x: np.ndarray) -> np.ndarray:
    """Scale a layer to peak 1 so mix gains compare."""
    return x / (np.max(np.abs(x)) + 1e-9)


def vary(rng, value: float, spread: float = 0.08) -> float:
    """A value nudged per variant so repeats differ."""
    return value * rng.uniform(1 - spread, 1 + spread)


def noise(rng, seconds: float, tilt: float = 0.0) -> np.ndarray:
    """Unit-RMS noise. tilt is dB per octave: 0 white, -3 pink, -6 brown."""
    n = samples(seconds)
    spectrum = np.fft.rfft(rng.standard_normal(n))
    freq = np.maximum(np.fft.rfftfreq(n, 1 / SR), 20.0)
    spectrum *= (freq / 1000.0) ** (tilt / 6.0206)
    out = np.fft.irfft(spectrum, n)
    return out / np.sqrt(np.mean(out ** 2))


def band(x: np.ndarray, lo: float | None = None, hi: float | None = None, order: int = 2) -> np.ndarray:
    """Fixed zero-phase filter: keeps lo..hi with Butterworth-style slopes."""
    spectrum = np.fft.rfft(x)
    freq = np.maximum(np.fft.rfftfreq(len(x), 1 / SR), 1e-6)
    gain = np.ones_like(freq)
    if lo:
        gain /= np.sqrt(1 + (lo / freq) ** (2 * order))
    if hi:
        gain /= np.sqrt(1 + (freq / hi) ** (2 * order))
    return np.fft.irfft(spectrum * gain, len(x))


def svf(x: np.ndarray, cutoff, q: float = 0.7, mode: str = "band") -> np.ndarray:
    """State-variable filter whose cutoff may be an array, for sweeps."""
    cutoff = np.broadcast_to(np.asarray(cutoff, dtype=float), x.shape)
    f = 2.0 * np.sin(np.pi * np.clip(cutoff, 20.0, SR / 6.5) / SR)
    damp = 1.0 / q
    low = bandv = 0.0
    out = np.empty_like(x)
    pick = {"low": 0, "band": 1, "high": 2}[mode]
    for i in range(len(x)):
        low += f[i] * bandv
        high = x[i] - low - damp * bandv
        bandv += f[i] * high
        out[i] = (low, bandv, high)[pick]
    return out


def sweep(freq: np.ndarray, phase: float = 0.0) -> np.ndarray:
    """Sine whose frequency follows the given per-sample curve."""
    return np.sin(2 * np.pi * np.cumsum(freq) / SR + phase)


def thump(seconds: float, top: float, floor: float, drop: float, rate: float, attack: float = 0.004) -> np.ndarray:
    """Pitch-dropping sine: the chest hit under an impact."""
    t = clock(seconds)
    return sweep(floor + (top - floor) * np.exp(-drop * t)) * decay(seconds, attack, rate)


def bang(rng, seconds: float, bright: float, close: float, rate: float, floor: float = 250) -> np.ndarray:
    """Broadband crack whose top end closes quickly, like an explosion's first instant."""
    t = clock(seconds)
    return unit(svf(noise(rng, seconds), bright * np.exp(-close * t) + floor, 0.8, "low")
                * decay(seconds, 0.001, rate))


def flutter(rng, seconds: float, speed: float, depth: float) -> np.ndarray:
    """Slow random gain wobble around 1, the unsteadiness of a flame."""
    wobble = band(noise(rng, seconds), hi=speed, order=3)
    wobble /= np.max(np.abs(wobble)) + 1e-9
    return 1.0 + depth * wobble


def crackle(rng, seconds: float, start: float, stop: float, count: int, fade: float,
            centre: float = 3800.0, q: float = 2.5) -> np.ndarray:
    """Sparse sparks: random clicks rung through a resonant band."""
    n = samples(seconds)
    clicks = np.zeros(n)
    if count:
        when = rng.uniform(start, min(stop, seconds - 0.01), count)
        size = rng.uniform(0.25, 1.0, count) ** 2 * np.exp(-fade * (when - start))
        np.add.at(clicks, (when * SR).astype(int), size * rng.choice([-1.0, 1.0], count))
    return svf(clicks, centre, q, "band") + 0.5 * svf(clicks, centre * 1.9, q, "band")


def reverb(rng, x: np.ndarray, seconds: float, mix: float, bright: float = 3000.0,
           predelay: float = 0.012) -> np.ndarray:
    """Tail made by convolving with decaying filtered noise."""
    n = samples(seconds)
    impulse = rng.standard_normal(n) * np.exp(-6.0 * np.arange(n) / n)
    impulse = band(impulse, lo=120, hi=bright)
    impulse /= np.sqrt(np.sum(impulse ** 2))
    gap = samples(predelay)
    size = len(x) + n + gap
    wet = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(impulse, size), size)
    dry = np.concatenate([x, np.zeros(n + gap)])
    return dry + mix * np.roll(wet, gap)


def place(seconds: float, *layers) -> np.ndarray:
    """Mix (offset_seconds, gain, audio) layers into one buffer."""
    out = np.zeros(samples(seconds))
    for offset, gain, audio in layers:
        at = samples(offset)
        end = min(len(out), at + len(audio))
        out[at:end] += gain * audio[: end - at]
    return out


def repitch(x: np.ndarray, played_at: float) -> np.ndarray:
    """Pre-shift audio so it sounds as designed when the game plays it at this pitch."""
    x = band(x, hi=0.45 * SR * min(played_at, 1.0))
    count = int(len(x) * played_at)
    return np.interp(np.linspace(0, len(x) - 1, count), np.arange(len(x)), x)


def finish(x: np.ndarray, drive: float = 1.4, peak: float = 0.8, tail: float = 0.04) -> np.ndarray:
    """Remove rumble below hearing, saturate for punch, trim, fade and level."""
    x = band(x, lo=28)
    x = np.tanh(x / (np.max(np.abs(x)) + 1e-9) * drive)
    loud = np.nonzero(np.abs(x) > 0.002 * np.max(np.abs(x)))[0]
    x = x[: min(len(x), loud[-1] + samples(tail))]
    fade_in, fade_out = samples(0.002), samples(tail)
    x[:fade_in] *= np.linspace(0, 1, fade_in)
    x[-fade_out:] *= np.linspace(1, 0, fade_out)
    return x / np.max(np.abs(x)) * peak


def write_ogg(path: Path, x: np.ndarray) -> None:
    import soundfile as sf

    path.parent.mkdir(parents=True, exist_ok=True)
    with sf.SoundFile(str(path), "w", SR, 1, format="OGG", subtype="VORBIS") as out:
        for start in range(0, len(x), 8192):
            out.write(x[start:start + 8192].astype(np.float32))


def write_wav(path: Path, x: np.ndarray) -> None:
    import wave

    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(SR)
        out.writeframes((np.clip(x, -1, 1) * 32767).astype("<i2").tobytes())
