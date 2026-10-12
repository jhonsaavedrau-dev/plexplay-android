# Genera los íconos del lanzador con el logo nuevo de PLEX PLAY (desde la 4.20 de la web): Manzana, el gato blanco
# y gris de la ficha de mascota, sobre el cuadrado azul de marca.
# Fuentes (salen de plexplay/herramientas/logo-m4.py):
#   scripts/logo-manzana-1080.png              a sangre: ícono cuadrado, redondo y de la tienda
#   scripts/logo-manzana-zona-segura-1080.png  a sangre, con Manzana dentro del 66 % central: ícono adaptable
import os
from PIL import Image, ImageDraw
AQUI = os.path.dirname(os.path.abspath(__file__))
LOGO = Image.open(os.path.join(AQUI, "logo-manzana-1080.png")).convert("RGBA")
SEGURO = Image.open(os.path.join(AQUI, "logo-manzana-zona-segura-1080.png")).convert("RGBA")
RES = os.path.join(AQUI, "..", "app", "src", "main", "res")

def forma(img, size, cual=None):
    im = img.resize((size, size), Image.LANCZOS)
    if not cual: return im
    mk = Image.new("L", (size * 4, size * 4), 0); dr = ImageDraw.Draw(mk)
    if cual == "round": dr.ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
    else: dr.rounded_rectangle((0, 0, size * 4 - 1, size * 4 - 1), radius=int(size * 4 * .225), fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0)); out.paste(im, (0, 0), mk.resize((size, size), Image.LANCZOS)); return out

for k, s in {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}.items():
    forma(SEGURO, int(108 * s)).save(os.path.join(RES, f"mipmap-{k}", "ic_launcher_fg.png"))   # adaptable: el sistema pone la forma
    lg = int(48 * s)
    forma(LOGO, lg, "square").save(os.path.join(RES, f"mipmap-{k}", "ic_launcher.png"))
    forma(LOGO, lg, "round").save(os.path.join(RES, f"mipmap-{k}", "ic_launcher_round.png"))
forma(LOGO, 512).convert("RGB").save(os.path.join(AQUI, "..", "store", "graficos", "icono-512.png"))   # Google Play redondea solo
print("íconos listos")
