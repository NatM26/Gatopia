package com.example.miappbd

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BaseDeDatos(context: Context) :
    SQLiteOpenHelper(context, "usuarios.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE usuarios (
                usuario TEXT PRIMARY KEY,
                password TEXT NOT NULL,
                rol TEXT NOT NULL DEFAULT 'usuario'
            )
        """.trimIndent())

        crearTablaEventos(db)
    }

    private fun crearTablaEventos(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS eventos_alarma (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tipo TEXT NOT NULL,
                fecha_hora TEXT NOT NULL,
                detalle TEXT NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        vieja: Int,
        nueva: Int
    ) {
        if (vieja < 2) {
            crearTablaEventos(db)
        }

        if (vieja < 3) {
            db.execSQL("""
                ALTER TABLE usuarios
                ADD COLUMN rol TEXT NOT NULL DEFAULT 'usuario'
            """.trimIndent())
        }
    }

    fun registrar(usuario: String, password: String): Boolean {
        val valores = ContentValues().apply {
            put("usuario", usuario)
            put("password", hash(password))
            put("rol", "usuario")
        }

        return writableDatabase.insert(
            "usuarios",
            null,
            valores
        ) != -1L
    }

    fun login(usuario: String, password: String): Boolean {
        val cursor = readableDatabase.rawQuery(
            """
            SELECT usuario FROM usuarios
            WHERE usuario = ? AND password = ?
            """.trimIndent(),
            arrayOf(usuario, hash(password))
        )

        return cursor.use { it.moveToFirst() }
    }

    fun obtenerRol(usuario: String): String {
        readableDatabase.rawQuery(
            "SELECT rol FROM usuarios WHERE usuario = ?",
            arrayOf(usuario)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }

        return "usuario"
    }

    fun registrarEvento(tipo: String, detalle: String): Boolean {
        val fecha = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.getDefault()
        ).format(Date())

        val valores = ContentValues().apply {
            put("tipo", tipo)
            put("fecha_hora", fecha)
            put("detalle", detalle)
        }

        return writableDatabase.insert(
            "eventos_alarma",
            null,
            valores
        ) != -1L
    }

    fun obtenerEventos(): String {
        val resultado = StringBuilder()

        readableDatabase.rawQuery(
            """
            SELECT tipo, fecha_hora, detalle
            FROM eventos_alarma
            ORDER BY id DESC
            LIMIT 100
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val tipo = cursor.getString(0)
                val fecha = cursor.getString(1)
                val detalle = cursor.getString(2)

                resultado.append(
                    "$fecha\n$tipo: $detalle\n\n"
                )
            }
        }

        return if (resultado.isEmpty()) {
            "Todavía no hay eventos registrados."
        } else {
            resultado.toString()
        }
    }

    private fun hash(texto: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(texto.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
