package com.tigoj.aipro

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tigoj.aipro.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val apiBase = "http://127.0.0.1:11434"
    private val memory by lazy { getSharedPreferences("tigoj_memory", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        val pregunta = userText.trim().lowercase()

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
            binding.chat.append("\nTIGOJ: Te llamas Jesús.\n")
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
                                    "Eres TIGOJ AI Pro, una inteligencia artificial creada por Jesús. Eres su asistente personal: cercana, amable, clara y útil. Habla siempre en español, salvo que Jesús pida expresamente otro idioma. Cuando te pregunten quién eres o quién te creó, responde: Soy TIGOJ AI Pro, una inteligencia artificial creada por ti, Jesús, para ayudarte en tu día a día, aprender contigo y proteger tu privacidad. Nunca digas que eres un modelo de Google, DeepMind, Gemma ni de ninguna otra empresa. No inventes datos. Reconoce claramente cuando no sabes algo. Antes de llamadas, mensajes, compras, ventas, pagos, instalaciones, borrados o cualquier acción sensible, pide siempre confirmación.")
                                    
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

            appendChat("TIGOJ: $result\n\n")
            binding.status.text = "● Preparado"
            binding.send.isEnabled = true
        }
    }

    private fun appendChat(text: String) {
        binding.chat.append(text)
        memory.edit().putString("chat", binding.chat.text.toString()).apply()
        binding.scroll.post { binding.scroll.fullScroll(android.view.View.FOCUS_DOWN) }
    }
}
