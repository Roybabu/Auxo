#!/usr/bin/env python3
"""Cuts the approved Auxo artwork out of the reference board. Nothing is redrawn.

Input : docs/branding/auxo-logo-reference.png (the approved presentation board)
Output: design/auxo/*.png and the Android drawables under app/src/main/res/drawable-nodpi

What it does to the pixels:
  * crops (the symbol, wordmark, tagline and colours are the original pixels);
  * on the panels with a flat background (white, deep green, light grey), turns that background into transparency. Solid pixels keep their exact
    colour and stay fully opaque; only the anti-aliased edge pixels get partial transparency;
  * for the symbol on its own, keeps only the pixels that belong to the symbol, so the neighbouring
    wordmark letter is left out;
  * for the launcher icon, scales the approved tile (docs/branding/auxo-icon-transparent.png, used as it is)
    down, never up, so the whole tile sits inside the adaptive icon's safe circle.
It never recolours, stretches, outlines or simplifies the artwork.

Needs: numpy, scipy, Pillow.   Run from the repository root:  python3 tools/extract_auxo_logo.py
"""
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage as ndi

ROOT = Path(__file__).resolve().parent.parent
REFERENCE = ROOT / "docs/branding/auxo-logo-reference.png"
ICON = ROOT / "docs/branding/auxo-icon-transparent.png"   # the approved launcher icon tile, background already removed
DESIGN = ROOT / "design/auxo"
RES = ROOT / "app/src/main/res/drawable-nodpi"

# Panels of the board, in pixels of the 2172 x 724 original.
LEFT = (0, 0, 1160, 724)            # large horizontal logo with tagline, on white
VERTICAL = (1674, 0, 2172, 450)     # vertical logo with tagline, on white
DARK = (1166, 454, 1670, 724)       # horizontal logo on deep green
MONO = (1674, 454, 2172, 724)       # grey-scale logo on light grey

SAFE_RADIUS_PX = 130                # adaptive icon: 66 dp circle on a 432 px canvas is 132 px; keep a margin
CANVAS = 432


def to_alpha_on_white(rgb: np.ndarray) -> np.ndarray:
    """RGBA from an RGB image whose background is white. Solid pixels are untouched."""
    px = rgb.astype(float)
    ink = 255.0 - px.min(axis=2)                       # how far each pixel is from white
    solid = ink > 70                                   # clearly artwork, not an edge
    # Colour of the nearest solid pixel, used to work out how much of an edge pixel is artwork.
    _, (iy, ix) = ndi.distance_transform_edt(~solid, return_indices=True)
    nearest = px[iy, ix]
    nearest_ink = np.maximum(255.0 - nearest.min(axis=2), 1.0)
    alpha = np.clip(ink / nearest_ink, 0.0, 1.0)
    alpha[solid] = 1.0
    alpha[ink < 10] = 0.0                       # the board has faint haze (up to 5) around the art
    out = np.where(solid[..., None], px, nearest)      # edge pixels take the colour of the artwork beside them
    return np.dstack([out, alpha * 255.0]).round().clip(0, 255).astype(np.uint8)


def to_alpha_on_colour(rgb: np.ndarray, background: tuple, solid_distance: float, noise: float = 16) -> np.ndarray:
    """Like to_alpha_on_white, for a panel with a flat background colour (deep green, light grey).
    Pixels far from the background keep their exact colour; only the soft edge pixels become partly transparent."""
    px = rgb.astype(float)
    bg = np.array(background, float)
    dist = np.linalg.norm(px - bg, axis=2)
    solid = dist > solid_distance
    _, (iy, ix) = ndi.distance_transform_edt(~solid, return_indices=True)
    nearest = px[iy, ix]
    span = nearest - bg
    alpha = np.clip(((px - bg) * span).sum(axis=2) / np.maximum((span * span).sum(axis=2), 1e-6), 0, 1)
    alpha[solid] = 1.0
    alpha[dist < noise] = 0.0                          # compression noise in the flat background
    out = np.dstack([np.where(solid[..., None], px, nearest), alpha * 255.0]).round().clip(0, 255).astype(np.uint8)
    out[out[..., 3] == 0] = 0
    return out


def trim(rgba: np.ndarray, pad: int = 0) -> np.ndarray:
    ys, xs = np.where(rgba[..., 3] > 0)
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    out = rgba[y0:y1, x0:x1]
    return np.pad(out, ((pad, pad), (pad, pad), (0, 0))) if pad else out


