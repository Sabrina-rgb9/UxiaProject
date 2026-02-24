package com.sabrina.uxiaproject

import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
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
import com.sabrina.uxiaproject.model.UserVerifyResponse
import com.sabrina.uxiaproject.utils.SessionManager

class VerifyFragment : Fragment() {

    // 6 EditTexts per als 6 dígits del codi
    private lateinit var etCode1: EditText
    private lateinit var etCode2: EditText
    private lateinit var etCode3: EditText
    private lateinit var etCode4: EditText
    private lateinit var etCode5: EditText
    private lateinit var etCode6: EditText
    private lateinit var btnVerify: Button
    private lateinit var btnResend: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var tvPhoneInfo: TextView
    private lateinit var tvTimer: TextView

    private lateinit var apiService: ApiService
    private lateinit var sessionManager: SessionManager
    private lateinit var handler: Handler
    private var telefon: String = ""
    private var timer: CountDownTimer? = null
    private val CODE_LENGTH = 6

    companion object {
        private const val ARG_PHONE = "phone_number"

        fun newInstance(phone: String): VerifyFragment {
            val fragment = VerifyFragment()
            val args = Bundle()
            args.putString(ARG_PHONE, phone)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            telefon = it.getString(ARG_PHONE, "")
        }
        handler = Handler(Looper.getMainLooper())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_verify, container, false)

        initializeViews(view)
        apiService = ApiService(requireContext())
        sessionManager = SessionManager(requireContext())

        setupUI()
        setupListeners()
        startResendTimer()

