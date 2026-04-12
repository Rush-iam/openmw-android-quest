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
import org.libsdl.app.SDLActivity
import kotlin.math.abs
import kotlin.math.roundToInt


/* The listener works only with the panel display events */
class PanelPointerToMouseTranslator(
    panelEntity: Entity, val panelDisplay: PanelDisplay, val isdkSystem: IsdkSystem
) : InputListener {
    private var lastClickedHand = Hand.RIGHT
    private var previousMouseX = 0f
    private var previousMouseY = 0f

    companion object {
        private val leftHandInputSources = listOf("left_controller", "left_hand")
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
            && (changed and buttonState and ButtonBits.AllButtonClickMask != 0)
        ) {
            // Switch the active hand for pointer move events
            // TODO: fix cursor jumps when switching hand causing shifts
            val sourceType = sourceOfInput.getComponent<AvatarAttachment>().type
            lastClickedHand = if (sourceType in leftHandInputSources) Hand.LEFT else Hand.RIGHT
        }
        return false
    }

    private fun translatePointerToMouse(event: PointerEvent) {
        if (isdkSystem.getHandForPointerEvent(event) != lastClickedHand)
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
            val deltaMouseX = abs(newMouseX - previousMouseX) / 0.02f
            val deltaMouseY = abs(newMouseY - previousMouseY) / 0.02f
            if (deltaMouseX < 1f && deltaMouseY < 1f) {
                newMouseX = previousMouseX * (1f - deltaMouseX) + newMouseX * deltaMouseX
                newMouseY = previousMouseY * (1f - deltaMouseY) + newMouseY * deltaMouseY
            }
            val surface = SDLActivity.getSurface()
            SDLActivity.sendRelativeMouseMotion(
                (newMouseX * surface.width).roundToInt() - SDLActivity.getMouseX(),
                (newMouseY * surface.height).roundToInt() - SDLActivity.getMouseY(),
            )
        }

        previousMouseX = newMouseX
        previousMouseY = newMouseY
    }

    override fun onClickDown(receiver: SceneObject, hitInfo: HitInfo, sourceOfInput: Entity) {
        handleOnClick(hitInfo, MotionEvent.ACTION_DOWN)
    }

    override fun onClick(receiver: SceneObject, hitInfo: HitInfo, sourceOfInput: Entity)  {
        handleOnClick(hitInfo, MotionEvent.ACTION_UP)
    }

    private fun handleOnClick(hitInfo: HitInfo, motionEvent: Int) {
        val eventTime = SystemClock.uptimeMillis()
        val x = hitInfo.textureCoordinate.x * panelDisplay.widthInPx
        val y = hitInfo.textureCoordinate.y * panelDisplay.heightInPx

        val clickEvent = MotionEvent.obtain(eventTime, eventTime, motionEvent, x, y, 0).apply {
            source = InputDevice.SOURCE_MOUSE
        }
        panelDisplay.dispatchEvent(clickEvent, isGenericEvent = false)
        clickEvent.recycle()
    }
}