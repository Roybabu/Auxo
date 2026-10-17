#!/usr/bin/env python3
"""Draws the six business-category icons as 3D soft-render artwork.

One shape list feeds both outputs, so what is previewed is what the app draws:
  * res/drawable/ic_cat_<name>.xml   - Android vector drawables (linear/radial gradients, no bitmaps)
  * <out>/preview_<name>.svg         - the same artwork as SVG, for a quick look in a browser

Usage: tools/gen_category_icons.py [preview_dir]
Light comes from the top left everywhere: bright on the left and top, dark on the right and bottom.
"""
import sys
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable"

DEEP, MID, BRIGHT, SOFT, MINT = "#064E3B", "#0B7A55", "#10A56F", "#34D399", "#A7F3D0"


def rrect(x, y, w, h, r):
    return (f"M{x + r:g},{y:g}H{x + w - r:g}A{r:g},{r:g} 0 0 1 {x + w:g},{y + r:g}V{y + h - r:g}"
            f"A{r:g},{r:g} 0 0 1 {x + w - r:g},{y + h:g}H{x + r:g}A{r:g},{r:g} 0 0 1 {x:g},{y + h - r:g}"
            f"V{y + r:g}A{r:g},{r:g} 0 0 1 {x + r:g},{y:g}Z")


def ellipse(cx, cy, rx, ry):
    return (f"M{cx - rx:g},{cy:g}A{rx:g},{ry:g} 0 1 1 {cx + rx:g},{cy:g}A{rx:g},{ry:g} 0 1 1 {cx - rx:g},{cy:g}Z")


def lin(x1, y1, x2, y2, *stops):
    return ("lin", x1, y1, x2, y2, stops)


def rad(cx, cy, r, *stops):
    return ("rad", cx, cy, r, stops)


def solid(color, alpha=1.0):
    return ("solid", color, alpha)


def shadow():
    return (ellipse(24, 43.6, 15, 2.4), rad(24, 43.6, 15, (0, "#40064E3B"), (1, "#00064E3B")))


def gloss(path, alpha=0.32):
    return (path, solid("#FFFFFF", alpha))


