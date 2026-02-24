package com.sabrina.uxiaproject

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.sabrina.uxiaproject.api.ApiService
import com.sabrina.uxiaproject.model.UserRegister
import com.sabrina.uxiaproject.model.UserRegisterResponse

class RegisterFragment : Fragment() {

    private lateinit var etNickname: EditText
    private lateinit var etPhone: EditText
    private lateinit var etEmail: EditText
    private lateinit var btnRegister: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView

    private lateinit var apiService: ApiService

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_register, container, false)

        // Inicialitzar vistes
        etNickname = view.findViewById(R.id.etNickname)
        etPhone = view.findViewById(R.id.etPhone)
        etEmail = view.findViewById(R.id.etEmail)
        btnRegister = view.findViewById(R.id.btnRegister)
        progressBar = view.findViewById(R.id.progressBar)
        tvError = view.findViewById(R.id.tvError)

        apiService = ApiService(requireContext())

        // Configurar listeners
        setupTextWatchers()
        setupButtons()

        return view
    }

    private fun setupTextWatchers() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateForm()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        etNickname.addTextChangedListener(textWatcher)
        etPhone.addTextChangedListener(textWatcher)
        etEmail.addTextChangedListener(textWatcher)
    }

    private fun setupButtons() {
        btnRegister.setOnClickListener {
            attemptRegister()
        }


    }

    private fun validateForm() {
        val nickname = etNickname.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val email = etEmail.text.toString().trim()

        var isValid = true

        // Validar nickname
        if (nickname.isEmpty()) {
            etNickname.error = "El nickname és obligatori"
            isValid = false
        } else if (nickname.length < 3) {
            etNickname.error = "El nickname ha de tenir almenys 3 caràcters"
            isValid = false
        } else if (nickname.length > 20) {
            etNickname.error = "El nickname no pot tenir més de 20 caràcters"
            isValid = false
        }

        // Validar telèfon
        if (phone.isEmpty()) {
            etPhone.error = "El telèfon és obligatori"
            isValid = false
        } else if (!phone.matches(Regex("^[0-9]{9,15}$"))) {
            etPhone.error = "Telèfon no vàlid (9-15 dígits)"
            isValid = false
        }

        // Validar email
        if (email.isEmpty()) {
            etEmail.error = "L'email és obligatori"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Email no vàlid"
            isValid = false
        }

        btnRegister.isEnabled = isValid
        btnRegister.alpha = if (isValid) 1.0f else 0.6f
    }

    private fun attemptRegister() {
        // Amagar error anterior
        tvError.visibility = View.GONE
        tvError.text = ""

        // Obtenir dades
        val nickname = etNickname.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val email = etEmail.text.toString().trim()

        // Crear objecte d'usuari
        val user = UserRegister(
            nickname = nickname,
            email = email,
            telefon = phone
        )

        // Mostrar loading
        showLoading(true)

        // Cridar API
        apiService.registerUser(user, object : ApiService.RegisterCallback {
            override fun onSuccess(response: UserRegisterResponse) {
                requireActivity().runOnUiThread {
                    showLoading(false)

                    if (response.status == "OK") {
                        // Registre exitós
                        Toast.makeText(
                            requireContext(),
                            "Registre completat! Ara verifica el teu número",
                            Toast.LENGTH_LONG
                        ).show()

                        // Navegar a la pantalla de verificació
                        goToVerificationScreen(phone)

                    } else {
                        // Error del servidor
                        tvError.text = "Error: ${response.message}"
                        tvError.visibility = View.VISIBLE
                    }
                }
            }

            override fun onError(error: String) {
                requireActivity().runOnUiThread {
                    showLoading(false)
                    tvError.text = "Error: $error"
                    tvError.visibility = View.VISIBLE
                }
            }
        })
    }

    private fun goToVerificationScreen(phone: String) {
        val verifyFragment = VerifyFragment.newInstance(phone)

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, verifyFragment)
            .addToBackStack("register_to_verify")
            .commit()
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            progressBar.visibility = View.VISIBLE
            btnRegister.isEnabled = false
            btnRegister.alpha = 0.6f
        } else {
            progressBar.visibility = View.GONE
            btnRegister.isEnabled = true
            btnRegister.alpha = 1.0f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Netejar referències si cal
    }
}