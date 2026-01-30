package com.sabrina.uxiaproject

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupBottomNavigation()

        // Cargar fragment inicial
        if (savedInstanceState == null) {
            loadFragment(UlladaFragment())
        }
    }

    private fun setupBottomNavigation() {
        val tabUllada = findViewById<android.widget.LinearLayout>(R.id.tab_ullada)
        val tabHistorial = findViewById<android.widget.LinearLayout>(R.id.tab_historial)
        val tabAjustos = findViewById<android.widget.LinearLayout>(R.id.tab_ajustos)

        tabUllada.setOnClickListener {
            loadFragment(UlladaFragment())
        }

        tabHistorial.setOnClickListener {
            loadFragment(HistorialFragment())
        }

        tabAjustos.setOnClickListener {
            loadFragment(AjustosFragment())
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }
}