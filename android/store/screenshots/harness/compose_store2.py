#!/usr/bin/env python3
"""Compose the Play listing set: caption band + 3D-perspective phone per panel.

Screens come straight from the device captures in raw/ — nothing inside the screen
is retouched. The device body, shadow and caption are the only added artwork.
"""
import os
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import phone3d

FONT_DIR = "/usr/share/fonts/rsms-inter-fonts"
DISPLAY_SB = f"{FONT_DIR}/InterDisplay-SemiBold.ttf"
SANS_SB = f"{FONT_DIR}/Inter-SemiBold.ttf"

# HemColors, from the app theme.
PAPER = (243, 238, 228)
INK = (20, 18, 16)
MUTED = (107, 100, 89)
BRONZE = (176, 116, 58)

ROOT = "/home/Koragan/projects/clothes31/android/store"
RAW = f"{ROOT}/screenshots/raw"
OUT = f"{ROOT}/screenshots"
PHOTOS = f"{ROOT}/photos"

# (capture, eyebrow, headline lines, dark bg?)
# Ordered for the listing: the score first, then the tools people pay for, then the
# wardrobe and the long game. Play accepts at most 8 phone screenshots, so this is a
# hard budget — `04_score_breakdown` and `09_you` were cut to make room for Try-on and
# Studio, which had no panel at all. Both are still in raw/.
PANELS = [
    ("03_score_detail",    "SCORE A LOOK",     ["One photo.", "One honest number."], False),
    ("18_tryon_result",    "TRY IT ON",        ["See it on you", "before you buy it."], False),
    ("12_roast_result",    "BRUTAL MODE",      ["Ask for it straight.", "Hem obliges."], True),
    ("14_versus_result",   "A VS B",           ["Two looks.", "One winner."], False),
    ("16_decode_result",   "DECODE",           ["Steal any look,", "piece by piece."], False),
    ("06_studio",          "YOUR CLOSET",      ["Every piece you own,", "in one place."], True),
    ("08_journal_grid",    "YOUR JOURNAL",     ["Every look you've worn,", "kept and ranked."], False),
    ("01_home",            "HEM, YOUR STYLIST", ["A read on your week,", "every morning."], False),
]

# device -> (canvas w, canvas h, caption band h)
# Phone only this round; tab7/tab10 keep the previous 8-panel set.
SPECS = {
    "phone": (1440, 2560, 470),
}


def caption(d, cw, band, eyebrow, lines, fg, accent):
    eb_size = int(cw * 0.0195)
    hl_size = int(cw * 0.062)
    f_eb = ImageFont.truetype(SANS_SB, eb_size)
    f_hl = ImageFont.truetype(DISPLAY_SB, hl_size)
    margin = int(cw * 0.085)
    y = int(band * 0.30)

    x = margin                                  # letterspaced by hand; PIL has no tracking
    for ch in eyebrow:
        d.text((x, y), ch, font=f_eb, fill=accent)
        x += d.textlength(ch, font=f_eb) + eb_size * 0.18
    y += int(eb_size * 2.25)

    for ln in lines:
        d.text((margin, y), ln, font=f_hl, fill=fg)
        y += int(hl_size * 1.16)


def panel(shot_path, eyebrow, lines, dark, spec, yaw):
    cw, ch, band = spec
    bg = INK if dark else PAPER
    fg = PAPER if dark else INK
    accent = (206, 150, 88) if dark else BRONZE

    canvas = Image.new("RGBA", (cw, ch), bg + (255,))
    caption(ImageDraw.Draw(canvas), cw, band, eyebrow, lines, fg, accent)

    # The device occupies everything under the caption band.
    dev_h = ch - band
    dev = phone3d.render(shot_path, size=(cw, dev_h), yaw=yaw, pitch=7,
                         roll=-2 if yaw < 0 else 2, fill=0.98)
    canvas.alpha_composite(dev, (0, band))
    return canvas.convert("RGB")


def feature_graphic(path):
    W, H = 1024, 500
    canvas = Image.new("RGBA", (W, H), PAPER + (255,))
    d = ImageDraw.Draw(canvas)
    f_name = ImageFont.truetype(DISPLAY_SB, 62)
    f_tag = ImageFont.truetype(DISPLAY_SB, 30)
    f_eb = ImageFont.truetype(SANS_SB, 15)

    x = 62
    d.text((x, 150), "Fitrater", font=f_name, fill=INK)
    d.text((x, 236), "An honest score for", font=f_tag, fill=INK)
    d.text((x, 276), "what you're wearing.", font=f_tag, fill=INK)
    d.rectangle([x, 336, x + 54, 340], fill=BRONZE)
    cx = x
    for ch in "SCORE · ROAST · COMPARE · TRY ON":
        d.text((cx, 362), ch, font=f_eb, fill=MUTED)
        cx += d.textlength(ch, font=f_eb) + 2.4

    for p, px, py, pw, ph in (
        (f"{PHOTOS}/man_street_grey_overshirt.jpg", 604, 96, 168, 300),
        (f"{PHOTOS}/man_street_navy_jacket.jpg", 786, 58, 196, 350),
    ):
        im = Image.open(p).convert("RGB")
        s = max(pw / im.width, ph / im.height)
        im = im.resize((int(im.width * s), int(im.height * s)), Image.LANCZOS)
        left = (im.width - pw) // 2
        im = im.crop((left, 0, left + pw, ph))
        phone3d_shadow(canvas, px, py, pw, ph)
        canvas.alpha_composite(round_corners(im, 16), (px, py))

    canvas.convert("RGB").save(path, quality=95)
    print(f"  feature graphic 1024x500 -> {os.path.basename(path)}")


def round_corners(img, r):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.size[0] - 1, img.size[1] - 1],
                                           radius=r, fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def phone3d_shadow(canvas, x, y, w, h):
    from PIL import ImageFilter
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle([x - 4, y + 10, x + w + 4, y + h + 14],
                                            radius=18, fill=(30, 24, 16, 70))
    canvas.alpha_composite(layer.filter(ImageFilter.GaussianBlur(14)))


def main():
    for dev, spec in SPECS.items():
        outdir = f"{OUT}/{dev}"
        os.makedirs(outdir, exist_ok=True)
        for old in os.listdir(outdir):
            if old.endswith(".png"):
                os.remove(os.path.join(outdir, old))
        for i, (name, eb, lines, dark) in enumerate(PANELS, 1):
            src = f"{RAW}/{dev}/{name}.png"
            if not os.path.exists(src):
                print(f"  MISSING {src}")
                continue
            yaw = -15 if i % 2 else 15          # alternate the tilt for rhythm
            img = panel(src, eb, lines, dark, spec, yaw)
            dst = f"{outdir}/{i:02d}_{name.split('_', 1)[1]}.png"
            img.save(dst)
            print(f"  {dev} {img.size[0]}x{img.size[1]} -> {os.path.basename(dst)}")
    feature_graphic(f"{ROOT}/feature-graphic-1024x500-v2.jpg")


if __name__ == "__main__":
    main()
