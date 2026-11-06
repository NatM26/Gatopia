from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit
from datetime import datetime
from pathlib import Path
import hashlib
import hmac
import json
import os
import secrets
import sqlite3
import threading

HOST = "0.0.0.0"
PUERTO = 8000

CARPETA = Path(__file__).resolve().parent
DB = CARPETA / "gatopia.db"

CLAVE_ANTIGUA = "gatopia-demo-2026"
CLAVE_ARDUINO = "gatopia-arduino-2026"

bloqueo = threading.RLock()
sesiones = {}


def conectar_db():
    conexion = sqlite3.connect(DB, timeout=10)
    conexion.row_factory = sqlite3.Row
    return conexion


def crear_tablas():
    with conectar_db() as db:
        db.execute("""
            CREATE TABLE IF NOT EXISTS usuarios (
                usuario TEXT PRIMARY KEY,
                salt TEXT NOT NULL,
                password_hash TEXT NOT NULL,
                rol TEXT NOT NULL
            )
        """)

        db.execute("""
            CREATE TABLE IF NOT EXISTS sistema (
                id INTEGER PRIMARY KEY,
                activada INTEGER NOT NULL
            )
        """)

        db.execute("""
            INSERT OR IGNORE INTO sistema
            (id, activada) VALUES (1, 0)
        """)

        db.execute("""
            CREATE TABLE IF NOT EXISTS eventos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tipo TEXT NOT NULL,
                detalle TEXT NOT NULL,
                fecha TEXT NOT NULL
            )
        """)


def crear_hash(password, salt=None):
    if salt is None:
        salt = secrets.token_bytes(16)
    else:
        salt = bytes.fromhex(salt)

    resultado = hashlib.pbkdf2_hmac(
        "sha256",
        password.encode("utf-8"),
        salt,
        200000
    )

    return salt.hex(), resultado.hex()


def registrar_usuario(usuario, password, rol="usuario"):
    usuario = usuario.strip()

    if not (3 <= len(usuario) <= 30):
        return False, "Usuario: entre 3 y 30 caracteres"

    if len(password) < 6:
        return False, "Contraseña: mínimo 6 caracteres"

    if rol not in ("admin", "usuario"):
        return False, "Rol inválido"

    salt, password_hash = crear_hash(password)

    try:
        with conectar_db() as db:
            db.execute("""
                INSERT INTO usuarios
                (usuario, salt, password_hash, rol)
                VALUES (?, ?, ?, ?)
            """, (usuario, salt, password_hash, rol))

        return True, "Usuario registrado"

    except sqlite3.IntegrityError:
        return False, "El usuario ya existe"


def crear_admin_inicial():
    usuario = os.environ.get("GATOPIA_ADMIN_USER", "")
    password = os.environ.get("GATOPIA_ADMIN_PASSWORD", "")

    if not usuario or not password:
        print("Administrador inicial no configurado.")
        print("Configura GATOPIA_ADMIN_USER y")
        print("GATOPIA_ADMIN_PASSWORD antes de iniciar.")
        return

    with conectar_db() as db:
        existe = db.execute(
            "SELECT usuario FROM usuarios WHERE usuario = ?",
            (usuario,)
        ).fetchone()

    if not existe:
        correcto, mensaje = registrar_usuario(
            usuario, password, "admin"
        )
        print("Administrador inicial:", mensaje)
    else:
        print("Administrador existente conservado.")


def autenticar(usuario, password):
    with conectar_db() as db:
        fila = db.execute("""
            SELECT usuario, salt, password_hash, rol
            FROM usuarios WHERE usuario = ?
        """, (usuario,)).fetchone()

    if fila is None:
        return None

    _, calculado = crear_hash(password, fila["salt"])

    if not hmac.compare_digest(
        calculado, fila["password_hash"]
    ):
        return None

    token = secrets.token_urlsafe(32)

    with bloqueo:
        sesiones[token] = {
            "usuario": fila["usuario"],
            "rol": fila["rol"]
        }

    return {
        "token": token,
        "usuario": fila["usuario"],
        "rol": fila["rol"]
    }


def obtener_sesion(cabeceras):
    autorizacion = cabeceras.get("Authorization", "")

    if not autorizacion.startswith("Bearer "):
        return None

    token = autorizacion[7:]

    with bloqueo:
        return sesiones.get(token)


def autorizado_control(cabeceras):
    sesion = obtener_sesion(cabeceras)

    if sesion:
        return sesion["rol"] == "admin"

    # Solo durante la migración desde la app antigua.
    clave = cabeceras.get("X-Gatopia-Key", "")

    return hmac.compare_digest(clave, CLAVE_ANTIGUA)


def estado_actual(db):
    fila = db.execute(
        "SELECT activada FROM sistema WHERE id = 1"
    ).fetchone()

    return bool(fila["activada"])


def registrar_evento(db, tipo, detalle):
    fecha = datetime.now().strftime(
        "%Y-%m-%d %H:%M:%S"
    )

    cursor = db.execute("""
        INSERT INTO eventos (tipo, detalle, fecha)
        VALUES (?, ?, ?)
    """, (tipo, detalle, fecha))

    return {
        "id": cursor.lastrowid,
        "tipo": tipo,
        "detalle": detalle,
        "fecha": fecha
    }


