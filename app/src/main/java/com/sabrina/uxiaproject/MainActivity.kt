package com.sabrina.uxiaproject

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import android.widget.ImageView
import android.widget.Toast


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val ullada = findViewById<ImageView>(R.id.icon_ullada)
        val historial = findViewById<ImageView>(R.id.icon_historial)
        val ajustos = findViewById<ImageView>(R.id.icon_ajustos)

        ullada.setOnClickListener {
            Toast.makeText(this, "Ullada", Toast.LENGTH_SHORT).show()
        }

        historial.setOnClickListener {
            Toast.makeText(this, "Historial", Toast.LENGTH_SHORT).show()
        }

        ajustos.setOnClickListener {
            Toast.makeText(this, "Ajustos", Toast.LENGTH_SHORT).show()
        }
    }
}



