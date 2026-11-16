package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.ai.LooiEmotion
import com.example.ui.EyeTarget
import com.example.ui.RobotUiState
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanSoft
import com.example.ui.theme.OledBlack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun RobotFaceCanvas(
  state: RobotUiState,
  onEyeTapped: (EyeTarget) -> Unit,
  onHeadPat: () -> Unit,
  onFaceTapped: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val textMeasurer = rememberTextMeasurer()

  val isSleeping = !state.isAwake || state.emotion == LooiEmotion.DORMIDO
  val isHappy = state.isAwake && state.emotion == LooiEmotion.FELIZ
  val isSurprised = state.isAwake && state.emotion == LooiEmotion.SORPRENDIDO
  val isAnnoyed = state.isAwake && state.emotion == LooiEmotion.MOLESTO
  val isListening = state.isAwake && state.emotion == LooiEmotion.ESCUCHANDO
  val isDizzy = state.isAwake && state.emotion == LooiEmotion.MAREADO

  val leftBlinkTarget =
    when {
      isSleeping -> 1f
      state.winkLeft -> 1f
      state.isBlinking -> 1f
      else -> 0f
    }
  val rightBlinkTarget =
    when {
      isSleeping -> 1f
      state.winkRight -> 1f
      state.isBlinking -> 1f
      else -> 0f
    }

  val leftBlink by
    animateFloatAsState(
      targetValue = leftBlinkTarget,
      animationSpec = tween(durationMillis = 105, easing = FastOutSlowInEasing),
      label = "leftBlink",
    )
  val rightBlink by
    animateFloatAsState(
      targetValue = rightBlinkTarget,
      animationSpec = tween(durationMillis = 105, easing = FastOutSlowInEasing),
      label = "rightBlink",
    )

  val happyFactor by
    animateFloatAsState(
      targetValue = if (isHappy) 1f else 0f,
      animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.72f),
      label = "happyFactor",
    )
  val surprisedFactor by
    animateFloatAsState(
      targetValue = if (isSurprised) 1f else 0f,
      animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.62f),
      label = "surprisedFactor",
    )
  val annoyedFactor by
    animateFloatAsState(
      targetValue = if (isAnnoyed) 1f else 0f,
      animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.78f),
      label = "annoyedFactor",
    )
  val listeningFactor by
    animateFloatAsState(
      targetValue = if (isListening) 1f else 0f,
      animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.8f),
      label = "listeningFactor",
    )
  val sleepFactor by
    animateFloatAsState(
      targetValue = if (isSleeping) 1f else 0f,
      animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
      label = "sleepFactor",
    )
  val dizzyFactor by
    animateFloatAsState(
      targetValue = if (isDizzy) 1f else 0f,
      animationSpec = tween(durationMillis = 280),
      label = "dizzyFactor",
    )

  val isTrackingFace = state.isAwake && state.isCameraTrackingEnabled && state.faceDetected
  val gazeTargetX = if (isSleeping) 0f else state.gazeTarget.x
  val gazeTargetY = if (isSleeping) 0f else state.gazeTarget.y

  val gazeX by
    animateFloatAsState(
      targetValue = gazeTargetX,
      animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.84f),
      label = "gazeX",
    )
  val gazeY by
    animateFloatAsState(
      targetValue = gazeTargetY,
      animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.84f),
      label = "gazeY",
    )

  // Pupils track slightly faster and further than the outer eye frame for natural parallax
  val pupilX by
    animateFloatAsState(
      targetValue = gazeTargetX,
      animationSpec =
        spring(
          stiffness = if (isTrackingFace) Spring.StiffnessMediumLow else Spring.StiffnessLow,
          dampingRatio = 0.78f,
        ),
      label = "pupilX",
    )
  val pupilY by
    animateFloatAsState(
      targetValue = gazeTargetY,
      animationSpec =
        spring(
          stiffness = if (isTrackingFace) Spring.StiffnessMediumLow else Spring.StiffnessLow,
          dampingRatio = 0.78f,
        ),
      label = "pupilY",
    )

  val micPulse by
    animateFloatAsState(
      targetValue = if (state.isListening) state.micLevel else 0f,
      animationSpec = tween(durationMillis = 90),
      label = "micPulse",
    )

  val infiniteTransition = rememberInfiniteTransition(label = "robotInfinite")
  val breathPhase by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 2800, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "breathPhase",
    )
  val zzzPhase by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 3200, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "zzzPhase",
    )
  val talkCycle by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 210, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "talkCycle",
    )
  val speakGate by
    animateFloatAsState(
      targetValue = if (state.isSpeaking) 1f else 0f,
      animationSpec = tween(durationMillis = 120),
      label = "speakGate",
    )
  val talkOpen = talkCycle * speakGate

  Canvas(
    modifier =
      modifier
        .fillMaxSize()
        .background(OledBlack)
        .pointerInput(state.isAwake) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startPos = down.position
            var maxDist = 0f
            var lastPos = startPos
            var patTriggered = false

            while (true) {
              val event = awaitPointerEvent()
              val change = event.changes.firstOrNull { it.id == down.id } ?: break
              if (!change.pressed) {
                lastPos = change.position
                break
              }
              val dist = hypot(change.position.x - startPos.x, change.position.y - startPos.y)
              if (dist > maxDist) maxDist = dist

              // Swipe along the upper zone pets LooiBot on the head
              if (!patTriggered &&
                maxDist > viewConfiguration.touchSlop * 2.5f &&
                startPos.y < size.height * 0.42f
              ) {
                patTriggered = true
                onHeadPat()
              }
              lastPos = change.position
            }

            if (!patTriggered && maxDist <= viewConfiguration.touchSlop * 2f) {
              val w = size.width.toFloat()
              val h = size.height.toFloat()
              val eyeSeparation = minOf(w * 0.36f, h * 0.62f)
              val leftCenter = Offset(w * 0.5f - eyeSeparation * 0.52f, h * 0.44f)
              val rightCenter = Offset(w * 0.5f + eyeSeparation * 0.52f, h * 0.44f)
              val hitRadius = minOf(w, h) * 0.25f

              val distLeft = hypot(lastPos.x - leftCenter.x, lastPos.y - leftCenter.y)
              val distRight = hypot(lastPos.x - rightCenter.x, lastPos.y - rightCenter.y)

              when {
                distLeft < hitRadius && distRight < hitRadius -> onEyeTapped(EyeTarget.BOTH)
                distLeft < hitRadius -> onEyeTapped(EyeTarget.LEFT)
                distRight < hitRadius -> onEyeTapped(EyeTarget.RIGHT)
                else -> onFaceTapped()
              }
            }
          }
        }
  ) {
    val w = size.width
    val h = size.height
    val baseUnit = minOf(w, h)
    val eyeSeparation = minOf(w * 0.36f, h * 0.62f)

    // Subtle organic breathing offset for alive desktop companion feel
    val breathOffsetY = sin(breathPhase * 2f * PI.toFloat()) * baseUnit * 0.012f * (1f - sleepFactor * 0.6f)

    val gazeOffsetX = gazeX * baseUnit * 0.055f
    val gazeOffsetY = gazeY * baseUnit * 0.038f

    val dizzyAngle = breathPhase * 2f * PI.toFloat()
    val dizzyLeftOffset =
      Offset(
        cos(dizzyAngle) * baseUnit * 0.035f * dizzyFactor,
        sin(dizzyAngle) * baseUnit * 0.035f * dizzyFactor,
      )
    val dizzyRightOffset =
      Offset(
        cos(dizzyAngle + PI.toFloat()) * baseUnit * 0.035f * dizzyFactor,
        sin(dizzyAngle + PI.toFloat()) * baseUnit * 0.035f * dizzyFactor,
      )

    val baseEyeY = h * 0.43f + breathOffsetY + gazeOffsetY
    val leftEyeCenter =
      Offset(w * 0.5f - eyeSeparation * 0.52f + gazeOffsetX, baseEyeY) + dizzyLeftOffset
    val rightEyeCenter =
      Offset(w * 0.5f + eyeSeparation * 0.52f + gazeOffsetX, baseEyeY) + dizzyRightOffset

    val eyeScale =
      1f +
        surprisedFactor * 0.20f +
        listeningFactor * (0.11f + micPulse * 0.09f) -
        annoyedFactor * 0.05f
    val eyeWidth = baseUnit * 0.255f * eyeScale
    val eyeHeight = baseUnit * 0.295f * eyeScale * (1f - annoyedFactor * 0.14f)

    // Draw listening pulse rings behind eyes if listening
    if (listeningFactor > 0.02f) {
      drawListeningHalo(
        center = Offset(w * 0.5f, baseEyeY),
        radius = baseUnit * (0.34f + micPulse * 0.08f + sin(breathPhase * 2f * PI.toFloat()) * 0.015f),
        alpha = listeningFactor * (0.14f + micPulse * 0.18f),
      )
    }

    // Left & Right Eyes with smoothly tracked pupils
    drawRobotEye(
      center = leftEyeCenter,
      width = eyeWidth,
      height = eyeHeight,
      isLeft = true,
      blink = leftBlink,
      happy = happyFactor,
      surprised = surprisedFactor,
      annoyed = annoyedFactor,
      listening = listeningFactor,
      dizzy = dizzyFactor,
      dizzyAngle = dizzyAngle,
      pupilX = pupilX,
      pupilY = pupilY,
      faceLocked = isTrackingFace,
    )
    drawRobotEye(
      center = rightEyeCenter,
      width = eyeWidth,
      height = eyeHeight,
      isLeft = false,
      blink = rightBlink,
      happy = happyFactor,
      surprised = surprisedFactor,
      annoyed = annoyedFactor,
      listening = listeningFactor,
      dizzy = dizzyFactor,
      dizzyAngle = dizzyAngle + 1.2f,
      pupilX = pupilX,
      pupilY = pupilY,
      faceLocked = isTrackingFace,
    )

    // Minimalist glowing curved mouth below eyes
    val mouthCenter =
      Offset(
        x = w * 0.5f + gazeOffsetX * 0.45f,
        y = h * 0.735f + breathOffsetY * 0.6f + gazeOffsetY * 0.35f,
      )
    drawRobotMouth(
      center = mouthCenter,
      baseUnit = baseUnit,
      happy = happyFactor,
      surprised = surprisedFactor,
      annoyed = annoyedFactor,
      sleepy = sleepFactor,
      dizzy = dizzyFactor,
      talkOpen = talkOpen,
      breathPhase = breathPhase,
    )

    // Floating "Zzz" when sleeping
    if (sleepFactor > 0.05f) {
      drawFloatingZzz(
        anchor = Offset(rightEyeCenter.x + eyeWidth * 0.48f, rightEyeCenter.y - eyeHeight * 0.25f),
        baseUnit = baseUnit,
        zzzPhase = zzzPhase,
        sleepFactor = sleepFactor,
        textMeasurer = textMeasurer,
      )
    }
  }
}

