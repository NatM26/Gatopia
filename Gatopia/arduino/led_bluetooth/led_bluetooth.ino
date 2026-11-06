// =====================================================================
//  Arduino UNO + HC-05: encender y apagar un LED desde la app Android
//
//  CONEXIONES
//    HC-05 VCC -> 5V
//    HC-05 GND -> GND
//    HC-05 TXD -> pin 10 del Arduino
//    HC-05 RXD -> pin 11 del Arduino (mejor con divisor de voltaje:
//                 resistencia de 1k en serie y 2k a GND, porque el
//                 HC-05 trabaja a 3.3V)
//    LED (+, pata larga) -> resistencia 220 ohm -> pin 13
//    LED (-, pata corta) -> GND
//    (El pin 13 también enciende el LED que trae la placa.)
// =====================================================================

// PASO 1: SoftwareSerial crea un puerto serie en otros pines (10 y 11),
//         así dejamos libres los pines 0 y 1 para subir el programa por USB.
#include <SoftwareSerial.h>
SoftwareSerial bluetooth(10, 11);  // (RX, TX) del Arduino

const int LED = 13;

void setup() {
  // PASO 2: El pin del LED es una SALIDA.
  pinMode(LED, OUTPUT);

  // PASO 3: El HC-05 se comunica a 9600 baudios por defecto.
  bluetooth.begin(9600);
}

void loop() {
  // PASO 4: ¿Llegó algún dato desde el teléfono?
  if (bluetooth.available()) {
    char dato = bluetooth.read();

    // PASO 5: "1" enciende el LED, "0" lo apaga.
    if (dato == '1') digitalWrite(LED, HIGH);
    if (dato == '0') digitalWrite(LED, LOW);
  }
}
