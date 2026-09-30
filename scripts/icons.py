# Genera los íconos del lanzador con el logo de PLEX PLAY (Manzana con boina en el círculo azul y «PLEX PLAY»).
# Fuente: scripts/logo-plexplay-1080.jpg (el mismo del ícono de Google Play, store/graficos/icono-512.png).
import os
from PIL import Image, ImageDraw
AQUI = os.path.dirname(os.path.abspath(__file__))
src = Image.open(os.path.join(AQUI, "logo-plexplay-1080.jpg")).convert("RGBA")
# el círculo del logo ocupa casi todo el cuadro: se recorta al disco (con su aro claro)
cx, cy, r = src.width // 2, src.height // 2, int(src.width * 0.472)
disco = src.crop((cx - r, cy - r, cx + r, cy + r))
NAVY = (13, 45, 140, 255)
RES = os.path.join(AQUI, "..", "app", "src", "main", "res")

def circulo(img, n):
    img = img.resize((n, n), Image.LANCZOS)
    m = Image.new("L", (n * 4, n * 4), 0); ImageDraw.Draw(m).ellipse((0, 0, n * 4 - 1, n * 4 - 1), fill=255)
    out = Image.new("RGBA", (n, n), (0, 0, 0, 0)); out.paste(img, (0, 0), m.resize((n, n), Image.LANCZOS)); return out

def lienzo(size, frac, bg, forma=None):
    im = Image.new("RGBA", (size, size), bg)
    d = int(size * frac); c = circulo(disco, d)
    im.paste(c, ((size - d) // 2, (size - d) // 2), c)
    if forma:
        mk = Image.new("L", (size * 4, size * 4), 0); dr = ImageDraw.Draw(mk)
        if forma == "round": dr.ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
        else: dr.rounded_rectangle((0, 0, size * 4 - 1, size * 4 - 1), radius=size * 4 // 5, fill=255)
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0)); out.paste(im, (0, 0), mk.resize((size, size), Image.LANCZOS)); return out
    return im

for k, s in {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}.items():
    fg = int(108 * s)   # ícono adaptable: el logo dentro de la zona segura (66 %)
    lienzo(fg, 0.64, (0, 0, 0, 0)).save(os.path.join(RES, f"mipmap-{k}", "ic_launcher_fg.png"))
    lg = int(48 * s)
    lienzo(lg, 0.96, NAVY, "square").save(os.path.join(RES, f"mipmap-{k}", "ic_launcher.png"))
    lienzo(lg, 1.0, NAVY, "round").save(os.path.join(RES, f"mipmap-{k}", "ic_launcher_round.png"))
print("íconos listos")