private fun DrawScope.drawListeningHalo(
  center: Offset,
  radius: Float,
  alpha: Float,
) {
  drawCircle(
    brush =
      Brush.radialGradient(
        colors = listOf(NeonCyan.copy(alpha = alpha), Color.Transparent),
        center = center,
        radius = radius * 1.45f,
      ),
    radius = radius * 1.45f,
    center = center,
  )
}

private fun DrawScope.drawRobotEye(
  center: Offset,
  width: Float,
  height: Float,
  isLeft: Boolean,
  blink: Float,
  happy: Float,
  surprised: Float,
  annoyed: Float,
  listening: Float,
  dizzy: Float,
  dizzyAngle: Float,
  pupilX: Float,
  pupilY: Float,
  faceLocked: Boolean,
) {
  // 1. Soft outer OLED glow halo
  val glowRadius = maxOf(width, height) * (1.15f + surprised * 0.15f + listening * 0.12f)
  drawCircle(
    brush =
      Brush.radialGradient(
        colors =
          listOf(
            NeonCyan.copy(alpha = (0.34f - blink * 0.18f).coerceAtLeast(0.10f)),
            NeonCyan.copy(alpha = 0.10f),
            Color.Transparent,
          ),
        center = center,
        radius = glowRadius,
      ),
    radius = glowRadius,
    center = center,
  )

  // 2. If happy arch dominates, draw upward crescent eye; blend smoothly via alpha & geometry
  val openWeight = (1f - blink).coerceIn(0f, 1f)
  val normalWeight = (1f - happy).coerceIn(0f, 1f) * openWeight

  if (normalWeight > 0.02f) {
    val activeHeight = (height * openWeight).coerceAtLeast(height * 0.06f)
    val topLeft = Offset(center.x - width * 0.5f, center.y - activeHeight * 0.5f)
    val corner = CornerRadius(width * 0.44f, minOf(width, activeHeight) * 0.48f)

    val eyePath =
      Path().apply {
        addRoundRect(
          androidx.compose.ui.geometry.RoundRect(
            rect = Rect(topLeft, Size(width, activeHeight)),
            cornerRadius = corner,
          )
        )
      }

    // Clip annoyed inward eyebrow slant on the top edge when annoyed > 0
    val clipMask =
      if (annoyed > 0.02f) {
        Path().apply {
          val slantDrop = activeHeight * 0.34f * annoyed
          if (isLeft) {
            // Slant downward toward inner right
            moveTo(topLeft.x - 10f, topLeft.y - 10f)
            lineTo(topLeft.x + width + 10f, topLeft.y + slantDrop)
            lineTo(topLeft.x + width + 10f, topLeft.y + activeHeight + 10f)
            lineTo(topLeft.x - 10f, topLeft.y + activeHeight + 10f)
          } else {
            // Slant downward toward inner left
            moveTo(topLeft.x - 10f, topLeft.y + slantDrop)
            lineTo(topLeft.x + width + 10f, topLeft.y - 10f)
            lineTo(topLeft.x + width + 10f, topLeft.y + activeHeight + 10f)
            lineTo(topLeft.x - 10f, topLeft.y + activeHeight + 10f)
          }
          close()
        }
      } else {
        null
      }

    val drawEyeCore: DrawScope.() -> Unit = {
      // Outer bloom strokes
      drawPath(
        path = eyePath,
        color = NeonCyan.copy(alpha = 0.24f * normalWeight),
        style = Stroke(width = width * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round),
      )
      drawPath(
        path = eyePath,
        color = NeonCyan.copy(alpha = normalWeight),
      )

      // Clip pupil, iris halo, and catchlight neatly inside the glowing eye shape
      clipPath(eyePath) {
        val pupilCenter =
          Offset(
            x = center.x + pupilX * width * 0.22f,
            y = center.y + pupilY * activeHeight * 0.20f,
          )
        val minEyeDim = minOf(width, activeHeight)
        val pupilRadius =
          minEyeDim *
            (0.24f + listening * 0.06f - surprised * 0.04f + if (faceLocked) 0.02f else 0f)

        // Luminous iris aura around the pupil
        drawCircle(
          brush =
            Brush.radialGradient(
              colors =
                listOf(
                  NeonCyanSoft.copy(alpha = 0.50f * normalWeight),
                  NeonCyan.copy(alpha = 0.0f),
                ),
              center = pupilCenter,
              radius = pupilRadius * 1.9f,
            ),
          radius = pupilRadius * 1.9f,
          center = pupilCenter,
        )

        // Sleek deep cyan-black pupil core that shifts smoothly toward the user
        val pupilVisibility = (1f - dizzy * 0.75f).coerceIn(0f, 1f) * normalWeight
        drawCircle(
          color = Color(0xFF00242B).copy(alpha = 0.86f * pupilVisibility),
          radius = pupilRadius,
          center = pupilCenter,
        )
        drawCircle(
          color = NeonCyanSoft.copy(alpha = 0.48f * pupilVisibility),
          radius = pupilRadius * 1.12f,
          center = pupilCenter,
          style = Stroke(width = minEyeDim * 0.025f),
        )

        // Specular catchlight glint moving with the pupil
        val glintCenter =
          Offset(
            x = pupilCenter.x - pupilRadius * 0.38f,
            y = pupilCenter.y - pupilRadius * 0.38f,
          )
        drawCircle(
          color = Color(0xFFE0FFFF).copy(alpha = 0.85f * pupilVisibility),
          radius = pupilRadius * 0.28f,
          center = glintCenter,
        )

        // Dizzy concentric rotating ring inside eye when shaken
        if (dizzy > 0.05f) {
          val spiralCenter =
            Offset(
              center.x + cos(dizzyAngle) * width * 0.1f,
              center.y + sin(dizzyAngle) * activeHeight * 0.1f,
            )
          drawCircle(
            color = OledBlack.copy(alpha = 0.85f * dizzy),
            radius = minEyeDim * 0.27f,
            center = spiralCenter,
            style = Stroke(width = width * 0.07f),
          )
          drawCircle(
            color = OledBlack.copy(alpha = 0.85f * dizzy),
            radius = minEyeDim * 0.11f,
            center = spiralCenter,
          )
        }
      }
    }

    if (clipMask != null) {
      clipPath(clipMask) { drawEyeCore() }
      // Crisp glowing angry brow accent line along the cut edge
      val slantDrop = activeHeight * 0.34f * annoyed
      val start =
        if (isLeft) Offset(topLeft.x, topLeft.y) else Offset(topLeft.x, topLeft.y + slantDrop)
      val end =
        if (isLeft) Offset(topLeft.x + width, topLeft.y + slantDrop)
        else Offset(topLeft.x + width, topLeft.y)
      drawLine(
        color = NeonCyanSoft.copy(alpha = 0.9f * annoyed * normalWeight),
        start = start,
        end = end,
        strokeWidth = width * 0.06f,
        cap = StrokeCap.Round,
      )
    } else {
      drawEyeCore()
    }
  }

  // 3. Happy upward arched eye (^ ^)
  if (happy > 0.02f && openWeight > 0.02f) {
    val archAlpha = happy * openWeight
    val archPath =
      Path().apply {
        val halfW = width * 0.48f
        val archPeak = height * 0.34f
        moveTo(center.x - halfW, center.y + height * 0.08f)
        quadraticTo(
          center.x,
          center.y - archPeak,
          center.x + halfW,
          center.y + height * 0.08f,
        )
      }
    drawPath(
      path = archPath,
      color = NeonCyan.copy(alpha = 0.25f * archAlpha),
      style = Stroke(width = width * 0.24f, cap = StrokeCap.Round),
    )
    drawPath(
      path = archPath,
      color = NeonCyan.copy(alpha = archAlpha),
      style = Stroke(width = width * 0.12f, cap = StrokeCap.Round),
    )
  }

  // 4. Closed / Blinking / Sleeping curved eyelid
  if (blink > 0.05f) {
    val lidAlpha = blink
    val lidPath =
      Path().apply {
        val halfW = width * 0.48f
        val sag = height * 0.16f * blink
        moveTo(center.x - halfW, center.y)
        quadraticTo(center.x, center.y + sag, center.x + halfW, center.y)
      }
    drawPath(
      path = lidPath,
      color = NeonCyan.copy(alpha = 0.26f * lidAlpha),
      style = Stroke(width = width * 0.18f, cap = StrokeCap.Round),
    )
    drawPath(
      path = lidPath,
      color = NeonCyan.copy(alpha = 0.92f * lidAlpha),
      style = Stroke(width = width * 0.085f, cap = StrokeCap.Round),
    )
  }
}

