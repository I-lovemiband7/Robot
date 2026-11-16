package com.example.vision

import android.content.Context
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors
import kotlin.math.abs

private const val TAG = "FaceTrackerManager"

data class FaceTrackingResult(
  val hasFace: Boolean,
  val gazeX: Float,
  val gazeY: Float,
)

class FaceTrackerManager(
  private val context: Context,
  private val onResult: (FaceTrackingResult) -> Unit,
  private val onError: (String) -> Unit,
) {
  private val analysisExecutor = Executors.newSingleThreadExecutor()
  private var cameraProvider: ProcessCameraProvider? = null

  // Exponential moving average smoothing to prevent frame-to-frame ML Kit jitter
  private var smoothedX = 0f
  private var smoothedY = 0f
  private var hasSmoothedSeed = false
  private var lastFaceTimestampMs = 0L

  private val detector: FaceDetector by lazy {
    val options =
      FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .build()
    FaceDetection.getClient(options)
  }

  fun start(
    lifecycleOwner: LifecycleOwner,
    targetRotation: Int = Surface.ROTATION_90,
  ) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener(
      {
        try {
          val provider = providerFuture.get()
          cameraProvider = provider

          val imageAnalysis =
            ImageAnalysis.Builder()
              .setTargetRotation(targetRotation)
              .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
              .build()

          imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
            processImageProxy(imageProxy)
          }

          val cameraSelector =
            if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
              CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
              CameraSelector.DEFAULT_BACK_CAMERA
            }

          provider.unbindAll()
          provider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)
        } catch (e: Exception) {
          Log.e(TAG, "Failed to start CameraX face tracking", e)
          onError("No se pudo iniciar la cámara frontal.")
        }
      },
      ContextCompat.getMainExecutor(context),
    )
  }

  fun stop() {
    hasSmoothedSeed = false
    smoothedX = 0f
    smoothedY = 0f
    lastFaceTimestampMs = 0L
    try {
      cameraProvider?.unbindAll()
    } catch (e: Exception) {
      Log.w(TAG, "Error unbinding camera provider", e)
    }
    onResult(FaceTrackingResult(hasFace = false, gazeX = 0f, gazeY = 0f))
  }

  @OptIn(ExperimentalGetImage::class)
  private fun processImageProxy(imageProxy: ImageProxy) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
      imageProxy.close()
      return
    }

    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
    val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

    val uprightWidth: Float
    val uprightHeight: Float
    if (rotationDegrees == 90 || rotationDegrees == 270) {
      uprightWidth = imageProxy.height.toFloat()
      uprightHeight = imageProxy.width.toFloat()
    } else {
      uprightWidth = imageProxy.width.toFloat()
      uprightHeight = imageProxy.height.toFloat()
    }

    detector
      .process(inputImage)
      .addOnSuccessListener { faces ->
        val now = System.currentTimeMillis()
        val largestFace =
          faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }

        if (largestFace != null && uprightWidth > 0f && uprightHeight > 0f) {
          lastFaceTimestampMs = now
          val centerX = largestFace.boundingBox.centerX().toFloat()
          val centerY = largestFace.boundingBox.centerY().toFloat()

          // Mirror X for front-facing camera so pupils look naturally toward the user
          val rawX = ((0.5f - (centerX / uprightWidth)) * 2.3f).coerceIn(-1f, 1f)
          val rawY = (((centerY / uprightHeight) - 0.5f) * 2.1f).coerceIn(-1f, 1f)

          if (!hasSmoothedSeed) {
            smoothedX = rawX
            smoothedY = rawY
            hasSmoothedSeed = true
          } else {
            val alpha = 0.28f
            val nextX = smoothedX + (rawX - smoothedX) * alpha
            val nextY = smoothedY + (rawY - smoothedY) * alpha
            if (abs(nextX - smoothedX) > 0.008f) smoothedX = nextX
            if (abs(nextY - smoothedY) > 0.008f) smoothedY = nextY
          }

          onResult(FaceTrackingResult(hasFace = true, gazeX = smoothedX, gazeY = smoothedY))
        } else if (now - lastFaceTimestampMs > 550L) {
          hasSmoothedSeed = false
          onResult(FaceTrackingResult(hasFace = false, gazeX = 0f, gazeY = 0f))
        }
      }
      .addOnFailureListener { e ->
        Log.w(TAG, "ML Kit face detection failed on frame", e)
      }
      .addOnCompleteListener {
        imageProxy.close()
      }
  }

  fun release() {
    stop()
    detector.close()
    analysisExecutor.shutdown()
  }
}
