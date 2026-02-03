package com.sabrina.uxiaproject

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupBottomNavigation()

        // Cargar fragment inicial
        if (savedInstanceState == null) {
            loadFragment(UlladaFragment())
            updateTabSelection(0) // ← AÑADIDO: seleccionar Ullada al inicio
        }
    }

    private fun setupBottomNavigation() {
        val tabUllada = findViewById<android.widget.LinearLayout>(R.id.tab_ullada)
        val tabHistorial = findViewById<android.widget.LinearLayout>(R.id.tab_historial)
        val tabAjustos = findViewById<android.widget.LinearLayout>(R.id.tab_ajustos)

        tabUllada.setOnClickListener {
            loadFragment(UlladaFragment())
            updateTabSelection(0) // ← AÑADIDO
        }

        tabHistorial.setOnClickListener {
            loadFragment(HistorialFragment())
            updateTabSelection(1) // ← AÑADIDO
        }

        tabAjustos.setOnClickListener {
            loadFragment(AjustosFragment())
            updateTabSelection(2) // ← AÑADIDO
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    // ← AÑADIDO: Nueva función para actualizar la selección
    private fun updateTabSelection(selectedIndex: Int) {
        val tabUllada = findViewById<android.widget.LinearLayout>(R.id.tab_ullada)
        val tabHistorial = findViewById<android.widget.LinearLayout>(R.id.tab_historial)
        val tabAjustos = findViewById<android.widget.LinearLayout>(R.id.tab_ajustos)

        val iconUllada = findViewById<android.widget.ImageView>(R.id.icon_ullada)
        val iconHistorial = findViewById<android.widget.ImageView>(R.id.icon_historial)
        val iconAjustos = findViewById<android.widget.ImageView>(R.id.icon_ajustos)

        // 1. Resetear todos los tabs
        resetTabAppearance(tabUllada, iconUllada)
        resetTabAppearance(tabHistorial, iconHistorial)
        resetTabAppearance(tabAjustos, iconAjustos)

        // 2. Aplicar estilo al tab seleccionado
        when (selectedIndex) {
            0 -> setTabSelected(tabUllada, iconUllada)
            1 -> setTabSelected(tabHistorial, iconHistorial)
            2 -> setTabSelected(tabAjustos, iconAjustos)
        }
    }

    // ← AÑADIDO: Función para resetear apariencia
    private fun resetTabAppearance(tab: android.widget.LinearLayout, icon: android.widget.ImageView) {
        // Fondo transparente
        tab.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
        // Icono negro
        icon.clearColorFilter()
    }

    // ← AÑADIDO: Función para marcar tab como seleccionado
    private fun setTabSelected(tab: android.widget.LinearLayout, icon: android.widget.ImageView) {
        // Fondo color secundario (#476568)
        tab.setBackgroundColor(ContextCompat.getColor(this, R.color.secondary))
        // Icono blanco
        icon.setColorFilter(ContextCompat.getColor(this, android.R.color.white))
    }
}