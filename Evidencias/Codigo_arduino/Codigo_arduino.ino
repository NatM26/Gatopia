
#include <WiFiS3.h>

// ==========================================
// GATOPIA - SISTEMA DE ALARMA INTELIGENTE
// Arduino UNO R4 WiFi + Sensor SW-420
// ==========================================

// CONFIGURACION WIFI
// Completar antes de subir al Arduino.
const char* WIFI_NOMBRE = "Nat";
const char* WIFI_CLAVE = "nm431273";

// CONFIGURACION SERVIDOR
// IP del notebook donde funciona Python.
// Cambiar si cambia la red.
const char* SERVIDOR_IP = "10.26.33.2";
const int SERVIDOR_PUERTO = 8000;

// Clave del dispositivo para el prototipo local.
const char* CLAVE_DISPOSITIVO = "gatopia-arduino-2026";

// PINES
const int PIN_SENSOR = 2;
const int PIN_LED_EXTERNO = 8;

// Algunos sensores SW-420 detectan con LOW.
const int NIVEL_VIBRACION = HIGH;

// TIEMPOS EN MILISEGUNDOS
const unsigned long INTERVALO_ESTADO = 2000;
const unsigned long INTERVALO_RECONEXION = 10000;
const unsigned long TIEMPO_ENTRE_ALERTAS = 3000;
const unsigned long DURACION_LED = 1000;
const unsigned long ESPERA_IP = 10000;

// VARIABLES
bool alarmaActivada = false;
bool lecturaAnterior = false;
bool primeraConsulta = true;
bool ledEncendido = false;

unsigned long ultimaConsulta = 0;
unsigned long ultimaConexion = 0;
unsigned long ultimaAlerta = 0;
unsigned long momentoEncenderLed = 0;

// ==========================================
// CONTROL DEL LED EXTERNO
// ==========================================
void encenderLed() {
  digitalWrite(PIN_LED_EXTERNO, HIGH);
  momentoEncenderLed = millis();
  ledEncendido = true;
}

void apagarLed() {
  digitalWrite(PIN_LED_EXTERNO, LOW);
  ledEncendido = false;
}

void actualizarLed() {
  if (
    ledEncendido &&
    millis() - momentoEncenderLed >= DURACION_LED
  ) {
    apagarLed();
  }
}

// ==========================================
// CONEXION WIFI
// ==========================================
void conectarWiFi() {

  if (WiFi.status() == WL_CONNECTED) {
    return;
  }

  Serial.println("Conectando a WiFi...");

  WiFi.begin(WIFI_NOMBRE, WIFI_CLAVE);

  unsigned long inicio = millis();

  while (
    WiFi.status() != WL_CONNECTED &&
    millis() - inicio < 15000
  ) {
    delay(500);
    Serial.print(".");
  }

  Serial.println();

  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("No fue posible conectar a WiFi");
    return;
  }

  Serial.println("WiFi conectado correctamente");

  // Esperar a que DHCP asigne una direccion IP.
  Serial.println("Esperando direccion IP...");

  unsigned long inicioIP = millis();

  while (
    WiFi.localIP() == IPAddress(0, 0, 0, 0) &&
    millis() - inicioIP < ESPERA_IP
  ) {
    delay(500);
  }

  Serial.print("IP del Arduino: ");
  Serial.println(WiFi.localIP());

  if (WiFi.localIP() == IPAddress(0, 0, 0, 0)) {
    Serial.println(
      "Aviso: IP no disponible todavia"
    );
  }

  primeraConsulta = true;
}

// ==========================================
// SOLICITUDES HTTP
// ==========================================
String solicitudHTTP(
  const char* metodo,
  const char* ruta,
  bool enviarClave
) {

  WiFiClient cliente;

  if (!cliente.connect(SERVIDOR_IP, SERVIDOR_PUERTO)) {
    Serial.println("No se pudo conectar al servidor");
    return "";
  }

  cliente.print(metodo);
  cliente.print(" ");
  cliente.print(ruta);
  cliente.println(" HTTP/1.1");

  cliente.print("Host: ");
  cliente.println(SERVIDOR_IP);

  if (enviarClave) {
    cliente.print("X-Gatopia-Key: ");
    cliente.println(CLAVE_DISPOSITIVO);
  }

  cliente.println("Connection: close");
  cliente.println("Content-Length: 0");
  cliente.println();

  String respuesta = "";
  unsigned long inicio = millis();

  while (
    cliente.connected() ||
    cliente.available()
  ) {

    while (cliente.available()) {
      char caracter = cliente.read();

      if (respuesta.length() < 2048) {
        respuesta += caracter;
      }
    }

    if (millis() - inicio > 4000) {
      Serial.println("Tiempo de espera HTTP agotado");
      break;
    }

    delay(1);
  }

  cliente.stop();
  return respuesta;
}