private fun DrawScope.drawRobotMouth(
  center: Offset,
  baseUnit: Float,
  happy: Float,
  surprised: Float,
  annoyed: Float,
  sleepy: Float,
  dizzy: Float,
  talkOpen: Float,
  breathPhase: Float,
) {
  val strokeW = baseUnit * 0.024f
  val mouthWidth = baseUnit * (0.145f + happy * 0.06f - surprised * 0.07f - sleepy * 0.05f)

  // Surprised 'O' mouth OR animated open speaking mouth
  val openness = (surprised * 0.9f + talkOpen * 0.78f).coerceIn(0f, 1f)

  if (openness > 0.18f) {
    val ovalW = mouthWidth * (0.58f + talkOpen * 0.45f)
    val ovalH = baseUnit * (0.045f + openness * 0.055f)
    val rectTopLeft = Offset(center.x - ovalW * 0.5f, center.y - ovalH * 0.35f)

    // Outer glow
    drawRoundRect(
      color = NeonCyan.copy(alpha = 0.25f),
      topLeft = Offset(rectTopLeft.x - 4f, rectTopLeft.y - 4f),
      size = Size(ovalW + 8f, ovalH + 8f),
      cornerRadius = CornerRadius(ovalW * 0.5f, ovalH * 0.5f),
      style = Stroke(width = strokeW * 1.8f),
    )
    drawRoundRect(
      color = NeonCyan,
      topLeft = rectTopLeft,
      size = Size(ovalW, ovalH),
      cornerRadius = CornerRadius(ovalW * 0.5f, ovalH * 0.5f),
      style = Stroke(width = strokeW),
    )
    return
  }

  val curveDepth =
    baseUnit * (0.036f + happy * 0.052f - annoyed * 0.068f - sleepy * 0.024f)

  val path =
    Path().apply {
      val leftX = center.x - mouthWidth * 0.5f
      val rightX = center.x + mouthWidth * 0.5f
      if (dizzy > 0.25f) {
        val wave = sin(breathPhase * 4f * PI.toFloat()) * baseUnit * 0.022f * dizzy
        moveTo(leftX, center.y)
        cubicTo(
          center.x - mouthWidth * 0.16f,
          center.y - wave,
          center.x + mouthWidth * 0.16f,
          center.y + wave,
          rightX,
          center.y,
        )
      } else {
        moveTo(leftX, center.y)
        quadraticTo(center.x, center.y + curveDepth, rightX, center.y)
      }
    }

  drawPath(
    path = path,
    color = NeonCyan.copy(alpha = 0.26f),
    style = Stroke(width = strokeW * 2.1f, cap = StrokeCap.Round),
  )
  drawPath(
    path = path,
    color = NeonCyan,
    style = Stroke(width = strokeW, cap = StrokeCap.Round),
  )
}

private fun DrawScope.drawFloatingZzz(
  anchor: Offset,
  baseUnit: Float,
  zzzPhase: Float,
  sleepFactor: Float,
  textMeasurer: TextMeasurer,
) {
  val letters = listOf("z", "Z", "Z")
  letters.forEachIndexed { index, letter ->
    val p = (zzzPhase + index * 0.33f) % 1f
    val alpha =
      when {
        p < 0.2f -> p / 0.2f
        p > 0.72f -> (1f - p) / 0.28f
        else -> 1f
      } * sleepFactor * 0.85f

    val pos =
      Offset(
        x = anchor.x + p * baseUnit * 0.15f + sin(p * 2f * PI.toFloat()) * baseUnit * 0.02f,
        y = anchor.y - p * baseUnit * 0.24f,
      )
    val fontSize = (18f + index * 5f + p * 8f).sp
    val style =
      TextStyle(
        color = NeonCyan.copy(alpha = alpha.coerceIn(0f, 1f)),
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
      )
    val measured = textMeasurer.measure(text = letter, style = style)
    drawText(
      textLayoutResult = measured,
      topLeft = pos,
    )
  }
}
