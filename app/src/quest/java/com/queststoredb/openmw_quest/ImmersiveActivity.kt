package com.queststoredb.openmw_quest

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.preference.PreferenceManager
import com.libopenmw.openmw.R
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Query
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.Vector3
import com.meta.spatial.isdk.IsdkCurvedPanel
import com.meta.spatial.isdk.IsdkDefaultCursorSystem
import com.meta.spatial.isdk.IsdkSystem
import com.meta.spatial.runtime.PanelSceneObject
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.toolkit.ActivityPanelRegistration
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.CylinderShapeOptions
import com.meta.spatial.toolkit.MediaPanelSettings
import com.meta.spatial.toolkit.Panel
import com.meta.spatial.toolkit.PanelInputOptions
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PixelDisplayOptions
import com.meta.spatial.toolkit.SceneObjectSystem
import com.meta.spatial.toolkit.SpatialActivityManager
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.createPanelEntity
import com.meta.spatial.vr.LocomotionSystem
import com.meta.spatial.vr.VRFeature
import constants.Constants
import permission.PermissionHelper
import ui.activity.GameActivity
import ui.activity.MainActivity
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin


class ImmersiveActivity : AppSystemActivity() {
    companion object {
        var isGameRunning = false
        const val REFRESH_RATE_HZ = 72
        private val PANEL_POSITION = Vector3(0f, -1f, -12f)
        private const val PANEL_RADIUS = 20f
        private const val PANEL_WIDTH = 12f
        private const val PANEL_RESOLUTION_WIDTH = 2400
        // 4:3 is the native aspect ratio of the game
        private const val PANEL_ASPECT_RATIO = 3/4f

        fun calculatePanelFieldOfView(): Float {
            val panelEntity = Query.where { has(Panel.id) }.eval().first {
                it.getComponent<Panel>().panelRegistrationId == R.id.panel
            }
            var panelRadius = 0f
            SpatialActivityManager.getAppSystemActivity().systemManager.findSystem<SceneObjectSystem>()
                .getSceneObject(panelEntity)!!.thenAccept { sceneObject ->
                    panelRadius = (sceneObject as PanelSceneObject)
                        .panelShapeConfig!!.radiusForCylinderOrSphere
                }
            val panelFovFromOrigin = panelEntity.getComponent<IsdkCurvedPanel>().fieldOfView
            val panelOffset = panelEntity.getComponent<Transform>().transform.t.z

            val halfPanelFov = Math.toRadians(panelFovFromOrigin / 2.0)
            val x = panelRadius * sin(halfPanelFov)
            val y = panelRadius * cos(halfPanelFov)
            val actualFov = Math.toDegrees(2.0 * atan2(abs(x), y + panelOffset)).toFloat()
            Log.d(this::class.simpleName, "calculated panel FoV: $actualFov")
            return actualFov
        }
    }

