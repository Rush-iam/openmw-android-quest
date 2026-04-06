package com.queststoredb.openmw_quest

import com.meta.spatial.core.Entity
import com.meta.spatial.core.Vector2
import com.meta.spatial.runtime.HitInfo
import com.meta.spatial.runtime.InputListener
import com.meta.spatial.runtime.SceneObject
import com.meta.spatial.toolkit.AvatarAttachment
import org.libsdl.app.SDLActivity
import kotlin.math.roundToInt


class PanelPointerToMouseTranslator : InputListener {
    companion object {
        var isEnabled = false
        // TODO: autoswitch to the last button-pressed side
        private val inputSources = listOf("right_controller", "right_hand")
    }

    override fun onPointerEvent(
        receiver: SceneObject,
        hitInfo: HitInfo,
        type: Int,
        sourceOfInput: Entity,
        scrollInfo: Vector2,
        semanticType: Int,
    ) {
        if (!isEnabled
            || SDLActivity.isMouseShown() == 0
            || sourceOfInput.getComponent<AvatarAttachment>().type !in inputSources)
            return

        val surface = SDLActivity.getSurface()
        // TODO: filter cursor to make it smoother
        SDLActivity.sendRelativeMouseMotion(
            (hitInfo.textureCoordinate.x * surface.width).roundToInt() - SDLActivity.getMouseX(),
            (hitInfo.textureCoordinate.y * surface.height).roundToInt() - SDLActivity.getMouseY(),
        )
    }
}