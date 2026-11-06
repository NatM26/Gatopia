package com.example.miappbd

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val servidor = "http://10.0.2.2:8000"

    private lateinit var campoUsuario: EditText
    private lateinit var campoPassword: EditText
    private lateinit var botonIngresar: Button
    private lateinit var botonRegistrar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        campoUsuario = findViewById(R.id.campoUsuario)
        campoPassword = findViewById(R.id.campoPassword)
        botonIngresar = findViewById(R.id.botonIngresar)
        botonRegistrar = findViewById(R.id.botonRegistrar)

        botonRegistrar.setOnClickListener {
            val usuario = campoUsuario.text.toString().trim()
            val password = campoPassword.text.toString()

            when {
                usuario.length !in 3..30 -> {
                    mensaje("Usuario: entre 3 y 30 caracteres")
                }
                password.length < 6 -> {
                    mensaje("La contraseña debe tener al menos 6 caracteres")
                }
                else -> {
                    enviarSolicitud(
                        "/registrar",
                        usuario,
                        password
                    )
                }
            }
        }

        botonIngresar.setOnClickListener {
            val usuario = campoUsuario.text.toString().trim()
            val password = campoPassword.text.toString()

            if (usuario.isEmpty() || password.isEmpty()) {
                mensaje("Completa ambos campos")
            } else {
                enviarSolicitud(
                    "/login",
                    usuario,
                    password
                )
            }
        }
    }

    private fun enviarSolicitud(
        ruta: String,
        usuario: String,
        password: String
    ) {
        cambiarBotones(false)

        Thread {
            var conexion: HttpURLConnection? = null

            try {
                conexion = URL(
                    servidor + ruta
                ).openConnection() as HttpURLConnection

                conexion.requestMethod = "POST"
                conexion.connectTimeout = 5000
                conexion.readTimeout = 5000
                conexion.doOutput = true

                conexion.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )

                val datos = JSONObject().apply {
                    put("usuario", usuario)
                    put("password", password)
                }

                conexion.outputStream.use {
                    it.write(
                        datos.toString().toByteArray(
                            Charsets.UTF_8
                        )
                    )
                }

                val codigo = conexion.responseCode

                val flujo = if (codigo in 200..299) {
                    conexion.inputStream
                } else {
                    conexion.errorStream
                }

                val respuesta = flujo
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: "{}"

                val json = JSONObject(respuesta)

                runOnUiThread {
                    cambiarBotones(true)

                    if (codigo !in 200..299) {
                        mensaje(
                            json.optString(
                                "error",
                                json.optString(
                                    "mensaje",
                                    "Error del servidor"
                                )
                            )
                        )
                        return@runOnUiThread
                    }

                    if (ruta == "/registrar") {
                        mensaje("Cuenta creada correctamente")
                        campoPassword.text.clear()
                    } else {
                        val token = json.getString("token")
                        val rol = json.getString("rol")
                        val nombre = json.getString("usuario")

                        val abrir = Intent(
                            this,
                            LedActivity::class.java
                        )

                        abrir.putExtra("usuario", nombre)
                        abrir.putExtra("rol", rol)
                        abrir.putExtra("token", token)

                        startActivity(abrir)
                        finish()
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    cambiarBotones(true)
                    mensaje(
                        "No se pudo conectar con Python: " +
                                (e.message ?: "Error desconocido")
                    )
                }

            } finally {
                conexion?.disconnect()
            }
        }.start()
    }

    private fun cambiarBotones(habilitados: Boolean) {
        botonIngresar.isEnabled = habilitados
        botonRegistrar.isEnabled = habilitados
    }

    private fun mensaje(texto: String) {
        Toast.makeText(
            this,
            texto,
            Toast.LENGTH_LONG
        ).show()
    }
}
