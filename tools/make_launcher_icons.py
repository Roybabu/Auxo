#!/usr/bin/env python3
"""Builds the Android adaptive launcher icon layers from the approved logo image.

    python3 tools/make_launcher_icons.py

Reads design/invision-logo-source.png (the approved logo on its white tile), cuts the logo
mark out of the tile without changing a pixel of its artwork, and writes for every density:
  res/mipmap-<d>/ic_launcher_foreground.png   the logo, centred inside the 66dp safe zone
  res/mipmap-<d>/ic_launcher_monochrome.png   the same shape as one colour, for themed icons
The white tile becomes the icon background (values/colors.xml: ic_launcher_background).
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "design" / "invision-logo-source.png"
RES = ROOT / "app" / "src" / "main" / "res"

# The area of the source that holds only the logo (no tile edge, shadow or caption).
CROP = (115, 95, 335, 315)
# Adaptive icons are 108dp square; launchers only guarantee the inner 66dp circle.
CANVAS_DP = 108
SAFE_RADIUS_DP = 33
# The logo's width on the original tile was about 72% of the tile. Launchers show about 72dp of
# the 108dp canvas, so the logo is 72% of that.
LOGO_WIDTH_DP = 0.72 * 72
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}


def cut_out_logo(rgb: np.ndarray):
    """Colour-to-alpha against white: the logo keeps its colours, the white tile becomes clear."""
    c = rgb.astype(np.float64)
    coverage = (255.0 - c.min(axis=2)) / 255.0
    alpha = np.clip((coverage - 0.04) / (0.5 - 0.04), 0.0, 1.0)
    safe = np.maximum(alpha, 1e-6)[..., None]
    colour = np.clip((c - 255.0 * (1.0 - alpha[..., None])) / safe, 0, 255)
    # Where the logo is solid keep the original pixel exactly.
    solid = alpha >= 1.0
    colour[solid] = c[solid]
    return colour, alpha


def main() -> int:
    source = Image.open(SOURCE).convert("RGB").crop(CROP)
    rgb = np.array(source)
    colour, alpha = cut_out_logo(rgb)

    # Check: laid back on white, the cut-out must look like the original.
    back = colour * alpha[..., None] + 255.0 * (1.0 - alpha[..., None])
    error = np.abs(back - rgb)
    print(f"cut-out vs original on white: mean error {error.mean():.2f}, max {error.max():.0f} (of 255)")

    ys, xs = np.where(alpha > 0.5)
    x0, x1, y0, y1 = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1
    centre = ((x0 + x1) / 2.0, (y0 + y1) / 2.0)
    scale_dp = LOGO_WIDTH_DP / (x1 - x0)  # dp per source pixel
    radius = np.hypot(xs - centre[0], ys - centre[1]).max() * scale_dp
    print(f"logo {x1 - x0}x{y1 - y0}px, width {LOGO_WIDTH_DP:.1f}dp, farthest point {radius:.1f}dp from centre (limit {SAFE_RADIUS_DP}dp)")
    if radius > SAFE_RADIUS_DP:
        print("logo does not fit the safe zone", file=sys.stderr)
        return 1

    rgba = np.dstack([colour, alpha * 255.0]).round().astype(np.uint8)
    logo = Image.fromarray(rgba, "RGBA").convert("RGBa")  # premultiplied, so resizing leaves no halo
    mono_alpha = Image.fromarray((alpha * 255.0).round().astype(np.uint8), "L")

    for name, density in DENSITIES.items():
        canvas_px = round(CANVAS_DP * density)
        factor = scale_dp * density  # canvas pixels per source pixel
        size = (round(source.width * factor), round(source.height * factor))
        # Place the logo's centre on the canvas centre.
        left = round(canvas_px / 2 - centre[0] * factor)
        top = round(canvas_px / 2 - centre[1] * factor)

        layer = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
        resized = logo.resize(size, Image.LANCZOS).convert("RGBA")
        layer.alpha_composite(resized, (left, top)) if left >= 0 and top >= 0 else layer.paste(resized, (left, top))

        mono = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
        mono_shape = mono_alpha.point(lambda v: 0 if v < 100 else min(255, (v - 100) * 3))  # solid shape only, no soft shadow.resize(size, Image.LANCZOS)
        mono.paste((0, 0, 0, 255), (left, top), mono_shape)

        folder = RES / f"mipmap-{name}"
        folder.mkdir(parents=True, exist_ok=True)
        layer.save(folder / "ic_launcher_foreground.png", optimize=True)
        mono.save(folder / "ic_launcher_monochrome.png", optimize=True)
        print(f"{name}: {canvas_px}px canvas, logo {size[0]}x{size[1]}px at ({left},{top})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