def save(rgba: np.ndarray, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(rgba, "RGBA").save(path, optimize=True)
    print(f"wrote {path.relative_to(ROOT)}  {rgba.shape[1]}x{rgba.shape[0]}")


def crop(board: np.ndarray, box) -> np.ndarray:
    x0, y0, x1, y1 = box
    return board[y0:y1, x0:x1]


def symbol_only(board: np.ndarray) -> np.ndarray:
    """The A + leaf from the large logo. The wordmark's first letter sits a few pixels from the symbol's
    right leg, so the symbol is selected by connected pieces instead of by a rectangle."""
    left = crop(board, LEFT)
    inked = left.min(axis=2) < 235
    labels, _ = ndi.label(inked)
    pieces = ndi.find_objects(labels)
    # The two symbol pieces start left of x=330 or sit below y=380 and end before x=460; the wordmark pieces start at x>=440.
    keep = []
    for i, box in enumerate(pieces, start=1):
        h, w = box[0].stop - box[0].start, box[1].stop - box[1].start
        if h * w < 2000:
            continue
        if box[1].start < 330 or (box[1].start < 440 and box[0].start > 360):
            keep.append(i)
    assert len(keep) == 2, f"expected the two symbol pieces, found {keep}"
    region = ndi.binary_dilation(np.isin(labels, keep), iterations=3)   # include the soft edge pixels
    rgba = to_alpha_on_white(left)
    rgba[~region, 3] = 0
    return trim(rgba)


def launcher(symbol: np.ndarray, mono: bool) -> np.ndarray:
    """432 px adaptive-icon layer: the symbol (scaled down only) centred inside the safe circle."""
    alpha = symbol[..., 3] > 0
    ys, xs = np.where(alpha)
    # Centre the layer on the middle of the symbol's bounding circle.
    cy, cx = (ys.min() + ys.max()) / 2, (xs.min() + xs.max()) / 2
    for _ in range(40):
        far = np.hypot(ys - cy, xs - cx)
        farthest = far.argmax()
        cy += (ys[farthest] - cy) * 0.05
        cx += (xs[farthest] - cx) * 0.05
    radius = np.hypot(ys - cy, xs - cx).max()
    scale = min(1.0, SAFE_RADIUS_PX / radius)
    img = Image.fromarray(symbol, "RGBA")
    img = img.resize((round(img.width * scale), round(img.height * scale)), Image.LANCZOS)
    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    canvas.alpha_composite(img, (round(CANVAS / 2 - cx * scale), round(CANVAS / 2 - cy * scale)))
    arr = np.asarray(canvas).copy()
    if mono:                                            # themed icons use only the shape
        arr[..., :3] = 0
    return arr


def launcher_tile(tile: np.ndarray) -> np.ndarray:
    """432 px adaptive-icon foreground: the tile, scaled down only, centred with every corner in the safe circle."""
    ys, xs = np.where(tile[..., 3] > 250)                      # the tile itself, not its soft shadow
    tile = tile[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    h, w = tile.shape[:2]
    ys, xs = np.where(tile[..., 3] > 250)
    radius = np.hypot(ys - h / 2, xs - w / 2).max()
    scale = min(1.0, SAFE_RADIUS_PX / radius)
    img = Image.fromarray(tile, "RGBA")
    img = img.resize((round(w * scale), round(h * scale)), Image.LANCZOS)
    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    canvas.alpha_composite(img, ((CANVAS - img.width) // 2, (CANVAS - img.height) // 2))
    return np.asarray(canvas).copy()


def main() -> None:
    board = np.asarray(Image.open(REFERENCE).convert("RGB"))
    assert board.shape[:2] == (724, 2172), board.shape

    horizontal = trim(to_alpha_on_white(crop(board, LEFT)), pad=12)
    save(horizontal, DESIGN / "auxo-logo-horizontal.png")
    save(horizontal, RES / "auxo_logo_horizontal.png")      # sign-in screens
    save(trim(to_alpha_on_white(crop(board, VERTICAL)), pad=12), DESIGN / "auxo-logo-vertical.png")
    # The dark version has a white wordmark (for dark backgrounds); the grey one has a black wordmark (for light ones).
    save(trim(to_alpha_on_colour(crop(board, DARK)[:, :-6], (8, 34, 29), 28), pad=12), DESIGN / "auxo-logo-horizontal-dark.png")
    save(trim(to_alpha_on_colour(crop(board, MONO), (244, 244, 244), 22), pad=12), DESIGN / "auxo-logo-mono.png")

    symbol = symbol_only(board)
    save(symbol, DESIGN / "auxo-symbol.png")
    tile = np.asarray(Image.open(ICON).convert("RGBA"))
    save(launcher_tile(tile), RES / "ic_launcher_foreground.png")
    # Themed (monochrome) icons need a plain shape, so that layer is the same symbol without the tile.
    save(launcher(symbol, mono=True), RES / "ic_launcher_monochrome.png")


if __name__ == "__main__":
    main()
