#!/usr/bin/env python3
"""Generates the raster brand assets for the Virtual Volume website.

Pure standard library: no Pillow, no network. Geometry mirrors the SVG mark used in the
app icon, the favicon and the header, so every surface shows the same logo.

Usage:
    python3 tools/generate_web_icons.py
"""

from __future__ import annotations

import math
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "website", "assets", "img")

# Brand palette (matches android/app/src/main/res/values/colors.xml)
BG_TOP = (0x16, 0x20, 0x2F)
BG_BOTTOM = (0x05, 0x08, 0x0E)
GLOW = (0x35, 0xD0, 0xBA)
MINT_TOP = (0x8F, 0xF0, 0xE2)
MINT_BOTTOM = (0x1F, 0xAE, 0x9B)
WHITE = (0xFF, 0xFF, 0xFF)


def lerp(a: float, b: float, t: float) -> float:
    return a + (b - a) * t


def mix(c1, c2, t: float):
    t = max(0.0, min(1.0, t))
    return (
        lerp(c1[0], c2[0], t),
        lerp(c1[1], c2[1], t),
        lerp(c1[2], c2[2], t),
    )


def rounded_rect_coverage(px: float, py: float, x: float, y: float, w: float, h: float, r: float) -> float:
    """Analytic coverage of a rounded rect for one sample point."""
    cx = x + w / 2.0
    cy = y + h / 2.0
    hx = w / 2.0
    hy = h / 2.0
    r = min(r, hx, hy)

    qx = abs(px - cx) - (hx - r)
    qy = abs(py - cy) - (hy - r)

    outside_x = max(qx, 0.0)
    outside_y = max(qy, 0.0)
    distance = math.hypot(outside_x, outside_y) + min(max(qx, qy), 0.0) - r
    return max(0.0, min(1.0, 0.5 - distance))


def render(width: int, height: int, painter) -> bytes:
    """Renders a painter callback into raw RGB rows with adaptive supersampling."""
    rows = bytearray()
    for y in range(height):
        rows.append(0)  # PNG filter type: None
        for x in range(width):
            colour = painter(x + 0.5, y + 0.5)
            if colour is None:
                colour = painter_subsampled(x, y, painter)
            rows += bytes(
                (
                    max(0, min(255, int(round(colour[0])))),
                    max(0, min(255, int(round(colour[1])))),
                    max(0, min(255, int(round(colour[2])))),
                )
            )
    return bytes(rows)


def painter_subsampled(x: int, y: int, painter, samples: int = 4):
    """Average a 4x4 grid for pixels on an edge."""
    r = g = b = 0.0
    total = samples * samples
    for sy in range(samples):
        for sx in range(samples):
            colour = painter(
                x + (sx + 0.5) / samples,
                y + (sy + 0.5) / samples,
                exact=True,
            )
            r += colour[0]
            g += colour[1]
            b += colour[2]
    return (r / total, g / total, b / total)


def write_png(path: str, width: int, height: int, raw: bytes) -> None:
    def chunk(tag: bytes, data: bytes) -> bytes:
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", header)
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(png)
    print(f"wrote {path} ({os.path.getsize(path) / 1024:.1f} KB)")


# ---------------------------------------------------------------- mark geometry

# Coordinates are in a 24x24 box, identical to the SVG used in the site header.
BARS = [
    (4.4, 7.0, 8.0, 2.4, 1.2, 0.95),
    (6.9, 11.2, 5.5, 2.4, 1.2, 0.70),
    (9.4, 15.4, 3.0, 2.4, 1.2, 0.45),
]
PILL = (14.6, 3.2, 3.0, 17.6, 1.5)


def make_icon_painter(size: int, mark_scale: float = 0.72):
    """Painter for a square launcher-style icon."""
    mark = size * mark_scale
    scale = mark / 24.0
    offset_x = (size - mark) / 2.0
    offset_y = (size - mark) / 2.0

    bars = [(x * scale + offset_x, y * scale + offset_y, w * scale, h * scale, r * scale, a)
            for (x, y, w, h, r, a) in BARS]
    pill = (
        PILL[0] * scale + offset_x,
        PILL[1] * scale + offset_y,
        PILL[2] * scale,
        PILL[3] * scale,
        PILL[4] * scale,
    )
    glow_cx = pill[0] + pill[2] / 2.0
    glow_cy = pill[1] + pill[3] / 2.0
    glow_radius = pill[3] * 0.75

    def painter(px: float, py: float, exact: bool = False):
        # Background gradient with a soft mint bloom behind the edge line.
        t = ((px / size) * 0.45 + (py / size) * 0.55)
        colour = mix(BG_TOP, BG_BOTTOM, t)

        dx = px - glow_cx
        dy = py - glow_cy
        dist = math.hypot(dx, dy) / glow_radius
        if dist < 1.0:
            bloom = (1.0 - dist) ** 2 * 0.28
            colour = mix(colour, GLOW, bloom)

        alpha_total = 1.0
        for (bx, by, bw, bh, br, ba) in bars:
            coverage = rounded_rect_coverage(px, py, bx, by, bw, bh, br)
            if coverage <= 0.0:
                continue
            a = coverage * ba
            colour = mix(colour, WHITE, a)
            alpha_total *= 1.0 - a

        coverage = rounded_rect_coverage(px, py, *pill)
        if coverage > 0.0:
            vertical = (py - pill[1]) / max(pill[3], 1e-6)
            pill_colour = mix(MINT_TOP, MINT_BOTTOM, vertical)
            colour = mix(colour, pill_colour, coverage)

        if not exact:
            # Decide whether this pixel needs supersampling.
            edge = False
            for (bx, by, bw, bh, br, _a) in bars:
                if _nearest_edge(px, py, bx, by, bw, bh, br):
                    edge = True
                    break
            if not edge and _nearest_edge(px, py, *pill):
                edge = True
            if not edge:
                return colour
            return None
        return colour

    return painter


