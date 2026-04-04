package com.queststoredb.openmw_quest

import android.net.Uri
import android.os.Bundle
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
import com.meta.spatial.runtime.LayerConfig
import com.meta.spatial.runtime.PanelShapeType
import kotlin.collections.mapOf
import permission.PermissionHelper

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
        // App hangs if there is a permission request by a panel activity onCreate
        PermissionHelper.getWriteExternalStoragePermission(this@ImmersiveActivity)
    }

    override fun onSceneReady() {
        super.onSceneReady()
        scene.setReferenceSpace(ReferenceSpace.LOCAL)
        activityScope
            .launch {
                glXFManager.inflateGLXF(Uri.parse("scenes/scene.glxf"), keyName = "scene")
            }
    }

    override fun registerPanels(): List<PanelRegistration> {
        return listOf(
            PanelRegistration(R.id.panel) {
                config {
                    width = 4.0f
                    height = 3.0f
                    layoutWidthInPx = 2400
                    layoutHeightInPx = 1800
                    panelShapeType = PanelShapeType.CYLINDER
                    radiusForCylinderOrSphere = 20.0f
                    layerConfig = LayerConfig()
                    unlit = true
                }
                activityClass = ImmersiveMainActivity::class.java
            },
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
}