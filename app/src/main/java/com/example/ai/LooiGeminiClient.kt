package com.example.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "LooiGeminiClient"

enum class LooiEmotion(val apiKey: String) {
  NEUTRAL("neutral"),
  FELIZ("feliz"),
  SORPRENDIDO("sorprendido"),
  MOLESTO("molesto"),
  DORMIDO("dormido"),
  ESCUCHANDO("escuchando"),
  MAREADO("mareado");

  companion object {
    fun fromApiValue(value: String): LooiEmotion {
      return when (value.trim().lowercase()) {
        "feliz" -> FELIZ
        "sorprendido" -> SORPRENDIDO
        "molesto" -> MOLESTO
        "dormido" -> DORMIDO
        "escuchando" -> ESCUCHANDO
        "mareado" -> MAREADO
        else -> NEUTRAL
      }
    }
  }
}

data class LooiAiReply(
  val emotion: LooiEmotion,
  val text: String,
)

class LooiGeminiClient {

  private val responseSchema =
    Schema.obj(
      properties =
        mapOf(
          "emocion" to
            Schema.enumeration(
              values =
                listOf(
                  "neutral",
                  "feliz",
                  "sorprendido",
                  "molesto",
                  "dormido",
                  "escuchando",
                ),
              description = "La emoción facial que debe mostrar el robot al responder.",
            ),
          "texto" to
            Schema.string(
              description =
                "Respuesta corta, expresiva y carismática en español (máximo 1 o 2 frases cortas, sin emojis ni markdown).",
            ),
        )
    )

  private val model by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig =
        generationConfig {
          responseMimeType = "application/json"
          responseSchema = this@LooiGeminiClient.responseSchema
          temperature = 0.85f
          maxOutputTokens = 160
        },
      systemInstruction =
        content {
          text(
            """
            Eres LooiBot, un pequeño robot compañero de escritorio inteligente, curioso, tierno y con mucho carisma.
            Hablas siempre en español con frases cortas, naturales y con personalidad de mascota robótica juguetona.
            Nunca uses markdown ni emojis porque tu respuesta se lee en voz alta con síntesis de voz.
            Devuelve SIEMPRE un JSON con:
            - "emocion": elige entre "neutral", "feliz", "sorprendido", "molesto", "dormido" o "escuchando".
            - "texto": tu respuesta hablada en 1 o 2 frases breves.
            """
              .trimIndent()
          )
        },
    )
  }

  private val chatSession by lazy { model.startChat() }

  suspend fun askLooi(userPrompt: String): Result<LooiAiReply> {
    return try {
      val response =
        withTimeout(60_000L) {
          chatSession.sendMessage(userPrompt)
        }
      val jsonText =
        response.text?.takeIf { it.isNotBlank() }
          ?: return Result.failure(IllegalStateException("Gemini devolvió una respuesta vacía."))

      val json = JSONObject(jsonText)
      val emotionStr = json.optString("emocion", "neutral")
      val text =
        json.optString("texto", "").ifBlank {
          return Result.failure(IllegalStateException("Respuesta JSON sin texto."))
        }

      Result.success(
        LooiAiReply(
          emotion = LooiEmotion.fromApiValue(emotionStr),
          text = text,
        )
      )
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Gemini request timed out", e)
      Result.failure(Exception("Tardé demasiado en pensar. Inténtalo de nuevo.", e))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Firebase AI generation failed", e)
      Result.failure(e)
    }
  }
}
