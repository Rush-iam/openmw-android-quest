package com.queststoredb.openmw_quest

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import com.meta.spatial.core.Hand
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.isdk.IsdkDefaultCursorSystem
import com.meta.spatial.isdk.IsdkSystem
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.runtime.PointerEvent
import com.meta.spatial.runtime.SemanticType
import com.meta.spatial.toolkit.AvatarBody
import com.meta.spatial.toolkit.Controller
import com.meta.spatial.toolkit.ControllerType
import com.meta.spatial.toolkit.Transform
import com.queststoredb.openmw_quest.utils.smoothMotionJitter
import org.libsdl.app.SDLActivity
import org.libsdl.app.SDLControllerManager
import kotlin.math.pow
import kotlin.math.roundToInt


class TouchControllersToGamepadSystem(
    val isdkSystem: IsdkSystem, val cursorSystem: IsdkDefaultCursorSystem, val screenWidth: Int
) : SystemBase() {
    private var isVirtualGamepadInitialized = false
    private var isCursorEnabled = true
    private var shouldDisableCursorInput = false
    private val defaultCursorLaserWidth = cursorSystem.laserConfigWidth
    private var previousControllerPose = Pose()
    private var controllerMotionActivateTime: Long? = null
    private val controllerMotionActivator = ButtonBits.ButtonThumbRTouch
    private val snapTurnEnable = false
    private val snapTurnSensitivity = 0.5f // depends on mouse sensitivity

    companion object {
        private const val VIRTUAL_DEVICE_ID = 1384510559  // random number
        private const val CONTROLLER_MOTION_SENSITIVITY = 4f // depends on mouse sensitivity
        private const val CONTROLLER_MOTION_START_DELAY_MS = 50
        private val controllerQuery = Query.where { has(Controller.id) }
        private val trackedButtonMask = (
            ButtonBits.AllButtonClickMask
                or ButtonBits.ButtonMenu
                or ButtonBits.ButtonSystem
                or ButtonBits.LeftThumbMotionMask
                or ButtonBits.RightThumbMotionMask
            )
        private val TOUCH_CONTROLLER_TO_GAMEPAD_BUTTON_PAIRS = listOf(
            ButtonBits.ButtonMenu to KeyEvent.KEYCODE_BUTTON_START,
            ButtonBits.ButtonA to KeyEvent.KEYCODE_BUTTON_A,
            ButtonBits.ButtonB to KeyEvent.KEYCODE_BUTTON_B,
            ButtonBits.ButtonX to KeyEvent.KEYCODE_BUTTON_X,
            ButtonBits.ButtonY to KeyEvent.KEYCODE_BUTTON_Y,
            ButtonBits.ButtonThumbLClick to KeyEvent.KEYCODE_BUTTON_THUMBL,
            ButtonBits.ButtonThumbRClick to KeyEvent.KEYCODE_BUTTON_THUMBR,
            ButtonBits.ButtonThumbRU to KeyEvent.KEYCODE_DPAD_UP,
            ButtonBits.ButtonThumbRR to KeyEvent.KEYCODE_DPAD_RIGHT,
            ButtonBits.ButtonThumbRD to KeyEvent.KEYCODE_DPAD_DOWN,
            ButtonBits.ButtonThumbRL to KeyEvent.KEYCODE_DPAD_LEFT,
            // Note: SDLJoystickHandler_API19 does not support passing L2 and R2 events
            ButtonBits.ButtonSqueezeL to KeyEvent.KEYCODE_BUTTON_L1,
            ButtonBits.ButtonSqueezeR to KeyEvent.KEYCODE_BUTTON_R1,
        )
    }

    init {
        isdkSystem.registerObserver(::translateThumbsticks)
    }

    fun initializeVirtualGamepad() {
        if (!isVirtualGamepadInitialized) {
            SDLControllerManager.nativeAddJoystick(
                VIRTUAL_DEVICE_ID, "Touch Controllers", "Quest", 0, 0, false, -1, 4, 0b1111, 0, 0
            )
            isVirtualGamepadInitialized = true
        }
    }

    override fun execute() {
        // Note: called every frame
        if (!ImmersiveActivity.isGameRunning)
            return
        if (!isVirtualGamepadInitialized)
            initializeVirtualGamepad()

        setCursorAndLaserVisibility()
        translateButtons()
        translateControllerMotion()
    }

    private fun setCursorAndLaserVisibility() {
        // Hide controller pointers and lasers if mouse cursor is hidden
        if (shouldDisableCursorInput) {
            shouldDisableCursorInput = false
            cursorSystem.enableInput(false)
        }
        if (isCursorEnabled && SDLActivity.isMouseShown() == 0) {
            isCursorEnabled = false
            cursorSystem.laserConfigWidth = 0.0f
            // Defer input disabling by one frame to redraw the laser as 0-width first
            shouldDisableCursorInput = true
        } else if (!isCursorEnabled && SDLActivity.isMouseShown() == 1) {
            isCursorEnabled = true
            cursorSystem.enableInput(true)
            cursorSystem.laserConfigWidth = defaultCursorLaserWidth
        }
    }

    private fun translateButtons() {
        for (entity in controllerQuery.eval().filter { it.isLocal() }) {
            val controller = entity.getComponent<Controller>()
            if (controller.isActive && controller.type == ControllerType.CONTROLLER) {
                val changedButtons = controller.changedButtons and trackedButtonMask
                if (changedButtons == 0)
                    continue

                // Spatial SDK emits ThumbClick when pushing a thumbstick to sides:
                // cancel it to avoid side effects.
                var pressedButtons = changedButtons and controller.buttonState
                if (controller.isPressed(ButtonBits.ButtonThumbLClick)
                    && controller.isDown(ButtonBits.LeftThumbMotionMask)) {
                    pressedButtons = pressedButtons and ButtonBits.ButtonThumbLClick.inv()
                }
                if (controller.isPressed(ButtonBits.ButtonThumbRClick)
                    && controller.isDown(ButtonBits.RightThumbMotionMask)) {
                    pressedButtons = pressedButtons and ButtonBits.ButtonThumbRClick.inv()
                }
                // Translate generic gamepad buttons
                for ((buttonBit, keyCode) in TOUCH_CONTROLLER_TO_GAMEPAD_BUTTON_PAIRS) {
                    if ((pressedButtons and buttonBit) != 0) {
                        sendGamepadKeyEvent(KeyEvent.ACTION_DOWN, keyCode)
                    } else if (controller.isReleased(buttonBit)) {
                        sendGamepadKeyEvent(KeyEvent.ACTION_UP, keyCode)
                    }
                }

                // A workaround mouse scroll for broken Right Thumbstick scroll
                if (SDLActivity.isMouseShown() == 1) {
                    if (controller.isPressed(ButtonBits.ButtonThumbLU or ButtonBits.ButtonThumbRU))
                        SDLActivity.onNativeMouse(
                            0, MotionEvent.ACTION_SCROLL, 0.0f, 1.0f, false
                        )
                    else if (controller.isPressed(ButtonBits.ButtonThumbLD or ButtonBits.ButtonThumbRD))
                        SDLActivity.onNativeMouse(
                            0, MotionEvent.ACTION_SCROLL, 0.0f, -1.0f, false
                        )
                }

                // Right stick snap turn
                if (
                    snapTurnEnable
                    && SDLActivity.isMouseShown() == 0
                    && controller.isPressed(ButtonBits.ButtonThumbRL or ButtonBits.ButtonThumbRR)
                ) {
                    val direction = if (controller.isPressed(ButtonBits.ButtonThumbRR)) 1 else -1
                    SDLActivity.sendRelativeMouseMotion(
                        (direction * screenWidth * snapTurnSensitivity).roundToInt(), 0
                    )
                }
            }
        }
    }

    private fun sendGamepadKeyEvent(action: Int, keyCode: Int) {
        if (action == KeyEvent.ACTION_DOWN) {
            SDLControllerManager.onNativePadDown(VIRTUAL_DEVICE_ID, keyCode)
        } else {
            SDLControllerManager.onNativePadUp(VIRTUAL_DEVICE_ID, keyCode)
        }
    }

    private fun translateThumbsticks(event: PointerEvent) {
        // Note: called only if controller points the panel
        // TODO: figure out a workaround for non-working off-panel pointer events
        // TODO: figure why thumbstick values are 0 when trigger is held
        if (!ImmersiveActivity.isGameRunning || event.semanticType != SemanticType.Scroll.id)
            return
        if (!isVirtualGamepadInitialized)
            initializeVirtualGamepad()
        val hand = isdkSystem.getHandForPointerEvent(event)
        if (hand == Hand.RIGHT)
            // Ignore the Right Thumbstick: the camera is controlled by the controller motion
            return

        val axis = if (hand == Hand.LEFT) 0 else 2
        if (SDLActivity.isMouseShown() == 0) {
            SDLControllerManager.onNativeJoy(VIRTUAL_DEVICE_ID, axis, event.scrollInfo.x)
            SDLControllerManager.onNativeJoy(VIRTUAL_DEVICE_ID, axis + 1, -event.scrollInfo.y)
        } else {
            // Disable Left Thumbstick cursor control because there is a controller pointer.
            // Disable Right Thumbstick scrolling: does not work correctly for an unknown reason.
            SDLControllerManager.onNativeJoy(VIRTUAL_DEVICE_ID, axis, 0.0f)
            SDLControllerManager.onNativeJoy(VIRTUAL_DEVICE_ID, axis + 1, 0.0f)
        }
    }

    private fun translateControllerMotion() {
        // Control the camera using controller motion if a finger is touching the right thumbstick
        val playerRightHand = Query.where { has(AvatarBody.id) }.eval().first {
            it.isLocal() && it.getComponent<AvatarBody>().isPlayerControlled
        }.getComponent<AvatarBody>().rightHand
        val controller = playerRightHand.tryGetComponent<Controller>() ?: return
        val controllerPose = playerRightHand.getComponent<Transform>().transform
        if (controller.isActive && SDLActivity.isMouseShown() == 0) {
            if (controller.isPressed(controllerMotionActivator)) {
                // Wait before activating tracking, as it starts annoyingly too early
                controllerMotionActivateTime = (
                    SystemClock.uptimeMillis() + CONTROLLER_MOTION_START_DELAY_MS
                )
            } else if (controller.isReleased(controllerMotionActivator)) {
                controllerMotionActivateTime = null
            } else if (
                controllerMotionActivateTime != null
                && controller.isDown(controllerMotionActivator)
                && SystemClock.uptimeMillis() > controllerMotionActivateTime!!
            ) {
                val previousControllerPoseWithoutRoll = Quaternion.lookRotation(
                    previousControllerPose.q * Vector3.Forward, Vector3.Up
                )
                val deltaWorld = controllerPose.t - previousControllerPose.t
                val delta = previousControllerPoseWithoutRoll.inverse() * deltaWorld
                var (deltaX, deltaY) = smoothMotionJitter(0f, 0f, delta.x, delta.y, 0.0002f)
                // Accelerate horizontal motion
                deltaX *= 1.5f.pow(1 + deltaX)
                // Normalize to the screen pixel density based on the resolution width
                SDLActivity.sendRelativeMouseMotion(
                    (screenWidth * deltaX * CONTROLLER_MOTION_SENSITIVITY).roundToInt(),
                    -(screenWidth * deltaY * CONTROLLER_MOTION_SENSITIVITY).roundToInt()
                )
            }
        }
        previousControllerPose = controllerPose
    }
}