#!/usr/bin/env python3
"""Recover the pixels under the Gemini sparkle by solving for its alpha map.

The sparkle is a fixed-position alpha composite of a near-white colour:
    O = B*(1-A) + C*A
A is identical in every image (same shape, same corner offset), so with several
images we can solve for it and invert the blend, recovering the true background
texture instead of synthesising a plausible replacement.

Per pixel we have one equation per image per channel:
    O_i - B0_i = A * (C - B0_i) + noise
where B0_i is a smooth Laplace fill of the hole from its boundary. The unknown
texture that B0 misses is independent across images, so least squares over the
set averages it out, and images whose background sits close to C contribute
little weight automatically.
"""
import sys
import numpy as np
from PIL import Image
from scipy.sparse import lil_matrix, csr_matrix
from scipy.ndimage import gaussian_filter
from scipy.sparse.linalg import spsolve

CX_OFF, CY_OFF = 117, 121   # sparkle centre, measured from the bottom-right corner
R = 42                       # radius of the region we solve over
C = 255.0                    # watermark colour (white)


def disc(n, r):
    yy, xx = np.ogrid[:n, :n]
    c = n // 2
    return ((xx - c) ** 2 + (yy - c) ** 2) <= r * r


def laplace_fill(patch, mask):
    """Smooth harmonic interpolation of `mask` from the surrounding known pixels."""
    h, w = mask.shape
    ys, xs = np.nonzero(mask)
    idx = -np.ones((h, w), np.int64)
    idx[ys, xs] = np.arange(len(ys))
    n = len(ys)

    A = lil_matrix((n, n))
    for k, (y, x) in enumerate(zip(ys, xs)):
        A[k, k] = 4.0
        for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
            j = idx[ny, nx]
            if j >= 0:
                A[k, j] = -1.0
    A = csr_matrix(A)

    out = patch.astype(np.float64).copy()
    for ch in range(patch.shape[2]):
        d = patch[..., ch].astype(np.float64)
        b = np.zeros(n)
        for k, (y, x) in enumerate(zip(ys, xs)):
            v = 0.0
            for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
                if idx[ny, nx] < 0:
                    v += d[ny, nx]
            b[k] = v
        out[ys, xs, ch] = spsolve(A, b)
    return out


def patch_of(path, pad=6):
    im = np.array(Image.open(path).convert("RGB")).astype(np.float64)
    h, w = im.shape[:2]
    cx, cy = w - CX_OFF, h - CY_OFF
    half = R + pad
    box = (slice(cy - half, cy + half + 1), slice(cx - half, cx + half + 1))
    return im, box


def main(paths, out_dir):
    n = 2 * (R + 6) + 1
    mask = disc(n, R)

    obs, base = [], []
    for p in paths:
        im, box = patch_of(p)
        o = im[box]
        obs.append(o)
        base.append(laplace_fill(o, mask))
        print(f"laplace fill done: {p}")

    # Weighted least squares for the shared alpha map, over all images x channels.
    num = np.zeros((n, n))
    den = np.zeros((n, n))
    for o, b in zip(obs, base):
        d = C - b                      # per-channel contrast against the watermark
        num += ((o - b) * d).sum(axis=2)
        den += (d * d).sum(axis=2)
    A = np.where(den > 1e-6, num / np.maximum(den, 1e-6), 0.0)
    A = np.clip(A, 0.0, 0.92)
    A = gaussian_filter(A, 0.8)
    A[~mask] = 0.0
    A[A < 0.02] = 0.0                  # kill estimation noise outside the star
    print(f"alpha map: max {A.max():.3f}  mean-in-star {A[A > 0].mean():.3f}  "
          f"covered px {int((A > 0).sum())}")

    np.save(f"{out_dir}/alpha.npy", A)
    Image.fromarray((A / max(A.max(), 1e-6) * 255).astype(np.uint8)).save(
        f"{out_dir}/alpha_preview.png")

    for i, p in enumerate(paths):
        im, box = patch_of(p)
        o = im[box]
        rec = (o - C * A[..., None]) / np.maximum(1.0 - A[..., None], 1e-3)
        im[box] = np.where(A[..., None] > 0, rec, o)
        out = Image.fromarray(np.clip(im, 0, 255).astype(np.uint8))
        out.save(f"{out_dir}/u{i + 1}.png")
        print(f"wrote {out_dir}/u{i + 1}.png")


if __name__ == "__main__":
    main(sys.argv[2:], sys.argv[1])
