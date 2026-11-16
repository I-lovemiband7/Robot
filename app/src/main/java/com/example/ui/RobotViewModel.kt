package com.example.ui

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.LooiEmotion
import com.example.ai.LooiGeminiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class EyeTarget {
  LEFT,
  RIGHT,
  BOTH,
}

enum class SoundCue {
  WAKE_UP,
  WINK,
  HAPPY_PURR,
  DIZZY,
  LISTENING_BEEP,
  SLEEP_SIGH,
}

data class RobotUiState(
  val isAwake: Boolean = false,
  val emotion: LooiEmotion = LooiEmotion.DORMIDO,
  val isBlinking: Boolean = false,
  val winkLeft: Boolean = false,
  val winkRight: Boolean = false,
  val isSpeaking: Boolean = false,
  val isListening: Boolean = false,
  val isThinking: Boolean = false,
  val isCameraTrackingEnabled: Boolean = false,
  val faceDetected: Boolean = false,
  val gazeTarget: Offset = Offset.Zero,
  val micLevel: Float = 0f,
  val captionText: String? = null,
  val errorMessage: String? = null,
  val hasMicPermission: Boolean = false,
  val hasCameraPermission: Boolean = false,
  val pendingSound: Pair<Long, SoundCue>? = null,
  val pendingSpeech: Pair<Long, String>? = null,
)

