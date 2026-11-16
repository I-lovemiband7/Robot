package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

private const val TAG = "RobotVoiceController"

class RobotVoiceController(
  private val context: Context,
  private val onListeningStarted: () -> Unit,
  private val onRmsChanged: (Float) -> Unit,
  private val onPartialResult: (String) -> Unit,
  private val onFinalResult: (String) -> Unit,
  private val onSpeechError: (String) -> Unit,
  private val onSpeakingChanged: (Boolean) -> Unit,
) {
  private val mainHandler = Handler(Looper.getMainLooper())
  private var speechRecognizer: SpeechRecognizer? = null
  private var textToSpeech: TextToSpeech? = null
  private var isTtsReady = false

  init {
    textToSpeech =
      TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
          val tts = textToSpeech ?: return@TextToSpeech
          val esLocale = Locale("es", "ES")
          val langResult = tts.setLanguage(esLocale)
          if (langResult == TextToSpeech.LANG_MISSING_DATA ||
            langResult == TextToSpeech.LANG_NOT_SUPPORTED
          ) {
            tts.setLanguage(Locale("es"))
          }
          // Cute high-pitched robotic companion voice
          tts.setPitch(1.42f)
          tts.setSpeechRate(1.10f)
          tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
              override fun onStart(utteranceId: String?) {
                mainHandler.post { onSpeakingChanged(true) }
              }

              override fun onDone(utteranceId: String?) {
                mainHandler.post { onSpeakingChanged(false) }
              }

              @Deprecated("Deprecated in Java")
              override fun onError(utteranceId: String?) {
                mainHandler.post { onSpeakingChanged(false) }
              }

              override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler.post { onSpeakingChanged(false) }
              }
            }
          )
          isTtsReady = true
        } else {
          Log.e(TAG, "TextToSpeech init failed with status=$status")
        }
      }
  }

  fun startListening() {
    mainHandler.post {
      if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        onSpeechError("El reconocimiento de voz no está disponible en este dispositivo.")
        return@post
      }

      try {
        textToSpeech?.stop()
        onSpeakingChanged(false)

        speechRecognizer?.destroy()
        speechRecognizer =
          SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
            setRecognitionListener(
              object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                  onListeningStarted()
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) {
                  val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                  this@RobotVoiceController.onRmsChanged(normalized)
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                  val msg =
                    when (error) {
                      SpeechRecognizer.ERROR_AUDIO -> "Error de audio al escuchar."
                      SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Necesito permiso de micrófono para escucharte."
                      SpeechRecognizer.ERROR_NETWORK,
                      SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Sin conexión para reconocer la voz."
                      SpeechRecognizer.ERROR_NO_MATCH,
                      SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "No alcancé a escucharte bien. ¿Me lo repites?"
                      else -> "No pude reconocer lo que dijiste."
                    }
                  onSpeechError(msg)
                }

                override fun onResults(results: Bundle?) {
                  val matches =
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                  val text = matches?.firstOrNull()?.trim().orEmpty()
                  if (text.isNotEmpty()) {
                    onFinalResult(text)
                  } else {
                    onSpeechError("No alcancé a escucharte bien. ¿Me lo repites?")
                  }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                  val partial =
                    partialResults
                      ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                      ?.firstOrNull()
                      ?.trim()
                  if (!partial.isNullOrEmpty()) {
                    onPartialResult(partial)
                  }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
              }
            )
          }

        val intent =
          Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
              RecognizerIntent.EXTRA_LANGUAGE_MODEL,
              RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
          }
        speechRecognizer?.startListening(intent)
      } catch (e: Exception) {
        Log.e(TAG, "Failed to start SpeechRecognizer", e)
        onSpeechError("No se pudo activar el micrófono.")
      }
    }
  }

  fun stopListening() {
    mainHandler.post {
      try {
        speechRecognizer?.stopListening()
      } catch (_: Exception) {}
    }
  }

  fun speak(text: String) {
    if (!isTtsReady) {
      Log.w(TAG, "TTS not ready yet; skipping speech")
      return
    }
    mainHandler.post {
      val utteranceId = UUID.randomUUID().toString()
      textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }
  }

  fun stopSpeaking() {
    mainHandler.post {
      textToSpeech?.stop()
      onSpeakingChanged(false)
    }
  }

  fun release() {
    mainHandler.post {
      try {
        speechRecognizer?.destroy()
        speechRecognizer = null
      } catch (_: Exception) {}
      try {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
      } catch (_: Exception) {}
    }
  }
}
