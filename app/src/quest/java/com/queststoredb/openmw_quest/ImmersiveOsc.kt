package com.queststoredb.openmw_quest

import android.view.Choreographer
import android.view.KeyEvent
import android.view.View
import android.widget.RelativeLayout
import com.libopenmw.openmw.R
import org.libsdl.app.SDLActivity
import ui.controls.CONTROL_DEFAULT_SIZE
import ui.controls.Osc
import ui.controls.OscCustomButton
import ui.controls.OscGestureButton
import ui.controls.OscImageButton
import ui.controls.OscVisibility
import ui.controls.TOP_BAR_SPACING


class ImmersiveOsc : Osc() {
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            showBasedOnState()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun placeElements(target: RelativeLayout) {
        btnTopToggle = OscCustomButton("Extra menu", "toggle.png", OscVisibility.ESSENTIAL,
            R.drawable.toggle, 0, 0, ::toggleTopControls)
        topButtons = arrayListOf(
            OscCustomButton("Keyboard", "keyboard.png", OscVisibility.ESSENTIAL,
                R.drawable.keyboard, TOP_BAR_SPACING * 1, 0, ::toggleKeyboard),
            OscImageButton("Post Processing", "postprocessing.png", OscVisibility.ESSENTIAL,
                R.drawable.postprocessing, TOP_BAR_SPACING * 2, 0, KeyEvent.KEYCODE_F2),
            OscGestureButton("Performance stats", "stats.png", OscVisibility.ESSENTIAL,
                R.drawable.stats, TOP_BAR_SPACING * 3, 0, CONTROL_DEFAULT_SIZE, false,
                KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_F4, KeyEvent.KEYCODE_F10, 0,
                KeyEvent.KEYCODE_F3),
        )
        for (button in topButtons)
            button.view?.tooltipText = button.uniqueId
        elements = ArrayList(arrayListOf(btnTopToggle) + topButtons)
        super.placeElements(target)
        // Parent call overrides visibility to NULL: revert back to ESSENTIAL
        for (element in elements)
            element.visibility = OscVisibility.ESSENTIAL

        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun showBasedOnState() {
        setVisibility(
            if (SDLActivity.isMouseShown() == 0) OscVisibility.NULL.v else OscVisibility.ESSENTIAL.v
        )
    }

    override fun setVisibility(newState: Int) {
        if (visibilityState != newState) {
            visibilityState = newState
            for (element in elements) {
                if (element.visibility != OscVisibility.NULL) {
                    if (newState and element.visibility.v == 0)
                        element.view?.visibility = View.GONE
                    else
                        element.view?.visibility = View.VISIBLE
                }
            }
        }
    }
}