ICONS = {
    # A shopping bag
    "general": [
        shadow(),
        (rrect(9, 16, 30, 26, 5), lin(9, 16, 39, 42, (0, SOFT), (0.5, BRIGHT), (1, DEEP))),
        (rrect(9, 16, 30, 6.5, 3), solid("#FFFFFF", 0.14)),
        ("STROKE", "M17,21V14.5A7,7 0 0 1 31,14.5V21", DEEP, 2.6),
        (ellipse(17, 22.5, 1.9, 1.9), solid("#F0FBF6")),
        (ellipse(31, 22.5, 1.9, 1.9), solid("#F0FBF6")),
        gloss("M12,25C12,25 12,38 12.6,38.6C13.2,39 14.6,38.6 14.6,38.6L14.6,25Z", 0.30),
        (rrect(19.5, 30, 9, 6, 2), solid("#FFFFFF", 0.82)),
    ],
    # A folded garment (T-shirt)
    "apparel": [
        shadow(),
        ("M16,8L8.5,12.5L4.5,21L11,24.2L14,20.5V39.5A2.5,2.5 0 0 0 16.5,42H31.5A2.5,2.5 0 0 0 34,39.5V20.5L37,24.2L43.5,21L39.5,12.5L32,8C31.2,11.4 28,13.6 24,13.6C20,13.6 16.8,11.4 16,8Z",
         lin(6, 8, 42, 42, (0, SOFT), (0.45, BRIGHT), (1, DEEP))),
        ("M16,8C16.8,11.4 20,13.6 24,13.6C28,13.6 31.2,11.4 32,8C30,9.9 27.2,10.9 24,10.9C20.8,10.9 18,9.9 16,8Z", solid(DEEP, 0.85)),
        ("M14,20.5L11,24.2L4.5,21L8.5,12.5L14,16Z", solid("#FFFFFF", 0.16)),
        gloss("M17.5,17C17.5,17 17,38 18,39C19,40 20.4,39.2 20.4,39.2L20.4,17Z", 0.26),
        ("STROKE", "M24,14V41", DEEP, 0.8, 0.25),
    ],
    # A takeaway cup
    "food": [
        shadow(),
        ("M12,18H36L33.2,40.6A2.4,2.4 0 0 1 30.8,42.6H17.2A2.4,2.4 0 0 1 14.8,40.6Z",
         lin(12, 18, 36, 42, (0, SOFT), (0.5, BRIGHT), (1, DEEP))),
        ("M13.2,24H34.8L33.9,33H14.1Z", lin(13, 24, 35, 33, (0, "#F0FBF6"), (1, MINT))),
        (rrect(10, 12.5, 28, 6, 3), lin(10, 12.5, 38, 18.5, (0, MID), (1, DEEP))),
        (rrect(14, 8.5, 20, 4.5, 2.2), lin(14, 8.5, 34, 13, (0, BRIGHT), (1, DEEP))),
        gloss("M15.4,20C15.4,20 16.4,38.4 17.2,39.2C18,39.8 19.2,39 19.2,39L18.6,20Z", 0.30),
        (ellipse(24, 28.5, 3, 3), solid(MID, 0.85)),
    ],
    # A shopping basket with produce
    "grocery": [
        shadow(),
        (ellipse(17.5, 19, 6.2, 6), lin(12, 13, 23, 25, (0, "#FCA5A5"), (0.5, "#EF4444"), (1, "#B91C1C"))),
        (ellipse(30, 18.5, 5.6, 5.6), lin(25, 13, 35, 24, (0, "#FDE68A"), (0.5, "#F59E0B"), (1, "#B45309"))),
        ("M24,17C24,12 26.5,9.5 31,9C30.6,13.6 28,16.4 24,17Z", lin(24, 9, 31, 17, (0, "#86EFAC"), (1, MID))),
        ("M8,24H40L36.4,40A3.2,3.2 0 0 1 33.3,42.4H14.7A3.2,3.2 0 0 1 11.6,40Z",
         lin(8, 24, 40, 42, (0, SOFT), (0.5, BRIGHT), (1, DEEP))),
        (rrect(6, 21.5, 36, 5.4, 2.7), lin(6, 21.5, 42, 27, (0, BRIGHT), (1, DEEP))),
        ("STROKE", "M17,28.5L18.2,39M24,28.5V39.5M31,28.5L29.8,39", "#F0FBF6", 1.5, 0.55),
        gloss("M9,27.5L11.4,27.5L13.6,38.8C13.6,38.8 12.6,38.8 12,38.2C11.4,37.6 9,27.5 9,27.5Z", 0.28),
    ],
    # A monitor
    "electronics": [
        shadow(),
        (rrect(5.5, 8, 37, 25, 3.4), lin(5.5, 8, 42.5, 33, (0, MID), (1, DEEP))),
        (rrect(8, 10.5, 32, 20, 1.8), lin(8, 10.5, 40, 30.5, (0, "#6EE7B7"), (0.55, BRIGHT), (1, MID))),
        gloss("M8,10.5H27L16,30.5H8Z", 0.22),
        ("STROKE", "M13,25L18,19.5L22,23L29,15L35,22", "#F0FBF6", 1.7, 0.85),
        ("M20.6,33H27.4L28.4,38.6H19.6Z", lin(20, 33, 28, 38.6, (0, BRIGHT), (1, DEEP))),
        (rrect(14.5, 38.2, 19, 3.8, 1.9), lin(14.5, 38.2, 33.5, 42, (0, SOFT), (0.5, MID), (1, DEEP))),
    ],
    # A cosmetic pump bottle
    "beauty": [
        shadow(),
        (rrect(20.8, 3.6, 11, 3.6, 1.8), lin(21, 3.6, 32, 7.2, (0, BRIGHT), (1, DEEP))),
        (rrect(22.2, 7, 3.6, 5.6, 0.8), solid(MID)),
        (rrect(18.6, 11.4, 10.8, 4.6, 1.4), lin(18.6, 11.4, 29.4, 16, (0, SOFT), (1, DEEP))),
        (rrect(13.5, 15.4, 21, 27, 6.5), lin(13.5, 15.4, 34.5, 42.4, (0, SOFT), (0.45, BRIGHT), (1, DEEP))),
        gloss("M16.6,22C16.6,22 16,37 17,38.4C18,39.6 19.6,38.8 19.6,38.8L19.6,22Z", 0.32),
        (rrect(18.2, 25, 12, 11, 2.4), solid("#FFFFFF", 0.86)),
        (ellipse(24.2, 30.5, 2.6, 2.6), solid(MID, 0.9)),
    ],
}


