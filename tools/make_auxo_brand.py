#!/usr/bin/env python3
"""Draws the Auxo brand assets from one set of shapes, so the app icon and every logo share the same symbol.

    pip install shapely cairosvg fonttools     # only needed to re-run this script
    python3 tools/make_auxo_brand.py

Writes:
  design/auxo/*.svg                          symbol and logo variants (wordmark outlines come from Poppins, SIL OFL 1.1)
  design/auxo/png/*.png                      the same logos as images, for places that cannot use SVG
  app/src/main/res/drawable/ic_auxo_symbol.xml        the symbol, for the login screens
  app/src/main/res/drawable/ic_launcher_foreground.xml  the symbol inside the adaptive icon's safe zone
  app/src/main/res/drawable/ic_launcher_monochrome.xml  the same shape in one colour, for themed icons

The symbol is an "A" made of a rising ribbon (left leg), a folded apex and a right leg, crossed by a leaf-like
stroke that points up and to the right: growth. The leaf is cut out of the A with a small gap, as in the
approved concept.
"""
from pathlib import Path

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from shapely.affinity import scale as sh_scale
from shapely.affinity import translate as sh_translate
from shapely.geometry import LineString, Polygon
from shapely.ops import unary_union

ROOT = Path(__file__).resolve().parent.parent
DESIGN = ROOT / "design" / "auxo"
RES = ROOT / "app" / "src" / "main" / "res"
FONTS = Path("/usr/share/fonts/truetype/google-fonts")

EMERALD = "#167D55"        # primary brand colour
EMERALD_DEEP = "#0E5A3C"
EMERALD_LIGHT = "#2FB583"
EMERALD_PALE = "#8FE0BC"
CHARCOAL = "#12261E"       # deep green-charcoal for the wordmark
WHITE = "#FFFFFF"
ICON_BACKGROUND = "#0B3B2A"


# ---------------------------------------------------------------- the symbol

def bezier(p0, p1, p2, p3, steps=40):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        a = (1 - t) ** 3
        b = 3 * (1 - t) ** 2 * t
        c = 3 * (1 - t) * t ** 2
        d = t ** 3
        pts.append((a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1]))
    return pts


def build_symbol():
    """Returns the pieces of the symbol as shapely polygons in a 100 x 100 space (y down)."""
    # The A: two legs of equal weight meeting in a flat apex, with flat top and bottom edges.
    box = Polygon([(0, 6), (100, 6), (100, 94), (0, 94)])
    left = LineString([(8.0, 112.0), (49.0, 2.0)]).buffer(10.0, cap_style="flat")
    right = LineString([(49.0, 2.0), (90.0, 112.0)]).buffer(10.0, cap_style="flat")
    a_shape = unary_union([left, right]).intersection(box)

    # The leaf: a slim pointed stroke rising from the lower left to the upper right, past the A.
    tail, tip = (4.0, 76.0), (99.0, 12.0)
    upper = bezier(tail, (24.0, 52.0), (58.0, 32.0), tip)
    lower = bezier(tip, (78.0, 46.0), (38.0, 78.0), tail)
    leaf = Polygon(upper + lower[1:]).buffer(0)

    gap = leaf.buffer(4.2, join_style="round")
    a_cut = a_shape.difference(gap)

    left_leg, fold, right_leg = [], [], []
    for piece in parts(a_cut):
        if piece.area < 1.0:
            continue
        if piece.centroid.y > 62:
            (left_leg if piece.centroid.x < 50 else right_leg).append(piece)
        else:
            # the apex piece: the left half continues the ribbon, the right half is the fold
            half = Polygon([(0, 0), (49.0, 0), (49.0, 100), (0, 100)])
            left_leg.append(piece.intersection(half))
            fold.append(piece.difference(half))
    return {
        "left": unary_union(left_leg),
        "fold": unary_union(fold),
        "right": unary_union(right_leg),
        "leaf": leaf,
    }


PALETTES = {
    # on white / light surfaces
    "light": {"left": EMERALD, "fold": EMERALD_DEEP, "right": EMERALD_LIGHT, "leaf": "#20A36F"},
    # on deep green / dark surfaces, and inside the launcher icon
    "dark": {"left": EMERALD_LIGHT, "fold": "#1B8F66", "right": "#F2FFF9", "leaf": EMERALD_PALE},
}


def parts(geom):
    return list(geom.geoms) if hasattr(geom, "geoms") else [geom]


def polygon_path(poly, transform, digits=2):
    def fmt(v):
        return f"{v:.{digits}f}".rstrip("0").rstrip(".")

    rings = [poly.exterior] + list(poly.interiors)
    out = []
    for ring in rings:
        pts = [transform(x, y) for x, y in list(ring.coords)[:-1]]
        out.append("M" + " L".join(f"{fmt(x)},{fmt(y)}" for x, y in pts) + " Z")
    return " ".join(out)


