#!/usr/bin/env python3
"""
Every icon the app ships, from one source render.

`art/icon-source.png` is the master: the pocket-and-cards render with its background
already knocked out. Everything else -- five densities of launcher icon, the two adaptive
layers behind them, the splash bitmap and the in-app loading mark -- is derived from it
here, so re-cutting the icon is one command rather than a morning in an image editor.

The adaptive background is a baked PNG rather than a solid colour on purpose. The render
is a dark object, and on the near-black ground the rest of the app uses, its silhouette
disappears into the launcher; a bloom behind it gives the shape an edge to sit against.
Baking that bloom into the *background* layer rather than the foreground matters too --
launchers derive the icon's drop shadow from the foreground's alpha, and a soft glow there
reads as a smear under the icon on every launcher that does.

    python tools/icons/generate_icons.py
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "art" / "icon-source.png"
ANDROID_RES = ROOT / "composeApp" / "src" / "androidMain" / "res"
COMPOSE_RES = ROOT / "composeApp" / "src" / "commonMain" / "composeResources" / "drawable"

# Ink.Background and the two brand lights, from ui/theme/Theme.kt. Kept in the same
# values so the launcher icon and the screen behind it are demonstrably the same palette.
INK_BACKGROUND = (7, 8, 11)
INK_DEEP = (18, 22, 42)
INK_ACCENT = (110, 139, 255)
INK_AURA = (124, 92, 224)

# Legacy square launcher bitmaps, in dp-independent pixels.
LEGACY_DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# Adaptive layers are 108dp; only the middle 72dp is guaranteed to survive masking.
ADAPTIVE_DENSITIES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}

# How much of the adaptive canvas the render occupies.
#
# The 66% figure everyone quotes is the *safe zone* -- the part of the 108dp canvas a
# launcher mask is guaranteed not to cut -- and filling it is a different thing from
# using it. Only the middle 72dp is ever visible, so artwork at 0.62 of the canvas came
# out at 67 of those 72dp: a render pressed against the rim of its own tile, with the
# plate behind it reduced to a hairline. 0.44 puts the render at 48 of the 72 visible
# dp, which is where a launcher icon's artwork normally sits -- an object on a tile
# rather than an object shaped like one.
ADAPTIVE_CONTENT = 0.44

# The legacy bitmap has no mask around it: what is drawn is what is seen. Scaled from
# ADAPTIVE_CONTENT by 108/72 so the render is the same size relative to the icon's edge
# whichever of the two a launcher picks up.
LEGACY_CONTENT = ADAPTIVE_CONTENT * 108 / 72


def trimmed(image: Image.Image) -> Image.Image:
    """The render with its transparent margin removed, so scaling is about the artwork."""
    box = image.getchannel("A").getbbox()
    return image.crop(box) if box else image


def fit(artwork: Image.Image, canvas: int, fraction: float) -> Image.Image:
    """The artwork centred on a transparent square, occupying [fraction] of its longer side."""
    target = canvas * fraction
    scale = target / max(artwork.size)
    size = (max(1, round(artwork.width * scale)), max(1, round(artwork.height * scale)))
    resized = artwork.resize(size, Image.LANCZOS)

    layer = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    layer.paste(
        resized,
        ((canvas - resized.width) // 2, (canvas - resized.height) // 2),
        resized,
    )
    return layer


# Where the light comes from, as (x, y, radius, strength, colour) in canvas fractions.
# Two lights rather than one, the second cooler and lower, so the tint has depth instead
# of reading as one flat wash -- the same trick headerBloom() plays on screen.
LIGHTS = (
    (0.34, 0.24, 0.66, 0.62, INK_ACCENT),
    (0.76, 0.74, 0.54, 0.34, INK_AURA),
)


def ground(canvas: int) -> Image.Image:
    """
    The ground the render sits on: the app's own near-black, lifted toward indigo at the
    top, with two off-centre blooms giving the light somewhere to come from.

    Computed per pixel on a small tile and scaled up rather than drawn with stacked
    ellipses. Concentric fills leave a visible disc edge wherever the step between two
    rings survives quantisation, and at icon sizes that edge reads as a scratch on the
    artwork; a continuous falloff has no rings to show.
    """
    tile = 96
    base = Image.new("RGB", (tile, tile))
    pixels = base.load()

    for y in range(tile):
        v = y / (tile - 1)
        # Eased rather than linear: a straight ramp puts its fastest change across the
        # middle of the icon, exactly where the artwork is.
        sky = tuple(INK_DEEP[c] + (INK_BACKGROUND[c] - INK_DEEP[c]) * (v * v) for c in range(3))
        for x in range(tile):
            u = x / (tile - 1)
            r, g, b = sky
            for cx, cy, radius, strength, colour in LIGHTS:
                d = math.hypot(u - cx, v - cy) / radius
                if d >= 1.0:
                    continue
                # smoothstep falloff, squared: zero value *and* zero slope at the rim,
                # which is what keeps the edge of a bloom from ever being locatable.
                f = 1.0 - d
                amount = strength * (f * f * (3.0 - 2.0 * f)) ** 2
                r += (colour[0] - r) * amount
                g += (colour[1] - g) * amount
                b += (colour[2] - b) * amount
            pixels[x, y] = (round(r), round(g), round(b))

    return base.resize((canvas, canvas), Image.LANCZOS).convert("RGBA")


def monochrome(canvas: int) -> Image.Image:
    """
    The themed-icon layer: the same object as a flat glyph.

    Drawn rather than derived from the render. A silhouette lifted off the source alpha is
    one solid blob -- the cards, the pocket and the gold rim all collapse into the same
    shape the moment colour is thrown away -- so the mark has to be redrawn with the gaps
    that carry it.

    Those gaps are the whole technique. Every piece is a solid fill, drawn back to front,
    and each one first clears a halo of its own shape out of everything already down. That
    is what separates the near card from the far one on a layer that has exactly one
    colour to work with; outlining the shapes instead gives a glyph that turns to lace at
    48px. Colour is irrelevant here -- Android tints the layer to the wallpaper and reads
    only the alpha.

    Geometry is in units of a 108 grid. It is drawn to fill that grid and scaled down to
    [ADAPTIVE_CONTENT] on the way out, so the numbers below stay about the shape of the
    mark and nothing here has to be re-tuned when the icon's padding changes.
    """
    grid = 108
    scale = 8
    size = grid * scale
    layer = Image.new("L", (size, size), 0)

    # The clearance drawn around each piece before it is filled in.
    gap = 3.0

    def stamp(shape: Image.Image, halo: Image.Image) -> None:
        """Punches [halo] out of what is already drawn, then adds [shape] on top."""
        layer.paste(0, (0, 0), halo)
        layer.paste(255, (0, 0), shape)

    def card(angle: float) -> tuple[Image.Image, Image.Image]:
        box = (43.0, 15.0, 65.0, 62.0)
        pair = []
        for grow in (0.0, gap):
            plate = Image.new("L", (size, size), 0)
            ImageDraw.Draw(plate).rounded_rectangle(
                tuple(
                    (v + (-grow if i < 2 else grow)) * scale for i, v in enumerate(box)
                ),
                radius=(3.0 + grow) * scale,
                fill=255,
            )
            if angle:
                # Rotated about the mouth of the pocket, so the fan opens from where the
                # cards are held rather than pivoting around the middle of the page.
                plate = plate.rotate(-angle, resample=Image.BICUBIC, center=(54 * scale, 64 * scale))
            pair.append(plate)
        return pair[0], pair[1]

    # Back to front: the outer two cards, then the one in front of them, then the pocket.
    for angle in (-19.0, 19.0, 0.0):
        shape, halo = card(angle)
        stamp(shape, halo)

    def pocket(grow: float) -> Image.Image:
        """
        The pocket body: straight sides, rounded base, and a mouth that dips to the middle.

        The dip is the whole reason the cards behind it read as being *in* something rather
        than stacked on top of it, so it is carried through the halo as well -- a halo with
        a flat top would erase the card bottoms in a straight line and undo it.
        """
        left, right = 25.0 - grow, 83.0 + grow
        floor, base = 86.0 + grow, 74.0
        rim, dip = 50.0 - grow, 64.0 - grow
        radius = 9.0 + grow

        points: list[tuple[float, float]] = []

        def cubic(p0, p1, p2, p3, steps: int = 48):
            for i in range(1, steps + 1):
                t = i / steps
                m = 1 - t
                points.append(
                    (
                        (m ** 3 * p0[0] + 3 * m * m * t * p1[0] + 3 * m * t * t * p2[0] + t ** 3 * p3[0]) * scale,
                        (m ** 3 * p0[1] + 3 * m * m * t * p1[1] + 3 * m * t * t * p2[1] + t ** 3 * p3[1]) * scale,
                    )
                )

        points.append((left * scale, rim * scale))
        points.append((left * scale, base * scale))
        cubic((left, base), (left, floor - radius * 0.1), (left + radius * 0.4, floor), (left + radius, floor))
        points.append(((right - radius) * scale, floor * scale))
        cubic((right - radius, floor), (right - radius * 0.4, floor), (right, floor - radius * 0.1), (right, base))
        points.append((right * scale, rim * scale))
        cubic((right, rim), (right - 8, dip - 2), (54 + 12, dip), (54, dip))
        cubic((54, dip), (54 - 12, dip), (left + 8, dip - 2), (left, rim))

        plate = Image.new("L", (size, size), 0)
        ImageDraw.Draw(plate).polygon(points, fill=255)
        return plate

    stamp(pocket(0.0), pocket(gap))

    glyph = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    glyph.putalpha(layer)
    # Drawn at whatever size reads well as geometry, then refitted to the same fraction
    # of the canvas the foreground uses. What has to match between the two layers is how
    # much of the tile the finished mark occupies -- a themed icon larger than the colour
    # icon it stands in for is a jump in size every time a launcher swaps between them.
    return fit(trimmed(glyph), canvas, ADAPTIVE_CONTENT)


def rounded_mask(canvas: int, radius_fraction: float = 0.225) -> Image.Image:
    """A squircle-ish mask for the legacy bitmap, antialiased by drawing it large."""
    scale = 4
    size = canvas * scale
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, size - 1, size - 1),
        radius=round(size * radius_fraction),
        fill=255,
    )
    return mask.resize((canvas, canvas), Image.LANCZOS)


def write(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "PNG", optimize=True)
    print(f"  {path.relative_to(ROOT).as_posix()}  {image.width}x{image.height}")


def main() -> None:
    if not SOURCE.exists():
        raise SystemExit(f"missing source render: {SOURCE}")

    artwork = trimmed(Image.open(SOURCE).convert("RGBA"))
    print(f"source {SOURCE.relative_to(ROOT).as_posix()} -> artwork {artwork.width}x{artwork.height}")

    print("adaptive layers")
    for density, canvas in ADAPTIVE_DENSITIES.items():
        write(ground(canvas), ANDROID_RES / f"mipmap-{density}" / "ic_launcher_background.png")
        write(
            fit(artwork, canvas, ADAPTIVE_CONTENT),
            ANDROID_RES / f"mipmap-{density}" / "ic_launcher_foreground.png",
        )
        write(
            monochrome(canvas),
            ANDROID_RES / f"mipmap-{density}" / "ic_launcher_monochrome.png",
        )

    print("legacy bitmaps")
    for density, canvas in LEGACY_DENSITIES.items():
        composed = ground(canvas)
        content = fit(artwork, canvas, LEGACY_CONTENT)
        composed.alpha_composite(content)

        square = composed.copy()
        square.putalpha(rounded_mask(canvas))
        write(square, ANDROID_RES / f"mipmap-{density}" / "ic_launcher.png")

        circle = Image.new("L", (canvas * 4, canvas * 4), 0)
        ImageDraw.Draw(circle).ellipse((0, 0, canvas * 4 - 1, canvas * 4 - 1), fill=255)
        round_icon = composed.copy()
        round_icon.putalpha(circle.resize((canvas, canvas), Image.LANCZOS))
        write(round_icon, ANDROID_RES / f"mipmap-{density}" / "ic_launcher_round.png")

    # The bitmap the window background shows while the process starts. Density-independent
    # and generous: it is centred on the whole screen, not fitted to a launcher grid cell.
    print("splash and in-app marks")
    write(
        fit(artwork, 384, 1.0),
        ANDROID_RES / "drawable-nodpi" / "splash_mark.png",
    )
    # The loading screen draws this one, so it keeps its transparency and its full detail.
    write(fit(artwork, 512, 1.0), COMPOSE_RES / "app_icon.png")
    # 512 on the brand ground, for a store listing or a README.
    store = ground(512)
    store.alpha_composite(fit(artwork, 512, LEGACY_CONTENT))
    write(store, ROOT / "art" / "icon-512.png")


if __name__ == "__main__":
    main()
