#!/usr/bin/env python3
"""生成全局游戏音效（程序合成，无外部素材）。

产物写入 game/common/src/main/res/raw/sfx_<cue>.wav：
16-bit PCM / 22050Hz / 单声道 / 峰值归一化 0.7。
可重复运行：同一随机种子，输出逐字节一致。
"""

from __future__ import annotations

import wave
from pathlib import Path
from typing import Callable, Dict, Tuple

import numpy as np

SAMPLE_RATE = 22050
PEAK = 0.7
FADE_IN_SECONDS = 0.003
DEFAULT_FADE_OUT_SECONDS = 0.008
OUTPUT_DIR = Path(__file__).resolve().parent.parent / "game/common/src/main/res/raw"


# ---------- 基础工具 ----------


def _samples(duration: float) -> int:
    return int(round(duration * SAMPLE_RATE))


def _time(duration: float) -> np.ndarray:
    return np.arange(_samples(duration), dtype=np.float64) / SAMPLE_RATE


def sine(t: np.ndarray, freq: float) -> np.ndarray:
    return np.sin(2.0 * np.pi * freq * t)


def triangle(t: np.ndarray, freq: float) -> np.ndarray:
    phase = t * freq
    return 2.0 * np.abs(2.0 * (phase - np.floor(phase + 0.5))) - 1.0


def square(t: np.ndarray, freq: float) -> np.ndarray:
    return np.sign(sine(t, freq))


def log_sweep(t: np.ndarray, f0: float, f1: float) -> np.ndarray:
    if t.size == 0:
        return t
    freqs = f0 * (f1 / f0) ** (t / t[-1]) if t[-1] > 0.0 else np.full_like(t, f0)
    return np.sin(2.0 * np.pi * np.cumsum(freqs) / SAMPLE_RATE)


def env_decay(t: np.ndarray, tau: float, attack: float = 0.002) -> np.ndarray:
    env = np.exp(-np.maximum(t, 0.0) / max(tau, 1e-4))
    n_attack = int(round(attack * SAMPLE_RATE))
    if n_attack > 1 and n_attack <= env.size:
        env[:n_attack] *= np.linspace(0.0, 1.0, n_attack)
    return env


def env_ar(t: np.ndarray, attack: float, release: float) -> np.ndarray:
    """线性起音 + 指数释音；t<0 时归零。"""
    local = np.maximum(t, 0.0)
    rise = np.clip(local / max(attack, 1e-4), 0.0, 1.0)
    return rise * np.exp(-local / max(release, 1e-4)) * (t >= 0.0)


def note(
    t: np.ndarray,
    freq: float,
    start: float,
    duration: float,
    tau: float,
    wave: str = "sine",
    attack: float = 0.002,
) -> np.ndarray:
    local = t - start
    mask = (local >= 0.0) & (local < duration)
    local = np.where(mask, local, 0.0)
    oscillators = {"sine": sine, "triangle": triangle, "square": square}
    return oscillators[wave](local, freq) * env_decay(local, tau, attack) * mask


def _spectral_mask(x: np.ndarray, keep: Callable[[np.ndarray], np.ndarray]) -> np.ndarray:
    spectrum = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(x.size, 1.0 / SAMPLE_RATE)
    return np.fft.irfft(spectrum * keep(freqs), n=x.size)


def lowpass(x: np.ndarray, cutoff: float) -> np.ndarray:
    return _spectral_mask(x, lambda f: (f <= cutoff).astype(np.float64))


def bandpass(x: np.ndarray, lo: float, hi: float) -> np.ndarray:
    return _spectral_mask(x, lambda f: ((f >= lo) & (f <= hi)).astype(np.float64))