def symbol_paths(pieces, palette, transform, mono=None):
    """[(colour, path data)] for each piece that is not empty, in drawing order.
    With [mono] the pieces are merged into one shape of that colour, so no seams show between the tones."""
    items = []
    if mono:
        merged = unary_union([p for p in pieces.values()])
        return [(mono, polygon_path(poly, transform)) for poly in parts(merged) if poly.area >= 0.5]
    for name in ("left", "right", "fold", "leaf"):
        for poly in parts(pieces[name]):
            if poly.is_empty or poly.area < 0.5:
                continue
            items.append((palette[name], polygon_path(poly, transform)))
    return items


def bounds_of(pieces):
    shape = unary_union([p for p in pieces.values()])
    return shape.bounds  # minx, miny, maxx, maxy


# ---------------------------------------------------------------- the wordmark

def glyph_paths(text, font_file, size, x, baseline, tracking=0.0):
    """Outline paths of [text] set in [font_file]; returns (path data, advance width, cap height)."""
    font = TTFont(font_file)
    cmap = font.getBestCmap()
    glyphs = font.getGlyphSet()
    scale = size / font["head"].unitsPerEm
    pen_x = x
    path = []
    for ch in text:
        name = cmap[ord(ch)]
        pen = SVGPathPen(glyphs, ntos=lambda v: f"{v:.2f}".rstrip("0").rstrip("."))
        tp = TransformPen(pen, (scale, 0, 0, -scale, pen_x, baseline))
        glyphs[name].draw(tp)
        path.append(pen.getCommands())
        pen_x += glyphs[name].width * scale + tracking
    cap = font["OS/2"].sCapHeight * scale
    return " ".join(p for p in path if p), pen_x - x - tracking, cap


# ---------------------------------------------------------------- SVG building

def svg_doc(width, height, body, title, background=None):
    bg = f'<rect width="{width:g}" height="{height:g}" fill="{background}"/>' if background else ""
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width:g} {height:g}" width="{width:g}" height="{height:g}" '
        f'role="img" aria-label="{title}">\n<title>{title}</title>\n{bg}{body}\n</svg>\n'
    )


def symbol_group(pieces, palette, ox, oy, size, mono=None):
    """The symbol placed with its top-left at (ox, oy), [size] units tall, fitted to its own bounds."""
    minx, miny, maxx, maxy = bounds_of(pieces)
    k = size / (maxy - miny)

    def tr(x, y):
        return (ox + (x - minx) * k, oy + (y - miny) * k)

    groups = []
    for colour, d in symbol_paths(pieces, palette, tr, mono):
        groups.append(f'<path fill="{colour}" d="{d}"/>')
    return "\n".join(groups), (maxx - minx) * k


def wordmark(x, baseline, size, colour):
    d, width, cap = glyph_paths("Auxo", FONTS / "Poppins-Bold.ttf", size, x, baseline, tracking=-size * 0.01)
    return f'<path fill="{colour}" d="{d}"/>', width, cap


def tagline(x, baseline, size, colour, align_width=None):
    d, width, _ = glyph_paths("Built to grow.", FONTS / "Poppins-Regular.ttf", size, x, baseline, tracking=size * 0.12)
    return f'<path fill="{colour}" d="{d}"/>', width


def horizontal(pieces, palette, word_colour, with_tagline=False, mono=None, background=None, title="Auxo"):
    pad = 24
    sym_h = 150
    sym, sym_w = symbol_group(pieces, palette, pad, pad, sym_h, mono)
    word_size = 118
    gap = 26
    wx = pad + sym_w + gap
    # centre the word (and tagline) block on the symbol
    cap = TTFont(FONTS / "Poppins-Bold.ttf")["OS/2"].sCapHeight * word_size / 1000
    tag_size = 24
    block = cap + (22 + tag_size * 0.72 if with_tagline else 0)
    top = pad + (sym_h - block) / 2
    baseline = top + cap
    word, ww, _ = wordmark(wx, baseline, word_size, mono or word_colour)
    body = sym + "\n" + word
    right = wx + ww
    if with_tagline:
        tag, tw = tagline(wx + 4, baseline + 22 + tag_size * 0.72, tag_size, mono or word_colour)
        body += "\n" + tag
        right = max(right, wx + 4 + tw)
    return svg_doc(right + pad, sym_h + 2 * pad, body, title, background)


def vertical(pieces, palette, word_colour, with_tagline=True, mono=None, background=None, title="Auxo"):
    pad = 30
    sym_h = 170
    word_size = 100
    cap = TTFont(FONTS / "Poppins-Bold.ttf")["OS/2"].sCapHeight * word_size / 1000
    _, ww, _ = glyph_paths("Auxo", FONTS / "Poppins-Bold.ttf", word_size, 0, 0, tracking=-word_size * 0.01)
    tag_size = 22
    _, tw = tagline(0, 0, tag_size, "#000")
    width = max(ww, tw) + 2 * pad
    sym, sym_w = symbol_group(pieces, palette, 0, pad, sym_h, mono)
    sym = sym.replace("<path", "<path", 1)
    # centre the symbol horizontally
    shift = (width - sym_w) / 2
    sym_group = f'<g transform="translate({shift:.2f},0)">\n{sym}\n</g>'
    baseline = pad + sym_h + 34 + cap
    word, ww, _ = wordmark((width - ww) / 2, baseline, word_size, mono or word_colour)
    body = sym_group + "\n" + word
    height = baseline + pad
    if with_tagline:
        tag, tw = tagline((width - tw) / 2, baseline + 18 + tag_size, tag_size, mono or word_colour)
        body += "\n" + tag
        height = baseline + 18 + tag_size + pad
    return svg_doc(width, height, body, title, background)