# ---------- writers ----------

def _fill_svg(spec, gid):
    kind = spec[0]
    if kind == "solid":
        return f'fill="{spec[1]}" fill-opacity="{spec[2]:g}"', ""
    if kind == "lin":
        _, x1, y1, x2, y2, stops = spec
        st = "".join(f'<stop offset="{o:g}" stop-color="#{c[-6:]}" stop-opacity="{(int(c[1:3], 16) / 255 if len(c) == 9 else 1):.3f}"/>' for o, c in stops)
        return f'fill="url(#{gid})"', f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{x1:g}" y1="{y1:g}" x2="{x2:g}" y2="{y2:g}">{st}</linearGradient>'
    _, cx, cy, r, stops = spec
    st = "".join(f'<stop offset="{o:g}" stop-color="#{c[-6:]}" stop-opacity="{(int(c[1:3], 16) / 255 if len(c) == 9 else 1):.3f}"/>' for o, c in stops)
    return f'fill="url(#{gid})"', f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{cx:g}" cy="{cy:g}" r="{r:g}">{st}</radialGradient>'


def to_svg(shapes):
    defs, body = [], []
    for i, s in enumerate(shapes):
        if s[0] == "STROKE":
            _, d, color, w, *rest = s
            a = rest[0] if rest else 1
            body.append(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{w:g}" stroke-opacity="{a:g}" stroke-linecap="round" stroke-linejoin="round"/>')
            continue
        d, spec = s
        attr, df = _fill_svg(spec, f"g{i}")
        if df:
            defs.append(df)
        body.append(f'<path d="{d}" {attr}/>')
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48" width="240" height="240"><defs>{"".join(defs)}</defs>{"".join(body)}</svg>'


def _argb(c, alpha=1.0):
    if len(c) == 9:
        return c.upper()
    return f"#{int(round(alpha * 255)):02X}{c[1:].upper()}"


def _gradient_xml(spec, ind):
    p = " " * ind
    if spec[0] == "lin":
        _, x1, y1, x2, y2, stops = spec
        head = f'{p}<gradient android:type="linear" android:startX="{x1:g}" android:startY="{y1:g}" android:endX="{x2:g}" android:endY="{y2:g}">'
    else:
        _, cx, cy, r, stops = spec
        head = f'{p}<gradient android:type="radial" android:centerX="{cx:g}" android:centerY="{cy:g}" android:gradientRadius="{r:g}">'
    items = "\n".join(f'{p}    <item android:offset="{o:g}" android:color="{_argb(c)}" />' for o, c in stops)
    return f"{head}\n{items}\n{p}</gradient>"


def to_vector(name, shapes):
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           f"<!-- Generated by tools/gen_category_icons.py ({name}). Edit the script, not this file. -->",
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    xmlns:aapt="http://schemas.android.com/aapt"',
           '    android:width="48dp"', '    android:height="48dp"',
           '    android:viewportWidth="48"', '    android:viewportHeight="48">']
    for s in shapes:
        if s[0] == "STROKE":
            _, d, color, w, *rest = s
            a = rest[0] if rest else 1
            out.append(f'    <path android:pathData="{d}" android:strokeColor="{_argb(color, a)}" android:strokeWidth="{w:g}" android:strokeLineCap="round" android:strokeLineJoin="round" />')
            continue
        d, spec = s
        if spec[0] == "solid":
            out.append(f'    <path android:pathData="{d}" android:fillColor="{_argb(spec[1], spec[2])}" />')
        else:
            out.append(f'    <path android:pathData="{d}">\n        <aapt:attr name="android:fillColor">\n{_gradient_xml(spec, 12)}\n        </aapt:attr>\n    </path>')
    out.append("</vector>")
    return "\n".join(out) + "\n"


if __name__ == "__main__":
    preview = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    if preview:
        preview.mkdir(parents=True, exist_ok=True)
    for name, shapes in ICONS.items():
        (RES / f"ic_cat_{name}.xml").write_text(to_vector(name, shapes))
        if preview:
            (preview / f"preview_{name}.svg").write_text(to_svg(shapes))
    print("wrote", ", ".join(ICONS))
