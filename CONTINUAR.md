# Continuar el launcher retro (léelo primero)

Este repositorio es la versión personal de Vladimir (BladimirJr) del launcher de
[CleanYourTV](https://github.com/Gathin23/cleanyourtv) (MIT). El proyecto original quedó como el primer commit;
todo lo propio está en el segundo. Este archivo es para el asistente (Claude) que siga el trabajo en otra PC:
qué hay hecho, cómo compilar y qué preguntar antes de tocar la TV. Habla con el usuario en español.

## Qué está hecho

Todo se activa con claves de tema (`config.json`); sin ellas el launcher es el original.

| Clave | Qué hace | Código |
|---|---|---|
| `"scene": "bridge"` | Puente sci‑fi animado bajo un cielo con anillo | `BridgeView` |
| `"scene": "arcade"` | Sol synthwave, montañas neón, palmeras, cuadrícula que avanza y celdas que se encienden; tiles transparentes con marco fucsia y el nombre en letra pixel; resaltado de foco rosa neón | `ArcadeView`, `ArcadeTiles`, `PixelFont` |
| `"scene": "snow"` | Noche de invierno con nieve y un gorrito navideño sobre la hora | `SnowView`, `drawable/santa_hat.xml` |
| `"scene": "kawaii"` | Cielo pastel, arcoíris, corazones, piso de baldosas; tiles con 5 marcos (moño, corazones, estrellas, notas, puntos); imágenes propias opcionales | `KawaiiView`, `KawaiiTiles` |
| `"launch_transition": "deploy"` | Destello al abrir una app, del color del tema | `DeployFlash`, `anim/deploy_*` |
| `"screensaver": "galaxy"` | Salvapantallas de galaxias con el reloj | `GalaxyView`, `ClockScene` |
| `"home_settings_button": true` | Botón de casita (ajustes del launcher) al lado del engranaje (ajustes de la TV) | `MainActivity` |
| `"touch_controls": true` | Cruceta táctil para probar en un teléfono. **Nunca en la TV.** | `TouchPad` |

También:

- **Pantalla de carga retro** (`BootSplash`): sale una vez por encendido, tras el logo de la TV (ese logo no se
  puede cambiar sin root), y espera en PRESS START hasta que se presione una tecla.
- **Ajustes del launcher** (la casita): "Fondo animado" para elegir el tema, "Ver pantalla de carga" para probarla,
  y la opacidad, que ahora también vuelve transparentes los tiles.

Los temas están en `themes/puente`, `themes/arcade`, `themes/nieve` y `themes/kawaii`. Todo se probó en un teléfono
Honor (Android 15); **todavía no se ha instalado en una TV**.

## Imágenes de Hello Kitty (en `themes/kawaii/`)

El tema kawaii muestra `character.png`, `fairy.png`, `peek.png`, `sticker.png`, `logo.png` y `bow.png` de
`themes/kawaii/`. Son imágenes de Sanrio que el usuario descargó para uso personal, y van en el repo porque es
**privado**. Si algún día se hace público, primero hay que sacarlas (ver `themes/kawaii/.gitignore`). Nunca
publicarlas en otro lado. Sin ellas, el tema funciona igual, solo sin personajes. Para quitarles el fondo a imágenes nuevas se
usó un programa Java sencillo (relleno desde los bordes para personajes; quitar todo lo claro para logos).

## Compilar

JDK 17+ (sirve el `jbr` de Android Studio), Android SDK 34. En `launcher/`:

```bash
./gradlew --no-daemon assembleRelease lintRelease
```

Lint debe quedar sin errores. En la PC del trabajo (Windows), Gradle fallaba con "Unable to establish loopback
connection": se resolvió apuntando `TMP`, `TEMP` y `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<carpeta>
-Djava.io.tmpdir=<carpeta>` a una carpeta simple como `C:\Users\<usuario>\.gradle\tmp`.

Para probar en el teléfono sin TV: instalar el APK, subir el tema a
`/sdcard/Android/data/com.gathin.tvhome/files/config.json` (más las imágenes) y abrir `.MainActivity`. Para
navegar ahí, añadir `"touch_controls": true` **solo** en la copia del teléfono.

## La TV: seguir PROMPT.md al pie de la letra

La parte en la TV (ADB, respaldo, quitar bloatware, instalar el launcher, cerrar ADB al final) se hace siguiendo
[PROMPT.md](PROMPT.md) paso a paso, con capturas de la TV después de cada cambio. Las herramientas están en
`tools/`. No inventar atajos: ahí están las advertencias que evitan dejar una TV sin arrancar.

### Preguntas que hay que hacerle al usuario, en este orden

Antes de conectar:

1. ¿Qué **modelo exacto** es la TV (marca y modelo)? Busca si hay una carpeta suya en `examples/`.
2. ¿La TV tiene Android TV / Google TV? (Samsung Tizen, LG webOS y Fire TV no sirven.)
3. ¿Ya activaste las Opciones de desarrollador y la Depuración USB (y la de red o ADB si aparece)?
4. ¿Cuál es la **IP** de la TV, y está en la misma Wi‑Fi que esta PC?

Al conectar y antes de cambiar nada:

5. Avisar que aparecerá "¿Permitir depuración USB?" en la TV para que la acepte.
6. ¿Qué hay conectado a los **puertos USB** de la TV?
7. Aparte: ¿la TV recibe internet por un **adaptador Ethernet USB**? (Algunas cortan la energía del USB con la
   depuración activa.)

Durante la limpieza (siempre con preguntas de casillas):

8. ¿Qué apps de streaming usas? Solo se desactivan las que no marque.
9. ¿Usas el asistente de voz, Chromecast, Miracast y las actualizaciones de la TV?
10. Si el nombre de un paquete del fabricante no deja claro qué hace, preguntar antes de desactivarlo.

Para el launcher:

11. ¿Cuántos espacios quieres en el dock (4, 5, 6 o 7), con qué apps y en qué orden?
12. ¿Qué tema quieres de inicio: puente, arcade, nieve o kawaii? (Luego se cambia en la casita.)
13. Si elige kawaii: las imágenes de Kitty ya están en `themes/kawaii/`, pero se suben a la TV con `adb push`
    a la carpeta del tema, porque `tools/theme.sh` solo sube `config.json` y fondos.
14. Tras quitar el launcher original de Google TV: que pruebe cada botón del control (Netflix, YouTube, Entradas,
    Ajustes) y decir cuáles hay que reasignar, avisando lo que cuesta (en algunas TCL se pierde la repetición
    del volumen).
15. Opcional: ¿quieres DNS que bloquee anuncios? (No bloquea los de YouTube; se puede deshacer.)

Al terminar:

16. Recordarle que desactive la depuración USB y la de red en la TV; comprobar con `tools/adb-closed.py` que el
    puerto quedó cerrado.

### Cuidado al subir el tema a la TV

- `touch_controls` debe ir **apagado** en la TV.
- La pantalla de carga y las animaciones se redibujan unas 30 veces por segundo. En una TV de 1 GB de RAM hay que
  comprobar que va fluida; si no, ofrecer `"reduced_motion": true` o un tema sin escena.
