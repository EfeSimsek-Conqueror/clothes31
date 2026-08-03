#!/usr/bin/env python3
"""Clean the tmp3 product batch and cut it to the aspects the app's UI actually uses.

Pipeline per photo: un-blend the sparkle -> grain -> crop -> JPEG q92 + PNG master.

Two findings from this batch that differ from the earlier ones (see
`[[gemini-watermark-removal]]`):

  * The watermark colour really is white (C=255). A grid search on reconstruction
    residual bottoms out near 230, but that minimum is meaningless -- the residual is
    dominated by texture the Laplace fill misses. The snow shot settles it: its
    background sits at 233 and the sparkle *brightens* it to 240, which is only
    possible if C > 233.
  * `unblend.py`'s `gaussian_filter(A, 0.8)` is too much. The star's anti-aliased rim is
    1-2 px; blurring it lowers and spreads the rim alpha, which leaves a bright outline
    on dark backgrounds even when the interior is perfect. sigma=0.3 with a 0.004 cutoff
    (instead of 0.02) removes it. With 16 images x 3 channels per pixel the solve is
    well-conditioned enough not to need the smoothing.

Per-image alpha scale `lam` is measured on the star core, where the Laplace baseline is
smoothest. Three photos get lam forced to 1.0: their fill crosses a hard edge (a bench, a
leg, a wall corner) so the measurement is unreliable -- the same failure mode recorded
for the earlier batch.
"""
import json
import os
import sys

import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from unblend import R, disc, laplace_fill, patch_of  # noqa: E402

SRC = "/home/Koragan/Downloads/tmp3"
OUT = os.path.dirname(os.path.abspath(__file__))
C = 255.0

ALL = ["1nji3m", "1o7j1t", "313v6w", "3zb4z3", "8u4694", "bjbf0e", "cdhydx", "e1jy9z",
       "f585y2", "f6zuz8", "gzo2jv", "jjudz7", "lcjc66", "qqnrs5", "x5yft8", "ydtm3t"]

# Laplace fill crosses a hard edge here -> core alpha measurement is garbage.
FORCE_ONE = {"1o7j1t", "3zb4z3", "f6zuz8", "313v6w", "cdhydx"}

# stem -> (output name, mode, crop-centre x as a fraction of width)
#   square : 768x768 cut, positioned so the garment's identifying end is in frame
#   tryon  : 0.82 portrait for TryOnScreen's result slot
#
# An earlier cut scaled the two widest flat-lays down to 768 wide and grew the wood
# vertically to reach a square. Don't: replicating the edge rows smears plank grain into
# vertical streaks, and the jeans shot has white backdrop wedges in its top corners that
# streaked the whole way down. Cropping loses the cuffs but invents nothing.
JOBS = {
    "qqnrs5": ("piece_charcoal_overcoat",  "square", 0.50),
    "e1jy9z": ("piece_olive_field_jacket", "square", 0.50),
    "x5yft8": ("piece_white_oxford",       "square", 0.50),
    "313v6w": ("piece_grey_sweatshirt",    "square", 0.50),
    "gzo2jv": ("piece_white_tee",          "square", 0.50),
    "f585y2": ("piece_navy_cap",           "square", 0.50),
    # jeans span ~1100 px, wider than the 768 frame -> bias left to keep the waistband,
    # fly and pockets, which is what makes them read as jeans in a 160dp tile
    "lcjc66": ("piece_straight_jeans",     "square", 0.379),
    "jjudz7": ("piece_khaki_chinos",       "square", 0.502),
    "1nji3m": ("tryon_charcoal_overcoat",  "tryon",  0.505),
    # spares -- cleaned and kept, not wired into the harness
    "1o7j1t": ("spare_worn_khaki_chinos",  "none",   0.50),
    "3zb4z3": ("spare_worn_chambray",      "none",   0.50),
    "8u4694": ("spare_worn_grey_sweat",    "none",   0.50),
    "bjbf0e": ("spare_worn_jeans",         "none",   0.50),
    "cdhydx": ("spare_worn_olive_cap",     "none",   0.50),
    "f6zuz8": ("spare_worn_olive_bomber",  "none",   0.50),
    "ydtm3t": ("spare_worn_white_tee",     "none",   0.50),
}


