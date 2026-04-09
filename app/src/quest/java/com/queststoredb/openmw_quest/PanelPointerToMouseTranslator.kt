package com.queststoredb.openmw_quest

import com.meta.spatial.core.Entity
import com.meta.spatial.core.Hand
import com.meta.spatial.core.Vector2
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.runtime.HitInfo
import com.meta.spatial.runtime.InputListener
import com.meta.spatial.runtime.SceneObject
import com.meta.spatial.toolkit.AvatarAttachment
import org.libsdl.app.SDLActivity
import kotlin.math.roundToInt


class PanelPointerToMouseTranslator : InputListener {
    private var lastClickedHand = Hand.RIGHT

    companion object {
        var isEnabled = false
        private val leftHandInputSources = listOf("left_controller", "left_hand")
        private val rightHandInputSources = listOf("right_controller", "right_hand")
    }

    override fun onInput(
        receiver: SceneObject,
        hitInfo: HitInfo,
        sourceOfInput: Entity,
        changed: Int,
        buttonState: Int,
        downTime: Long,
    ): Boolean {
        if (!isEnabled)
            return false
        if (
            SDLActivity.isMouseShown() == 1
            && (changed and buttonState and ButtonBits.AllButtonClickMask != 0)
        )
            lastClickedHand =
                if (sourceOfInput.getComponent<AvatarAttachment>().type in leftHandInputSources)
                    Hand.LEFT else Hand.RIGHT
        return true
    }

    override fun onPointerEvent(
        receiver: SceneObject,
        hitInfo: HitInfo,
        type: Int,
        sourceOfInput: Entity,
        scrollInfo: Vector2,
        semanticType: Int,
    ) {
        if (!isEnabled || SDLActivity.isMouseShown() == 0)
            return
        val sourceType = sourceOfInput.getComponent<AvatarAttachment>().type
        if (
            lastClickedHand == Hand.LEFT && sourceType in rightHandInputSources
            || lastClickedHand == Hand.RIGHT && sourceType in leftHandInputSources
        )
            return

        val surface = SDLActivity.getSurface()
        // TODO: filter cursor to make it smoother
        SDLActivity.sendRelativeMouseMotion(
            (hitInfo.textureCoordinate.x * surface.width).roundToInt() - SDLActivity.getMouseX(),
            (hitInfo.textureCoordinate.y * surface.height).roundToInt() - SDLActivity.getMouseY(),
        )
    }
}