#!/usr/bin/env python3
"""Fit a clean, shared alpha map for the Gemini sparkle, then un-blend all images.

A per-pixel least-squares fit overfits: the texture each estimator image happens to
have under the star gets baked into the map and then injected into the others. Two
things fix that — take the *median* across images (rejecting the ones whose Laplace
fill crosses a hard edge), and exploit the sparkle's 4-fold symmetry, averaging the
map over the 8 dihedral transforms so noise drops by ~sqrt(8) while the star itself
is untouched.
"""
import sys
import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter

from unblend import patch_of, disc, laplace_fill, R, C


def per_image_alpha(path, mask):
    im, box = patch_of(path)
    o = im[box]
    b = laplace_fill(o, mask)
    d = C - b
    a = ((o - b) * d).sum(axis=2) / np.maximum((d * d).sum(axis=2), 1e-6)
    # Where the background sits close to the watermark colour the estimate is
    # near-singular; flag it so the median can ignore it.
    conf = np.sqrt((d * d).sum(axis=2))
    return np.where(mask, a, 0.0), conf


def symmetrize(A):
    """Average over the dihedral group of the square (the sparkle is 4-fold)."""
    v = [A, np.fliplr(A), np.flipud(A), np.flipud(np.fliplr(A))]
    v += [x.T for x in v]
    return np.mean(v, axis=0)


def main(paths, out_dir):
    n = 2 * (R + 6) + 1
    mask = disc(n, R)

    alphas, confs = [], []
    for p in paths:
        a, c = per_image_alpha(p, mask)
        alphas.append(a)
        confs.append(c)
        print(f"  estimated from {p.split('_')[-1][:12]}")
    alphas = np.array(alphas)
    confs = np.array(confs)

    # Median across images, ignoring low-confidence (near-white background) pixels.
    good = confs > np.percentile(confs, 25)
    A = np.where(good, alphas, np.nan)
    with np.errstate(all="ignore"):
        A = np.nanmedian(A, axis=0)
    A = np.nan_to_num(A)

    A = symmetrize(A)
    A = gaussian_filter(A, 1.0)
    A = np.clip(A, 0.0, 0.95)
    A[~mask] = 0.0
    A[A < 0.012] = 0.0
    print(f"alpha: max {A.max():.3f}  core mean {A[A > 0.3 * A.max()].mean():.3f}  "
          f"support {int((A > 0).sum())}px")

    np.save(f"{out_dir}/alpha_fit.npy", A)
    Image.fromarray((A / A.max() * 255).astype(np.uint8)).save(
        f"{out_dir}/alpha_fit_preview.png")

    for i, p in enumerate(paths):
        im, box = patch_of(p)
        o = im[box]
        rec = (o - C * A[..., None]) / np.maximum(1.0 - A[..., None], 1e-3)
        im[box] = np.where(A[..., None] > 0, rec, o)
        Image.fromarray(np.clip(im, 0, 255).astype(np.uint8)).save(
            f"{out_dir}/f{i + 1}.png")
    print(f"wrote {out_dir}/f1..f{len(paths)}.png")


if __name__ == "__main__":
    main(sys.argv[2:], sys.argv[1])
