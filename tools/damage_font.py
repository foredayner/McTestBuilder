#!/usr/bin/env python3
"""
Draws the damage-number glyphs (src/main/resources/assets/minecraft-ai-build-assistant/textures/misc/damage_numbers.png)
and their widths (.../damage_numbers.json): three styles x three strengths, the characters 0-9 . M I S and a sparkle.

  classic : heavy round digits, thick black outline, a warm top-to-bottom gradient
  flame   : leaning bold digits, a white inner rim, a dark drop shadow, red-to-purple
  ice     : narrow angular digits, a navy outline, sky-to-white with a glint across the top

Fonts: Noto Sans CJK Black (OFL) and DejaVu Sans (free licence), only as shapes drawn into the picture.
Run: python3 tools/damage_font.py
"""
import json
import os

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

CHARS = "0123456789.MIS*"
CELL_W, CELL_H = 56, 64
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "minecraft-ai-build-assistant",
                   "textures", "misc")

NOTO = "/usr/share/fonts/opentype/noto/NotoSansCJK-Black.ttc"
DEJAVU_BO = "/usr/share/fonts/truetype/dejavu/DejaVuSans-BoldOblique.ttf"
DEJAVU_CB = "/usr/share/fonts/truetype/dejavu/DejaVuSansCondensed-Bold.ttf"

# strength: low, mid, high -> (top colour, bottom colour)
STYLES = {
    "classic": {"font": (NOTO, 44, 7), "outline": 5, "edge": (25, 18, 10),
                "tiers": [((255, 255, 255), (255, 232, 140)), ((255, 240, 120), (255, 150, 30)), ((255, 170, 70), (220, 30, 20))]},
    "flame": {"font": (DEJAVU_BO, 44, 2), "outline": 4, "edge": (40, 0, 30),
              "tiers": [((255, 200, 230), (230, 90, 170)), ((255, 120, 90), (200, 30, 120)), ((255, 80, 60), (120, 20, 160))]},
    "ice": {"font": (DEJAVU_CB, 46, 2), "outline": 4, "edge": (15, 30, 80),
            "tiers": [((240, 250, 255), (170, 210, 245)), ((210, 245, 255), (80, 170, 240)), ((255, 255, 255), (40, 120, 230))]},
}


def gradient(top, bottom, w, h):
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        t = y / max(1, h - 1)
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        for x in range(w):
            px[x, y] = c + (255,)
    return img


def mask_of(ch, font_spec):
    path, size, index = font_spec
    try:
        font = ImageFont.truetype(path, size, index=index) if path.endswith(".ttc") else ImageFont.truetype(path, size)
    except OSError:
        font = ImageFont.truetype(path, size)
    m = Image.new("L", (CELL_W, CELL_H), 0)
    d = ImageDraw.Draw(m)
    if ch == "*":
        # a four-pointed sparkle
        cx, cy, r, q = CELL_W / 2, CELL_H / 2, 20, 6
        d.polygon([(cx, cy - r), (cx + q, cy - q), (cx + r, cy), (cx + q, cy + q), (cx, cy + r), (cx - q, cy + q), (cx - r, cy), (cx - q, cy - q)],
                  fill=255)
        return m
    box = d.textbbox((0, 0), ch, font=font)
    w, h = box[2] - box[0], box[3] - box[1]
    # bottom-aligned on a common baseline so digits and letters line up
    x = (CELL_W - w) / 2 - box[0]
    y = CELL_H - 10 - h - box[1] if ch != "." else CELL_H - 10 - box[3]
    if ch != ".":
        y = CELL_H - 10 - box[3]
    d.text((x, y), ch, font=font, fill=255)
    return m


def dilate(m, r):
    return m.filter(ImageFilter.MaxFilter(r * 2 + 1)) if r > 0 else m


def glyph(style, tier, ch):
    s = STYLES[style]
    top, bottom = s["tiers"][tier]
    m = mask_of(ch, s["font"])
    out = Image.new("RGBA", (CELL_W, CELL_H), (0, 0, 0, 0))
    if style == "flame":
        shadow = dilate(m, s["outline"] + 1)
        shadow = ImageChops.offset(shadow, 2, 3)
        out.paste(Image.new("RGBA", out.size, s["edge"] + (255,)), (0, 0), shadow)
        out.paste(Image.new("RGBA", out.size, s["edge"] + (255,)), (0, 0), dilate(m, s["outline"]))
        out.paste(Image.new("RGBA", out.size, (255, 255, 255, 255)), (0, 0), dilate(m, 2))
    else:
        out.paste(Image.new("RGBA", out.size, s["edge"] + (255,)), (0, 0), dilate(m, s["outline"]))
    out.paste(gradient(top, bottom, CELL_W, CELL_H), (0, 0), m)
    if style == "ice":
        # a glint: a light band slanting across the upper part
        glint = Image.new("L", out.size, 0)
        gd = ImageDraw.Draw(glint)
        gd.polygon([(0, 22), (CELL_W, 10), (CELL_W, 18), (0, 30)], fill=150)
        glint = ImageChops.multiply(glint, m)
        out.paste(Image.new("RGBA", out.size, (255, 255, 255, 255)), (0, 0), glint)
    if style == "classic":
        # a soft shine on the upper half
        shine = Image.new("L", out.size, 0)
        ImageDraw.Draw(shine).rectangle([0, 0, CELL_W, CELL_H * 0.42], fill=70)
        shine = ImageChops.multiply(shine, m.filter(ImageFilter.MinFilter(5)))
        out.paste(Image.new("RGBA", out.size, (255, 255, 255, 255)), (0, 0), shine)
    return out


def width_of(img):
    alpha = img.split()[3]
    box = alpha.getbbox()
    if box is None:
        return 0, 0
    return box[0], box[2]


def main():
    names = list(STYLES)
    sheet = Image.new("RGBA", (CELL_W * len(CHARS), CELL_H * len(names) * 3), (0, 0, 0, 0))
    metrics = {"cell": [CELL_W, CELL_H], "chars": CHARS, "styles": names, "glyphs": {}}
    for si, style in enumerate(names):
        for tier in range(3):
            row = si * 3 + tier
            for ci, ch in enumerate(CHARS):
                g = glyph(style, tier, ch)
                sheet.paste(g, (ci * CELL_W, row * CELL_H), g)
                if tier == 1:
                    left, right = width_of(g)
                    metrics["glyphs"].setdefault(style, {})[ch] = [left, right]
    os.makedirs(OUT, exist_ok=True)
    sheet.save(os.path.join(OUT, "damage_numbers.png"))
    with open(os.path.join(OUT, "damage_numbers.json"), "w") as f:
        json.dump(metrics, f, indent=1)


if __name__ == "__main__":
    main()
