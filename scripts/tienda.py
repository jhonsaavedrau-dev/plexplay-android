"""Capturas de la ficha de Google Play (1080x1920): titular arriba y la pantalla de la app en un teléfono.

1. Pantallas crudas: node herramientas/probar-arcade.mjs --prueba pruebas/tienda.mjs --tema light --ancho 390 --alto 844 --out <carpeta>
2. python plexplay-android/scripts/tienda.py <carpeta>   → escribe las páginas y la lista en <carpeta>/montar
3. node herramientas/probar-arcade.mjs --url about:blank --prueba pruebas/tienda-montar.mjs --ancho 540 --alto 960 --out plexplay-android/store/graficos
   (540x960 con escala 2 = 1080x1920; Chrome sin ventana se colgaba al capturar, por eso se usa el harness)
"""
import os
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[1]
SALIDA = RAIZ / "store" / "graficos"
FUENTES = RAIZ.parent / "web" / "app" / "fonts"
CHROME = Path(os.environ["LOCALAPPDATA"]) / "ms-playwright" / "chromium-1234" / "chrome-win64" / "chrome.exe"

# archivo crudo, nombre final, línea 1, línea 2 (resaltada), color de fondo claro, oscuro
PIEZAS = [
    ("t-1-inicio", "captura-1-inicio", "Tu francés de A1 a C1,", "en tu bolsillo", "#2B5BD7", "#0B2D74"),
    ("t-2-aprender", "captura-2-lecciones", "Tu curso completo,", "unidad por unidad", "#1CA0E8", "#0B4F9C"),
    ("t-6-runner", "captura-3-juegos", "Aprende corriendo", "con Manzana", "#7A5CF0", "#3B1F9E"),
    ("t-7-racha", "captura-4-racha", "Practica un poco", "cada día", "#FF8A1F", "#C2410C"),
    ("t-3-jugar", "captura-5-arcade", "Minijuegos para", "cada lección", "#E0457B", "#8E1850"),
    ("t-8-leccion", "captura-6-leccion", "Explicaciones claras,", "con audio", "#12A58A", "#0B5E50"),
    ("t-4-ranking", "captura-7-ranking", "Compite con", "tus compañeros", "#F2A900", "#A15C00"),
    ("t-5-perfil", "captura-8-perfil", "Tu gato, tu nivel,", "tu progreso", "#5B6BD6", "#1F2A7A"),
]

HTML = """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>
@font-face{{font-family:B;src:url('{f}/bricolage-latin.woff2') format('woff2');font-weight:200 800}}
@font-face{{font-family:B;src:url('{f}/bricolage-latin-ext.woff2') format('woff2');font-weight:200 800;unicode-range:U+0100-024F}}
html,body{{margin:0;width:540px;height:960px;overflow:hidden}}
.lienzo{{width:1080px;height:1920px;transform:scale(.5);transform-origin:0 0;position:relative;overflow:hidden}}
body{{margin:0}} .lienzo{{background:radial-gradient(circle at 50% 18%,{c1} 0,{c2} 78%);font-family:B,system-ui,sans-serif;position:relative}}
.brillo{{position:absolute;inset:0;background:radial-gradient(circle at 85% 90%,rgba(255,255,255,.14) 0,transparent 40%),radial-gradient(circle at 8% 60%,rgba(255,255,255,.08) 0,transparent 35%)}}
h1{{position:absolute;left:70px;right:70px;top:96px;margin:0;text-align:center;color:#fff;font-weight:800;font-size:92px;line-height:1.02;letter-spacing:-2px;text-shadow:0 6px 24px rgba(0,0,0,.18)}}
h1 span{{display:block;color:#FFD200}}
.tel{{position:absolute;left:50%;top:430px;width:720px;height:1558px;margin-left:-360px;border-radius:92px;background:#0B0D18;padding:22px;box-sizing:border-box;
  box-shadow:0 50px 90px -30px rgba(0,0,0,.6),inset 0 0 0 3px rgba(255,255,255,.12)}}
.tel img{{display:block;width:100%;height:100%;object-fit:cover;object-position:top;border-radius:72px}}
.isla{{position:absolute;left:50%;top:44px;width:150px;height:40px;margin-left:-75px;border-radius:30px;background:#0B0D18;z-index:2}}
</style></head><body><div class="lienzo"><div class="brillo"></div><h1>{l1}<span>{l2}</span></h1><div class="tel"><div class="isla"></div><img src="{img}"></div></div></body></html>"""


def main(cruda):
    cruda = Path(cruda)
    dest = cruda / "montar"
    dest.mkdir(parents=True, exist_ok=True)
    lista = []
    for src, dst, l1, l2, c1, c2 in PIEZAS:
        img = cruda / (src + ".png")
        if not img.exists():
            print("falta", img); continue
        html = dest / (dst + ".html")
        html.write_text(HTML.format(f=FUENTES.as_uri(), c1=c1, c2=c2, l1=l1, l2=l2, img=img.as_uri()), encoding="utf-8")
        lista.append({"url": html.as_uri(), "nombre": dst})
    import json
    (dest / "lista.json").write_text(json.dumps(lista), encoding="utf-8")
    print(len(lista), "páginas en", dest)


if __name__ == "__main__":
    main(sys.argv[1])
