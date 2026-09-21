"""Generates SpendLocker's app icon (a padlock/vault mark) as a 1024x1024 PNG,
then derives the platform-specific .icns (macOS) and .ico (Windows) files from it.
Re-run this after Pillow is installed: python3 generate_icon.py
"""
import math
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw

SIZE = 1024
NAVY_DARK = (15, 23, 42, 255)      # slate-900
NAVY_LIGHT = (30, 41, 59, 255)     # slate-800
WHITE = (248, 250, 252, 255)       # slate-50
ACCENT = (37, 99, 235, 255)        # blue-600, matches the app's accent color

OUT_DIR = Path(__file__).parent


def rounded_rect_mask(size, radius):
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return mask


def vertical_gradient(size, top, bottom):
    grad = Image.new("RGBA", (1, size), color=0)
    for y in range(size):
        t = y / (size - 1)
        pixel = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(4))
        grad.putpixel((0, y), pixel)
    return grad.resize((size, size))


def build_icon():
    bg = vertical_gradient(SIZE, NAVY_LIGHT, NAVY_DARK)
    mask = rounded_rect_mask(SIZE, radius=200)
    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    canvas.paste(bg, (0, 0), mask)

    draw = ImageDraw.Draw(canvas)
    cx = SIZE // 2

    # Shackle: a thick white arc forming the lock's U-shape.
    shackle_w = 340
    shackle_top = 230
    shackle_bottom = 560
    shackle_stroke = 56
    bbox = [cx - shackle_w // 2, shackle_top, cx + shackle_w // 2, shackle_bottom + shackle_w // 2]
    draw.arc(bbox, start=180, end=360, fill=WHITE, width=shackle_stroke)
    # Square off the arc's open ends so they read as straight shackle legs.
    leg_h = 130
    draw.rectangle([cx - shackle_w // 2 - shackle_stroke // 2, shackle_bottom,
                    cx - shackle_w // 2 + shackle_stroke // 2, shackle_bottom + leg_h], fill=WHITE)
    draw.rectangle([cx + shackle_w // 2 - shackle_stroke // 2, shackle_bottom,
                    cx + shackle_w // 2 + shackle_stroke // 2, shackle_bottom + leg_h], fill=WHITE)

    # Body: rounded white rectangle.
    body_w, body_h = 460, 380
    body_top = 520
    body = [cx - body_w // 2, body_top, cx + body_w // 2, body_top + body_h]
    draw.rounded_rectangle(body, radius=64, fill=WHITE)

    # Keyhole: accent-blue circle + tapered slot.
    hole_r = 48
    hole_cy = body_top + 150
    draw.ellipse([cx - hole_r, hole_cy - hole_r, cx + hole_r, hole_cy + hole_r], fill=ACCENT)
    slot_w = 46
    slot_top = hole_cy + 10
    slot_bottom = body_top + body_h - 70
    draw.polygon([
        (cx - slot_w // 2, slot_top),
        (cx + slot_w // 2, slot_top),
        (cx + slot_w // 4, slot_bottom),
        (cx - slot_w // 4, slot_bottom),
    ], fill=ACCENT)

    canvas.save(OUT_DIR / "icon.png")
    print("wrote", OUT_DIR / "icon.png")


def build_icns():
    iconset = OUT_DIR / "icon.iconset"
    iconset.mkdir(exist_ok=True)
    sizes = [16, 32, 64, 128, 256, 512, 1024]
    src = OUT_DIR / "icon.png"
    for size in sizes:
        subprocess.run(["sips", "-z", str(size), str(size), str(src),
                         "--out", str(iconset / f"icon_{size}x{size}.png")], check=True, capture_output=True)
        if size <= 512:
            subprocess.run(["sips", "-z", str(size * 2), str(size * 2), str(src),
                             "--out", str(iconset / f"icon_{size}x{size}@2x.png")], check=True, capture_output=True)
    subprocess.run(["iconutil", "-c", "icns", str(iconset), "-o", str(OUT_DIR / "icon.icns")], check=True)
    print("wrote", OUT_DIR / "icon.icns")


def build_ico():
    img = Image.open(OUT_DIR / "icon.png").convert("RGBA")
    img.save(OUT_DIR / "icon.ico", sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    print("wrote", OUT_DIR / "icon.ico")


if __name__ == "__main__":
    build_icon()
    build_ico()
    if sys.platform == "darwin":
        build_icns()
    else:
        print("Skipping .icns (macOS-only, needs iconutil) — run this script on a Mac to produce it.")
