#!/usr/bin/env python3
"""Render a screenshot inside a 3D-perspective phone.

The phone is built flat first — bezel, rounded screen, punch-hole camera, glare —
then treated as a rectangle in 3D, rotated (yaw/pitch/roll) and projected. The
flat artwork is warped onto the projected front face, the visible side faces are
filled as real quads so the body has thickness, and a blurred contact shadow is
laid underneath. No external assets.
"""
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

BODY = (26, 26, 29)          # bezel face
RIM_LIGHT = (198, 198, 205)  # polished titanium edge
RIM_DARK = (58, 58, 66)


# ---------------------------------------------------------------- flat artwork

def build_flat_phone(shot, bezel_frac=0.030, corner_frac=0.085, scale=2):
    """Screenshot -> flat RGBA phone front, at `scale`x for clean warping."""
    sw, sh = shot.size
    b = int(sw * bezel_frac)
    W, H = sw + 2 * b, sh + 2 * b
    W, H, b = W * scale, H * scale, b * scale
    shot = shot.resize((sw * scale, sh * scale), Image.LANCZOS)
    sw, sh = shot.size

    body_r = int(W * corner_frac)
    screen_r = max(2, body_r - b)

    phone = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(phone)

    # Outer polished rim, then the darker bezel face inset by a hairline.
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=body_r, fill=RIM_DARK)
    hair = max(1, int(W * 0.004))
    d.rounded_rectangle([hair, hair, W - 1 - hair, H - 1 - hair],
                        radius=body_r - hair, fill=BODY)

    # Screen, with its own rounded corners.
    mask = Image.new("L", (sw, sh), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, sw - 1, sh - 1],
                                           radius=screen_r, fill=255)
    scr = shot.convert("RGBA")
    scr.putalpha(mask)
    phone.alpha_composite(scr, (b, b))

    # Punch-hole camera.
    cr = int(W * 0.021)
    cx, cy = W // 2, b + int(cr * 1.9)
    d.ellipse([cx - cr, cy - cr, cx + cr, cy + cr], fill=(12, 12, 14, 255))
    d.ellipse([cx - cr // 2, cy - cr // 2, cx + cr // 2, cy + cr // 2],
              fill=(30, 34, 46, 255))

    # Gentle diagonal screen glare.
    glare = Image.new("L", (W, H), 0)
    gd = ImageDraw.Draw(glare)
    gd.polygon([(0, int(H * 0.10)), (W, int(-H * 0.14)),
                (W, int(H * 0.16)), (0, int(H * 0.40))], fill=34)
    glare = glare.filter(ImageFilter.GaussianBlur(W * 0.05))
    white = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    white.putalpha(glare)
    shine = Image.new("L", (W, H), 0)
    ImageDraw.Draw(shine).rounded_rectangle([0, 0, W - 1, H - 1], radius=body_r, fill=255)
    white.putalpha(Image.composite(white.getchannel("A"), Image.new("L", (W, H), 0), shine))
    phone.alpha_composite(white)
    return phone


# ------------------------------------------------------------------ projection

def rounded_outline(W, H, r, per_corner=14):
    """Points tracing a rounded rectangle, centred on the origin, clockwise."""
    hw, hh = W / 2, H / 2
    cs = [(hw - r, hh - r, 0), (-hw + r, hh - r, 90),
          (-hw + r, -hh + r, 180), (hw - r, -hh + r, 270)]
    pts = []
    for cx, cy, a0 in cs:
        for k in range(per_corner + 1):
            a = math.radians(a0 + 90 * k / per_corner)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def rotate(p, yaw, pitch, roll):
    cy_, sy_ = math.cos(yaw), math.sin(yaw)
    cp, sp = math.cos(pitch), math.sin(pitch)
    cr, sr = math.cos(roll), math.sin(roll)
    Ry = np.array([[cy_, 0, sy_], [0, 1, 0], [-sy_, 0, cy_]])
    Rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]])
    Rz = np.array([[cr, -sr, 0], [sr, cr, 0], [0, 0, 1]])
    return p @ (Rz @ Rx @ Ry).T


