# PLEX PLAY en Google Play

## Estado (30 de septiembre de 2026)

La app **PLEX PLAY: francés A1 a C1** (paquete `co.plexplay.app`) ya está creada en Play Console, gratis, categoría Educación.

**Hecho en Play Console:** configuración de la tienda (categoría y contacto), política de privacidad, anuncios (no), público objetivo (18+),
ID de publicidad (no), app gubernamental (no), funciones financieras (ninguna), salud (ninguna), clasificación de contenido (IARC),
seguridad de los datos, y los textos de la ficha (guardados como borrador).

**Falta (lo tiene que hacer Jhon):**
1. *Datos de inicio de sesión*: poner el correo y la contraseña de una cuenta de Google de prueba para los revisores
   (las instrucciones en inglés ya están). Los revisores no pueden usar cuentas propias.
2. *Ficha de Play Store → Gráficos*: subir `graficos/icono-512.png`, `graficos/feature-1024x500.png` y las 8 capturas en orden.
3. *Prueba cerrada*: subir `PLEX-PLAY.aab` de la última release de GitHub, crear la lista de testers (14+ Gmail) y compartir el enlace.

**Direcciones:** sitio `https://plexplay.app` · privacidad `https://plexplay.app/app/privacy` · borrar cuenta `https://plexplay.app/app/borrar-cuenta`

## 1. Ficha de la tienda (Store listing)

**Nombre de la app** (máx. 30): `PLEX PLAY: francés A1 a C1`

**Descripción breve** (máx. 80):
`Francés de A1 a C1 con lecciones, minijuegos, rachas y Manzana, tu gato.`

**Descripción completa** (máx. 4000):

```
PLEX PLAY es una app gratuita para aprender y practicar francés de A1 a C1, acompañado por Manzana, un gato con boina que te anima cada día. Nació para los estudiantes de Lenguas Extranjeras y sigue sus cursos semestre a semestre, pero cualquiera que quiera aprender francés puede usarla.

LECCIONES DE TU CURSO
• Desde Primeros pasos hasta C1: gramática, conjugación, vocabulario, fonética, texto académico, cultura y literatura.
• Cada lección trae una explicación clara en español, ejemplos con audio y ejercicios que se corrigen al instante.
• Repaso inteligente: lo que fallas vuelve cuando más lo necesitas.

APRENDE JUGANDO
• Minijuegos para cada lección: Grammar Run (corre y elige la respuesta correcta), Memory Rush, Voice Duel, Boss Battle y más.
• Rachas diarias con celebraciones, misiones, cofres y un ranking con tus compañeros.
• PLEX 1V1: reta a otro estudiante.

SONIDOS Y VOZ
• Los sonidos que más cuestan a quien habla español: la u francesa, las nasales, la r, la é y la è…
• Repite con el micrófono y entrena el oído.

LEE, ESCRIBE Y CONVERSA
• Lecturas con audio, dictados y simulacros de examen.
• Conversa en francés con Manzana (IA) en situaciones reales y recibe correcciones cortas en español.

PARA DOCENTES
• Panel docente: crea clases con un código, sigue el avance de cada estudiante, asigna tareas y publica avisos.

Sin publicidad y sin compras. Entras con tu cuenta de Google.

PLEX PLAY es un proyecto independiente creado por un estudiante de Lenguas Extranjeras. No es una app oficial de ninguna universidad.
```

**Categoría:** Educación · **Correo de contacto:** jhonsaavedrau@gmail.com · **Sitio web:** https://plexplay.app

**Gráficos** (carpeta `graficos/`, se regeneran con `scripts/tienda.py`):
- Ícono 512 × 512: `icono-512.png`
- Gráfico de funciones 1024 × 500: `feature-1024x500.png`
- Capturas de teléfono 1080 × 1920, en este orden: `captura-1-inicio.png` … `captura-8-perfil.png`

## 2. Contenido de la app (App content)

