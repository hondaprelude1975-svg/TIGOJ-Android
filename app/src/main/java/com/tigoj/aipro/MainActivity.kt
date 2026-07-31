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
        binding.send.isEnabled = false
        binding.status.text = "TIGOJ está pensando…"

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val body = JSONObject().apply {
                        put("model", "local-model")
                        put("temperature", 0.7)
                        put("max_tokens", 500)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "system")
                                put("content",
                                    "Eres TIGOJ AI Pro, el asistente personal local de Jesús. " +
                                    "Habla siempre en español. Sé claro, útil y honesto. " +
                                    "No inventes datos y pide confirmación antes de acciones sensibles.")
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
