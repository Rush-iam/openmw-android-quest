package com.queststoredb.openmw_quest

import android.view.KeyEvent
import android.view.MotionEvent
import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.runtime.PointerEvent
import com.meta.spatial.runtime.SemanticType
import com.meta.spatial.toolkit.Controller
import org.libsdl.app.SDLActivity
import org.libsdl.app.SDLControllerManager


class TouchControllersToGamepadSystem : SystemBase() {
    companion object {
        var isEnabled = false
        private const val VIRTUAL_DEVICE_ID = 1384510559  // random number
        private const val SDL_MOUSE_BUTTON_LEFT_KEYCODE = 1
        const val LEFT_HAND_POINTER_TYPE = 17
        const val RIGHT_HAND_POINTER_TYPE = 18
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

    override fun execute() {
        translateButtons()
    }

    private fun translateButtons() {
        if (!isEnabled)
            return

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
                if (SDLActivity.isMouseShown() == 0) {
                    if ((pressedButtons and anyTriggerMask) != 0)
                        SDLActivity.sendMouseButton(1, SDL_MOUSE_BUTTON_LEFT_KEYCODE)
                    else if ((releasedButtons and anyTriggerMask) != 0
                        && (controller.buttonState and anyTriggerMask) == 0)
                        SDLActivity.sendMouseButton(0, SDL_MOUSE_BUTTON_LEFT_KEYCODE)
                }

                // A workaround mouse scroll for broken Right Thumbstick scroll
                if (SDLActivity.isMouseShown() == 1) {
                    if ((pressedButtons and (ButtonBits.ButtonThumbRU)) != 0)
                        SDLActivity.onNativeMouse(
                            0, MotionEvent.ACTION_SCROLL, 0.0f, 1.0f, false
                        )
                    else if ((pressedButtons and (ButtonBits.ButtonThumbRD)) != 0)
                        SDLActivity.onNativeMouse(
                            0, MotionEvent.ACTION_SCROLL, 0.0f, -1.0f, false
                        )
                }
            }
        }
    }

    fun translateThumbsticks(event: PointerEvent) {
        if (!isEnabled || event.semanticType != SemanticType.Scroll.id)
            return
        if (SDLActivity.isMouseShown() == 1) {
            // Disable Left Thumbstick cursor control because there is a controller pointer.
            // Disable Right Thumbstick scrolling: does not work correctly for an unknown reason.
            return
        }

        val axis = if (event.pointerType == LEFT_HAND_POINTER_TYPE) 0 else 2
        SDLControllerManager.onNativeJoy(VIRTUAL_DEVICE_ID, axis, event.scrollInfo.x)
        SDLControllerManager.onNativeJoy(VIRTUAL_DEVICE_ID, axis + 1, -event.scrollInfo.y)
    }

    private fun sendGamepadKeyEvent(action: Int, keyCode: Int) {
        if (action == KeyEvent.ACTION_DOWN) {
            SDLControllerManager.onNativePadDown(VIRTUAL_DEVICE_ID, keyCode)
        } else {
            SDLControllerManager.onNativePadUp(VIRTUAL_DEVICE_ID, keyCode)
        }
    }
}