class ServidorGatopia(BaseHTTPRequestHandler):

    def responder(self, codigo, datos):
        contenido = json.dumps(
            datos, ensure_ascii=False
        ).encode("utf-8")

        self.send_response(codigo)
        self.send_header(
            "Content-Type",
            "application/json; charset=utf-8"
        )
        self.send_header("Cache-Control", "no-store")
        self.send_header(
            "Content-Length", str(len(contenido))
        )
        self.end_headers()
        self.wfile.write(contenido)

    def leer_json(self):
        cantidad = int(
            self.headers.get("Content-Length", "0")
        )

        if cantidad > 8192:
            raise ValueError("Solicitud demasiado grande")

        contenido = self.rfile.read(cantidad)

        if not contenido:
            return {}

        datos = json.loads(contenido.decode("utf-8"))

        if not isinstance(datos, dict):
            raise ValueError("JSON inválido")

        return datos

    def do_GET(self):
        ruta = urlsplit(self.path).path

        if ruta == "/estado":
            with conectar_db() as db:
                activada = estado_actual(db)

            self.responder(200, {
                "estado": (
                    "activada" if activada
                    else "desactivada"
                ),
                "mensaje": "Estado consultado"
            })
            return

        if ruta == "/eventos":
            with conectar_db() as db:
                filas = db.execute("""
                    SELECT id, tipo, detalle, fecha
                    FROM eventos
                    ORDER BY id DESC
                    LIMIT 100
                """).fetchall()

            self.responder(200, {
                "eventos": [
                    dict(fila) for fila in reversed(filas)
                ]
            })
            return

        self.responder(404, {
            "error": "Ruta no encontrada"
        })

    def do_POST(self):
        ruta = urlsplit(self.path).path

        try:
            if ruta == "/registrar":
                datos = self.leer_json()

                correcto, mensaje = registrar_usuario(
                    str(datos.get("usuario", "")),
                    str(datos.get("password", ""))
                )

                self.responder(
                    201 if correcto else 400,
                    {"mensaje": mensaje}
                )
                return

            if ruta == "/login":
                datos = self.leer_json()

                resultado = autenticar(
                    str(datos.get("usuario", "")),
                    str(datos.get("password", ""))
                )

                if resultado is None:
                    self.responder(401, {
                        "error": "Credenciales incorrectas"
                    })
                else:
                    self.responder(200, resultado)
                return

            if ruta == "/logout":
                autorizacion = self.headers.get(
                    "Authorization", ""
                )

                if autorizacion.startswith("Bearer "):
                    token = autorizacion[7:]
                    with bloqueo:
                        sesiones.pop(token, None)

                self.responder(200, {
                    "mensaje": "Sesión cerrada"
                })
                return

            if ruta in ("/activar", "/desactivar"):
                if not autorizado_control(self.headers):
                    self.responder(403, {
                        "error": "Solo administradores"
                    })
                    return

                activada = ruta == "/activar"

                with bloqueo:
                    with conectar_db() as db:
                        db.execute("""
                            UPDATE sistema
                            SET activada = ?
                            WHERE id = 1
                        """, (int(activada),))

                        sesion = obtener_sesion(self.headers)
                        actor = (
                            sesion["usuario"]
                            if sesion else "Cliente anterior"
                        )

                        registrar_evento(
                            db,
                            "ACTIVACIÓN" if activada
                            else "DESACTIVACIÓN",
                            f"Comando de {actor}"
                        )

                self.responder(200, {
                    "estado": (
                        "activada" if activada
                        else "desactivada"
                    ),
                    "mensaje": "Comando autorizado"
                })
                return

            if ruta == "/vibracion":
                clave = self.headers.get(
                    "X-Gatopia-Key", ""
                )

                es_arduino = hmac.compare_digest(
                    clave, CLAVE_ARDUINO
                )

                if not (
                    es_arduino
                    or autorizado_control(self.headers)
                ):
                    self.responder(403, {
                        "error": "Acceso denegado"
                    })
                    return

                with bloqueo:
                    with conectar_db() as db:
                        if not estado_actual(db):
                            self.responder(409, {
                                "error": "Alarma desactivada"
                            })
                            return

                        evento = registrar_evento(
                            db,
                            "VIBRACIÓN",
                            "Vibración SW-420 detectada"
                        )

                self.responder(200, {
                    "mensaje": "Alerta registrada",
                    "evento": evento
                })
                return

            self.responder(404, {
                "error": "Ruta no encontrada"
            })

        except (ValueError, json.JSONDecodeError):
            self.responder(400, {
                "error": "Datos inválidos"
            })

        except Exception as error:
            print("Error interno:", error)
            self.responder(500, {
                "error": "Error interno del servidor"
            })


if __name__ == "__main__":
    crear_tablas()
    crear_admin_inicial()

    servidor = ThreadingHTTPServer(
        (HOST, PUERTO),
        ServidorGatopia
    )

    print("GATOPÍA - SERVIDOR CENTRAL")
    print("Base de datos:", DB)
    print("Dirección:", f"http://{HOST}:{PUERTO}")
    print("Esperando conexiones...")

    servidor.serve_forever()
