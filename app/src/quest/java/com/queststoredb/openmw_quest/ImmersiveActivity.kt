package com.queststoredb.openmw_quest

import android.net.Uri
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
import com.meta.spatial.runtime.LayerConfig
import com.meta.spatial.runtime.PanelShapeType

class ImmersiveActivity : AppSystemActivity() {
    private val activityScope = CoroutineScope(Dispatchers.Main)

    override fun registerFeatures(): List<SpatialFeature> {
        return listOf(VRFeature(this))
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
                activityClass = MainActivity::class.java
            },
        )
    }
}