        return view
    }

    private fun initializeViews(view: View) {
        etCode1 = view.findViewById(R.id.etCode1)
        etCode2 = view.findViewById(R.id.etCode2)
        etCode3 = view.findViewById(R.id.etCode3)
        etCode4 = view.findViewById(R.id.etCode4)
        etCode5 = view.findViewById(R.id.etCode5)
        etCode6 = view.findViewById(R.id.etCode6)
        btnVerify = view.findViewById(R.id.btnVerify)
        btnResend = view.findViewById(R.id.btnResend)
        progressBar = view.findViewById(R.id.progressBar)
        tvError = view.findViewById(R.id.tvError)
        tvPhoneInfo = view.findViewById(R.id.tvPhoneInfo)
        tvTimer = view.findViewById(R.id.tvTimer)
    }

    private fun setupUI() {
        // Mostrar número de telèfon formatejat
        val formattedPhone = formatPhoneNumber(telefon)
        tvPhoneInfo.text = "Hem enviat un codi de verificació a $formattedPhone"

        // Deshabilitar botó de verificar inicialment
        btnVerify.isEnabled = false
        btnVerify.alpha = 0.6f
    }

    private fun formatPhoneNumber(phone: String): String {
        return when {
            phone.length == 9 -> "${phone.substring(0,3)} ${phone.substring(3,6)} ${phone.substring(6,9)}"
            phone.length > 9 -> "${phone.substring(0,3)} ${phone.substring(3,6)} ${phone.substring(6)}"
            else -> phone
        }
    }

    private fun setupListeners() {
        setupCodeInputs()

        btnVerify.setOnClickListener {
            attemptVerification()
        }

        btnResend.setOnClickListener {
            resendCode()
        }
    }

    private fun setupCodeInputs() {
        val codeInputs = listOf(etCode1, etCode2, etCode3, etCode4, etCode5, etCode6)

        // Configurar cada camp
        codeInputs.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    when {
                        // Si s'ha introduït un dígit
                        s?.length == 1 -> {
                            if (index < codeInputs.size - 1) {
                                codeInputs[index + 1].requestFocus()
                            } else {
                                // Últim camp, amagar teclat
                                hideKeyboard()
                            }
                        }
                        // Si s'ha esborrat
                        s?.length == 0 -> {
                            if (index > 0) {
                                codeInputs[index - 1].requestFocus()
                            }
                        }
                    }
                    checkCodeComplete()
                }
            })

            // Gestionar tecla "borrar"
            editText.setOnKeyListener { _, keyCode, event ->
                if (keyCode == android.view.KeyEvent.KEYCODE_DEL &&
                    event.action == android.view.KeyEvent.ACTION_DOWN) {
                    if (editText.text.isEmpty() && index > 0) {
                        codeInputs[index - 1].requestFocus()
                        codeInputs[index - 1].text.clear()
                    }
                }
                false
            }
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    private fun checkCodeComplete() {
        val code = getEnteredCode()
        val isComplete = code.length == CODE_LENGTH

        btnVerify.isEnabled = isComplete
        btnVerify.alpha = if (isComplete) 1.0f else 0.6f
    }

    private fun getEnteredCode(): String {
        return etCode1.text.toString() +
                etCode2.text.toString() +
                etCode3.text.toString() +
                etCode4.text.toString() +
                etCode5.text.toString() +
                etCode6.text.toString()
    }

    private fun attemptVerification() {
        val codiValidacio = getEnteredCode()

        Log.d("VERIFY_DEBUG", "========== INTENT DE VERIFICACIÓ ==========")
        Log.d("VERIFY_DEBUG", "📤 Telefon enviat: '$telefon'")
        Log.d("VERIFY_DEBUG", "📤 Codi enviat: '$codiValidacio'")

        if (codiValidacio.length != CODE_LENGTH) {
            showError("El codi ha de tenir 6 dígits")
            return
        }

        hideError()
        showLoading(true)

        apiService.verifyUser(telefon, codiValidacio, object : ApiService.VerifyCallback {
            override fun onSuccess(response: UserVerifyResponse) {
                Log.d("VERIFY_DEBUG", "📥 Resposta rebuda - status: ${response.status}")
                Log.d("VERIFY_DEBUG", "📥 api_key: ${response.data?.api_key}")

                requireActivity().runOnUiThread {
                    showLoading(false)

                    // ✅ Comprovar si hi ha api_key (això indica èxit)
                    if (response.status == "OK" && response.data?.api_key != null) {
                        Log.d("VERIFY_DEBUG", "✅ VERIFICACIÓ EXITOSA!")
                        handleVerificationSuccess(response)
                    } else {
                        Log.d("VERIFY_DEBUG", "❌ VERIFICACIÓ FALLIDA: ${response.message}")
                        showError("Codi incorrecte. Torna a intentar-ho.")
                        clearCodeInputs()
                    }
                }
            }

            override fun onError(error: String) {
                Log.e("VERIFY_DEBUG", "❌ ERROR: $error")
                requireActivity().runOnUiThread {
                    showLoading(false)
                    showError("Error: $error")
                }
            }
        })
    }

    private fun handleVerificationSuccess(response: UserVerifyResponse) {
        // ✅ Guardar l'api_key (ara ve directament)
        response.data?.api_key?.let { apiKey ->
            Log.d("VERIFY_DEBUG", "💾 Guardant API Key: $apiKey")
            sessionManager.saveApiToken(apiKey)
            sessionManager.setVerified(true)
            sessionManager.savePhone(telefon)
        }

        Toast.makeText(
            requireContext(),
            "✅ Número verificat correctament!",
            Toast.LENGTH_LONG
        ).show()

        goToMainScreen()
    }

    private fun resendCode() {
        // Deshabilitar botó temporalment
        btnResend.isEnabled = false
        btnResend.alpha = 0.6f

        // Mostrar indicador
        progressBar.visibility = View.VISIBLE
        tvError.visibility = View.GONE

        // Simular enviament (aquí hauries de cridar l'API real)
        handler.postDelayed({
            progressBar.visibility = View.GONE

            // Reactivar timer
            startResendTimer()

            // Netejar camps
            clearCodeInputs()

            Toast.makeText(requireContext(), "Codi reenviat!", Toast.LENGTH_SHORT).show()
        }, 2000)
    }

    private fun startResendTimer() {
        btnResend.isEnabled = false
        btnResend.alpha = 0.6f

        timer = object : CountDownTimer(60000, 1000) { // 60 segons
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = millisUntilFinished / 1000
                tvTimer.text = "Reenviar codi en $secondsLeft s"
            }

            override fun onFinish() {
                btnResend.isEnabled = true
                btnResend.alpha = 1.0f
                tvTimer.text = "Reenviar codi"
            }
        }.start()
    }

    private fun clearCodeInputs() {
        val codeInputs = listOf(etCode1, etCode2, etCode3, etCode4, etCode5, etCode6)
        codeInputs.forEach { it.text.clear() }
        etCode1.requestFocus()
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            progressBar.visibility = View.VISIBLE
            btnVerify.isEnabled = false
            btnVerify.alpha = 0.6f
        } else {
            progressBar.visibility = View.GONE
            btnVerify.isEnabled = true
            btnVerify.alpha = 1.0f
        }
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    private fun hideError() {
        tvError.visibility = View.GONE
        tvError.text = ""
    }

    private fun goToMainScreen() {
        // Navegar a UlladaFragment
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, UlladaFragment())
            .commit()
    }

    override fun onPause() {
        super.onPause()
        timer?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
        handler.removeCallbacksAndMessages(null)
    }
}