# MiAppBd: login con SQLite + LED por Bluetooth (Arduino)

1. **Login**: usuario y contraseña guardados en SQLite (**Ingresar** / **Registrarse**).
2. **Control del LED**: después de ingresar, la app se conecta al **HC-05** y envía `1` (encender) o `0` (apagar) al **Arduino UNO**.

## Archivos

| Orden | Archivo | Qué se enseña |
|---|---|---|
| 1 | `app/src/main/res/layout/activity_main.xml` | Diseñar la pantalla de login |
| 2 | `app/src/main/java/com/example/miappbd/BaseDeDatos.kt` | Crear la tabla, INSERT, SELECT y hash |
| 3 | `app/src/main/java/com/example/miappbd/MainActivity.kt` | Botones + base de datos, abrir otra pantalla |
| 4 | `arduino/led_bluetooth/led_bluetooth.ino` | Recibir datos por Bluetooth y prender el LED |
| 5 | `app/src/main/res/layout/activity_led.xml` + `LedActivity.kt` | Conectarse al HC-05 y enviar datos |
| 6 | `app/src/main/AndroidManifest.xml` | Permisos de Bluetooth |

## Circuito

| HC-05 | Arduino UNO |
|---|---|
| VCC | 5V |
| GND | GND |
| TXD | pin 10 |
| RXD | pin 11 (idealmente con divisor 1k / 2k) |

LED: pin 13 → resistencia 220 Ω → LED → GND (el LED de la placa también funciona).

## Antes de la clase

1. Subir `led_bluetooth.ino` al Arduino (con el IDE de Arduino).
2. En el teléfono: **Ajustes → Bluetooth** → emparejar **HC-05** (PIN `1234` o `0000`).
3. Instalar la app desde Android Studio (▶ Run) en un **teléfono real**: el emulador no tiene Bluetooth.

## Plan sugerido (60 min)

- **5 min**: qué es SQLite y la tabla `usuarios (usuario, password)`.
- **10 min**: `activity_main.xml`.
- **15 min**: `BaseDeDatos.kt`.
- **10 min**: `MainActivity.kt`.
- **5 min**: sketch de Arduino y circuito.
- **10 min**: `LedActivity.kt` + `activity_led.xml` + permisos.
- **5 min**: probar: registrarse → ingresar → conectar → encender/apagar.

> Para ahorrar tiempo, el circuito puede estar armado y el HC-05 emparejado antes de empezar.