def project(W, H, T, yaw, pitch, roll, dist, f, radius=0.0):
    """Project the slab: 4 face corners plus the rounded silhouette rings.

    Returns (face_corners_2d, front_ring_2d, back_ring_2d, ring_normals_3d).
    The rings let the side wall follow the body's real rounded outline instead
    of cutting straight between corners, which is what makes the edge read as a
    phone rather than a flat card behind one.
    """
    hw, hh, ht = W / 2, H / 2, T / 2
    face = [[-hw, -hh, -ht], [hw, -hh, -ht], [hw, hh, -ht], [-hw, hh, -ht]]

    ring = rounded_outline(W, H, radius) if radius > 0 else \
        [(-hw, -hh), (hw, -hh), (hw, hh), (-hw, hh)]
    fr = [[x, y, -ht] for x, y in ring]
    bk = [[x, y, ht] for x, y in ring]
    # Outward normal of each silhouette point, for shading the wall.
    nrm = [[x, y, 0] for x, y in ring]
    nrm = [np.array(n) / (np.linalg.norm(n) or 1) for n in nrm]

    allp = np.array(face + fr + bk, float)
    allp = rotate(allp, yaw, pitch, roll)
    nrm = rotate(np.array(nrm, float), yaw, pitch, roll)

    z = allp[:, 2] + dist
    s = f / np.maximum(z, 1e-6)
    p2 = np.stack([allp[:, 0] * s, allp[:, 1] * s], axis=1)
    n = len(ring)
    return p2[:4], p2[4:4 + n], p2[4 + n:], nrm


def perspective_coeffs(dst, src):
    """Coefficients for PIL PERSPECTIVE: maps dst pixel -> src pixel."""
    A, b = [], []
    for (dx, dy), (sx, sy) in zip(dst, src):
        A.append([dx, dy, 1, 0, 0, 0, -sx * dx, -sx * dy])
        A.append([0, 0, 0, dx, dy, 1, -sy * dx, -sy * dy])
        b += [sx, sy]
    return np.linalg.solve(np.array(A, float), np.array(b, float))


def render(shot, size=(1600, 2200), yaw=-20, pitch=8, roll=-2,
           thickness_frac=0.052, fill=0.80, shadow=True):
    """Screenshot -> RGBA canvas holding the 3D phone."""
    if isinstance(shot, str):
        shot = Image.open(shot)
    shot = shot.convert("RGB")
    flat = build_flat_phone(shot)
    fw, fh = flat.size

    CW, CH = size
    T = fw * thickness_frac
    radius = fw * 0.085
    face, fring, bring, nrm = project(
        fw, fh, T, math.radians(yaw), math.radians(pitch), math.radians(roll),
        dist=fh * 2.6, f=fh * 2.6, radius=radius)

    # Fit the projection into the canvas (same transform for every ring).
    allp = np.vstack([face, fring, bring])
    mn, mx = allp.min(axis=0), allp.max(axis=0)
    span = mx - mn
    k = min(CW * fill / span[0], CH * fill / span[1])
    ctr, off = (mn + mx) / 2, np.array([CW / 2, CH / 2])
    face = (face - ctr) * k + off
    fring = (fring - ctr) * k + off
    bring = (bring - ctr) * k + off

    canvas = Image.new("RGBA", (CW, CH), (0, 0, 0, 0))

    if shadow:
        sh = Image.new("RGBA", (CW, CH), (0, 0, 0, 0))
        soff = np.array([CW * 0.012, CH * 0.030])
        ImageDraw.Draw(sh).polygon([tuple(p + soff) for p in fring],
                                   fill=(18, 14, 10, 120))
        canvas.alpha_composite(sh.filter(ImageFilter.GaussianBlur(CW * 0.022)))

    # Side wall: one quad per silhouette segment, shaded by its outward normal,
    # drawn only where it faces the camera.
    side = Image.new("RGBA", (CW, CH), (0, 0, 0, 0))
    sd = ImageDraw.Draw(side)
    light = np.array([-0.55, -0.35, -0.76])
    n = len(fring)
    for i in range(n):
        j = (i + 1) % n
        quad = [fring[i], fring[j], bring[j], bring[i]]
        area = 0.0
        for a in range(4):
            x1, y1 = quad[a]
            x2, y2 = quad[(a + 1) % 4]
            area += x1 * y2 - x2 * y1
        if area <= 0:
            continue                                   # facing away
        t = float(np.clip(np.dot(nrm[i], light), 0.0, 1.0)) ** 0.7
        col = tuple(int(RIM_DARK[c] + (RIM_LIGHT[c] - RIM_DARK[c]) * t) for c in range(3))
        sd.polygon([tuple(p) for p in quad], fill=col + (255,))
    canvas.alpha_composite(side)
    front = face

    # Warp the flat artwork onto the front face.
    coeffs = perspective_coeffs(
        [tuple(p) for p in front],
        [(0, 0), (fw, 0), (fw, fh), (0, fh)],
    )
    warped = flat.transform((CW, CH), Image.PERSPECTIVE, coeffs,
                            resample=Image.BICUBIC)
    canvas.alpha_composite(warped)
    return canvas


if __name__ == "__main__":
    import sys
    out = render(sys.argv[1])
    bg = Image.new("RGB", out.size, (243, 238, 228))
    bg.paste(out, (0, 0), out)
    bg.save(sys.argv[2])
    print(f"wrote {sys.argv[2]} {out.size[0]}x{out.size[1]}")