| Sección | Respuesta |
|---|---|
| Política de privacidad | La URL de arriba |
| Anuncios | No, la app no tiene anuncios |
| Datos de inicio de sesión | Sí hay acceso restringido (entrar con Google). Cuenta de Google de prueba para los revisores + instrucciones en inglés (ya puestas). |
| Clasificación de contenido | Cuestionario IARC → categoría «Referencia, noticias o educativa». Sin violencia, sexo, lenguaje, drogas ni juegos de azar. Los usuarios interactúan (clasificación y duelos 1V1), sin chat libre entre personas. Resultado esperado: **Para todos / PEGI 3**. |
| Público objetivo | **18 años o más**. (Con 16-17 Google pide requisitos de apps para menores.) |
| App de noticias | No |
| Apps de salud / finanzas / gobierno | No |
| Eliminación de cuentas | Sí: dentro de la app (**avatar → Ajustes → Borrar mi cuenta**). URL: https://plexplay.app/app/borrar-cuenta |

### Seguridad de los datos (Data safety)

- **¿Recopila o comparte datos?** Recopila: sí. Comparte con terceros: no. Los proveedores que procesan datos para la app (Supabase, Google Gemini, Brevo) cuentan como proveedores de servicio, no como «compartir».
- **Cifrado en tránsito:** sí (HTTPS).
- **El usuario puede pedir que se borren sus datos:** sí.

| Tipo de dato | Se recopila | Obligatorio | Para qué |
|---|---|---|---|
| Correo electrónico | Sí | Sí | Gestión de la cuenta |
| Nombre (apodo) | Sí | Sí | Funciones de la app (clasificación) |
| ID de usuario | Sí | Sí | Gestión de la cuenta, funciones de la app |
| Interacciones en la app (progreso, XP, respuestas) | Sí | Sí | Funciones de la app |
| Grabaciones de voz | Sí, solo en expresión oral con IA | No | Funciones de la app. Se procesan y no se guardan. |
| Otros contenidos del usuario (textos para corregir y mensajes a Manzana) | Sí | No | Funciones de la app |
| Registros de fallos | Sí | Sí | Análisis (arreglar errores) |

## 3. Prueba cerrada: 12 personas durante 14 días

Las cuentas personales nuevas de Google Play deben hacer una prueba cerrada antes de publicar: **mínimo 12 personas inscritas y activas durante 14 días seguidos**.

1. **Crea la cuenta** en play.google.com/console. Tipo «Personal». Pago único de 25 USD y verificación de identidad.
2. **Crea la app:** «Crear app» → nombre `PLEX PLAY` → App → Gratis → acepta las declaraciones.
3. **Sube el AAB:** Pruebas → Prueba cerrada → Crear pista → Crear versión → sube `PLEX-PLAY.aab`. Está en la última versión de *Releases* de este repositorio. Acepta la **firma de apps de Google Play** (Play App Signing).
   - La clave con la que firma GitHub Actions queda como *clave de subida*. Guarda una copia del archivo `.jks` y de su contraseña fuera de GitHub.
4. **Testers:** en «Testers», crea una lista de correo y agrega **al menos 14 correos de Gmail**. Son las cuentas de Google con las que tus compañeros usan Play Store. Pon más de 12 por si alguno no se activa.
5. **Comparte el enlace de inscripción** («Unirse en la Web») por WhatsApp. Cada persona debe:
   1. Abrir el enlace con la cuenta de Google de su celular y tocar **«Convertirse en tester»**.
   2. Instalar PLEX PLAY desde el enlace de Play Store (no desde el APK).
   3. **Abrirla y usarla al menos un rato cada día o cada dos días**, y no desinstalarla durante los 14 días.
6. **Completa la ficha y el contenido de la app** (secciones 1 y 2) mientras corren los 14 días.
7. **Día 15:** en el Panel aparece «Solicitar acceso a producción». Responde las preguntas sobre la prueba:
   - ¿Cómo reclutaste testers? «Compañeros del programa de Lenguas Extranjeras».
   - ¿Qué comentarios recibiste? Anota 2 o 3 cosas que te dijeron y qué cambiaste.
   - La revisión tarda unos 7 días.
8. **Producción:** crea la versión en «Producción» con el mismo AAB o uno más nuevo y envíala a revisión.

**Importante:**
- Cada nuevo AAB necesita un `versionCode` mayor. GitHub Actions ya lo sube solo con cada compilación, así que siempre sirve el AAB más reciente.
- Mensaje para los testers: «Necesito 12 personas que prueben PLEX PLAY por 14 días para poder publicarla en Play Store 🙏. 1) Entra a este enlace con tu Gmail y toca "Convertirse en tester". 2) Instálala desde Play Store. 3) Ábrela un ratico cada día. ¡Gracias!»
