package com.queststoredb.openmw_quest

import android.view.KeyEvent
import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.runtime.PointerEvent
import com.meta.spatial.runtime.SemanticType
import com.meta.spatial.toolkit.Controller
import org.libsdl.app.SDLActivity
import org.libsdl.app.SDLControllerManager


class ControllerToGamepadSystem : SystemBase() {
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
        private val SPATIAL_BUTTON_TO_ANDROID_KEY_EVENT_PAIRS = listOf(
            ButtonBits.ButtonA to KeyEvent.KEYCODE_BUTTON_A,
            ButtonBits.ButtonB to KeyEvent.KEYCODE_BUTTON_B,
            ButtonBits.ButtonX to KeyEvent.KEYCODE_BUTTON_X,
            ButtonBits.ButtonY to KeyEvent.KEYCODE_BUTTON_Y,
            ButtonBits.ButtonMenu to KeyEvent.KEYCODE_BUTTON_START,
            ButtonBits.ButtonThumbRU to KeyEvent.KEYCODE_BUTTON_SELECT,
            // TODO: fix non-working grip buttons. Conflicts with the Grabbable component?
            ButtonBits.ButtonSqueezeL to KeyEvent.KEYCODE_BUTTON_L2,
            ButtonBits.ButtonSqueezeR to KeyEvent.KEYCODE_BUTTON_R2,
            ButtonBits.ButtonThumbLClick to KeyEvent.KEYCODE_BUTTON_THUMBL,
            ButtonBits.ButtonThumbRClick to KeyEvent.KEYCODE_BUTTON_THUMBR,
        )

        fun initialize() {
            SDLControllerManager.nativeAddJoystick(
                VIRTUAL_DEVICE_ID, "Touch Controllers", "Quest", 0, 0, false, -1, 4, 0b1111, 0, 0
            )
        }

        val thumbstickTranslator = { event: PointerEvent ->
            if (isEnabled && event.semanticType == SemanticType.Scroll.id) {
                // TODO: turn sticks to mouse scrolls if there is a cursor
                val axis = if (event.pointerType == LEFT_HAND_POINTER_TYPE) 0 else 2
                SDLControllerManager.onNativeJoy(VIRTUAL_DEVICE_ID, axis, event.scrollInfo.x)
                SDLControllerManager.onNativeJoy(VIRTUAL_DEVICE_ID, axis + 1, -event.scrollInfo.y)
            }
            Unit
        }
    }

    override fun execute() {
        if (!isEnabled) return
        for (entity in controllerQuery.eval().filter { it.isLocal() }) {
            val controller = entity.getComponent<Controller>()
            if (controller.isActive) {
                val changedButtons = controller.changedButtons and trackedButtonMask
                if (changedButtons == 0)
                    continue
                var pressedThisFrame = changedButtons and controller.buttonState
                val releasedThisFrame = changedButtons and controller.buttonState.inv()

                // Spatial SDK triggers ThumbClick when pushing a thumbstick to sides.
                // Cancel it to avoid side effects.
                if (pressedThisFrame and ButtonBits.ButtonThumbLClick != 0
                    && controller.buttonState and ButtonBits.LeftThumbMotionMask != 0) {
                    pressedThisFrame = pressedThisFrame and ButtonBits.ButtonThumbLClick.inv()
                }
                if (pressedThisFrame and ButtonBits.ButtonThumbRClick != 0
                    && controller.buttonState and ButtonBits.RightThumbMotionMask != 0) {
                    pressedThisFrame = pressedThisFrame and ButtonBits.ButtonThumbRClick.inv()
                }

                for ((buttonBit, keyCode) in SPATIAL_BUTTON_TO_ANDROID_KEY_EVENT_PAIRS) {
                    if ((pressedThisFrame and buttonBit) != 0) {
                        sendGamepadKeyEvent(KeyEvent.ACTION_DOWN, keyCode)
                    } else if ((releasedThisFrame and buttonBit) != 0) {
                        sendGamepadKeyEvent(KeyEvent.ACTION_UP, keyCode)
                    }
                }
                // TODO: ButtonBits.ButtonThumbRTouch for mouse-like turns + up jump / down sneak
                // TODO: Snap turns + head-tracked vertical movement
                if (SDLActivity.isMouseShown() == 0) {
                    if ((pressedThisFrame and (ButtonBits.ButtonTriggerR)) != 0)
                        SDLActivity.sendMouseButton(1, SDL_MOUSE_BUTTON_LEFT_KEYCODE)
                    else if ((releasedThisFrame and (ButtonBits.ButtonTriggerR)) != 0)
                        SDLActivity.sendMouseButton(0, SDL_MOUSE_BUTTON_LEFT_KEYCODE)
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
}