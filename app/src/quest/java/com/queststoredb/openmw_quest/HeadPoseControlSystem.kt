package com.queststoredb.openmw_quest

import com.meta.spatial.core.Entity
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.AvatarBody
import com.meta.spatial.toolkit.Transform
import org.libsdl.app.SDLActivity
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.withSign


/* Unusable: very nauseating due to input lag, game's unstable fps, and 2D panel distortion */
class HeadPoseControlSystem(val panel: Entity) : SystemBase() {
    private val originalPanelPosition = panel.getComponent<Transform>().transform.t.copy()
    private var previousHeadYaw = 0f
    private var previousHeadPitch = 0f
    private val gameMouseSensitivity = 0.25f

    override fun execute() {
        val playerBody = Query.where { has(AvatarBody.id) }.eval().filter {
            it.isLocal() && it.getComponent<AvatarBody>().isPlayerControlled
        }.first().getComponent<AvatarBody>()
        val headPose = playerBody.head.tryGetComponent<Transform>()?.transform ?: return

        val headYaw = getYaw(headPose.q)
        val headPitch = getPitch(headPose.q)

        if (ImmersiveActivity.isGameRunning && SDLActivity.isMouseShown() == 0) {
            val surface = SDLActivity.getSurface()
            SDLActivity.sendRelativeMouseMotion(
                ((headYaw - previousHeadYaw) * surface.width * gameMouseSensitivity).roundToInt(),
                ((headPitch - previousHeadPitch) * surface.height * gameMouseSensitivity).roundToInt(),
            )
        }

        val panelTransform = panel.getComponent<Transform>().transform
        panelTransform.q = removeRoll(headPose.q)
        panelTransform.t = headPose.t  + (panelTransform.q * originalPanelPosition)
        panel.setComponent(Transform(panelTransform))

        previousHeadYaw = headYaw
        previousHeadPitch = headPitch
    }

    private fun getPitch(q: Quaternion): Float {
        val sinp = 2.0f * (q.w * q.x - q.z * q.y)
        return if (abs(sinp) >= 1f) (Math.PI / 2).toFloat().withSign(sinp) else asin(sinp)
    }

    private fun getYaw(q: Quaternion): Float {
        val siny = 2.0f * (q.w * q.y + q.x * q.z)
        val cosy = 1.0f - 2.0f * (q.y * q.y + q.z * q.z)
        return atan2(siny, cosy)
    }

    fun removeRoll(q: Quaternion): Quaternion {
        return Quaternion.lookRotation(q * Vector3.Forward, Vector3.Up)
    }
}
