package com.queststoredb.openmw_quest

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Hand
import com.meta.spatial.isdk.IsdkSystem
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.runtime.HitInfo
import com.meta.spatial.runtime.InputListener
import com.meta.spatial.runtime.PanelDisplay
import com.meta.spatial.runtime.PointerEvent
import com.meta.spatial.runtime.SceneObject
import com.meta.spatial.runtime.SemanticType
import com.meta.spatial.toolkit.AvatarAttachment
import com.queststoredb.openmw_quest.utils.smoothMotionJitter
import org.libsdl.app.SDLActivity
import kotlin.math.roundToInt


/* The listener works only with the panel display events */
class PanelPointerToMouseListener(
    panelEntity: Entity, val panelDisplay: PanelDisplay, val isdkSystem: IsdkSystem
) : InputListener {
    private var activeHand = Hand.RIGHT
    private var activeDownHand: Hand? = null
    private var previousMouseX = 0f
    private var previousMouseY = 0f

    companion object {
        private val leftHandInputSources = listOf("left_controller", "left_hand")
        private val anyTriggerOrGripButtonMask = (
            ButtonBits.ButtonTriggerL
                or ButtonBits.ButtonTriggerR
                or ButtonBits.ButtonSqueezeL
                or ButtonBits.ButtonSqueezeR
                // Hand-tracking "click" buttons are A and X
                or ButtonBits.ButtonA
                or ButtonBits.ButtonX
            )
        private const val SDL_MOUSE_BUTTON_LEFT_KEYCODE = 1
    }

    init {
        isdkSystem.registerInteractableObserver(panelEntity, ::translatePointerToMouse)
    }

    override fun onInput(
        receiver: SceneObject,
        hitInfo: HitInfo,
        sourceOfInput: Entity,
        changed: Int,
        buttonState: Int,
        downTime: Long,
    ): Boolean {
        if (
            ImmersiveActivity.isGameRunning &&
            SDLActivity.isMouseShown() == 1
            && (changed and buttonState and anyTriggerOrGripButtonMask != 0)
        ) {
            // Switch the active hand for mouse move events
            val sourceType = sourceOfInput.getComponent<AvatarAttachment>().type
            val newHand = if (sourceType in leftHandInputSources) Hand.LEFT else Hand.RIGHT
            if (activeHand != newHand && (activeDownHand == null || activeDownHand == newHand))
                activeHand = newHand
        }
        return false
    }

    private fun translatePointerToMouse(event: PointerEvent) {
        if (isdkSystem.getHandForPointerEvent(event) != activeHand)
            return

        var newMouseX = event.hitInfo.textureCoordinate.x
        var newMouseY = event.hitInfo.textureCoordinate.y

        if (!ImmersiveActivity.isGameRunning) {
            // Dispatch a standard Android event
            val eventTime = SystemClock.uptimeMillis()
            val hoverEvent = MotionEvent.obtain(
                eventTime,
                eventTime,
                if (event.semanticType == SemanticType.Select.id)
                    MotionEvent.ACTION_MOVE else MotionEvent.ACTION_HOVER_MOVE,
                newMouseX * panelDisplay.widthInPx,
                newMouseY * panelDisplay.heightInPx,
                0,
            ).apply { source = InputDevice.SOURCE_MOUSE }
            panelDisplay.dispatchEvent(hoverEvent, isGenericEvent = true)
            hoverEvent.recycle()

        } else if (ImmersiveActivity.isGameRunning && SDLActivity.isMouseShown() == 1) {
            // Dispatch a game mouse move event
            // Smooth out cursor jitter for more stable/readable tooltips
            val (smoothMouseX, smoothMouseY) = smoothMotionJitter(
                previousMouseX, previousMouseY, newMouseX, newMouseY, 0.02f
            )
            newMouseX = smoothMouseX
            newMouseY = smoothMouseY
            SDLActivity.sendRelativeMouseMotion(
                (newMouseX * panelDisplay.widthInPx).roundToInt() - SDLActivity.getMouseX(),
                (newMouseY * panelDisplay.heightInPx).roundToInt() - SDLActivity.getMouseY(),
            )
        }

        previousMouseX = newMouseX
        previousMouseY = newMouseY
    }

    override fun onClickDown(receiver: SceneObject, hitInfo: HitInfo, sourceOfInput: Entity) {
        handleOnClick(hitInfo, sourceOfInput, MotionEvent.ACTION_DOWN)
    }

    override fun onClick(receiver: SceneObject, hitInfo: HitInfo, sourceOfInput: Entity)  {
        handleOnClick(hitInfo, sourceOfInput, MotionEvent.ACTION_UP)
    }

    private fun handleOnClick(hitInfo: HitInfo, sourceOfInput: Entity, motionEvent: Int) {
        val sourceType = sourceOfInput.getComponent<AvatarAttachment>().type
        val hand = if (sourceType in leftHandInputSources) Hand.LEFT else Hand.RIGHT
        if (activeDownHand != null && hand != activeDownHand)
            // Skip other hand clicks while another hand holds a button
            return
        activeDownHand = if (motionEvent == MotionEvent.ACTION_DOWN) hand else null

        val eventTime = SystemClock.uptimeMillis()
        val x = hitInfo.textureCoordinate.x * panelDisplay.widthInPx
        val y = hitInfo.textureCoordinate.y * panelDisplay.heightInPx

        val clickEvent = MotionEvent.obtain(eventTime, eventTime, motionEvent, x, y, 0).apply {
            source = InputDevice.SOURCE_MOUSE
        }
        panelDisplay.dispatchEvent(clickEvent, isGenericEvent = false)
        clickEvent.recycle()

        if (ImmersiveActivity.isGameRunning) {
            if (motionEvent == MotionEvent.ACTION_DOWN) {
                if (SDLActivity.isMouseShown() == 1)
                    // Move cursor first, to avoid click jumps when switching hands
                    SDLActivity.sendRelativeMouseMotion(
                        x.roundToInt() - SDLActivity.getMouseX(),
                        y.roundToInt() - SDLActivity.getMouseY(),
                    )
                SDLActivity.sendMouseButton(1, SDL_MOUSE_BUTTON_LEFT_KEYCODE)
            } else
                SDLActivity.sendMouseButton(0, SDL_MOUSE_BUTTON_LEFT_KEYCODE)
        }
    }
}