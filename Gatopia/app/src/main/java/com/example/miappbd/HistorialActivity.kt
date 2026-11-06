package com.example.miappbd

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HistorialActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historial)

        findViewById<TextView>(R.id.textoHistorialCompleto).text =
            BaseDeDatos(this).obtenerEventos()

        findViewById<Button>(R.id.botonVolver).setOnClickListener {
            finish()
        }
    }
}
