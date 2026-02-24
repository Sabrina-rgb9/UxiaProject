package com.sabrina.uxiaproject

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.sabrina.uxiaproject.ui.AuthBlockerDialog
import com.sabrina.uxiaproject.utils.SessionManager

class MainActivity : AppCompatActivity(), AuthBlockerDialog.AuthBlockerListener {

    private lateinit var bottomNavigation: LinearLayout
    private lateinit var sessionManager: SessionManager

    // Elements de la bottom navigation
    private lateinit var tabUllada: LinearLayout
    private lateinit var tabHistorial: LinearLayout
    private lateinit var tabAjustos: LinearLayout
    private lateinit var iconUllada: ImageView
    private lateinit var iconHistorial: ImageView
    private lateinit var iconAjustos: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicialitzar SessionManager
        sessionManager = SessionManager(this)

        // Inicialitzar vistes
        initializeViews()

        // Configurar bottom navigation
        setupBottomNavigation()

        // Comprovar autenticació i estat de registre
        checkAuthAndNavigate()
    }

    private fun initializeViews() {
        bottomNavigation = findViewById(R.id.bottom_navigation)

        // Tabs
        tabUllada = findViewById(R.id.tab_ullada)
        tabHistorial = findViewById(R.id.tab_historial)
        tabAjustos = findViewById(R.id.tab_ajustos)

        // Icones
        iconUllada = findViewById(R.id.icon_ullada)
        iconHistorial = findViewById(R.id.icon_historial)
        iconAjustos = findViewById(R.id.icon_ajustos)
    }

    private fun setupBottomNavigation() {
        tabUllada.setOnClickListener {
            if (sessionManager.isLoggedIn()) {
                loadFragment(UlladaFragment(), showBottomNav = true)
                updateTabSelection(0)
            } else {
                showAuthBlocker()
            }
        }

        tabHistorial.setOnClickListener {
            if (sessionManager.isLoggedIn()) {
                loadFragment(HistorialFragment(), showBottomNav = true)
                updateTabSelection(1)
            } else {
                showAuthBlocker()
            }
        }

        tabAjustos.setOnClickListener {
            if (sessionManager.isLoggedIn()) {
                loadFragment(AjustosFragment(), showBottomNav = true)
                updateTabSelection(2)
            } else {
                showAuthBlocker()
            }
        }
    }

    private fun checkAuthAndNavigate() {
        if (!sessionManager.isLoggedIn()) {
            // Usuari no autenticat - mostrar bloqueig
            showAuthBlocker()
        } else {
            // Usuari autenticat
            if (!isUserRegistered()) {
                // Usuari autenticat però no registrat (cas estrany)
                loadFragment(RegisterFragment(), showBottomNav = false)
            } else {
                // Usuari autenticat i registrat - pantalla principal
                loadFragment(UlladaFragment(), showBottomNav = true)
                updateTabSelection(0)
            }
        }
    }

    private fun isUserRegistered(): Boolean {
        // Comprovar si l'usuari ha completat el registre
        // Pots tenir una preferència específica per això
        val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        return prefs.getBoolean("is_registered", false)
    }

    fun setUserRegistered() {
        val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("is_registered", true).apply()

        // Després del registre, mostrar UlladaFragment amb bottom navigation
        loadFragment(UlladaFragment(), showBottomNav = true)
        updateTabSelection(0)
    }

    fun loadFragment(fragment: Fragment, showBottomNav: Boolean = true) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()

        // Controlar visibilitat de la bottom navigation
        bottomNavigation.visibility = if (showBottomNav) View.VISIBLE else View.GONE
    }

    private fun updateTabSelection(selectedIndex: Int) {
        // Resetear tots els tabs
        resetTabAppearance(tabUllada, iconUllada)
        resetTabAppearance(tabHistorial, iconHistorial)
        resetTabAppearance(tabAjustos, iconAjustos)

        // Aplicar estil al tab seleccionat
        when (selectedIndex) {
            0 -> setTabSelected(tabUllada, iconUllada)
            1 -> setTabSelected(tabHistorial, iconHistorial)
            2 -> setTabSelected(tabAjustos, iconAjustos)
        }
    }

    private fun resetTabAppearance(tab: LinearLayout, icon: ImageView) {
        tab.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
        icon.clearColorFilter()
        icon.setColorFilter(ContextCompat.getColor(this, R.color.secondary))
    }

    private fun setTabSelected(tab: LinearLayout, icon: ImageView) {
        tab.setBackgroundColor(ContextCompat.getColor(this, R.color.secondary))
        icon.setColorFilter(ContextCompat.getColor(this, android.R.color.white))
    }

    // AuthBlockerDialog.AuthBlockerListener implementation
    override fun onGoToRegister() {
        // Navegar a pantalla de registre
        loadFragment(RegisterFragment(), showBottomNav = false)
    }

    override fun onGoToLogin() {
        // Si tens pantalla de login, navegar-hi
        // Per ara, navegar a registre
        loadFragment(RegisterFragment(), showBottomNav = false)
    }

    fun showAuthBlocker() {
        val dialog = AuthBlockerDialog()
        dialog.show(supportFragmentManager, "AuthBlocker")
    }

    fun logout() {
        // Netejar sessió
        sessionManager.clearSession()

        // Netejar preferències de registre
        val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        prefs.edit().clear().apply()

        // Mostrar bloqueig
        showAuthBlocker()
    }

    override fun onBackPressed() {
        // Si hi ha fragments a la pila, permetre tornar enrere
        if (supportFragmentManager.backStackEntryCount > 0) {
            super.onBackPressed()
        } else {
            // Si estem a la pantalla principal, tancar app
            finish()
        }
    }
}