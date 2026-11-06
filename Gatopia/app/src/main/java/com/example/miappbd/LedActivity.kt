package com.example.miappbd

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LedActivity : AppCompatActivity() {

    private val servidor = "http://10.0.2.2:8000"
    private val handler = Handler(Looper.getMainLooper())
    private val intervalo = 2000L

    private var alarmaActivada = false
    private var estadoConfirmado = false
    private var solicitudEnCurso = false
    private var monitoreoActivo = false
    private var generacion = 0

    private var ultimoEventoVisto = 0
    private var eventosInicializados = false

    private lateinit var bd: BaseDeDatos
    private lateinit var usuarioActual: String
    private lateinit var rolActual: String
    private lateinit var token: String

    private lateinit var textoEstado: TextView
    private lateinit var textoHistorial: TextView

    private lateinit var botonEncender: Button
    private lateinit var botonApagar: Button
    private lateinit var botonConectar: Button
    private lateinit var botonMovimiento: Button

    private fun esAdministrador(): Boolean {
        return rolActual == "admin"
    }

    private val tareaMonitoreo = object : Runnable {
        override fun run() {
            if (!monitoreoActivo) return

            if (!solicitudEnCurso) {
                consultarServidor()
            }

            handler.postDelayed(this, intervalo)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_led)

        bd = BaseDeDatos(this)

        usuarioActual = intent.getStringExtra("usuario") ?: ""
        rolActual = intent.getStringExtra("rol") ?: ""
        token = intent.getStringExtra("token") ?: ""

        if (
            usuarioActual.isBlank() ||
            token.isBlank() ||
            rolActual !in listOf("admin", "usuario")
        ) {
            volverAlLogin()
            return
        }

        findViewById<TextView>(R.id.textoUsuario).text =
            if (esAdministrador()) {
                "Hola, $usuarioActual · Administrador"
            } else {
                "Hola, $usuarioActual · Usuario"
            }

        textoEstado = findViewById(R.id.textoEstado)
        textoHistorial = findViewById(R.id.textoHistorial)

        botonEncender = findViewById(R.id.botonEncender)
        botonApagar = findViewById(R.id.botonApagar)
        botonConectar = findViewById(R.id.botonConectar)
        botonMovimiento = findViewById(R.id.botonMovimiento)

        if (!esAdministrador()) {
            botonEncender.visibility = View.GONE
            botonApagar.visibility = View.GONE
            botonMovimiento.visibility = View.GONE
        }

        actualizarEstado()
        actualizarHistorial()
        actualizarBotones()

        botonEncender.setOnClickListener {
            enviarPost("/activar", "ACTIVACIÓN")
        }

        botonApagar.setOnClickListener {
            enviarPost("/desactivar", "DESACTIVACIÓN")
        }

        botonMovimiento.setOnClickListener {
            enviarPost("/vibracion", "VIBRACIÓN")
        }

        botonConectar.setOnClickListener {
            consultarServidor()
        }

        findViewById<Button>(
            R.id.botonHistorial
        ).setOnClickListener {
            startActivity(
                Intent(this, HistorialActivity::class.java)
            )
        }

        findViewById<Button>(
            R.id.botonCerrarSesion
        ).setOnClickListener {
            cerrarSesion()
        }
    }

    override fun onStart() {
        super.onStart()

        monitoreoActivo = true
        generacion++

        handler.removeCallbacks(tareaMonitoreo)
        handler.post(tareaMonitoreo)
    }

    override fun onStop() {
        monitoreoActivo = false
        generacion++

        handler.removeCallbacks(tareaMonitoreo)
        super.onStop()
    }

    private fun consultarServidor() {
        if (solicitudEnCurso) return

        solicitudEnCurso = true
        actualizarBotones()

        val generacionActual = generacion

        Thread {
            try {
                val estado = obtenerJson("/estado")
                val historial = obtenerJson("/eventos")

                runOnUiThread {
                    if (
                        !monitoreoActivo ||
                        generacionActual != generacion
                    ) {
                        solicitudEnCurso = false
                        return@runOnUiThread
                    }

                    val estadoTexto = estado.getString("estado")

                    if (
                        estadoTexto != "activada" &&
                        estadoTexto != "desactivada"
                    ) {
                        throw Exception("Estado inválido")
                    }

                    alarmaActivada = estadoTexto == "activada"
                    estadoConfirmado = true

                    actualizarEstado()

                    procesarEventos(
                        historial.getJSONArray("eventos")
                    )

                    solicitudEnCurso = false
                    actualizarBotones()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    if (
                        !monitoreoActivo ||
                        generacionActual != generacion
                    ) {
                        solicitudEnCurso = false
                        return@runOnUiThread
                    }

                    estadoConfirmado = false
                    actualizarEstado()

                    solicitudEnCurso = false
                    actualizarBotones()
                }
            }
        }.start()
    }

    private fun enviarPost(
        ruta: String,
        tipo: String
    ) {
        if (!esAdministrador() || solicitudEnCurso) {
            return
        }

        solicitudEnCurso = true
        actualizarBotones()

        val generacionActual = generacion

        Thread {
            var conexion: HttpURLConnection? = null

            try {
                conexion = URL(
                    servidor + ruta
                ).openConnection() as HttpURLConnection

                conexion.requestMethod = "POST"
                conexion.connectTimeout = 5000
                conexion.readTimeout = 5000

                conexion.setRequestProperty(
                    "Authorization",
                    "Bearer $token"
                )

                val codigo = conexion.responseCode

                if (codigo != 200) {
                    val error = conexion.errorStream
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: ""

                    throw Exception(
                        "HTTP $codigo $error"
                    )
                }

                val respuesta = conexion.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                val json = JSONObject(respuesta)

                runOnUiThread {
                    if (
                        !monitoreoActivo ||
                        generacionActual != generacion
                    ) {
                        solicitudEnCurso = false
                        return@runOnUiThread
                    }

                    if (ruta != "/vibracion") {
                        alarmaActivada =
                            json.getString("estado") == "activada"

                        estadoConfirmado = true
                        actualizarEstado()
                    }

                    solicitudEnCurso = false
                    actualizarBotones()

                    if (ruta == "/vibracion") {
                        mensaje("¡Vibración compartida!")
                    } else {
                        mensaje("Comando confirmado")
                    }

                    // El siguiente monitoreo recuperará
                    // el evento desde la base central.
                }

            } catch (e: Exception) {
                runOnUiThread {
                    if (
                        !monitoreoActivo ||
                        generacionActual != generacion
                    ) {
                        solicitudEnCurso = false
                        return@runOnUiThread
                    }

                    solicitudEnCurso = false
                    actualizarBotones()

                    mensaje(
                        "No se pudo enviar: ${e.message}"
                    )
                }

            } finally {
                conexion?.disconnect()
            }
        }.start()
    }

    private fun obtenerJson(ruta: String): JSONObject {
        val conexion = URL(
            servidor + ruta
        ).openConnection() as HttpURLConnection

        try {
            conexion.requestMethod = "GET"
            conexion.connectTimeout = 4000
            conexion.readTimeout = 4000

            conexion.setRequestProperty(
                "Authorization",
                "Bearer $token"
            )

            if (conexion.responseCode != 200) {
                throw Exception(
                    "HTTP ${conexion.responseCode}"
                )
            }

            val respuesta = conexion.inputStream
                .bufferedReader()
                .use { it.readText() }

            return JSONObject(respuesta)

        } finally {
            conexion.disconnect()
        }
    }

    private fun procesarEventos(eventos: JSONArray) {
        var maximoId = ultimoEventoVisto
        var nuevaVibracion = false

        val preferencias = getSharedPreferences(
            "gatopia_sync",
            MODE_PRIVATE
        )

        // Se conserva el último ID entre aperturas
        // para no duplicar los eventos locales.
        if (!eventosInicializados) {
            ultimoEventoVisto = preferencias.getInt(
                "ultimo_evento_central",
                0
            )

            maximoId = ultimoEventoVisto
            eventosInicializados = true
        }

        for (i in 0 until eventos.length()) {
            val evento = eventos.getJSONObject(i)
            val id = evento.getInt("id")

            if (id > ultimoEventoVisto) {
                val tipo = evento.getString("tipo")
                val detalle = evento.getString("detalle")
                val fecha = evento.getString("fecha")

                bd.registrarEvento(
                    tipo,
                    "CENTRAL [$fecha]: $detalle"
                )

                if (
                    tipo == "VIBRACIÓN" &&
                    ultimoEventoVisto != 0
                ) {
                    nuevaVibracion = true
                }

                if (id > maximoId) {
                    maximoId = id
                }
            }
        }

        ultimoEventoVisto = maximoId

        preferencias.edit()
            .putInt(
                "ultimo_evento_central",
                ultimoEventoVisto
            )
            .apply()

        actualizarHistorial()

        if (nuevaVibracion) {
            mensaje("¡ALERTA! Vibración detectada")
        }
    }

    private fun actualizarEstado() {
        when {
            !estadoConfirmado -> {
                textoEstado.text =
                    "SIN CONEXIÓN O ESTADO NO CONFIRMADO"

                textoEstado.setBackgroundColor(
                    Color.parseColor("#ECE8F2")
                )

                textoEstado.setTextColor(
                    Color.parseColor("#62445B")
                )
            }

            alarmaActivada -> {
                textoEstado.text =
                    "ALARMA ACTIVADA - SERVIDOR"

                textoEstado.setBackgroundColor(
                    Color.parseColor("#D8F2E5")
                )

                textoEstado.setTextColor(
                    Color.parseColor("#246B59")
                )
            }

            else -> {
                textoEstado.text =
                    "ALARMA DESACTIVADA - SERVIDOR"

                textoEstado.setBackgroundColor(
                    Color.parseColor("#F9DDE1")
                )

                textoEstado.setTextColor(
                    Color.parseColor("#A52E45")
                )
            }
        }
    }

    private fun actualizarHistorial() {
        textoHistorial.text = bd.obtenerEventos()
    }

    private fun actualizarBotones() {
        val puedeControlar =
            esAdministrador() && !solicitudEnCurso

        botonEncender.isEnabled = puedeControlar
        botonApagar.isEnabled = puedeControlar
        botonMovimiento.isEnabled = puedeControlar

        botonConectar.isEnabled = !solicitudEnCurso
    }

    private fun cerrarSesion() {
        val tokenAnterior = token

        // Se cierra la pantalla inmediatamente.
        volverAlLogin()

        // Se solicita invalidar el token en Python.
        Thread {
            var conexion: HttpURLConnection? = null

            try {
                conexion = URL(
                    "$servidor/logout"
                ).openConnection() as HttpURLConnection

                conexion.requestMethod = "POST"
                conexion.connectTimeout = 2000
                conexion.readTimeout = 2000

                conexion.setRequestProperty(
                    "Authorization",
                    "Bearer $tokenAnterior"
                )

                conexion.responseCode

            } catch (_: Exception) {
                // El cierre local ya se realizó.
            } finally {
                conexion?.disconnect()
            }
        }.start()
    }

    private fun volverAlLogin() {
        monitoreoActivo = false
        generacion++

        handler.removeCallbacks(tareaMonitoreo)

        val volver = Intent(
            this,
            MainActivity::class.java
        )

        volver.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(volver)
        finish()
    }

    private fun mensaje(texto: String) {
        Toast.makeText(
            this,
            texto,
            Toast.LENGTH_LONG
        ).show()
    }
}
