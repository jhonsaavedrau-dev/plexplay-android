# Genera los íconos del lanzador (Manzana) a partir de la imagen publicada de la app web.
import io, urllib.request
from PIL import Image, ImageDraw
SRC = "https://jhonsaavedrau-dev.github.io/portfolio-francais-c1-1/plexplay/icons/mz-maskable-512.png"
import os
raw = open(os.environ["ICON_SRC"], "rb").read() if os.environ.get("ICON_SRC") else urllib.request.urlopen(SRC).read()
src = Image.open(io.BytesIO(raw)).convert("RGBA")
cx, cy, r = 262, 275, 165
face = src.crop((cx - r, cy - r, cx + r, cy + r))
NAVY = (14, 42, 94, 255); GOLD = (232, 178, 62, 255)
RES = "app/src/main/res"

def disc(size, rad, bg):
    S = size * 4; im = Image.new("RGBA", (S, S), bg)
    R = rad * 4; f = face.resize((2 * R, 2 * R), Image.LANCZOS)
    m = Image.new("L", (2 * R, 2 * R), 0); ImageDraw.Draw(m).ellipse((0, 0, 2 * R - 1, 2 * R - 1), fill=255)
    ring = int(R * 0.07)
    ImageDraw.Draw(im).ellipse((S // 2 - R - ring, S // 2 - R - ring, S // 2 + R + ring, S // 2 + R + ring), fill=GOLD)
    im.paste(f, (S // 2 - R, S // 2 - R), m)
    return im.resize((size, size), Image.LANCZOS)

def masked(im, shape):
    n = im.size[0]; mk = Image.new("L", (n * 4, n * 4), 0); d = ImageDraw.Draw(mk)
    if shape == "round": d.ellipse((0, 0, n * 4 - 1, n * 4 - 1), fill=255)
    else: d.rounded_rectangle((0, 0, n * 4 - 1, n * 4 - 1), radius=n * 4 // 5, fill=255)
    out = Image.new("RGBA", (n, n), (0, 0, 0, 0)); out.paste(im, (0, 0), mk.resize((n, n), Image.LANCZOS)); return out

for k, s in {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}.items():
    fg = int(108 * s); disc(fg, int(fg * 0.30), (0, 0, 0, 0)).save(f"{RES}/mipmap-{k}/ic_launcher_fg.png")
    lg = int(48 * s)
    masked(disc(lg, int(lg * 0.40), NAVY), "square").save(f"{RES}/mipmap-{k}/ic_launcher.png")
    masked(disc(lg, int(lg * 0.42), NAVY), "round").save(f"{RES}/mipmap-{k}/ic_launcher_round.png")
print("íconos listos")
