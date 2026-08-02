package com.tigoj.aipro

import android.os.Bundle
import android.view.WindowManager
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.tigoj.aipro.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private var ttsPreparado = false
    private var textoPendiente: String? = null
    private lateinit var binding: ActivityMainBinding
    private val apiBase = "http://127.0.0.1:11434"
    private val memory by lazy { getSharedPreferences("tigoj_memory", MODE_PRIVATE) }

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
                        put("model", "default")
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
            hablar(textoLimpio.removePrefix("TIGOJ:").trim())
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
        if (ttsPreparado) {
            tts.speak(
                texto,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "tigoj_respuesta"
            )
        } else {
            textoPendiente = texto
        }
    }

    override fun onDestroy() {
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