def _nearest_edge(px, py, x, y, w, h, r) -> bool:
    cx = x + w / 2.0
    cy = y + h / 2.0
    qx = abs(px - cx) - (w / 2.0 - min(r, w / 2.0, h / 2.0))
    qy = abs(py - cy) - (h / 2.0 - min(r, w / 2.0, h / 2.0))
    distance = math.hypot(max(qx, 0.0), max(qy, 0.0)) + min(max(qx, qy), 0.0) - min(r, w / 2.0, h / 2.0)
    return -1.1 < distance < 1.1


def make_og_painter(width: int, height: int):
    """1200x630 social card: the mark on the left, a device with the live control on the right."""
    device_w = 250.0
    device_h = 470.0
    device_x = width - device_w - 120.0
    device_y = (height - device_h) / 2.0
    screen_pad = 9.0
    bar_x = device_x + device_w - screen_pad - 20.0
    bar_y = device_y + device_h * 0.28
    bar_h = device_h * 0.44

    mark = 210.0
    mark_scale = mark / 24.0
    mark_x = 130.0
    mark_y = (height - mark) / 2.0
    bars = [(x * mark_scale + mark_x, y * mark_scale + mark_y, w * mark_scale, h * mark_scale, r * mark_scale, a)
            for (x, y, w, h, r, a) in BARS]
    pill = (
        PILL[0] * mark_scale + mark_x,
        PILL[1] * mark_scale + mark_y,
        PILL[2] * mark_scale,
        PILL[3] * mark_scale,
        PILL[4] * mark_scale,
    )

    shapes = list(bars)
    shapes.append((device_x, device_y, device_w, device_h, 40.0, 0.10))
    shapes.append((device_x + screen_pad, device_y + screen_pad,
                   device_w - screen_pad * 2, device_h - screen_pad * 2, 32.0, 0.22))
    shapes.append((bar_x, bar_y, 7.0, bar_h, 3.5, 0.28))

    def painter(px: float, py: float, exact: bool = False):
        t = (px / width) * 0.5 + (py / height) * 0.5
        colour = mix(BG_TOP, BG_BOTTOM, t * 1.1)

        # Ambient bloom behind the device.
        dx = px - (device_x + device_w * 0.5)
        dy = py - (device_y + device_h * 0.5)
        dist = math.hypot(dx / (device_w * 1.15), dy / (device_h * 1.0))
        if dist < 1.0:
            colour = mix(colour, GLOW, (1.0 - dist) ** 2 * 0.14)

        for (sx, sy, sw, sh, sr, sa) in shapes:
            coverage = rounded_rect_coverage(px, py, sx, sy, sw, sh, sr)
            if coverage <= 0.0:
                continue
            colour = mix(colour, (0xE6, 0xEE, 0xF7), coverage * sa)

        # The control itself: filled from the bottom, mint, with a glow.
        fill_top = bar_y + bar_h * 0.34
        fill_coverage = rounded_rect_coverage(px, py, bar_x, fill_top, 7.0, bar_y + bar_h - fill_top, 3.5)
        if fill_coverage > 0.0:
            vertical = (py - fill_top) / max(bar_y + bar_h - fill_top, 1e-6)
            colour = mix(colour, mix(MINT_TOP, MINT_BOTTOM, vertical), fill_coverage)

        gdx = px - (bar_x + 3.5)
        gdy = py - (bar_y + bar_h / 2.0)
        gdist = math.hypot(gdx / 46.0, gdy / (bar_h * 0.75))
        if gdist < 1.0:
            colour = mix(colour, GLOW, (1.0 - gdist) ** 2 * 0.30)

        coverage = rounded_rect_coverage(px, py, *pill)
        if coverage > 0.0:
            vertical = (py - pill[1]) / max(pill[3], 1e-6)
            colour = mix(colour, mix(MINT_TOP, MINT_BOTTOM, vertical), coverage)

        if not exact:
            edge = _nearest_edge(px, py, *pill) or _nearest_edge(px, py, bar_x, fill_top, 7.0, bar_y + bar_h - fill_top, 3.5)
            if not edge:
                for (sx, sy, sw, sh, sr, _a) in shapes:
                    if _nearest_edge(px, py, sx, sy, sw, sh, sr):
                        edge = True
                        break
            if not edge:
                return colour
            return None
        return colour

    return painter


def main() -> None:
    icon_painter = make_icon_painter(180)
    write_png(os.path.join(OUT_DIR, "apple-touch-icon.png"), 180, 180,
              render(180, 180, icon_painter))

    icon_512 = make_icon_painter(512)
    write_png(os.path.join(OUT_DIR, "icon-512.png"), 512, 512, render(512, 512, icon_512))

    og = make_og_painter(1200, 630)
    write_png(os.path.join(OUT_DIR, "og-cover.png"), 1200, 630, render(1200, 630, og))


if __name__ == "__main__":
    main()
