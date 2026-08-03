#!/usr/bin/env python3
"""Remove the Gemini sparkle watermark by exemplar patch search + Poisson blend.

The watermark sits at a fixed offset from the bottom-right corner: (W-117, H-121).
Strategy: cut a hole around it, find the best-matching donor region elsewhere in the
image by comparing the ring of known pixels around the hole, then Poisson-blend the
donor's gradients into the hole so the seam disappears.
"""
import sys
import numpy as np
from PIL import Image
from scipy.sparse import lil_matrix, csr_matrix
from scipy.sparse.linalg import spsolve

CX_OFF, CY_OFF = 117, 121   # watermark centre, measured from bottom-right corner
R_HOLE = 40                  # hole radius (sparkle is ~28px half-extent + soft halo)
RING = 26                    # width of the known-pixel ring used for matching


def build_mask(h, w, cx, cy, r):
    yy, xx = np.ogrid[:h, :w]
    return ((xx - cx) ** 2 + (yy - cy) ** 2) <= r * r


def find_donor(img, cx, cy, r, ring, search=320, step=2, min_shift=None, tol=1.25):
    """Best (dx, dy) such that img shifted by it matches the ring around the hole.

    Two stages: first score every candidate on the ring of known pixels, then among
    all candidates within `tol` of the best score, keep the one whose *interior* is
    least structured. Ring matching alone happily donates a leaf or a grout joint —
    it only sees the border — and a cloned object is a more obvious tell than the
    watermark was.
    """
    h, w = img.shape[:2]
    half = r + ring
    y0, y1, x0, x1 = cy - half, cy + half + 1, cx - half, cx + half + 1
    box = img[y0:y1, x0:x1].astype(np.float32)
    n = box.shape[0]
    yy, xx = np.ogrid[:n, :n]
    d2 = (xx - half) ** 2 + (yy - half) ** 2
    ring_mask = (d2 > (r + 4) ** 2) & (d2 <= half * half)
    hole_mask = d2 <= r * r
    if min_shift is None:
        min_shift = 2 * r + 8

    gy, gx = np.gradient(img.astype(np.float32))
    grad = np.hypot(gx, gy)

    cands = []
    for dy in range(-search, search + 1, step):
        for dx in range(-search, search + 1, step):
            if dx * dx + dy * dy < min_shift ** 2:
                continue                      # donor must not overlap the hole
            sy0, sx0 = y0 + dy, x0 + dx
            if sy0 < 0 or sx0 < 0 or sy0 + n > h or sx0 + n > w:
                continue
            cand = img[sy0:sy0 + n, sx0:sx0 + n].astype(np.float32)
            err = np.mean((cand[ring_mask] - box[ring_mask]) ** 2)
            struct = np.percentile(grad[sy0:sy0 + n, sx0:sx0 + n][hole_mask], 99)
            cands.append((err, struct, dx, dy))

    if not cands:
        return None, None
    best_err = min(c[0] for c in cands)
    short = [c for c in cands if c[0] <= best_err * tol]
    err, struct, dx, dy = min(short, key=lambda c: c[1])
    return (dx, dy), err


def poisson_blend(dst, src, mask):
    """Solve laplace(out) = laplace(src) inside mask, out = dst on the boundary."""
    h, w = mask.shape
    ys, xs = np.nonzero(mask)
    idx = -np.ones((h, w), np.int64)
    idx[ys, xs] = np.arange(len(ys))
    n = len(ys)

    A = lil_matrix((n, n))
    out = dst.astype(np.float64).copy()
    for ch in range(3):
        b = np.zeros(n)
        s, d = src[..., ch].astype(np.float64), dst[..., ch].astype(np.float64)
        for k, (y, x) in enumerate(zip(ys, xs)):
            if ch == 0:
                A[k, k] = 4.0
            v = 4.0 * s[y, x]
            for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
                v -= s[ny, nx]
                j = idx[ny, nx]
                if j >= 0:
                    if ch == 0:
                        A[k, j] = -1.0
                else:
                    v += d[ny, nx]
            b[k] = v
        if ch == 0:
            A = csr_matrix(A)
        sol = spsolve(A, b)
        out[ys, xs, ch] = np.clip(sol, 0, 255)
    return out


def main(path, out_path, r=R_HOLE):
    im = Image.open(path).convert("RGB")
    a = np.array(im)
    h, w = a.shape[:2]
    cx, cy = w - CX_OFF, h - CY_OFF

    gray = a.mean(axis=2)
    off, err = find_donor(gray, cx, cy, r, RING)
    if off is None:
        sys.exit(f"{path}: no donor patch found")
    dx, dy = off
    print(f"{path}: donor offset ({dx:+d},{dy:+d}) ring-rmse {err ** 0.5:.2f}")

    half = r + 3
    y0, y1, x0, x1 = cy - half, cy + half + 1, cx - half, cx + half + 1
    dst = a[y0:y1, x0:x1]
    src = a[y0 + dy:y1 + dy, x0 + dx:x1 + dx]
    m = build_mask(dst.shape[0], dst.shape[1], half, half, r)

    a = a.astype(np.float64)
    a[y0:y1, x0:x1] = poisson_blend(dst, src, m)
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)).save(out_path)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else R_HOLE)