def src(stem):
    return f"{SRC}/Gemini_Generated_Image_{stem}{stem}{stem[:4]}.png"


def solve_map():
    """Shared alpha map, least squares over every image and channel."""
    n = 2 * (R + 6) + 1
    mask = disc(n, R)
    obs, base = [], []
    for s in ALL:
        im, box = patch_of(src(s))
        o = im[box]
        obs.append(o)
        base.append(laplace_fill(o, mask))
    O, B = np.array(obs), np.array(base)
    d = C - B
    num = ((O - B) * d).sum(axis=(0, 3))
    den = (d * d).sum(axis=(0, 3))
    A = np.clip(np.where(den > 1e-6, num / np.maximum(den, 1e-6), 0.0), 0.0, 0.95)
    A = gaussian_filter(A, 0.3)
    A[~mask] = 0.0
    A[A < 0.004] = 0.0

    core = A > 0.85 * A.max()
    a_core = A[core].mean()
    lam = {}
    for i, s in enumerate(ALL):
        b, o = B[i][core].mean(), O[i][core].mean()
        lam[s] = 1.0 if s in FORCE_ONE else float(
            np.clip(((o - b) / (C - b)) / a_core, 0.85, 1.25))
    return A, lam


def unblend(stem, A, lam):
    im, box = patch_of(src(stem))
    o = im[box]
    a = np.clip(A * lam, 0.0, 0.95)[..., None]
    im[box] = np.where(a > 0, (o - C * a) / np.maximum(1.0 - a, 1e-3), o)
    return np.clip(im, 0, 255)


def grain(im):
    """Luma-dependent grain -- heavier in shadow, as a real sensor behaves."""
    lum = im.mean(axis=2, keepdims=True) / 255.0
    sigma = 1.9 - 1.1 * lum
    rng = np.random.default_rng(20260803)
    return np.clip(im + rng.normal(0.0, 1.0, im.shape) * sigma, 0, 255)


def cut(im, mode, cx_frac):
    h, w, _ = im.shape
    if mode == "none":
        return im
    if mode == "square":
        cx = int(w * cx_frac)
        x0 = max(0, min(w - h, cx - h // 2))
        return im[:, x0:x0 + h]
    if mode == "tryon":
        tw = int(round(h * 0.82))
        cx = int(w * cx_frac)
        x0 = max(0, min(w - tw, cx - tw // 2))
        crop = im[:, x0:x0 + tw]
        return np.asarray(
            Image.fromarray(crop.astype(np.uint8)).resize(
                (tw * 2, h * 2), Image.LANCZOS)).astype(np.float64)
    raise ValueError(mode)


def main():
    A, lam = solve_map()
    print(f"alpha map: max {A.max():.3f}  core {A[A > 0.85 * A.max()].mean():.3f}  "
          f"px {int((A > 0).sum())}")
    np.save(f"{OUT}/alpha_tmp3.npy", A)
    json.dump(lam, open(f"{OUT}/alpha_tmp3_lam.json", "w"), indent=1)

    for stem, (name, mode, cxf) in JOBS.items():
        im = cut(grain(unblend(stem, A, lam[stem])), mode, cxf)
        out = Image.fromarray(np.clip(im, 0, 255).astype(np.uint8))
        out.save(f"{OUT}/{name}.png")
        out.save(f"{OUT}/{name}.jpg", quality=92, subsampling=2, optimize=True)
        print(f"  {name:30s} {mode:6s} lam={lam[stem]:.3f}  {out.size}")


if __name__ == "__main__":
    main()
