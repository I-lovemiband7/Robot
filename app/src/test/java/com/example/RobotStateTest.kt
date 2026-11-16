package com.example

import com.example.ai.LooiEmotion
import com.example.ui.EyeTarget
import com.example.ui.RobotViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RobotStateTest {

  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `emotion parses from Gemini JSON keys accurately`() {
    assertEquals(LooiEmotion.FELIZ, LooiEmotion.fromApiValue("feliz"))
    assertEquals(LooiEmotion.SORPRENDIDO, LooiEmotion.fromApiValue("Sorprendido"))
    assertEquals(LooiEmotion.MOLESTO, LooiEmotion.fromApiValue("molesto"))
    assertEquals(LooiEmotion.DORMIDO, LooiEmotion.fromApiValue("dormido"))
    assertEquals(LooiEmotion.ESCUCHANDO, LooiEmotion.fromApiValue("escuchando"))
    assertEquals(LooiEmotion.NEUTRAL, LooiEmotion.fromApiValue("unknown"))
  }

  @Test
  fun `wake up, head pat, wink, and shake update robot state`() = runTest {
    val vm = RobotViewModel()
    assertFalse(vm.uiState.value.isAwake)

    vm.wakeUpRobot(micGranted = true, cameraGranted = true)
    assertTrue(vm.uiState.value.isAwake)
    assertEquals(LooiEmotion.FELIZ, vm.uiState.value.emotion)

    vm.onEyeTapped(EyeTarget.LEFT)
    advanceTimeBy(50)
    assertTrue(vm.uiState.value.winkLeft)
    advanceTimeBy(400)
    assertFalse(vm.uiState.value.winkLeft)

    vm.onShakeDetected()
    assertEquals(LooiEmotion.MAREADO, vm.uiState.value.emotion)

    vm.onHeadPat()
    assertEquals(LooiEmotion.FELIZ, vm.uiState.value.emotion)
  }

  @Test
  fun `face tracking updates gaze coordinates and recenters when lost`() = runTest {
    val vm = RobotViewModel()
    vm.wakeUpRobot(micGranted = true, cameraGranted = true)
    vm.toggleCameraTracking(cameraGranted = true)
    assertTrue(vm.uiState.value.isCameraTrackingEnabled)

    vm.onFaceTrackingUpdate(hasFace = true, gazeX = 0.65f, gazeY = -0.35f)
    assertTrue(vm.uiState.value.faceDetected)
    assertEquals(0.65f, vm.uiState.value.gazeTarget.x, 0.001f)
    assertEquals(-0.35f, vm.uiState.value.gazeTarget.y, 0.001f)

    vm.onFaceTrackingUpdate(hasFace = false, gazeX = 0f, gazeY = 0f)
    assertFalse(vm.uiState.value.faceDetected)
    advanceTimeBy(1500)
    assertEquals(0f, vm.uiState.value.gazeTarget.x, 0.001f)
    assertEquals(0f, vm.uiState.value.gazeTarget.y, 0.001f)
  }
}
