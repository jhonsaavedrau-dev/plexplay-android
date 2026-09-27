# PLEX PLAY en Google Play: todo listo para subir

Esta carpeta tiene los textos, las imágenes y las respuestas que pide Google Play Console.
Solo falta crear la cuenta de desarrollador. Es un pago único de **25 USD**, por eso aún no se ha hecho.

## 1. Ficha de la tienda (Store listing)

**Nombre de la app** (máx. 30): `PLEX PLAY: francés A1 a C1`

**Descripción breve** (máx. 80):
`Francés de A1 a C1: lecciones, sonidos, vocabulario y conversación con IA.`

**Descripción completa** (máx. 4000):

```
PLEX PLAY es una app gratuita para practicar francés fuera del aula, pensada para los estudiantes del programa de Lenguas Extranjeras de la Universidad de Pamplona. Sigue la malla de la licenciatura, de Francés A1 a C1.

LECCIONES DE TU CURSO
• Cursos de A1 a C1: gramática, conjugación, vocabulario, fonética, texto académico, cultura y literatura.
• Cada lección trae una explicación en español con ejemplos y audio, y ejercicios que te corrigen al instante.
• Repaso inteligente: los ejercicios que fallas vuelven cuando más los necesitas.

SONIDOS DEL FRANCÉS
• Los sonidos que más cuestan a quien habla español: la u francesa, las nasales, la r, la é y la è, la liaison…
• Escucha, repite con el micrófono y entrena el oído con pares mínimos.

VOCABULARIO POR CURSO
• Las palabras clave de cada curso, por temas, con audio y un ejemplo.
• Tarjetas y el reto «¿Qué significa?» para memorizarlas.

CONVERSA CON MANZANA (IA)
• Chatea en francés a tu nivel en situaciones reales: presentarte, pedir en un café, contar tu fin de semana, debatir…
• Manzana te responde y te corrige con una explicación corta en español.

MÁS PRÁCTICA
• Lecturas graduadas con audio, dictados, expresión oral con IA y simulacros DELF/DALF.
• Rachas, misiones diarias, ligas semanales y duelos 1V1 con tus compañeros.
• Personaliza a Manzana, tu gato.

PARA DOCENTES
• Panel docente: crea clases con un código, sigue el avance de cada estudiante, asigna tareas y publica avisos.

Sin publicidad y sin compras. Entras con tu correo institucional @unipamplona.edu.co. Si solo quieres mirar, toca «Probar sin cuenta».

PLEX PLAY es un proyecto personal de un estudiante de la licenciatura. No es una app oficial de la Universidad de Pamplona.
```

**Categoría:** Educación · **Etiquetas:** Aprendizaje de idiomas, Educación
**Correo de contacto:** jhon.saavedra@unipamplona.edu.co
**Sitio web:** https://jhonsaavedrau-dev.github.io/portfolio-francais-c1-1/plexplay/
**Política de privacidad:** https://jhonsaavedrau-dev.github.io/portfolio-francais-c1-1/plexplay/privacy.html

**Gráficos** (carpeta `graficos/`):
- Ícono 512 × 512: `icono-512.png`
- Gráfico de funciones 1024 × 500: `feature-1024x500.png`
- Capturas de teléfono 1080 × 1920, en este orden: `captura-1-inicio.png` … `captura-8-retos.png`

## 2. Contenido de la app (App content)

| Sección | Respuesta |
|---|---|
| Política de privacidad | La URL de arriba |
| Anuncios | No, la app no tiene anuncios |
| Acceso a la app | «Toda la funcionalidad está disponible sin acceso especial»: en la primera pantalla, toca **Probar sin cuenta** (modo demo). Si Google pide una cuenta de prueba, crea una con un correo institucional tuyo de pruebas, nunca con tu contraseña personal. |
| Clasificación de contenido | Cuestionario IARC → categoría «Referencia, noticias o educativa». Sin violencia, sexo, lenguaje, drogas ni juegos de azar. Los usuarios interactúan (clasificación y duelos 1V1), sin chat libre entre personas. Resultado esperado: **Para todos / PEGI 3**. |
| Público objetivo | **18 años o más** (estudiantes universitarios). No está dirigida a niños. |
| App de noticias | No |
| Apps de salud / finanzas / gobierno | No |
| Eliminación de cuentas | Sí: dentro de la app (**Perfil → Ajustes → Borrar mi cuenta**). URL para pedirlo desde la web: la política de privacidad, sección 8. |

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