    override fun registerFeatures(): List<SpatialFeature> {
        return mutableListOf(VRFeature(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Permission requests by panel activities do not work
        PermissionHelper.getWriteExternalStoragePermission(this)
        scene.setPreferredDisplayRate(REFRESH_RATE_HZ.toFloat())
        scene.setReferenceSpace(ReferenceSpace.LOCAL)
        systemManager.findSystem<LocomotionSystem>().enableLocomotion(false)
        Entity.createPanelEntity(R.id.panel, Transform(Pose(PANEL_POSITION)))
        systemManager.registerEarlySystem(
            TouchControllersToGamepadSystem(
                systemManager.findSystem<IsdkSystem>(),
                systemManager.findSystem<IsdkDefaultCursorSystem>(),
                PANEL_RESOLUTION_WIDTH,
            )
        )
    }

    override fun registerPanels(): List<PanelRegistration> {
        return listOf(
            ActivityPanelRegistration(
                R.id.panel,
                classIdCreator = { ImmersiveMainActivity::class.java },
                settingsCreator = {
                    MediaPanelSettings(
                        shape = CylinderShapeOptions(
                            PANEL_RADIUS, PANEL_WIDTH, PANEL_WIDTH * PANEL_ASPECT_RATIO
                        ),
                        display = PixelDisplayOptions(
                            PANEL_RESOLUTION_WIDTH,
                            (PANEL_RESOLUTION_WIDTH * PANEL_ASPECT_RATIO).roundToInt()
                        ),
                        input = PanelInputOptions(0),
                    )
                },
                panelSetup = { panel, entity -> panel.addInputListener(
                    PanelPointerToMouseListener(
                        entity, panel.display!!, systemManager.findSystem<IsdkSystem>()
                    )
                ) }
            ),
        )
    }
}

class ImmersiveMainActivity: MainActivity() {

    override fun determineScaling(): Float {
        // Override default scaling: it shouldn't depend on render resolution
        return 2.3f
    }

    override fun getConfigDefaults(scaling: Float): Map<String, String> {
        // Override in-game defaults
        return super.getConfigDefaults(scaling) + mapOf(
            "viewing distance" to "7168.0",
            "maximum light distance" to "4096.0",
            "actors processing range" to "5376",
            "field of view" to ImmersiveActivity.calculatePanelFieldOfView().toString(),
            "target framerate" to ImmersiveActivity.REFRESH_RATE_HZ.toString(),
            "framerate limit" to ImmersiveActivity.REFRESH_RATE_HZ.toString(),
            // Journal font is too large at the default size 16
            "font size" to "14",
            "match sunlight to sun" to "true",
            // Lowest sensitivity of 0.2 increases camera control precision
            "camera sensitivity" to "0.2",
            //TODO: joystick bindings, enable toggle sneak script

            "settings x" to "0.13",
            "settings y" to "0.13",
            "settings w" to "0.73",
            "settings h" to "0.69",
            "stats x" to "0.065",
            "stats y" to "0.0",
            "stats w" to "0.41",
            "stats h" to "0.44",
            "spells x" to "0.675",
            "spells y" to "0.44",
            "spells w" to "0.325",
            "spells h" to "0.56",
            "map x" to "0.675",
            "map y" to "0.0",
            "map w" to "0.325",
            "map h" to "0.44",
            "inventory x" to "0",
            "inventory y" to "0.44",
            "inventory w" to "0.675",
            "inventory h" to "0.56",
            "inventory container x" to "0.0",
            "inventory container y" to "0.35",
            "inventory container w" to "0.5",
            "inventory container h" to "0.5",
            "inventory barter x" to "0.0",
            "inventory barter y" to "0.35",
            "inventory barter w" to "0.5",
            "inventory barter h" to "0.5",
            "inventory companion x" to "0.0",
            "inventory companion y" to "0.35",
            "inventory companion w" to "0.5",
            "inventory companion h" to "0.5",
            "dialogue x" to "0.2",
            "dialogue y" to "0.17",
            "dialogue w" to "0.65",
            "dialogue h" to "0.62",
            "container x" to "0.5",
            "container y" to "0.35",
            "container w" to "0.46",
            "container h" to "0.5",
            "barter x" to "0.5",
            "barter y" to "0.21",
            "barter w" to "0.46",
            "barter h" to "0.64",
            "companion x" to "0.5",
            "companion y" to "0.35",
            "companion w" to "0.46",
            "companion h" to "0.5"
        )
    }

    override fun runGame() {
        val intent = Intent(this, ImmersiveGameActivity::class.java)
        finish()
        this.startActivityForResult(intent, 1)
    }
}

class ImmersiveGameActivity : GameActivity() {
    override fun onPause() {
        super.onPause()
        ImmersiveActivity.isGameRunning = false
        if (isMouseShown() == 0) {
            // Pause the game
            onNativeKeyDown(KeyEvent.KEYCODE_ESCAPE)
            onNativeKeyUp(KeyEvent.KEYCODE_ESCAPE)
        }
    }

    override fun onResume() {
        super.onResume()
        ImmersiveActivity.isGameRunning = true
    }

    override fun showControls() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean(Constants.HIDE_CONTROLS, false)) {
            ImmersiveOsc().placeElements(layout)
        }
    }
}
