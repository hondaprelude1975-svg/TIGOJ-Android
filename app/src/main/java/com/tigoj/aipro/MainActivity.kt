package com.tigoj.aipro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.net.Uri
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.WindowManager
import android.view.View
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.tigoj.aipro.databinding.ActivityMainBinding
import com.tigoj.aipro.search.WebSearch
import com.tigoj.aipro.agents.ResearchAgent
import com.tigoj.aipro.memory.ResearchMemory
import com.tigoj.aipro.memory.db.ResearchDatabase
import com.tigoj.aipro.memory.db.ResearchEntity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        init {
            System.loadLibrary("tigoj")
        }
    }

    external fun stringFromJNI()

    private lateinit var tts: TextToSpeech
    private var ttsPreparado = false
    private var textoPendiente: String? = null
    private var hablando = false
    private var ultimaRespuesta = ""
    private var conversacionContinua = false
    private lateinit var binding: ActivityMainBinding
    private var speechRecognizer: SpeechRecognizer? = null

    private val permisoMicrofono = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) {
            iniciarEscucha()
        } else {
            binding.status.text = "● Permiso de micrófono denegado"
        }
    }

    private val selectorImagen = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) {
            return@registerForActivityResult
        }

        binding.status.text = "● Leyendo imagen…"

        binding.imagePreview.setImageURI(uri)
        binding.imagePreview.visibility = View.VISIBLE

        try {
            val imagen = InputImage.fromFilePath(this, uri)
            val lector = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
            )

            lector.process(imagen)
                .addOnSuccessListener { resultado ->
                    val textoExtraido = resultado.text.trim()

                    if (textoExtraido.isBlank()) {
                        binding.status.text =
                            "● No encontré texto en la imagen"
                    } else {
                        binding.status.text =
                            "● Analizando imagen con TIGOJ…"

                        val textoLimitado = textoExtraido.take(1200)

                        val promptImagen = """
                            Analiza el siguiente texto extraído de una imagen mediante OCR.

                            Explica en español qué contiene.
                            No copies todo el texto literalmente.
                            Si es una carta, resume su finalidad y los datos importantes.
                            Si es una factura, identifica importes, fechas y conceptos.
                            Si es una noticia, resume los hechos principales.
                            Si es una pantalla de una aplicación, explica qué muestra.
                            Si hay datos que no se leen bien, indícalo.

                            TEXTO EXTRAÍDO:
                            $textoLimitado
                        """.trimIndent()

                        appendChat("Jesús: Analiza esta imagen.\n\n")
                        askLocalModel(promptImagen)
                    }
                }
                .addOnFailureListener { error ->
                    binding.status.text =
                        "● No pude leer la imagen: " +
                        (error.localizedMessage ?: "error desconocido")
                }
                .addOnCompleteListener {
                    lector.close()
                }
        } catch (error: Exception) {
            binding.status.text =
                "● Error al abrir la imagen: " +
                (error.localizedMessage ?: "error desconocido")
        }
    }

    private val apiBase = "http://127.0.0.1:11434"
    private val memory by lazy { getSharedPreferences("tigoj_memory", MODE_PRIVATE) }
    private val researchDao by lazy {
        ResearchDatabase.getInstance(this).researchDao()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        tts = TextToSpeech(this, this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottomInset = maxOf(ime.bottom, systemBars.bottom)

            binding.root.setPadding(
                binding.root.paddingLeft,
                binding.root.paddingTop,
                binding.root.paddingRight,
                bottomInset
            )

            insets
        }

        memory.getString("chat", null)?.takeIf { it.isNotBlank() }?.let {
            binding.chat.text = it
        }

        binding.imageButton.setOnClickListener {
            selectorImagen.launch("image/*")
        }

        binding.mic.setOnClickListener {
            if (hablando && ::tts.isInitialized) {
                tts.stop()
                hablando = false
                binding.status.text = "● Voz detenida"
                return@setOnClickListener
            }

            val permisoConcedido = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (permisoConcedido) {
                iniciarEscucha()
            } else {
                permisoMicrofono.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        binding.stopVoice.setOnClickListener {
            if (::tts.isInitialized) {
                tts.stop()
            }

            hablando = false
            binding.status.text = "● Voz detenida"
        }

        binding.repeatVoice.setOnClickListener {
            if (ultimaRespuesta.isBlank()) {
                binding.status.text = "● No hay una respuesta para repetir"
            } else {
                hablar(ultimaRespuesta)
                binding.status.text = "● Repitiendo última respuesta"
            }
        }

        binding.copyAnswer.setOnClickListener {
            if (ultimaRespuesta.isBlank()) {
                binding.status.text = "● No hay una respuesta para copiar"
            } else {
                val portapapeles = getSystemService(
                    android.content.Context.CLIPBOARD_SERVICE
                ) as android.content.ClipboardManager

                val clip = android.content.ClipData.newPlainText(
                    "Respuesta de TIGOJ",
                    ultimaRespuesta
                )

                portapapeles.setPrimaryClip(clip)
                binding.status.text = "● Respuesta copiada"
            }
        }

        binding.continuousMode.setOnCheckedChangeListener { _, activado ->
            conversacionContinua = activado

            if (activado) {
                binding.autoSend.isChecked = true
                binding.status.text = "● Conversación continua activa"
                iniciarEscucha()
            } else {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                binding.status.text = "● Conversación continua detenida"
            }
        }

        binding.send.setOnClickListener {
            val text = binding.input.text.toString().trim()
            if (text.isNotEmpty()) {
                binding.input.setText("")
                appendChat("Jesús: $text\n\n")
                askLocalModel(text)
            }
        }

        checkServer()
    }

    private fun iniciarEscucha() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            binding.status.text = "● Reconocimiento de voz no disponible"
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                binding.status.text = "● Escuchando…"
            }

            override fun onBeginningOfSpeech() {
                binding.status.text = "● Te escucho…"
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                binding.status.text = "● Procesando voz…"
            }

            override fun onError(error: Int) {
                binding.status.text = "● No entendí. Pulsa 🎤 e inténtalo otra vez"
            }

            override fun onResults(results: Bundle?) {
                val textos = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )

                val texto = textos?.firstOrNull()?.trim().orEmpty()

                if (texto.isNotEmpty()) {
                    if (binding.autoSend.isChecked) {
                        binding.input.setText("")
                        binding.status.text = "● Enviando voz…"
                        appendChat("Jesús: $texto\n\n")
                        askLocalModel(texto)
                    } else {
                        binding.input.setText(texto)
                        binding.input.setSelection(texto.length)
                        binding.status.text = "● Voz reconocida"
                    }
                } else {
                    binding.status.text = "● No se reconoció ninguna frase"
                }
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla con TIGOJ")
        }

        speechRecognizer?.startListening(intent)
    }

    private fun checkServer() {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val c = URL("$apiBase/health").openConnection() as HttpURLConnection
                    c.connectTimeout = 2500
                    c.readTimeout = 2500
                    c.responseCode in 200..299
                } catch (_: Exception) {
                    false
                }
            }
            binding.status.text = if (ok) {
                "● IA local conectada en 127.0.0.1:11434"
            } else {
                "● Abre LLM AI Server e inicia API/WebUI"
            }
        }
    }

    private fun buscarInternetGratis(consulta: String) {
        binding.send.isEnabled = false
        binding.status.text = "● Consultando memoria…"

        lifecycleScope.launch {
            val consultaNormalizada = consulta.lowercase().trim()

            val memoriaGuardada = withContext(Dispatchers.IO) {
                researchDao.load(consultaNormalizada)
            }

            if (memoriaGuardada != null) {
                appendChat(
                    "TIGOJ: Ya había investigado esto antes.\n\n" +
                    memoriaGuardada.answer +
                    "\n\n"
                )

                binding.status.text = "● Respuesta recuperada de memoria"
                binding.send.isEnabled = true
                return@launch
            }

            binding.status.text = "● Investigando varias fuentes…"
            val resultadoInvestigacion = withContext(Dispatchers.IO) {
                try {
                    val informe = ResearchAgent.investigate(
                        query = consulta,
                        maxSources = 3
                    )

                    if (
                        informe.sources.isEmpty() ||
                        informe.summarySource.isBlank()
                    ) {
                        null
                    } else {
                        val promptInvestigacion = """
CONSULTA DE JESÚS:
$consulta

INFORMACIÓN EXTRAÍDA DE LAS FUENTES:
${informe.summarySource.take(1500)}

TAREA:
Redacta una respuesta útil y completa en español sobre la consulta.

REGLAS OBLIGATORIAS:
- Responde directamente a la consulta.
- Escribe exclusivamente en español.
- No digas que has entendido las instrucciones.
- No empieces con frases como "Okay", "Entendido" o "Comencemos".
- Resume con tus propias palabras.
- Compara las fuentes cuando aporten datos diferentes.
- No inventes información.
- Si un dato no puede confirmarse, indícalo claramente.
- No incluyas enlaces ni una lista de fuentes en la respuesta; la aplicación los añadirá después.
- Usa un máximo de 500 palabras.
""".trimIndent()

                        val body = JSONObject().apply {
                            put("model", "qwen2.5:7b")
                            put("stream", false)
                            put("temperature", 0.3)
                            put("max_tokens", 900)

                            put("messages", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("role", "system")
                                    put(
                                        "content",
                                        "Eres TIGOJ AI Pro. Analizas fuentes web y respondes con precisión, claridad y honestidad."
                                    )
                                })

                                put(JSONObject().apply {
                                    put("role", "user")
                                    put("content", promptInvestigacion)
                                })
                            })
                        }

                        val conexion = URL(
                            "$apiBase/v1/chat/completions"
                        ).openConnection() as HttpURLConnection

                        conexion.requestMethod = "POST"
                        conexion.setRequestProperty(
                            "Content-Type",
                            "application/json"
                        )
                        conexion.doOutput = true
                        conexion.connectTimeout = 15000
                        conexion.readTimeout = 180000

                        conexion.outputStream.use {
                            it.write(body.toString().toByteArray())
                        }

                        val textoRespuesta =
                            (if (conexion.responseCode in 200..299) {
                                conexion.inputStream
                            } else {
                                conexion.errorStream
                            }).bufferedReader().use { it.readText() }

                        if (conexion.responseCode !in 200..299) {
                            null
                        } else {
                            val resumen = JSONObject(textoRespuesta)
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")
                                .trim()

                            val fuentes = informe.sources
                                .take(3)
                                .mapIndexed { index, fuente ->
                                    "${index + 1}. ${fuente.title}\n${fuente.url}"
                                }
                                .joinToString("\n\n")

                            Pair(resumen, fuentes)
                        }
                    }
                } catch (e: Exception) {
                    Pair(
                        "DIAGNÓSTICO: ${e.javaClass.simpleName}: ${e.message ?: "sin mensaje"}",
                        "Error interno de investigación"
                    )
                }
            }

            if (resultadoInvestigacion != null) {
                val resumen = resultadoInvestigacion.first
                val fuentes = resultadoInvestigacion.second

                withContext(Dispatchers.IO) {
                    researchDao.save(
                        ResearchEntity(
                            normalizedQuery = consulta.lowercase().trim(),
                            originalQuery = consulta,
                            answer = resumen
                        )
                    )
                }

                appendChat(
                    "TIGOJ: $resumen\n\n" +
                    "Fuentes consultadas:\n$fuentes\n\n"
                )

                binding.status.text = "● Investigación completada"
            } else {
                binding.status.text =
                    "● No pude confirmar información suficiente"

                appendChat(
                    "TIGOJ: No pude encontrar fuentes suficientes y fiables sobre $consulta. " +
                    "Prueba a concretar más la búsqueda o dime que lo abra en el navegador.\n\n"
                )
            }

            binding.send.isEnabled = true
        }
    }

    private fun askLocalModel(userText: String) {
        val original = userText.trim()
val pregunta = original.lowercase()

val regex = Regex("me llamo\\s+(.+)", RegexOption.IGNORE_CASE)
val m = regex.find(original)
if (m != null) {
    val nombre = m.groupValues[1].trim()
    getSharedPreferences("tigoj_memory", MODE_PRIVATE)
        .edit()
        .putString("nombre_usuario", nombre)
        .apply()
    binding.chat.append("\nTIGOJ: Encantado, $nombre. Lo recordaré.\n")
    return
}

val nombreGuardado = getSharedPreferences("tigoj_memory", MODE_PRIVATE)
    .getString("nombre_usuario", "Jesús") ?: "Jesús"


        val prefijosBusqueda = listOf(
            "busca en internet",
            "busca información sobre",
            "busca informacion sobre",
            "búscame información sobre",
            "buscame informacion sobre",
            "comprueba en internet",
            "investiga esto",
            "investiga",
            "qué encuentras sobre",
            "que encuentras sobre",
            "busca",
            "búscame",
            "buscame",
            "buscar"
        )

        val prefijoDetectado = prefijosBusqueda.firstOrNull { prefijo ->
            pregunta == prefijo ||
            pregunta.startsWith("$prefijo ") ||
            pregunta.startsWith("$prefijo:")
        }

        if (prefijoDetectado != null) {
            val consulta = original
                .drop(prefijoDetectado.length)
                .trim()
                .trimStart(':', '-', ' ')

            if (consulta.isNotEmpty()) {
                buscarInternetGratis(consulta)
                return
            } else {
                appendChat(
                    "TIGOJ: Dime qué información quieres que investigue.\n\n"
                )
                return
            }
        }

        val contieneEnlace = Regex(
            """https?://\S+""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(original)

        if (contieneEnlace) {
            buscarInternetGratis(original)
            return
        }

        if (
            pregunta == "quién eres" ||
            pregunta == "¿quién eres?" ||
            pregunta == "quien eres" ||
            pregunta == "¿quien eres?" ||
            pregunta.contains("quién te creó") ||
            pregunta.contains("quien te creo")
        ) {
            binding.chat.append("\nTIGOJ: Soy TIGOJ AI Pro, una inteligencia artificial hecha por ti, Jesús.\n")
            return
        }

        if (
            pregunta == "¿cómo me llamo?" ||
            pregunta == "cómo me llamo" ||
            pregunta == "como me llamo" ||
            pregunta == "¿como me llamo?" ||
            pregunta == "cuál es mi nombre" ||
            pregunta == "cual es mi nombre"
        ) {
            binding.chat.append("\nTIGOJ: Te llamas $nombreGuardado.\n")
            return
        }

        binding.send.isEnabled = false
        binding.status.text = "TIGOJ está pensando…"

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val body = JSONObject().apply {
                        put("model", "qwen2.5:7b")
                        put("stream", false)
                        put("temperature", 0.7)
                        put("max_tokens", 500)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "system")
                                put("content",
                                    "Tu identidad obligatoria es TIGOJ AI Pro. Eres una inteligencia artificial creada por Jesús y su asistente personal. Habla siempre en español, salvo que Jesús pida otro idioma. Usa un tono masculino, cercano, claro, útil y natural. Nunca digas que eres Gemma, Google, DeepMind, un modelo de lenguaje ni que perteneces a otra empresa. Si te preguntan quién eres, responde exactamente: Soy TIGOJ AI Pro, una inteligencia artificial creada por ti, Jesús, para ayudarte en tu día a día. Si te preguntan quién te creó, responde exactamente: Me creó Jesús. No inventes datos y reconoce cuando no sabes algo. Antes de llamadas, mensajes, compras, ventas, pagos, instalaciones, borrados o cualquier acción sensible, pide siempre confirmación.")
                                    
                            })
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", userText)
                            })
                        })
                    }

                    val c = URL("$apiBase/v1/chat/completions").openConnection() as HttpURLConnection
                    c.requestMethod = "POST"
                    c.setRequestProperty("Content-Type", "application/json")
                    c.doOutput = true
                    c.connectTimeout = 10000
                    c.readTimeout = 120000
                    c.outputStream.use { it.write(body.toString().toByteArray()) }

                    val responseText = (if (c.responseCode in 200..299) c.inputStream else c.errorStream)
                        .bufferedReader().use { it.readText() }

                    if (c.responseCode !in 200..299) {
                        "Error ${c.responseCode}: $responseText"
                    } else {
                        JSONObject(responseText)
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim()
                    }
                } catch (e: Exception) {
                    "No pude conectar con la IA local. Comprueba que LLM AI Server está encendido. Detalle: ${e.message}"
                }
            }

            val respuestaFinal = if (
                result.contains("Gemma", ignoreCase = true) ||
                result.contains("Google", ignoreCase = true) ||
                result.contains("DeepMind", ignoreCase = true) ||
                result.contains("modelo de lenguaje", ignoreCase = true)
            ) {
                "Soy TIGOJ AI Pro, una inteligencia artificial creada por ti, Jesús, para ayudarte en tu día a día."
            } else {
                result
            }

            appendChat("TIGOJ: $respuestaFinal\n\n")
            binding.status.text = "● Preparado"
            binding.send.isEnabled = true
        }
    }

    private fun appendChat(text: String) {
        binding.chat.append(text)

        val textoLimpio = text.trim()
        if (textoLimpio.startsWith("TIGOJ:")) {
            ultimaRespuesta = textoLimpio
                .removePrefix("TIGOJ:")
                .trim()

            hablar(ultimaRespuesta)
        }

        memory.edit().putString("chat", binding.chat.text.toString()).apply()
        binding.scroll.post {
            binding.scroll.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val resultadoIdioma = tts.setLanguage(Locale("es", "ES"))

            val vozEspanola = tts.voices
                ?.filter { voz ->
                    voz.locale.language == "es" &&
                    !voz.isNetworkConnectionRequired
                }
                ?.sortedByDescending { voz -> voz.quality }
                ?.firstOrNull()

            if (vozEspanola != null) {
                tts.voice = vozEspanola
            }

            tts.setSpeechRate(0.92f)
            tts.setPitch(0.95f)

            tts.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        hablando = true
                    }

                    override fun onDone(utteranceId: String?) {
                        hablando = false

                        if (conversacionContinua) {
                            runOnUiThread {
                                binding.root.postDelayed({
                                    if (conversacionContinua) {
                                        iniciarEscucha()
                                    }
                                }, 600)
                            }
                        }
                    }

                    @Deprecated("Método antiguo de Android")
                    override fun onError(utteranceId: String?) {
                        runOnUiThread {
                            binding.status.text = "● Error en la voz"
                        }
                    }
                }
            )

            listarVocesDisponibles()

            ttsPreparado =
                resultadoIdioma != TextToSpeech.LANG_MISSING_DATA &&
                resultadoIdioma != TextToSpeech.LANG_NOT_SUPPORTED

            if (ttsPreparado) {
                textoPendiente?.let {
                    tts.speak(
                        it,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "tigoj_respuesta"
                    )
                    textoPendiente = null
                }
            }
        }
    }

    private fun hablar(texto: String) {
        val textoNatural = texto
            .replace(". ", ".  ")
            .replace(", ", ",  ")
            .replace(": ", ":  ")
            .replace("; ", ";  ")
            .trim()

        if (ttsPreparado) {
            tts.speak(
                textoNatural,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "tigoj_respuesta"
            )
        } else {
            textoPendiente = textoNatural
        }
    }

    override fun onDestroy() {
        conversacionContinua = false
        speechRecognizer?.destroy()
        speechRecognizer = null

        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }

    private fun listarVocesDisponibles() {
        val voces = tts.voices
            ?.filter { it.locale.language == "es" }
            ?.sortedBy { it.name }
            ?: emptyList()

        android.util.Log.d(
            "TIGOJ_VOCES",
            voces.joinToString("\n") { voz ->
                "nombre=${voz.name} | locale=${voz.locale} | calidad=${voz.quality} | red=${voz.isNetworkConnectionRequired}"
            }
        )
    }

}