def finalize(signal: np.ndarray, fade_out: float = DEFAULT_FADE_OUT_SECONDS) -> np.ndarray:
    # 先做防爆音淡入淡出，再峰值归一化到 0.7：
    # 保证首尾从 0 出发（无爆音），同时每个音效实际峰值一致。
    n = signal.size
    n_in = min(int(round(FADE_IN_SECONDS * SAMPLE_RATE)), n // 2)
    if n_in > 0:
        signal[:n_in] *= np.linspace(0.0, 1.0, n_in)
    n_out = min(int(round(fade_out * SAMPLE_RATE)), n // 2)
    if n_out > 0:
        signal[-n_out:] *= np.linspace(1.0, 0.0, n_out)
    peak = float(np.max(np.abs(signal))) if signal.size else 0.0
    if peak > 1e-9:
        signal = signal * (PEAK / peak)
    return np.clip(signal, -1.0, 1.0)


# ---------- 音效配方 ----------


def sfx_ui_click(rng: np.random.Generator) -> np.ndarray:
    t = _time(0.05)
    body = sine(t, 1200.0) * env_decay(t, 0.010, attack=0.0005)
    click = rng.standard_normal(t.size) * env_decay(t, 0.002, attack=0.0002) * 0.6
    return body + click


def sfx_jump(rng: np.random.Generator) -> np.ndarray:
    t = _time(0.13)
    return log_sweep(t, 300.0, 750.0) * env_decay(t, 0.05, attack=0.004)


def sfx_coin(rng: np.random.Generator) -> np.ndarray:
    t = _time(0.14)
    first = note(t, 1320.0, 0.0, 0.07, 0.05, wave="triangle", attack=0.001)
    second = note(t, 1760.0, 0.055, 0.085, 0.06, wave="triangle", attack=0.001)
    return first + second


def sfx_star(rng: np.random.Generator) -> np.ndarray:
    t = _time(0.20)
    out = np.zeros_like(t)
    for i, freq in enumerate((1046.5, 1318.5, 1568.0)):
        out += note(t, freq, i * 0.06, 0.09, 0.045, attack=0.002)
    return out


def sfx_catch(rng: np.random.Generator) -> np.ndarray:
    t = _time(0.10)
    body = sine(t, 520.0) * env_ar(t, attack=0.015, release=0.045)
    air = bandpass(rng.standard_normal(t.size), 900.0, 2400.0) * env_ar(t, attack=0.010, release=0.035)
    return body + air * 0.35


def sfx_block(rng: np.random.Generator) -> np.ndarray:
    t = _time(0.14)
    metal = 0.65 * sine(t, 2000.0) + 0.45 * sine(t, 3000.0) + 0.15 * sine(t, 4200.0)
    return metal * env_decay(t, 0.025, attack=0.001)


def sfx_hit(rng: np.random.Generator) -> np.ndarray:
    t = _time(0.12)
    thud = sine(t, 120.0) * env_decay(t, 0.045, attack=0.001)
    dust = lowpass(rng.standard_normal(t.size), 500.0) * env_decay(t, 0.02, attack=0.001)
    return thud + dust * 0.5


def sfx_bump(rng: np.random.Generator) -> np.ndarray:
    t = _time(0.08)
    wood = sine(t, 220.0) * env_decay(t, 0.02, attack=0.001)
    tap = bandpass(rng.standard_normal(t.size), 600.0, 1800.0) * env_decay(t, 0.008, attack=0.0005)
    return wood + tap * 0.3


def sfx_pot(rng: np.random.Generator) -> np.ndarray:
    t = _time(0.22)
    drop = log_sweep(t, 700.0, 200.0) * env_decay(t, 0.09, attack=0.002)
    tail_local = t - 0.14
    mask = (tail_local >= 0.0).astype(np.float64)
    pocket = bandpass(rng.standard_normal(t.size), 250.0, 1200.0) * env_ar(tail_local, 0.008, 0.06) * mask
    return drop + pocket * 0.35


def sfx_cue(rng: np.random.Generator) -> np.ndarray:
    t = _time(0.05)
    body = sine(t, 800.0) * env_decay(t, 0.004, attack=0.0003)
    tic = bandpass(rng.standard_normal(t.size), 1500.0, 4000.0) * env_decay(t, 0.002, attack=0.0002)
    return body + tic * 0.5


def sfx_rail(rng: np.random.Generator) -> np.ndarray:
    t = _time(0.08)
    wood = sine(t, 360.0) * env_decay(t, 0.018, attack=0.001)
    overtone = sine(t, 720.0) * env_decay(t, 0.010, attack=0.001)
    return wood + overtone * 0.3


def sfx_key(rng: np.random.Generator) -> np.ndarray:
    t = _time(0.15)
    first = note(t, 1568.0, 0.0, 0.09, 0.06, attack=0.001)
    second = note(t, 2093.0, 0.045, 0.105, 0.07, attack=0.001)
    shimmer = note(t, 3136.0, 0.0, 0.12, 0.035, attack=0.001)
    return first * 0.9 + second + shimmer * 0.25


def sfx_portal(rng: np.random.Generator) -> np.ndarray:
    t = _time(0.25)
    freqs = 200.0 * (1200.0 / 200.0) ** (t / t[-1])
    phase = 2.0 * np.pi * np.cumsum(freqs) / SAMPLE_RATE
    phase += 0.35 * np.sin(2.0 * np.pi * 7.0 * t) * np.linspace(0.0, 1.0, t.size)
    return np.sin(phase) * env_decay(t, 0.15, attack=0.010)


def sfx_goal(rng: np.random.Generator) -> np.ndarray:
    t = _time(0.35)
    out = np.zeros_like(t)
    for i, freq in enumerate((523.25, 659.25, 783.99)):
        out += note(t, freq, i * 0.06, 0.16, 0.07, attack=0.003)
    cheer_local = t - 0.10
    cheer_mask = np.clip(cheer_local / 0.06, 0.0, 1.0) * (cheer_local >= 0.0)
    cheer = bandpass(rng.standard_normal(t.size), 1500.0, 5000.0) * cheer_mask * np.exp(-np.maximum(cheer_local, 0.0) / 0.14)
    return out + cheer * 0.06


def sfx_foul(rng: np.random.Generator) -> np.ndarray:
    t = _time(0.25)
    freqs = 300.0 * (150.0 / 300.0) ** (t / t[-1])
    phase = 2.0 * np.pi * np.cumsum(freqs) / SAMPLE_RATE
    buzz = lowpass(np.sign(np.sin(phase)), 900.0)
    return buzz * env_decay(t, 0.12, attack=0.005) * 0.8


def sfx_win(rng: np.random.Generator) -> np.ndarray:
    t = _time(0.45)
    out = np.zeros_like(t)
    for i, freq in enumerate((523.25, 659.25, 783.99, 1046.5)):
        out += note(t, freq, i * 0.085, 0.20, 0.09, attack=0.003)
    return out


def sfx_lose(rng: np.random.Generator) -> np.ndarray:
    t = _time(0.35)
    first = note(t, 392.0, 0.0, 0.18, 0.10, wave="triangle", attack=0.004)
    second = note(t, 261.63, 0.14, 0.21, 0.12, wave="triangle", attack=0.004)
    return first + second


def sfx_whistle(rng: np.random.Generator) -> np.ndarray:
    t = _time(0.18)
    freqs = 2100.0 + 60.0 * np.sin(2.0 * np.pi * 18.0 * t)
    phase = 2.0 * np.pi * np.cumsum(freqs) / SAMPLE_RATE
    tone = np.sin(phase) + 0.3 * np.sin(2.0 * phase)
    return tone * env_ar(t, attack=0.008, release=0.09)


# (文件名, 配方, 淡出秒数)
CUES: Dict[str, Tuple[Callable[[np.random.Generator], np.ndarray], float]] = {
    "sfx_ui_click": (sfx_ui_click, 0.005),
    "sfx_jump": (sfx_jump, 0.008),
    "sfx_coin": (sfx_coin, 0.006),
    "sfx_star": (sfx_star, 0.008),
    "sfx_catch": (sfx_catch, 0.008),
    "sfx_block": (sfx_block, 0.008),
    "sfx_hit": (sfx_hit, 0.008),
    "sfx_bump": (sfx_bump, 0.006),
    "sfx_pot": (sfx_pot, 0.010),
    "sfx_cue": (sfx_cue, 0.005),
    "sfx_rail": (sfx_rail, 0.006),
    "sfx_key": (sfx_key, 0.008),
    "sfx_portal": (sfx_portal, 0.010),
    "sfx_goal": (sfx_goal, 0.010),
    "sfx_foul": (sfx_foul, 0.010),
    "sfx_win": (sfx_win, 0.010),
    "sfx_lose": (sfx_lose, 0.010),
    "sfx_whistle": (sfx_whistle, 0.008),
}


def write_wav(path: Path, signal: np.ndarray) -> None:
    data = (np.clip(signal, -1.0, 1.0) * 32767.0).astype("<i2")
    with wave.open(str(path), "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(SAMPLE_RATE)
        handle.writeframes(data.tobytes())


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(20260913)
    total_bytes = 0
    print(f"输出目录: {OUTPUT_DIR}")
    for name, (recipe, fade_out) in CUES.items():
        signal = finalize(recipe(rng), fade_out)
        path = OUTPUT_DIR / f"{name}.wav"
        write_wav(path, signal)
        peak = float(np.max(np.abs(signal))) if signal.size else 0.0
        size = path.stat().st_size
        total_bytes += size
        print(
            f"  {path.name:<18} {signal.size / SAMPLE_RATE:5.2f}s  "
            f"{size / 1024:6.1f}KB  peak={peak:.3f}"
        )
    print(f"共 {len(CUES)} 个音效，合计 {total_bytes / 1024:.1f}KB")


if __name__ == "__main__":
    main()
