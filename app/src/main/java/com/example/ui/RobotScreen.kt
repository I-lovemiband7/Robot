package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ai.LooiEmotion
import com.example.audio.RobotSoundEngine
import com.example.sensors.ShakeDetector
import com.example.ui.components.RobotFaceCanvas
import com.example.ui.theme.AlertRose
import com.example.ui.theme.DeepCyanContainer
import com.example.ui.theme.DeepCyanSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.OledBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.vision.FaceTrackerManager
import com.example.voice.RobotVoiceController

@Composable
fun RobotScreen(
  viewModel: RobotViewModel = viewModel(),
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  var showTextPromptDialog by remember { mutableStateOf(false) }
  var typedMessage by remember { mutableStateOf("") }

  val soundEngine = remember { RobotSoundEngine() }

  val voiceController =
    remember(context) {
      RobotVoiceController(
        context = context,
        onListeningStarted = { viewModel.onListeningStarted() },
        onRmsChanged = { level -> viewModel.onMicRmsChanged(level) },
        onPartialResult = { partial -> viewModel.onSpeechPartial(partial) },
        onFinalResult = { finalText -> viewModel.submitUserPrompt(finalText) },
        onSpeechError = { err -> viewModel.onSpeechError(err) },
        onSpeakingChanged = { speaking -> viewModel.onSpeakingChanged(speaking) },
      )
    }

  val faceTracker =
    remember(context) {
      FaceTrackerManager(
        context = context,
        onResult = { res -> viewModel.onFaceTrackingUpdate(res.hasFace, res.gazeX, res.gazeY) },
        onError = { err -> viewModel.onCameraTrackingError(err) },
      )
    }

  val shakeDetector =
    remember(context) {
      ShakeDetector(context = context) {
        viewModel.onShakeDetected()
      }
    }

  // Lifecycle management for sensors, audio, voice, and camera
  DisposableEffect(lifecycleOwner, state.isAwake) {
    val observer =
      LifecycleEventObserver { _, event ->
        when (event) {
          Lifecycle.Event.ON_RESUME -> {
            if (state.isAwake) shakeDetector.start()
          }
          Lifecycle.Event.ON_PAUSE -> {
            shakeDetector.stop()
            voiceController.stopListening()
          }
          else -> Unit
        }
      }
    lifecycleOwner.lifecycle.addObserver(observer)
    if (state.isAwake) shakeDetector.start()
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      shakeDetector.stop()
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      soundEngine.release()
      voiceController.release()
      faceTracker.release()
    }
  }

  val view = androidx.compose.ui.platform.LocalView.current

  // Start / stop CameraX ML Kit tracking reactively
  LaunchedEffect(state.isAwake, state.isCameraTrackingEnabled) {
    if (state.isAwake && state.isCameraTrackingEnabled) {
      val rotation = view.display?.rotation ?: android.view.Surface.ROTATION_90
      faceTracker.start(lifecycleOwner, targetRotation = rotation)
    } else {
      faceTracker.stop()
    }
  }

  // Trigger synthetic sounds from ViewModel events
  LaunchedEffect(state.pendingSound) {
    val cue = state.pendingSound?.second ?: return@LaunchedEffect
    when (cue) {
      SoundCue.WAKE_UP -> soundEngine.playWakeUp()
      SoundCue.WINK -> soundEngine.playWink()
      SoundCue.HAPPY_PURR -> soundEngine.playHappyPurr()
      SoundCue.DIZZY -> soundEngine.playDizzy()
      SoundCue.LISTENING_BEEP -> soundEngine.playListeningBeep()
      SoundCue.SLEEP_SIGH -> soundEngine.playSleepSigh()
    }
  }

  // Trigger TextToSpeech from ViewModel events
  LaunchedEffect(state.pendingSpeech) {
    val text = state.pendingSpeech?.second ?: return@LaunchedEffect
    voiceController.speak(text)
  }

  val permissionsLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
      val micGranted =
        grants[Manifest.permission.RECORD_AUDIO] == true ||
          ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
          PackageManager.PERMISSION_GRANTED
      val cameraGranted =
        grants[Manifest.permission.CAMERA] == true ||
          ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
          PackageManager.PERMISSION_GRANTED
      if (!state.isAwake) {
        viewModel.wakeUpRobot(micGranted = micGranted, cameraGranted = cameraGranted)
      } else {
        viewModel.onPermissionsUpdated(micGranted = micGranted, cameraGranted = cameraGranted)
      }
    }

  val singleMicPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
      val cameraGranted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
          PackageManager.PERMISSION_GRANTED
      viewModel.onPermissionsUpdated(micGranted = granted, cameraGranted = cameraGranted)
      if (granted) {
        voiceController.startListening()
      } else {
        viewModel.onSpeechError(context.getString(R.string.error_mic_permission))
      }
    }

  val singleCameraPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
      val micGranted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
          PackageManager.PERMISSION_GRANTED
      viewModel.onPermissionsUpdated(micGranted = micGranted, cameraGranted = granted)
      viewModel.toggleCameraTracking(cameraGranted = granted)
    }

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(OledBlack)
  ) {
    // Fullscreen Animated OLED Robot Face
    RobotFaceCanvas(
      state = state,
      onEyeTapped = { target -> viewModel.onEyeTapped(target) },
      onHeadPat = { viewModel.onHeadPat() },
      onFaceTapped = { viewModel.onFaceTapped() },
    )

    // Safe HUD overlay respecting notches / display cutouts
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
      // Top-left: Subtle emotion badge + sleep button when awake
      AnimatedVisibility(
        visible = state.isAwake,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopStart),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Surface(
            color = DeepCyanSurface.copy(alpha = 0.72f),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.25f)),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Box(
                modifier =
                  Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                      if (state.emotion == LooiEmotion.MOLESTO) AlertRose else NeonCyan
                    )
              )
              Text(
                text = emotionLabel(state.emotion),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
              )
            }
          }

          if (state.emotion != LooiEmotion.DORMIDO) {
            IconButton(
              onClick = { viewModel.putToSleep() },
              modifier =
                Modifier
                  .size(48.dp)
                  .testTag("sleep_robot_button"),
              colors =
                IconButtonDefaults.iconButtonColors(
                  containerColor = DeepCyanSurface.copy(alpha = 0.55f),
                  contentColor = TextSecondary,
                ),
            ) {
              Icon(
                imageVector = Icons.Outlined.Bedtime,
                contentDescription = stringResource(R.string.cd_sleep_robot),
                modifier = Modifier.size(20.dp),
              )
            }
          }
        }
      }

      // Top-right: Discreet CameraX + ML Kit Face Tracking toggle button
      AnimatedVisibility(
        visible = state.isAwake,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopEnd),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (state.isCameraTrackingEnabled) {
            Surface(
              color = DeepCyanContainer.copy(alpha = 0.78f),
              shape = RoundedCornerShape(50),
              border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.35f)),
            ) {
              Text(
                text =
                  if (state.faceDetected) {
                    stringResource(R.string.status_face_locked)
                  } else {
                    stringResource(R.string.status_camera_searching)
                  },
                style = MaterialTheme.typography.labelMedium,
                color = if (state.faceDetected) NeonCyan else TextSecondary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              )
            }
          }

          IconButton(
            onClick = {
              val camGranted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                  PackageManager.PERMISSION_GRANTED
              if (camGranted) {
                viewModel.toggleCameraTracking(cameraGranted = true)
              } else {
                singleCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
              }
            },
            modifier =
              Modifier
                .size(48.dp)
                .testTag("camera_tracking_button"),
            colors =
              IconButtonDefaults.iconButtonColors(
                containerColor =
                  if (state.isCameraTrackingEnabled) DeepCyanContainer.copy(alpha = 0.9f)
                  else DeepCyanSurface.copy(alpha = 0.55f),
                contentColor = if (state.isCameraTrackingEnabled) NeonCyan else TextSecondary,
              ),
          ) {
            Icon(
              imageVector =
                if (state.isCameraTrackingEnabled) Icons.Outlined.Videocam
                else Icons.Outlined.VideocamOff,
              contentDescription = stringResource(R.string.cd_camera_tracking_toggle),
              modifier = Modifier.size(22.dp),
            )
          }
        }
      }

      // Top-center: Error banner (transparent Gemini / Speech / Permission error feedback)
      AnimatedVisibility(
        visible = state.errorMessage != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter),
      ) {
        state.errorMessage?.let { msg ->
          Surface(
            color = DeepCyanSurface.copy(alpha = 0.92f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AlertRose.copy(alpha = 0.6f)),
            modifier = Modifier.widthIn(max = 420.dp),
          ) {
            Row(
              modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = msg,
                style = MaterialTheme.typography.bodyLarge,
                color = AlertRose,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
              )
              IconButton(
                onClick = { viewModel.dismissError() },
                modifier = Modifier.size(48.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = stringResource(R.string.cd_dismiss_error),
                  tint = AlertRose,
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
        }
      }

      // Bottom-center: Initial "Despertar Robot" button when sleeping at launch
      AnimatedVisibility(
        visible = !state.isAwake,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.padding(bottom = 12.dp),
        ) {
          Button(
            onClick = {
              permissionsLauncher.launch(
                arrayOf(
                  Manifest.permission.RECORD_AUDIO,
                  Manifest.permission.CAMERA,
                )
              )
            },
            shape = RoundedCornerShape(50),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = NeonCyan,
                contentColor = OledBlack,
              ),
            modifier =
              Modifier
                .size(width = 240.dp, height = 54.dp)
                .testTag("wake_robot_button"),
          ) {
            Icon(
              imageVector = Icons.Default.PowerSettingsNew,
              contentDescription = null,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.wake_robot_button),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }
          Text(
            text = stringResource(R.string.wake_robot_subtitle),
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary.copy(alpha = 0.8f),
          )
        }
      }

      // Bottom-center: Spoken / recognized caption pill when awake
      AnimatedVisibility(
        visible = state.isAwake && !state.captionText.isNullOrBlank(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
      ) {
        Surface(
          color = DeepCyanSurface.copy(alpha = 0.78f),
          shape = RoundedCornerShape(20.dp),
          border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.28f)),
          modifier =
            Modifier
              .padding(bottom = 4.dp)
              .widthIn(max = 520.dp),
        ) {
          Text(
            text =
              if (state.isThinking) stringResource(R.string.status_thinking)
              else state.captionText.orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
          )
        }
      }

      // Bottom-right: Discreet voice mic button + optional text fallback button
      AnimatedVisibility(
        visible = state.isAwake,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomEnd),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          IconButton(
            onClick = { showTextPromptDialog = true },
            modifier =
              Modifier
                .size(48.dp)
                .testTag("text_prompt_button"),
            colors =
              IconButtonDefaults.iconButtonColors(
                containerColor = DeepCyanSurface.copy(alpha = 0.65f),
                contentColor = TextSecondary,
              ),
          ) {
            Icon(
              imageVector = Icons.Default.Keyboard,
              contentDescription = stringResource(R.string.cd_text_input),
              modifier = Modifier.size(20.dp),
            )
          }

          IconButton(
            onClick = {
              if (state.isListening) {
                voiceController.stopListening()
              } else {
                val micGranted =
                  ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                if (micGranted) {
                  voiceController.startListening()
                } else {
                  singleMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
              }
            },
            modifier =
              Modifier
                .size(56.dp)
                .testTag("voice_listen_button"),
            colors =
              IconButtonDefaults.iconButtonColors(
                containerColor =
                  if (state.isListening) NeonCyan else DeepCyanContainer.copy(alpha = 0.85f),
                contentColor = if (state.isListening) OledBlack else NeonCyan,
              ),
          ) {
            Icon(
              imageVector = Icons.Default.Mic,
              contentDescription = stringResource(R.string.cd_voice_listen),
              modifier = Modifier.size(26.dp),
            )
          }
        }
      }
    }
  }

  if (showTextPromptDialog) {
    AlertDialog(
      onDismissRequest = { showTextPromptDialog = false },
      containerColor = DeepCyanSurface,
      titleContentColor = NeonCyan,
      textContentColor = TextPrimary,
      title = { Text(text = stringResource(R.string.dialog_send_message_title)) },
      text = {
        OutlinedTextField(
          value = typedMessage,
          onValueChange = { typedMessage = it },
          placeholder = { Text(stringResource(R.string.dialog_send_message_hint)) },
          singleLine = true,
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = NeonCyan,
              unfocusedBorderColor = NeonCyan.copy(alpha = 0.4f),
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary,
              cursorColor = NeonCyan,
            ),
          modifier = Modifier.testTag("text_prompt_input"),
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            val msg = typedMessage.trim()
            if (msg.isNotEmpty()) {
              viewModel.submitUserPrompt(msg)
              typedMessage = ""
              showTextPromptDialog = false
            }
          },
          modifier = Modifier.testTag("send_text_prompt_button"),
        ) {
          Text(text = stringResource(R.string.dialog_send), color = NeonCyan)
        }
      },
      dismissButton = {
        TextButton(onClick = { showTextPromptDialog = false }) {
          Text(text = stringResource(R.string.dialog_cancel), color = TextSecondary)
        }
      },
    )
  }
}

@Composable
private fun emotionLabel(emotion: LooiEmotion): String {
  return when (emotion) {
    LooiEmotion.NEUTRAL -> stringResource(R.string.emotion_neutral)
    LooiEmotion.FELIZ -> stringResource(R.string.emotion_happy)
    LooiEmotion.SORPRENDIDO -> stringResource(R.string.emotion_surprised)
    LooiEmotion.MOLESTO -> stringResource(R.string.emotion_annoyed)
    LooiEmotion.DORMIDO -> stringResource(R.string.emotion_sleepy)
    LooiEmotion.ESCUCHANDO -> stringResource(R.string.emotion_listening)
    LooiEmotion.MAREADO -> stringResource(R.string.emotion_dizzy)
  }
}
