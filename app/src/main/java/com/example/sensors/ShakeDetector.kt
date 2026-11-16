package com.example.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class ShakeDetector(
  context: Context,
  private val onShake: () -> Unit,
) : SensorEventListener {

  private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
  private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

  private var lastShakeTimeMs = 0L
  private val shakeThresholdG = 2.25f
  private val cooldownMs = 1800L

  fun start() {
    accelerometer?.let {
      sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
    }
  }

  fun stop() {
    sensorManager?.unregisterListener(this)
  }

  override fun onSensorChanged(event: SensorEvent?) {
    val values = event?.values ?: return
    val x = values[0] / SensorManager.GRAVITY_EARTH
    val y = values[1] / SensorManager.GRAVITY_EARTH
    val z = values[2] / SensorManager.GRAVITY_EARTH
    val gForce = sqrt(x * x + y * y + z * z)

    if (gForce > shakeThresholdG) {
      val now = System.currentTimeMillis()
      if (now - lastShakeTimeMs > cooldownMs) {
        lastShakeTimeMs = now
        onShake()
      }
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