class RobotViewModel(
  private val geminiClient: LooiGeminiClient = LooiGeminiClient(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(RobotUiState())
  val uiState: StateFlow<RobotUiState> = _uiState.asStateFlow()

  private var inactivityJob: Job? = null
  private var blinkJob: Job? = null
  private var gazeJob: Job? = null
  private var transientEmotionJob: Job? = null
  private var captionClearJob: Job? = null
  private var faceLostResetJob: Job? = null

  companion object {
    private const val INACTIVITY_SLEEP_MS = 30_000L
    private const val FACE_LOST_RECENTER_MS = 1_400L
  }

  fun onPermissionsUpdated(micGranted: Boolean, cameraGranted: Boolean) {
    _uiState.update {
      it.copy(
        hasMicPermission = micGranted,
        hasCameraPermission = cameraGranted,
      )
    }
  }

  fun wakeUpRobot(micGranted: Boolean, cameraGranted: Boolean) {
    _uiState.update {
      it.copy(
        isAwake = true,
        emotion = LooiEmotion.FELIZ,
        hasMicPermission = micGranted,
        hasCameraPermission = cameraGranted,
        errorMessage = null,
        captionText = "¡Hola! Ya estoy despierto.",
      )
    }
    emitSound(SoundCue.WAKE_UP)
    emitSpeech("¡Hola! Ya estoy despierto.")
    scheduleTransientReturnToNeutral(2600L)
    scheduleCaptionClear(3800L)
    startBackgroundLoops()
    resetInactivityTimer()
  }

  fun putToSleep() {
    transientEmotionJob?.cancel()
    inactivityJob?.cancel()
    _uiState.update {
      it.copy(
        emotion = LooiEmotion.DORMIDO,
        gazeTarget = Offset.Zero,
        isListening = false,
        captionText = null,
      )
    }
    emitSound(SoundCue.SLEEP_SIGH)
  }

  private fun wakeIfSleeping(targetEmotion: LooiEmotion = LooiEmotion.NEUTRAL) {
    val state = _uiState.value
    if (!state.isAwake) return
    if (state.emotion == LooiEmotion.DORMIDO) {
      _uiState.update { it.copy(emotion = targetEmotion) }
    }
    resetInactivityTimer()
  }

  fun onEyeTapped(target: EyeTarget) {
    if (!_uiState.value.isAwake) return
    wakeIfSleeping(_uiState.value.emotion)
    emitSound(SoundCue.WINK)

    viewModelScope.launch {
      when (target) {
        EyeTarget.LEFT -> {
          _uiState.update { it.copy(winkLeft = true) }
          delay(360L)
          _uiState.update { it.copy(winkLeft = false) }
        }
        EyeTarget.RIGHT -> {
          _uiState.update { it.copy(winkRight = true) }
          delay(360L)
          _uiState.update { it.copy(winkRight = false) }
        }
        EyeTarget.BOTH -> {
          _uiState.update { it.copy(winkLeft = true, winkRight = true) }
          delay(300L)
          _uiState.update { it.copy(winkLeft = false, winkRight = false) }
        }
      }
    }
  }

  fun onHeadPat() {
    if (!_uiState.value.isAwake) return
    resetInactivityTimer()
    _uiState.update {
      it.copy(
        emotion = LooiEmotion.FELIZ,
        captionText = "¡Mmm! Me encanta que me acaricies.",
      )
    }
    emitSound(SoundCue.HAPPY_PURR)
    scheduleTransientReturnToNeutral(3000L)
    scheduleCaptionClear(3200L)
  }

  fun onFaceTapped() {
    if (!_uiState.value.isAwake) return
    val wasAsleep = _uiState.value.emotion == LooiEmotion.DORMIDO
    resetInactivityTimer()
    if (wasAsleep) {
      _uiState.update {
        it.copy(
          emotion = LooiEmotion.SORPRENDIDO,
          captionText = "¡Oh! ¡Me despertaste!",
        )
      }
      emitSound(SoundCue.WAKE_UP)
      scheduleTransientReturnToNeutral(1800L)
      scheduleCaptionClear(2400L)
    }
  }

  fun onShakeDetected() {
    if (!_uiState.value.isAwake) return
    resetInactivityTimer()
    _uiState.update {
      it.copy(
        emotion = LooiEmotion.MAREADO,
        captionText = "¡Whoa! ¡Todo me da vueltas!",
      )
    }
    emitSound(SoundCue.DIZZY)
    scheduleTransientReturnToNeutral(2800L)
    scheduleCaptionClear(3000L)
  }

  fun onListeningStarted() {
    if (!_uiState.value.isAwake) return
    transientEmotionJob?.cancel()
    inactivityJob?.cancel()
    _uiState.update {
      it.copy(
        emotion = LooiEmotion.ESCUCHANDO,
        isListening = true,
        isThinking = false,
        errorMessage = null,
        captionText = "Escuchándote...",
      )
    }
    emitSound(SoundCue.LISTENING_BEEP)
  }

  fun onMicRmsChanged(level: Float) {
    _uiState.update { it.copy(micLevel = level) }
  }

  fun onSpeechPartial(partial: String) {
    _uiState.update { it.copy(captionText = "\"$partial\"") }
  }

  fun onSpeechError(message: String) {
    _uiState.update {
      it.copy(
        isListening = false,
        micLevel = 0f,
        emotion = LooiEmotion.NEUTRAL,
        errorMessage = message,
      )
    }
    resetInactivityTimer()
  }

  fun submitUserPrompt(prompt: String) {
    val cleanPrompt = prompt.trim()
    if (cleanPrompt.isEmpty()) return

    transientEmotionJob?.cancel()
    inactivityJob?.cancel()
    _uiState.update {
      it.copy(
        isListening = false,
        isThinking = true,
        micLevel = 0f,
        emotion = LooiEmotion.ESCUCHANDO,
        errorMessage = null,
        captionText = "Tú: \"$cleanPrompt\"",
      )
    }

    viewModelScope.launch {
      val result = geminiClient.askLooi(cleanPrompt)
      result.fold(
        onSuccess = { reply ->
          _uiState.update {
            it.copy(
              isThinking = false,
              emotion = reply.emotion,
              captionText = reply.text,
              errorMessage = null,
            )
          }
          emitSpeech(reply.text)
          if (reply.emotion != LooiEmotion.DORMIDO) {
            scheduleTransientReturnToNeutral(6000L)
            resetInactivityTimer()
          }
          scheduleCaptionClear(7000L)
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isThinking = false,
              emotion = LooiEmotion.MOLESTO,
              errorMessage = error.localizedMessage ?: "Error al conectar con Gemini.",
            )
          }
          scheduleTransientReturnToNeutral(3000L)
          resetInactivityTimer()
        },
      )
    }
  }

  fun onSpeakingChanged(speaking: Boolean) {
    _uiState.update { it.copy(isSpeaking = speaking) }
    if (!speaking && _uiState.value.isAwake && _uiState.value.emotion != LooiEmotion.DORMIDO) {
      resetInactivityTimer()
    }
  }

  fun toggleCameraTracking(cameraGranted: Boolean) {
    if (!cameraGranted) {
      _uiState.update {
        it.copy(errorMessage = "Necesito permiso de cámara frontal para seguir tu rostro.")
      }
      return
    }
    resetInactivityTimer()
    _uiState.update { state ->
      val nextEnabled = !state.isCameraTrackingEnabled
      if (!nextEnabled) faceLostResetJob?.cancel()
      state.copy(
        isCameraTrackingEnabled = nextEnabled,
        faceDetected = if (nextEnabled) state.faceDetected else false,
        gazeTarget = if (nextEnabled) state.gazeTarget else Offset.Zero,
        errorMessage = null,
      )
    }
  }

  fun onCameraTrackingError(message: String) {
    faceLostResetJob?.cancel()
    _uiState.update {
      it.copy(
        isCameraTrackingEnabled = false,
        faceDetected = false,
        errorMessage = message,
      )
    }
  }

  fun onFaceTrackingUpdate(hasFace: Boolean, gazeX: Float, gazeY: Float) {
    val state = _uiState.value
    if (!state.isCameraTrackingEnabled || !state.isAwake) return
    if (state.emotion == LooiEmotion.DORMIDO) return

    if (hasFace) {
      faceLostResetJob?.cancel()
      _uiState.update {
        it.copy(
          faceDetected = true,
          gazeTarget = Offset(gazeX.coerceIn(-1f, 1f), gazeY.coerceIn(-1f, 1f)),
        )
      }
    } else {
      _uiState.update { it.copy(faceDetected = false) }
      if (faceLostResetJob?.isActive != true) {
        faceLostResetJob =
          viewModelScope.launch {
            delay(FACE_LOST_RECENTER_MS)
            val current = _uiState.value
            if (current.isCameraTrackingEnabled && !current.faceDetected) {
              _uiState.update { it.copy(gazeTarget = Offset.Zero) }
            }
          }
      }
    }
  }

  fun dismissError() {
    _uiState.update { it.copy(errorMessage = null) }
  }

  private fun emitSound(cue: SoundCue) {
    _uiState.update { it.copy(pendingSound = System.nanoTime() to cue) }
  }

  private fun emitSpeech(text: String) {
    _uiState.update { it.copy(pendingSpeech = System.nanoTime() to text) }
  }

  private fun resetInactivityTimer() {
    inactivityJob?.cancel()
    if (!_uiState.value.isAwake) return
    inactivityJob =
      viewModelScope.launch {
        delay(INACTIVITY_SLEEP_MS)
        val current = _uiState.value
        if (current.isAwake && !current.isListening && !current.isThinking && !current.isSpeaking) {
          _uiState.update {
            it.copy(
              emotion = LooiEmotion.DORMIDO,
              gazeTarget = Offset.Zero,
              captionText = null,
            )
          }
          emitSound(SoundCue.SLEEP_SIGH)
        }
      }
  }

  private fun scheduleTransientReturnToNeutral(delayMs: Long) {
    transientEmotionJob?.cancel()
    transientEmotionJob =
      viewModelScope.launch {
        delay(delayMs)
        val current = _uiState.value
        if (current.isAwake &&
          !current.isListening &&
          !current.isThinking &&
          !current.isSpeaking &&
          current.emotion != LooiEmotion.DORMIDO
        ) {
          _uiState.update { it.copy(emotion = LooiEmotion.NEUTRAL) }
        }
      }
  }

  private fun scheduleCaptionClear(delayMs: Long) {
    captionClearJob?.cancel()
    captionClearJob =
      viewModelScope.launch {
        delay(delayMs)
        if (!_uiState.value.isListening && !_uiState.value.isThinking && !_uiState.value.isSpeaking) {
          _uiState.update { it.copy(captionText = null) }
        }
      }
  }

  private fun startBackgroundLoops() {
    if (blinkJob == null) {
      blinkJob =
        viewModelScope.launch {
          while (true) {
            delay(Random.nextLong(2200L, 6200L))
            val state = _uiState.value
            if (state.isAwake &&
              state.emotion != LooiEmotion.DORMIDO &&
              state.emotion != LooiEmotion.FELIZ
            ) {
              _uiState.update { it.copy(isBlinking = true) }
              delay(125L)
              _uiState.update { it.copy(isBlinking = false) }

              // Occasional natural double-blink
              if (Random.nextFloat() < 0.22f) {
                delay(140L)
                _uiState.update { it.copy(isBlinking = true) }
                delay(115L)
                _uiState.update { it.copy(isBlinking = false) }
              }
            }
          }
        }
    }

    if (gazeJob == null) {
      gazeJob =
        viewModelScope.launch {
          while (true) {
            delay(Random.nextLong(1700L, 3900L))
            val state = _uiState.value
            val trackingActiveWithFace = state.isCameraTrackingEnabled && state.faceDetected
            if (state.isAwake &&
              !trackingActiveWithFace &&
              state.emotion == LooiEmotion.NEUTRAL
            ) {
              val nextX = (Random.nextFloat() * 1.1f) - 0.55f
              val nextY = (Random.nextFloat() * 0.65f) - 0.32f
              _uiState.update { it.copy(gazeTarget = Offset(nextX, nextY)) }
            } else if (!trackingActiveWithFace && state.emotion != LooiEmotion.NEUTRAL) {
              _uiState.update { it.copy(gazeTarget = Offset.Zero) }
            }
          }
        }
    }
  }

  override fun onCleared() {
    super.onCleared()
    inactivityJob?.cancel()
    blinkJob?.cancel()
    gazeJob?.cancel()
    transientEmotionJob?.cancel()
    captionClearJob?.cancel()
    faceLostResetJob?.cancel()
  }
}