def symbol_only(pieces, palette, mono=None, background=None, title="Auxo symbol", pad=8, size=100):
    sym, sym_w = symbol_group(pieces, palette, pad, pad, size, mono)
    return svg_doc(sym_w + 2 * pad, size + 2 * pad, sym, title, background)


# ---------------------------------------------------------------- Android vectors

def android_vector(name, size_dp, viewport, items, comment):
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f"<!-- {comment}",
        "     Generated by tools/make_auxo_brand.py; edit the script, not this file. -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{size_dp}dp"',
        f'    android:height="{size_dp}dp"',
        f'    android:viewportWidth="{viewport}"',
        f'    android:viewportHeight="{viewport}">',
    ]
    for colour, d in items:
        lines.append(f'    <path android:fillColor="{colour}" android:pathData="{d}" />')
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def launcher_items(pieces, palette, mono=None, radius=30.5):
    """The symbol centred in the 108 x 108 adaptive-icon canvas, inside a circle of [radius] around the centre
    (launchers only guarantee the inner 66 dp circle, radius 33)."""
    minx, miny, maxx, maxy = bounds_of(pieces)
    cx, cy = (minx + maxx) / 2, (miny + maxy) / 2
    shape = unary_union(list(pieces.values()))
    far = max(((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 for poly in parts(shape) for x, y in poly.exterior.coords)
    k = radius / far

    def tr(x, y):
        return (54 + (x - cx) * k, 54 + (y - cy) * k)

    return symbol_paths(pieces, palette, tr, mono), k


def main():
    pieces = build_symbol()
    DESIGN.mkdir(parents=True, exist_ok=True)
    (DESIGN / "png").mkdir(exist_ok=True)
    light, dark = PALETTES["light"], PALETTES["dark"]
    black = "#000000"

    files = {
        "auxo-symbol.svg": symbol_only(pieces, light),
        "auxo-symbol-dark.svg": symbol_only(pieces, dark, background=ICON_BACKGROUND),
        "auxo-symbol-mono.svg": symbol_only(pieces, light, mono=black),
        "auxo-logo-horizontal.svg": horizontal(pieces, light, CHARCOAL),
        "auxo-logo-horizontal-tagline.svg": horizontal(pieces, light, CHARCOAL, with_tagline=True),
        "auxo-logo-horizontal-dark.svg": horizontal(pieces, dark, WHITE, background=ICON_BACKGROUND),
        "auxo-logo-vertical.svg": vertical(pieces, light, CHARCOAL, with_tagline=False),
        "auxo-logo-vertical-tagline.svg": vertical(pieces, light, CHARCOAL, with_tagline=True),
        "auxo-logo-mono.svg": horizontal(pieces, light, black, mono=black),
    }
    for name, text in files.items():
        (DESIGN / name).write_text(text, encoding="utf-8")

    import cairosvg
    for name in files:
        stem = name[:-4]
        cairosvg.svg2png(url=str(DESIGN / name), write_to=str(DESIGN / "png" / f"{stem}.png"), output_width=1600 if "symbol" not in stem else 512)

    # Android resources
    sym_items = symbol_paths(pieces, light, lambda x, y: (x, y))
    minx, miny, maxx, maxy = bounds_of(pieces)
    # symbol drawable: fitted into a square viewport of 100 with the symbol centred
    k = 100.0 / max(maxx - minx, maxy - miny)
    ox, oy = (100 - (maxx - minx) * k) / 2, (100 - (maxy - miny) * k) / 2
    drawable_items = symbol_paths(pieces, light, lambda x, y: (ox + (x - minx) * k, oy + (y - miny) * k))
    (RES / "drawable" / "ic_auxo_symbol.xml").write_text(
        android_vector("ic_auxo_symbol", 100, 100, drawable_items, "The Auxo symbol in its brand colours, for screens."), encoding="utf-8")
    fg, k_fg = launcher_items(pieces, dark)
    (RES / "drawable" / "ic_launcher_foreground.xml").write_text(
        android_vector("ic_launcher_foreground", 108, 108, fg, "Adaptive launcher icon, foreground layer: the Auxo symbol inside the 66 dp safe zone."), encoding="utf-8")
    mono_items, _ = launcher_items(pieces, dark, mono="#FF000000")
    (RES / "drawable" / "ic_launcher_monochrome.xml").write_text(
        android_vector("ic_launcher_monochrome", 108, 108, mono_items, "Adaptive launcher icon, themed (monochrome) layer."), encoding="utf-8")
    print("wrote", len(files), "SVG logos and 3 Android vectors; launcher scale", round(k_fg, 3))


if __name__ == "__main__":
    main()
