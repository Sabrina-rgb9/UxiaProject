package com.sabrina.uxiaproject

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.sabrina.uxiaproject.api.ImageUploader
import com.sabrina.uxiaproject.ui.BLEconnDialog
import org.json.JSONObject
import java.io.File

class UlladaFragment : Fragment(), BLEconnDialog.BLEConnectionCallback {

    private lateinit var imageViewReceived: ImageView
    private lateinit var btnReceiveImage: Button
    private lateinit var tvNoDeviceSelected: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvDescription: TextView
    private lateinit var tvConfidence: TextView

    private lateinit var jsonSettings: JsonSettings
    private lateinit var imageUploader: ImageUploader
    private var bleDialog: BLEconnDialog? = null

    private var textToSpeech: android.speech.tts.TextToSpeech? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startBluetoothConnection()
        } else {
            tvStatus.text = "Permisos denegados"
            Toast.makeText(requireContext(), "Permisos necesarios", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ullada, container, false)

        btnReceiveImage = view.findViewById(R.id.btn_receive_image)
        tvNoDeviceSelected = view.findViewById(R.id.tv_no_device_selected)
        tvStatus = view.findViewById(R.id.tv_status)
        imageViewReceived = view.findViewById(R.id.imageViewReceived)
        progressBar = view.findViewById(R.id.progressBar)
        tvDescription = view.findViewById(R.id.tvDescription)
        tvConfidence = view.findViewById(R.id.tvConfidence)
        // Inicialitzar TTS
        textToSpeech = android.speech.tts.TextToSpeech(requireContext()) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(java.util.Locale("ca"))
                if (result == android.speech.tts.TextToSpeech.LANG_MISSING_DATA ||
                    result == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "El català no està suportat")
                }
            }
        }


        jsonSettings = JsonSettings(requireContext())
        imageUploader = ImageUploader(requireContext())

        btnReceiveImage.setOnClickListener {
            onReceiveImageClicked()
        }

        tvNoDeviceSelected.setOnClickListener {
            navigateToAjustos()
        }

        updateButtonState()

        return view
    }

    override fun onResume() {
        super.onResume()
        updateButtonState()
    }

    private fun updateButtonState() {
        val device = jsonSettings.getSelectedDevice()

        if (device != null) {
            val (deviceName, _) = device
            btnReceiveImage.isEnabled = true
            btnReceiveImage.alpha = 1.0f
            tvNoDeviceSelected.visibility = View.GONE
            tvStatus.visibility = View.VISIBLE
            tvStatus.text = "Dispositiu: $deviceName"
        } else {
            btnReceiveImage.isEnabled = false
            btnReceiveImage.alpha = 0.5f
            tvNoDeviceSelected.visibility = View.VISIBLE
            tvStatus.visibility = View.GONE
        }
    }

    private fun onReceiveImageClicked() {
        val device = jsonSettings.getSelectedDevice()

        if (device == null) {
            Toast.makeText(requireContext(), "Selecciona un dispositiu primer", Toast.LENGTH_SHORT).show()
            navigateToAjustos()
            return
        }

        if (!checkBluetoothPermissions()) {
            requestBluetoothPermissions()
            return
        }

        if (!checkStoragePermissions()) {
            requestStoragePermissions()
            return
        }

        startBluetoothConnection()
    }

    private fun navigateToAjustos() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, AjustosFragment())
            .addToBackStack("ullada_to_ajustos")
            .commit()
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothConnection() {
        val device = jsonSettings.getSelectedDevice() ?: return
        val (_, deviceAddress) = device

        try {
            val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)

            // Amagar resultats anteriors
            imageViewReceived.visibility = View.GONE
            tvDescription.visibility = View.GONE
            tvConfidence.visibility = View.GONE
            progressBar.visibility = View.GONE

            bleDialog = BLEconnDialog(requireContext(), device, this)
            bleDialog?.show()

            tvStatus.text = "Connectant amb ESP32..."

        } catch (e: Exception) {
            tvStatus.text = "Error: ${e.message}"
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // CALLBACKS del BLEconnDialog
    override fun onConnectionSuccess(gatt: BluetoothGatt) {
        requireActivity().runOnUiThread {
            tvStatus.text = "Connectat! Esperant imatge..."
            Toast.makeText(requireContext(), "Connectat al ESP32", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConnectionFailed(error: String) {
        requireActivity().runOnUiThread {
            tvStatus.text = "Error: $error"
            Toast.makeText(requireContext(), "Error: $error", Toast.LENGTH_LONG).show()
        }
    }

    override fun onConnectionCancelled() {
        requireActivity().runOnUiThread {
            tvStatus.text = "Connexió cancel·lada"
            Toast.makeText(requireContext(), "Connexió cancel·lada", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onImageReceived(file: File, imageData: ByteArray) {
        requireActivity().runOnUiThread {
            // 1. Mostrar la imatge
            mostrarImatge(file, imageData)

            // 2. Enviar al servidor
            enviarImatgeAlServidor(file, imageData)
        }
    }

    // Mantenim el callback antic per compatibilitat (però el nou el sobreescriu)
    override fun onReceivedImage(file: File) {
        // Aquest mètode ja no s'usa, però l'hem de mantenir per la interfície
        Log.d("Ullada", "onReceivedImage cridat (obsolet)")
    }

    private fun mostrarImatge(file: File, imageData: ByteArray) {
        try {
            val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
            if (bitmap != null) {
                imageViewReceived.setImageBitmap(bitmap)
                imageViewReceived.visibility = View.VISIBLE
                tvStatus.text = "Imatge rebuda correctament"
            } else {
                tvStatus.text = "Error mostrant imatge"
            }
        } catch (e: Exception) {
            Log.e("Ullada", "Error mostrant imatge: ${e.message}")
            tvStatus.text = "Error: ${e.message}"
        }
    }

    private fun enviarImatgeAlServidor(file: File, imageData: ByteArray) {
        // Mostrar indicador de càrrega
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Enviant al servidor..."

        imageUploader.uploadImage(file, object : ImageUploader.UploadCallback {
            override fun onSuccess(response: String) {
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvStatus.text = "Imatge enviada correctament"

                    // Processar la resposta del servidor
                    processarRespostaServidor(response)

                    Toast.makeText(requireContext(), "Imatge enviada!", Toast.LENGTH_LONG).show()
                }
            }

            override fun onError(error: String) {
                requireActivity().runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvStatus.text = "Error enviant: $error"
                    Toast.makeText(requireContext(), "Error: $error", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun processarRespostaServidor(response: String) {
        try {
            val json = JSONObject(response)

            // QUAN EL SERVIDOR ESTIGUI LLEST, REBRÀS ALGUNA COSA AIXÍ:
            // {
            //   "descripcio": "Aquesta és una cadira de fusta amb respatller alt",
            //   "tags": ["cadira", "fusta", "moble", "interior"]
            // }

            val descripcio = json.optString("descripcio", "Descripció no disponible")

            // Obtenir tags (pot ser un array o un string)
            val tags = when {
                json.has("tags") -> {
                    val tagsJson = json.getJSONArray("tags")
                    (0 until tagsJson.length()).map { tagsJson.getString(it) }
                }
                json.has("etiquetes") -> {
                    val tagsJson = json.getJSONArray("etiquetes")
                    (0 until tagsJson.length()).map { tagsJson.getString(it) }
                }
                else -> emptyList()
            }

            // Mostrar a la UI
            tvDescription.text = descripcio
            tvDescription.visibility = View.VISIBLE

            if (tags.isNotEmpty()) {
                tvConfidence.text = "Tags: ${tags.joinToString(" • ")}"
                tvConfidence.visibility = View.VISIBLE
            }

            // CRIDAR AL TTS PER LOCUTAR
            locutarDescripcioITags(descripcio, tags)

        } catch (e: Exception) {
            Log.e("Ullada", "Error processant resposta: ${e.message}")
            tvStatus.text = "Error processant resposta del servidor"
        }
    }

    private fun locutarDescripcioITags(descripcio: String, tags: List<String>) {
        try {
            // Crear el text complet
            val textPerLocutar = if (tags.isNotEmpty()) {
                "$descripcio. Tags: ${tags.joinToString(", ")}"
            } else {
                descripcio
            }

            // Iniciar TTS
            iniciarTTS(textPerLocutar)

        } catch (e: Exception) {
            Log.e("TTS", "Error preparant TTS: ${e.message}")
        }
    }

    private fun iniciarTTS(text: String) {
        try {
            if (textToSpeech == null) {
                Log.e("TTS", "TTS no inicialitzat")
                return
            }

            // Aturar qualsevol locució anterior
            textToSpeech?.stop()

            // Configurar idioma català
            textToSpeech?.language = java.util.Locale("ca")

            // Locutar
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textToSpeech?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
            } else {
                textToSpeech?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null)
            }

            Log.d("TTS", "Locutant: $text")

        } catch (e: Exception) {
            Log.e("TTS", "Error en TTS: ${e.message}")
        }
    }

    // Verificació de permisos (els teus mètodes existents)
    private fun checkBluetoothPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }

        return permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }

        requestPermissionLauncher.launch(permissions)
    }

    private fun checkStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf()
        } else {
            arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDestroy() {
        super.onDestroy()
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (e: Exception) {
            Log.e("TTS", "Error alliberant TTS: ${e.message}")
        }
        bleDialog?.dismiss()
    }
}