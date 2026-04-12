package com.queststoredb.openmw_quest

import android.view.KeyEvent
import android.view.MotionEvent
import com.meta.spatial.core.Hand
import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.isdk.IsdkDefaultCursorSystem
import com.meta.spatial.isdk.IsdkSystem
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.runtime.PointerEvent
import com.meta.spatial.runtime.SemanticType
import com.meta.spatial.toolkit.Controller
import org.libsdl.app.SDLActivity
import org.libsdl.app.SDLControllerManager


class TouchControllersToGamepadSystem(
    val isdkSystem: IsdkSystem, val cursorSystem: IsdkDefaultCursorSystem
) : SystemBase() {
    private var isCursorEnabled = true
    private var shouldDisableCursorInput = false
    private val defaultCursorLaserWidth = cursorSystem.laserConfigWidth

    companion object {
        private const val VIRTUAL_DEVICE_ID = 1384510559  // random number
        private const val SDL_MOUSE_BUTTON_LEFT_KEYCODE = 1
        private val controllerQuery = Query.where { has(Controller.id) }
        private val trackedButtonMask = (
            ButtonBits.AllButtonClickMask
                or ButtonBits.ButtonMenu
                or ButtonBits.ButtonSystem
                or ButtonBits.LeftThumbMotionMask
                or ButtonBits.RightThumbMotionMask
            )
        private val anyTriggerMask = ButtonBits.ButtonTriggerL or ButtonBits.ButtonTriggerR
        private val TOUCH_CONTROLLER_TO_GAMEPAD_BUTTON_PAIRS = listOf(
            ButtonBits.ButtonMenu to KeyEvent.KEYCODE_BUTTON_START,
            ButtonBits.ButtonA to KeyEvent.KEYCODE_BUTTON_A,
            ButtonBits.ButtonB to KeyEvent.KEYCODE_BUTTON_B,
            ButtonBits.ButtonX to KeyEvent.KEYCODE_BUTTON_X,
            ButtonBits.ButtonY to KeyEvent.KEYCODE_BUTTON_Y,
            ButtonBits.ButtonThumbLClick to KeyEvent.KEYCODE_BUTTON_THUMBL,
            ButtonBits.ButtonThumbRClick to KeyEvent.KEYCODE_BUTTON_THUMBR,
            // Note: SDLJoystickHandler_API19 does not support passing L2 and R2 events
            ButtonBits.ButtonSqueezeL to KeyEvent.KEYCODE_BUTTON_L1,
            ButtonBits.ButtonSqueezeR to KeyEvent.KEYCODE_BUTTON_R1,
        )

        fun initialize() {
            SDLControllerManager.nativeAddJoystick(
                VIRTUAL_DEVICE_ID, "Touch Controllers", "Quest", 0, 0, false, -1, 4, 0b1111, 0, 0
            )
        }

    }

    init {
        isdkSystem.registerObserver(::translateThumbsticks)
    }

    override fun execute() {
        // Note: called every frame
        if (!ImmersiveActivity.isGameRunning)
            return
        translateButtons()
        setCursorAndLaserVisibility()
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
            if (controller.isActive) {
                val changedButtons = controller.changedButtons and trackedButtonMask
                if (changedButtons == 0)
                    continue
                var pressedButtons = changedButtons and controller.buttonState
                val releasedButtons = changedButtons and controller.buttonState.inv()

                // Spatial SDK emits ThumbClick when pushing a thumbstick to sides:
                // cancel it to avoid side effects.
                if ((pressedButtons and ButtonBits.ButtonThumbLClick) != 0
                    && (controller.buttonState and ButtonBits.LeftThumbMotionMask) != 0) {
                    pressedButtons = pressedButtons and ButtonBits.ButtonThumbLClick.inv()
                }
                if ((pressedButtons and ButtonBits.ButtonThumbRClick) != 0
                    && (controller.buttonState and ButtonBits.RightThumbMotionMask) != 0) {
                    pressedButtons = pressedButtons and ButtonBits.ButtonThumbRClick.inv()
                }

                // Translate generic gamepad buttons
                for ((buttonBit, keyCode) in TOUCH_CONTROLLER_TO_GAMEPAD_BUTTON_PAIRS) {
                    if ((pressedButtons and buttonBit) != 0) {
                        sendGamepadKeyEvent(KeyEvent.ACTION_DOWN, keyCode)
                    } else if ((releasedButtons and buttonBit) != 0) {
                        sendGamepadKeyEvent(KeyEvent.ACTION_UP, keyCode)
                    }
                }

                // Pass any trigger as the left mouse button
                if ((pressedButtons and anyTriggerMask) != 0)
                    SDLActivity.sendMouseButton(1, SDL_MOUSE_BUTTON_LEFT_KEYCODE)
                else if ((releasedButtons and anyTriggerMask) != 0
                    && (controller.buttonState and anyTriggerMask) == 0)
                    SDLActivity.sendMouseButton(0, SDL_MOUSE_BUTTON_LEFT_KEYCODE)

                // A workaround mouse scroll for broken Right Thumbstick scroll
                if (SDLActivity.isMouseShown() == 1) {
                    if ((pressedButtons and (ButtonBits.ButtonThumbLU or ButtonBits.ButtonThumbRU)) != 0)
                        SDLActivity.onNativeMouse(
                            0, MotionEvent.ACTION_SCROLL, 0.0f, 1.0f, false
                        )
                    else if ((pressedButtons and (ButtonBits.ButtonThumbLD or ButtonBits.ButtonThumbRD)) != 0)
                        SDLActivity.onNativeMouse(
                            0, MotionEvent.ACTION_SCROLL, 0.0f, -1.0f, false
                        )
                }
            }
        }
    }

    private fun translateThumbsticks(event: PointerEvent) {
        // Note: called only if controller points the panel
        // TODO: figure out a workaround for non-working off-panel pointer events
        // TODO: figure why thumbstick values are 0 when trigger is held
        if (!ImmersiveActivity.isGameRunning || event.semanticType != SemanticType.Scroll.id)
            return
        val axis = if (isdkSystem.getHandForPointerEvent(event) == Hand.LEFT) 0 else 2
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

    private fun sendGamepadKeyEvent(action: Int, keyCode: Int) {
        if (action == KeyEvent.ACTION_DOWN) {
            SDLControllerManager.onNativePadDown(VIRTUAL_DEVICE_ID, keyCode)
        } else {
            SDLControllerManager.onNativePadUp(VIRTUAL_DEVICE_ID, keyCode)
        }
    }
}