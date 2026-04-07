package com.queststoredb.openmw_quest

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.preference.PreferenceManager
import com.libopenmw.openmw.BuildConfig
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.vr.VRFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ui.activity.MainActivity
import com.libopenmw.openmw.R
import com.meta.spatial.castinputforward.CastInputForwardFeature
import com.meta.spatial.isdk.IsdkDefaultCursorSystem
import com.meta.spatial.isdk.IsdkSystem
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.toolkit.ActivityPanelRegistration
import com.meta.spatial.toolkit.CylinderShapeOptions
import com.meta.spatial.toolkit.MediaPanelSettings
import com.meta.spatial.toolkit.PanelInputOptions
import com.meta.spatial.toolkit.PixelDisplayOptions
import com.meta.spatial.vr.LocomotionSystem
import constants.Constants
import kotlin.collections.mapOf
import permission.PermissionHelper
import ui.activity.GameActivity
import ui.controls.Osc


class ImmersiveActivity : AppSystemActivity() {
    private val activityScope = CoroutineScope(Dispatchers.Main)

    override fun registerFeatures(): List<SpatialFeature> {
        val features: MutableList<SpatialFeature> = mutableListOf(VRFeature(this))
        if (BuildConfig.DEBUG) {
            features.add(CastInputForwardFeature(this))
        }
        return features
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Permission requests by panel activities do not work
        PermissionHelper.getWriteExternalStoragePermission(this)
    }

    override fun onSceneReady() {
        super.onSceneReady()
        activityScope.launch {
            glXFManager.inflateGLXF(Uri.parse("scenes/scene.glxf"), keyName = "scene")
        }
        scene.setReferenceSpace(ReferenceSpace.LOCAL)
        systemManager.findSystem<LocomotionSystem>().enableLocomotion(false)

        val touchControllersToGamepadSystem = TouchControllersToGamepadSystem()
        systemManager.registerSystem(touchControllersToGamepadSystem)
        val isdkSystem = systemManager.findSystem<IsdkSystem>()
        // TODO: why off-panel events do not work?
        isdkSystem.registerObserver(touchControllersToGamepadSystem::translateThumbsticks)

        val cursorSystem = systemManager.findSystem<IsdkDefaultCursorSystem>()
        // TODO: hide pointers & lasers if SDL cursor is hidden
    }

    override fun registerPanels(): List<PanelRegistration> {
        return listOf(
            ActivityPanelRegistration(
                R.id.panel,
                classIdCreator = { ImmersiveMainActivity::class.java },
                settingsCreator = {
                    MediaPanelSettings(
                        shape = CylinderShapeOptions(20.0f, 4.0f, 3.0f),
                        display = PixelDisplayOptions(2400, 1800),
                        input = PanelInputOptions(ButtonBits.ButtonTriggerL or ButtonBits.ButtonTriggerR),
                    )
                },
                // TODO: replace with registerInteractableObserver
                panelSetup = { panel, entity -> panel.addInputListener(PanelPointerToMouseTranslator()) }
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
            "field of view" to "75.0",
            "target framerate" to "72",
            // Journal font is too large at the default size 16
            "font size" to "14",
            "match sunlight to sun" to "true",

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
            "spells w" to "0.32",
            "spells h" to "0.56",
            "map x" to "0.675",
            "map y" to "0.0",
            "map w" to "0.32",
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TouchControllersToGamepadSystem.initialize()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        PanelPointerToMouseTranslator.isEnabled = hasFocus
        TouchControllersToGamepadSystem.isEnabled = hasFocus
    }

    override fun onPause() {
        super.onPause()
        PanelPointerToMouseTranslator.isEnabled = false
        TouchControllersToGamepadSystem.isEnabled = false
    }

    override fun showControls() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean(Constants.HIDE_CONTROLS, false)) {
            ImmersiveOsc().placeElements(layout)
        }
    }
}