// ==========================================
// CONSULTAR ESTADO DE LA ALARMA
// ==========================================
void consultarEstado() {

  String respuesta = solicitudHTTP(
    "GET",
    "/estado",
    false
  );

  if (
    !respuesta.startsWith("HTTP/1.1 200") &&
    !respuesta.startsWith("HTTP/1.0 200")
  ) {
    alarmaActivada = false;
    Serial.println("Estado no confirmado");
    return;
  }

  int separador = respuesta.indexOf("\r\n\r\n");

  if (separador < 0) {
    alarmaActivada = false;
    Serial.println("Respuesta HTTP invalida");
    return;
  }

  String cuerpo = respuesta.substring(separador + 4);

  if (
    cuerpo.indexOf("\"estado\": \"activada\"") >= 0 ||
    cuerpo.indexOf("\"estado\":\"activada\"") >= 0
  ) {
    alarmaActivada = true;

    if (primeraConsulta) {
      Serial.println("Alarma ACTIVADA");
    }

  } else if (
    cuerpo.indexOf("\"estado\": \"desactivada\"") >= 0 ||
    cuerpo.indexOf("\"estado\":\"desactivada\"") >= 0
  ) {
    alarmaActivada = false;

    if (primeraConsulta) {
      Serial.println("Alarma DESACTIVADA");
    }

  } else {
    alarmaActivada = false;
    Serial.println("Estado desconocido");
  }

  primeraConsulta = false;
}

// ==========================================
// ENVIAR ALERTA AL SERVIDOR
// ==========================================
void enviarAlerta() {

  Serial.println("Enviando alerta a Gatopia...");

  String respuesta = solicitudHTTP(
    "POST",
    "/vibracion",
    true
  );

  if (
    respuesta.startsWith("HTTP/1.1 200") ||
    respuesta.startsWith("HTTP/1.0 200")
  ) {
    Serial.println("ALERTA REGISTRADA EN SERVIDOR");
  } else {
    Serial.println("Error al registrar alerta");
  }
}

// ==========================================
// CONFIGURACION INICIAL
// ==========================================
void setup() {

  Serial.begin(9600);

  pinMode(PIN_SENSOR, INPUT);
  pinMode(PIN_LED_EXTERNO, OUTPUT);

  apagarLed();

  // Prueba del LED al iniciar.
  for (int i = 0; i < 2; i++) {
    digitalWrite(PIN_LED_EXTERNO, HIGH);
    delay(300);
    digitalWrite(PIN_LED_EXTERNO, LOW);
    delay(300);
  }

  Serial.println("==========================");
  Serial.println("      GATOPIA ARDUINO     ");
  Serial.println("==========================");

  conectarWiFi();

  if (WiFi.status() == WL_CONNECTED) {
    consultarEstado();
    ultimaConsulta = millis();
  }
}

// ==========================================
// BUCLE PRINCIPAL
// ==========================================
void loop() {

  unsigned long ahora = millis();

  // 1. Actualizar LED
  actualizarLed();

  // 2. Verificar conexion WiFi
  if (WiFi.status() != WL_CONNECTED) {

    alarmaActivada = false;
    apagarLed();

    if (
      ahora - ultimaConexion >= INTERVALO_RECONEXION
    ) {
      ultimaConexion = ahora;
      conectarWiFi();
    }

    return;
  }

  // 3. Consultar estado del servidor
  if (
    ahora - ultimaConsulta >= INTERVALO_ESTADO
  ) {
    ultimaConsulta = ahora;
    consultarEstado();
  }

  // 4. Leer sensor SW-420
  bool vibracionActual =
    digitalRead(PIN_SENSOR) == NIVEL_VIBRACION;

  bool nuevaVibracion =
    vibracionActual && !lecturaAnterior;

  bool tiempoCumplido =
    ultimaAlerta == 0 ||
    ahora - ultimaAlerta >= TIEMPO_ENTRE_ALERTAS;

  // 5. Procesar vibracion
  if (
    alarmaActivada &&
    nuevaVibracion &&
    tiempoCumplido
  ) {

    ultimaAlerta = ahora;

    Serial.println("VIBRACION DETECTADA");

    encenderLed();
    enviarAlerta();

    // Mantener el LED visible despues
    // de terminar la solicitud HTTP.
    momentoEncenderLed = millis();
  }

  lecturaAnterior = vibracionActual;

  // 6. Apagar LED cuando corresponda
  actualizarLed();
}
