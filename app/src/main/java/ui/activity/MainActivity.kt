/*
    Copyright (C) 2015, 2016 sandstranger
    Copyright (C) 2018, 2019 Ilya Zhuravlev

    This file is part of OpenMW-Android.

    OpenMW-Android is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    OpenMW-Android is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with OpenMW-Android.  If not, see <https://www.gnu.org/licenses/>.
*/

package ui.activity

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.system.Os
import android.util.DisplayMetrics
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.libopenmw.openmw.BuildConfig
import com.libopenmw.openmw.R
import constants.Constants
import file.GameInstaller
import file.utils.CopyFilesFromAssets
import org.xmlpull.v1.XmlPullParser
import permission.PermissionHelper
import ui.fragments.FragmentSettings
import utils.MyApp
import utils.Utils.hideAndroidControls
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.io.bufferedReader
import kotlin.io.copyRecursively
import kotlin.io.readText
import kotlin.io.useLines
import kotlin.io.writeText
import kotlin.system.exitProcess
import kotlin.use

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MyApp.app.defaultScaling = determineScaling()

        Thread.setDefaultUncaughtExceptionHandler(CaptureCrash())

        PermissionHelper.getWriteExternalStoragePermission(this@MainActivity)
        setContentView(R.layout.main)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        populatePreferenceDefaults()

        val theme = prefs.getInt(getString(R.string.theme), 0)
        if(theme == 0) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        else if(theme == 1) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        fragmentManager.beginTransaction()
            .replace(R.id.content_frame, FragmentSettings()).commit()

        setSupportActionBar(findViewById(R.id.main_toolbar))

        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener { checkStartGame() }

        if (prefs.getString("bugsnag_consent", "")!! == "") {
            askBugsnagConsent()
        }

        // create user dirs
        File(Constants.USER_CONFIG).mkdirs()
        File(Constants.USER_FILE_STORAGE + "/launcher/icons").mkdirs()
        File(Constants.USER_FILE_STORAGE + "/launcher/delta").mkdirs()
        File(Constants.USER_FILE_STORAGE + "/launcher/ModCollections").mkdirs()

        if (!File(Constants.USER_OPENMW_CFG).exists())
            File(Constants.USER_OPENMW_CFG).writeText("# This is the user openmw.cfg. Feel free to modify it as you wish.\n")

        val currentPreset = PreferenceManager.getDefaultSharedPreferences(this).getString("modCollection", "Default")!!
        if (!File(Constants.USER_FILE_STORAGE + "/launcher/ModCollections/" + currentPreset).exists())
            File(Constants.USER_FILE_STORAGE + "/launcher/ModCollections/" + currentPreset).writeText("# This is the user openmw.cfg. Feel free to modify it as you wish.\n")

        if (!File(Constants.USER_FILE_STORAGE + "/launcher/ModCollections/Default").exists())
            File(Constants.USER_FILE_STORAGE + "/launcher/ModCollections/Default").writeText("")

        // create icons files hint
        if (!File(Constants.USER_FILE_STORAGE + "/launcher/icons/paste custom icons here.txt").exists())
            File(Constants.USER_FILE_STORAGE + "/launcher/icons/paste custom icons here.txt").writeText(
"attack.png \ninventory.png \njournal.png \njump.png \nkeyboard.png \nmouse.png \npause.png \npointer_arrow.png \nrun.png \nsave.png \nsneak.png \nthird_person.png \ntoggle_magic.png \ntoggle_weapon.png \ntoggle.png \nuse.png \nwait.png \nscroll_wheel.png \npostprocessing.png \nstats.png")

    }

    /**
     * Apply defaults without overwriting user's existing settings
     */
    private fun populatePreferenceDefaults() {
        val androidNs = "http://schemas.android.com/apk/res/android"
        for (field in R.xml::class.java.fields) {
            if (!field.name.startsWith("gs_") && field.name != "settings")
                continue
            try {
                this.resources.getXml(field.getInt(null)).use { parser ->
                    var eventType = parser.eventType
                    val editor = prefs.edit()
                    var changed = false

                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG) {
                            val key = parser.getAttributeValue(androidNs, "key")

                            if (key != null && !prefs.contains(key)) {
                                val resId = parser.getAttributeResourceValue(androidNs, "defaultValue", 0)
                                val rawValue = parser.getAttributeValue(androidNs, "defaultValue")
                                if (resId != 0 || rawValue != null) {
                                    when (parser.name) {
                                        "SwitchPreferenceCompat",
                                        "SwitchPreference",
                                        "CheckBoxPreference" -> {
                                            val value = if (resId != 0) resources.getBoolean(resId) else rawValue.toBoolean()
                                            editor.putBoolean(key, value)
                                        }
                                        "SeekBarPreference" -> {
                                            val value = if (resId != 0) resources.getInteger(resId) else rawValue?.toInt() ?: 0
                                            editor.putInt(key, value)
                                        }
                                        else -> {
                                            val value = if (resId != 0) resources.getString(resId) else rawValue
                                            editor.putString(key, value)
                                        }
                                    }
                                    changed = true
                                }
                            }
                        }
                        eventType = parser.next()
                    }
                    if (changed) editor.apply()
                }
            } catch (e: Exception) {
                Log.e("Settings", "Could not populate defaults for ${field.name}", e)
            }
        }
    }

    /**
     * Set new user consent and maybe restart the app
     * @param consent New value of bugsnag consent
     */
    @SuppressLint("ApplySharedPref")
    private fun setBugsnagConsent(consent: String) {
        val currentConsent = prefs.getString("bugsnag_consent", "")!!
        if (currentConsent == consent)
            return

        // We only need to force a restart if the user revokes their consent
        // If user grants consent, crashes won't be reported for 1 game session, but that's alright
        val needRestart = currentConsent == "true" && consent == "false"

        with (prefs.edit()) {
            putString("bugsnag_consent", consent)
            commit()
        }

        if (needRestart) {
            AlertDialog.Builder(this)
                .setOnDismissListener { System.exit(0) }
                .setTitle(R.string.bugsnag_consent_restart_title)
                .setMessage(R.string.bugsnag_consent_restart_message)
                .setPositiveButton(android.R.string.ok) { _, _ -> System.exit(0) }
                .show()
        }
    }

    /**
     * Opens the url in a web browser and gracefully handles the failure
     * @param url Url to open
     */
    fun openUrl(url: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(browserIntent)
        } catch (e: ActivityNotFoundException) {
            AlertDialog.Builder(this)
                .setTitle(R.string.no_browser_title)
                .setMessage(getString(R.string.no_browser_message, url))
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .show()
        }
    }

    /**
     * Asks the user if they want to automatically report crashes
     */
    private fun askBugsnagConsent() {
        // Do nothing for builds without api-key
        if (!MyApp.haveBugsnagApiKey)
            return

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.bugsnag_consent_title)
            .setMessage(R.string.bugsnag_consent_message)
            .setNeutralButton(R.string.bugsnag_policy) { _, _ -> /* set up below */ }
            .setNegativeButton(R.string.bugsnag_no) { _, _ -> setBugsnagConsent("false") }
            .setPositiveButton(R.string.bugsnag_yes) { _, _ -> setBugsnagConsent("true") }
            .create()

        dialog.show()

        // don't close the dialog when the privacy-policy button is clicked
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            openUrl("https://omw.xyz.is/privacy-policy.html")
        }
    }

    /**
     * Checks that the game is properly installed and if so, starts the game
     * - the game files must be selected
     * - there must be at least 1 activated mod (user can ignore this warning)
     */
    private fun checkStartGame() {
        // First, check that there are game files present
        val inst = GameInstaller(prefs.getString("game_files", "")!!)
        if (!inst.check()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.no_data_files_title)
                .setMessage(R.string.no_data_files_message)
                .setNeutralButton(R.string.dialog_howto) { _, _ ->
                    openUrl("https://omw.xyz.is/game.html")
                }
                .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int -> }
                .show()
            return
        }

        // If everything's alright, start the game
        startGame()
    }

    private fun deleteRecursive(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory)
            for (child in fileOrDirectory.listFiles())
                deleteRecursive(child)

        fileOrDirectory.delete()
    }

    private fun logConfig() {

    }

    private fun runGame() {
        logConfig()
        val intent = Intent(this@MainActivity,
            GameActivity::class.java)
        finish()

        this@MainActivity.startActivityForResult(intent, 1)
    }


    /**
     * Set up fixed screen resolution
     * This doesn't do anything unless the user chose to override screen resolution
     */
    private fun obtainFixedScreenResolution() {
        // Split resolution e.g 640x480 to width/height
        val customResolution = prefs.getString("pref_customResolution", "")
        val sep = customResolution!!.indexOf("x")
        if (sep > 0) {
            try {
                val x = Integer.parseInt(customResolution.substring(0, sep))
                val y = Integer.parseInt(customResolution.substring(sep + 1))

                resolutionX = x
                resolutionY = y
            } catch (e: NumberFormatException) {
                // user entered resolution wrong, just ignore it
            }
        }
    }

    /**
     * Generates openmw.cfg using values from openmw.base.cfg combined with mod manager settings
     */
    private fun generateOpenmwCfg() {
        // contents of openmw.base.cfg
        val base: String
        // contents of openmw.fallback.cfg
        val fallback: String

        // try to read the files
        try {
            base = File(Constants.OPENMW_BASE_CFG).readText()
            // TODO: support user custom options
            fallback = File(Constants.OPENMW_FALLBACK_CFG).readText()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read openmw.base.cfg or openmw.fallback.cfg", e)
            return
        }

        try {
            // generate final output.cfg
            var output = base + "\n" + fallback + "\n"

            // Add Data Files and default plugins when missing
            val gameDir = PreferenceManager.getDefaultSharedPreferences(this).getString("game_files", "")
            if (!File(Constants.USER_OPENMW_CFG).readText().contains(gameDir + "/Data Files")) {
                File(Constants.USER_OPENMW_CFG).writeText("data=" + gameDir + "/Data Files\ncontent=Morrowind.esm\ncontent=Tribunal.esm\ncontent=Bloodmoon.esm\nfallback-archive=Morrowind.bsa\nfallback-archive=Tribunal.bsa\nfallback-archive=Bloodmoon.bsa\n")
            }

            // write everything to openmw.cfg
            File(Constants.OPENMW_CFG).writeText(output)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to generate openmw.cfg.", e)
        }
    }

    /**
     * Determines required screen scaling based on resolution and physical size of the device
     */
    private fun determineScaling(): Float {
        // The idea is to stretch an old-school 1280x960 monitor to the device screen
        // Assume that 1x scaling corresponds to resolution of 1280x960
        // Assume that the longest side of the device corresponds to the 1280 side
        // Therefore scaling is calculated as longest size of the device divided by 1280
        // Note that it doesn't take into account DPI at all. Which is fine for now, but in future
        // we might want to add some bonus scaling to e.g. phone devices so that it's easier
        // to click things.

        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(dm)
        return maxOf(dm.heightPixels, dm.widthPixels) / 1280.0f
    }

    /**
     * Removes old and creates new files located in private application directories
     * (i.e. under getFilesDir(), or /data/data/.../files)
     */
    private fun reinstallStaticFiles() {
        // we store global "config" and "resources" under private files

        // wipe old version first
        removeStaticFiles()

        // copy in the new version
        val assetCopier = CopyFilesFromAssets(this)
        assetCopier.copy("libopenmw/resources", Constants.RESOURCES)
        assetCopier.copy("libopenmw/openmw", Constants.GLOBAL_CONFIG)

        // set version stamp
        File(Constants.VERSION_STAMP).writeText(BuildConfig.RANDOMIZER.toString())
    }

    /**
     * Removes global static files, these include resources and config
     */
    private fun removeStaticFiles() {
        // remove version stamp so that reinstallStaticFiles is called during game launch
        File(Constants.VERSION_STAMP).delete()

        deleteRecursive(File(Constants.GLOBAL_CONFIG))
        deleteRecursive(File(Constants.RESOURCES))
    }

    /**
     * Resets user config to default values by removing it
     */
    private fun removeUserConfig() {
        deleteRecursive(File(Constants.USER_CONFIG))
        File(Constants.USER_CONFIG).mkdirs()
        File(Constants.USER_OPENMW_CFG).writeText("# This is the user openmw.cfg. Feel free to modify it as you wish.\n")
    }

    /**
     * Reset user resource files to default
     */
    private fun removeResourceFiles() {
        reinstallStaticFiles()
        deleteRecursive(File(Constants.USER_FILE_STORAGE + "/resources/"))

        var src = File(Constants.RESOURCES)
        var dst = File(Constants.USER_FILE_STORAGE + "/resources/")
        dst.mkdirs()
        src.copyRecursively(dst, true) 
    }

    private fun configureDefaultsBin(args: Map<String, String>) {
        val defaults = File(Constants.DEFAULTS_BIN).readText()
        val decoded = String(android.util.Base64.decode(defaults, android.util.Base64.DEFAULT))
        val lines = decoded.lines().map {
            for ((k, v) in args) {
                if (it.startsWith("$k ="))
                    return@map "$k = $v"
            }
            it
        }
        val data = lines.joinToString("\n")

        val encoded = android.util.Base64.encodeToString(data.toByteArray(), android.util.Base64.NO_WRAP)
        File(Constants.DEFAULTS_BIN).writeText(encoded)
    }

    private fun writeSetting(category: String, name: String, value: String?) {
        if (value == null)
            throw NullPointerException("Missing value for setting $name")

        var lineList = mutableListOf<String>()
        var lineNumber = 0
        var categoryFound = 0
        var categoryLine = 0
        var nameFound = 0
        var nameLine = 0
        var currentCategory = ""

        File(Constants.USER_CONFIG + "/settings.cfg").useLines {
	    lines -> lines.forEach {
		lineList.add(it)
                if (it.contains("[") && it.contains("]")) currentCategory = it.replace("[", "").replace("]", "").replace(" ", "")
                if (currentCategory == category.replace(" ", "") && categoryFound == 0 ) { categoryLine = lineNumber; categoryFound = 1 } 
                if (currentCategory == category.replace(" ", "") && it.substringBefore("=").replace(" ", "") == name.replace(" ", ""))
		    { nameLine = lineNumber; nameFound = 1 }

                lineNumber++
	    }
	}

        if(nameFound == 1)
            lineList.set(nameLine, name + " = " + value)
        if(categoryFound == 1 && nameFound == 0)
            lineList.add(categoryLine + 1, name + " = " + value)
        if(categoryFound == 0 && nameFound == 0) 
            lineList.add(lineNumber, "\n" + "[" + category + "]" + "\n" + name + " = " + value)

        var output = ""
        lineList.forEach { output += it + "\n" }

        File(Constants.USER_CONFIG + "/settings.cfg").writeText(output)
    }


    private fun writeUserSettings() {
        File(Constants.USER_CONFIG + "/settings.cfg").createNewFile()

	// Write resolution to prevent issues if incorect one is set
        val displayInCutoutArea = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_display_cutout_area", true)
	val dm = DisplayMetrics()
	windowManager.defaultDisplay.getRealMetrics(dm)
	val orientation = this.getResources().getConfiguration().orientation
	var displayWidth = 0
	var displayHeight = 0
        val cutout = if (android.os.Build.VERSION.SDK_INT < 29) null else windowManager.defaultDisplay.getCutout()

        if (cutout != null) {
	    if (orientation == Configuration.ORIENTATION_PORTRAIT)
	    {
		    displayWidth = if(resolutionX == 0) dm.heightPixels else resolutionX
		    displayHeight = if(resolutionY == 0) dm.widthPixels else resolutionY
                    if( displayInCutoutArea == false && resolutionX == 0) {
                        val cutoutRectTop = cutout.getBoundingRectTop()
                        val cutoutRectBottom = cutout.getBoundingRectBottom()
                        displayWidth = dm.heightPixels - maxOf(cutoutRectTop.bottom, cutoutRectBottom.bottom - cutoutRectBottom.top)
                    }
	    }
	    else
	    {
		    displayWidth = if(resolutionX == 0) dm.widthPixels else resolutionX
		    displayHeight = if(resolutionY == 0) dm.heightPixels else resolutionY
                    if( displayInCutoutArea == false && resolutionY == 0) {
                        val cutoutRectLeft = cutout.getBoundingRectLeft()
                        val cutoutRectRight = cutout.getBoundingRectRight()
                        displayWidth = dm.widthPixels - maxOf(cutoutRectLeft.right, cutoutRectRight.right - cutoutRectRight.left)
                    }
	    }

	    writeSetting("Video", "resolution x", displayWidth.toString())
	    writeSetting("Video", "resolution y", displayHeight.toString())
        }
        else {
            if (resolutionX != 0 && resolutionY != 0) {
                writeSetting("Video", "resolution x", displayWidth.toString())
	        writeSetting("Video", "resolution y", displayHeight.toString())
            }
            else {
                if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                    writeSetting("Video", "resolution x", dm.heightPixels.toString())
	            writeSetting("Video", "resolution y", dm.widthPixels.toString())
                }
                else {
                    writeSetting("Video", "resolution x", dm.widthPixels.toString())
	            writeSetting("Video", "resolution y", dm.heightPixels.toString())
                }
            }
        }

        // Game Mechanics
        writeSetting("Game", "uncapped damage fatigue", prefs.getBoolean("gs_uncapped_damage_fatigue", false).toString())
        writeSetting("Game", "rebalance soul gem values", prefs.getBoolean("gs_soulgem_values_rebalance", false).toString())
        writeSetting("Game", "followers attack on sight", prefs.getBoolean("gs_followers_defend_immediately", false).toString())
        writeSetting("Game", "barter disposition change is permanent", prefs.getBoolean("gs_permanent_barter_disposition_changes", false).toString())
        writeSetting("Game", "NPCs avoid collisions", prefs.getBoolean("gs_npc_avoid_collision", false).toString())
        writeSetting("Game", "only appropriate ammunition bypasses resistance", prefs.getBoolean("gs_only_weapon_bs", false).toString())
        writeSetting("Game", "normalise race speed", prefs.getBoolean("gs_racial_variation_in_speed_fix", false).toString())
        writeSetting("Game", "swim upward correction", prefs.getBoolean("gs_swim_upward_correction", false).toString())
        writeSetting("Game", "can loot during death animation", prefs.getBoolean("gs_can_loot_during_death_animation", false).toString())
        writeSetting("Game", "enchanted weapons are magical", prefs.getBoolean("gs_enchanted_weapons_are_magical", false).toString())
        writeSetting("Game", "classic reflected absorb spells behavior", prefs.getBoolean("gs_classic_reflected_absorb_spells_behavior", false).toString())
        writeSetting("Game", "always allow stealing from knocked out actors", prefs.getBoolean("gs_always_allow_stealing_from_knocked_out_actors", false).toString())
        writeSetting("Game", "allow actors to follow over water surface", prefs.getBoolean("gs_always_allow_npc_to_follow_over_water_surface", false).toString())
        writeSetting("Game", "strength influences hand to hand", prefs.getString("gs_factor_strength_into_hand-to-hand_combat", null))

        // Visuals terrain
        writeSetting("Terrain", "object paging min size", prefs.getString("gs_object_paging_min_size", null))
        writeSetting("Terrain", "distant terrain", prefs.getBoolean("gs_distant_land", false).toString())
        writeSetting("Terrain", "object paging active grid", prefs.getBoolean("gs_active_grid_object_paging", false).toString())

        // Visuals graphics
        writeSetting("Video", "framerate limit", prefs.getString("gs_framerate_limit", null))
        writeSetting("Camera", "reverse z", prefs.getBoolean("gs_reverse_z", false).toString())

        // Visuals shaders
        writeSetting("Shaders", "auto use object normal maps", prefs.getBoolean("gs_auto_use_object_normal_maps", false).toString())
        writeSetting("Shaders", "auto use object specular maps", prefs.getBoolean("gs_auto_use_object_specular_maps", false).toString())
        writeSetting("Shaders", "auto use terrain normal maps", prefs.getBoolean("gs_auto_use_terrain_normal_maps", false).toString())
        writeSetting("Shaders", "auto use terrain specular maps", prefs.getBoolean("gs_auto_use_terrain_specular_maps", false).toString())
        writeSetting("Shaders", "apply lighting to environment maps", prefs.getBoolean("gs_bump_map_local_lighting", false).toString())
        writeSetting("Shaders", "weather particle occlusion", prefs.getBoolean("gs_weather_particle_occlusion", false).toString())

        // Visuals fog
        writeSetting("Fog", "radial fog", prefs.getBoolean("gs_radial_fog", false).toString())
        writeSetting("Fog", "exponential fog", prefs.getBoolean("gs_exponential_fog", false).toString())
        writeSetting("Fog", "sky blending", prefs.getBoolean("gs_sky_blending", false).toString())

        // Visuals PostProcessing
        writeSetting("Shaders", "soft particles", prefs.getBoolean("gs_soft_particles", false).toString())
        writeSetting("Post Processing", "transparent postpass", prefs.getBoolean("gs_transparent_postpass", false).toString())

        // Visuals Shadows
        if(File(Constants.USER_FILE_STORAGE + "/launcher/extensions.log").exists() &&
            File(Constants.USER_FILE_STORAGE + "/launcher/extensions.log").readText().contains("GL_EXT_depth_clamp")) {

            writeSetting("Shadows", "enable shadows",
                (prefs.getBoolean("gs_object_shadows", false) || prefs.getBoolean("gs_terrain_shadows", false) ||
                        prefs.getBoolean("gs_actor_shadows", false) || prefs.getBoolean("gs_player_shadows", false)).toString())

            writeSetting("Shadows", "object shadows", prefs.getBoolean("gs_object_shadows", false).toString())
            writeSetting("Shadows", "terrain shadows", prefs.getBoolean("gs_terrain_shadows", false).toString())
            writeSetting("Shadows", "actor shadows", prefs.getBoolean("gs_actor_shadows", false).toString())
            writeSetting("Shadows", "player shadows", prefs.getBoolean("gs_player_shadows", false).toString())
            writeSetting("Shadows", "indoor shadows", prefs.getBoolean("gs_indoor_shadows", false).toString())
            writeSetting("Shadows", "shadow map resolution", prefs.getString("gs_shadow_map_resolution", null))
            writeSetting("Shadows", "compute scene bounds", prefs.getString("gs_shadow_computation_method", null))
            writeSetting("Shadows", "maximum shadow map distance", prefs.getString("gs_shadows_distance", null))
            writeSetting("Shadows", "shadow fade start", prefs.getString("gs_shadows_fade_start", null))
            writeSetting("Shadows", "percentage closer filtering", prefs.getString("gs_shadows_pcf", null))
        }

        // Animations
        writeSetting("Game", "player movement ignores animation", prefs.getBoolean("gs_player_movement_ignores_animation", false).toString())
        writeSetting("Game", "use magic item animations", prefs.getBoolean("gs_use_magic_item_animation", false).toString())
        writeSetting("Game", "use additional anim sources", prefs.getBoolean("gs_use_additional_animation_sources", false).toString())
        writeSetting("Game", "weapon sheathing", prefs.getBoolean("gs_weapon_sheating", false).toString())
        writeSetting("Game", "shield sheathing", prefs.getBoolean("gs_shield_sheating", false).toString())
        writeSetting("Game", "graphic herbalism", prefs.getBoolean("gs_enable_graphics_herbalism", false).toString())
        writeSetting("Game", "smooth movement", prefs.getBoolean("gs_smooth_movement", false).toString())
        writeSetting("Game", "turn to movement direction", prefs.getBoolean("gs_turn_to_movement_direction", false).toString())
        writeSetting("Game", "smooth animation transitions", prefs.getBoolean("gs_smooth_animation_transitions", false).toString())

        // Interface
        writeSetting("GUI", "controller menus", prefs.getBoolean("gs_controller_menus", false).toString())
        writeSetting("GUI", "controller tooltips", prefs.getBoolean("gs_controller_tooltips", false).toString())
        writeSetting("Game", "show owned", prefs.getString("gs_show_owned", null))
        writeSetting("Game", "show effect duration", prefs.getBoolean("gs_show_effect_duration", false).toString())
        writeSetting("Game", "show enchant chance", prefs.getBoolean("gs_show_enchant_chance", false).toString())
        writeSetting("Game", "show melee info", prefs.getBoolean("gs_show_melee_info", false).toString())
        writeSetting("Game", "show projectile damage", prefs.getBoolean("gs_show_projectile_damage", false).toString())
        writeSetting("GUI", "color topic enable", prefs.getBoolean("gs_change_dialogue_topic_color", false).toString())
        writeSetting("GUI", "stretch menu background", prefs.getBoolean("gs_stretch_menu_background", false).toString())
        writeSetting("Map", "allow zooming", prefs.getBoolean("gs_can_zoom_on_maps", false).toString())

        // Bug Fixes
        writeSetting("Game", "prevent merchant equipping", prefs.getBoolean("gs_merchant_equipping_fix", false).toString())
        writeSetting("Game", "trainers training skills based on base skill", prefs.getBoolean("gs_trainers_bs", false).toString())

        // Miscellaneous
        writeSetting("Saves", "timeplayed", prefs.getBoolean("gs_add_time_to_saves", false).toString())
        writeSetting("Saves", "max quicksaves", prefs.getString("gs_maximum_quicksaves", null))

        // Engine Settings
        writeSetting("Groundcover", "enabled", prefs.getBoolean("gs_groundcover_handling", false).toString())
        writeSetting("Navigator", "enable", prefs.getBoolean("gs_build_navmesh", false).toString())
        writeSetting("Navigator", "write to navmeshdb", prefs.getBoolean("gs_write_navmesh", false).toString())
        writeSetting("Navigator", "async nav mesh updater threads", prefs.getString("gs_navmesh_threads", null))
        writeSetting("Physics", "async num threads", prefs.getString("gs_physics_threads", null))
        writeSetting("Cells", "preload num threads", prefs.getString("gs_preload_threads", null))
    }

    private fun startGame() {
        // Get scaling factor from config; if invalid or not provided, generate one
        var scaling = 0f

        try {
            scaling = prefs.getString("pref_uiScaling", "")!!.toFloat()
        } catch (e: NumberFormatException) {
            // Reset the invalid setting
            with(prefs.edit()) {
                putString("pref_uiScaling", "")
                apply()
            }
        }

        // If scaling didn't get set, determine it automatically
        if (scaling == 0f) {
            scaling = MyApp.app.defaultScaling
        }

        val dialog = ProgressDialog.show(
            this, "", "Preparing for launch...", true)

        val activity = this

        // hide the controls so that ScreenResolutionHelper can get the right resolution
        hideAndroidControls(this)

        val th = Thread {
            try {
                // Only reinstall static files if they are of a mismatched version
                try {
                    val stamp = File(Constants.VERSION_STAMP).readText().trim()
                    if (stamp.toInt() != BuildConfig.RANDOMIZER) {
                        //reinstallStaticFiles()
                        removeResourceFiles()
                    }
                } catch (e: Exception) {
                    //reinstallStaticFiles()
                    removeResourceFiles()
                }

                val inst = GameInstaller(prefs.getString("game_files", "")!!)

                // Regenerate the fallback file in case user edits their Morrowind.ini
                inst.convertIni(prefs.getString("pref_encoding",
                    R.string.pref_encoding_default.toString()
                )!!)

                generateOpenmwCfg()

                // openmw.cfg: data, resources
                val gameVFS = "\"" + Constants.USER_FILE_STORAGE + "resources/vfs-mw\"\n"
                val ktxFolder = if (prefs.getBoolean("pref_loadKTX", false) == true) "data=\"" + Constants.USER_FILE_STORAGE + "launcher/ktx\"\n" else ""

                file.Writer.write(Constants.OPENMW_CFG, "resources", Constants.RESOURCES)
                file.Writer.write(Constants.OPENMW_CFG, "data", gameVFS+ktxFolder/* + "data=\"" + inst.findDataFiles() + "\""*/)

                file.Writer.write(Constants.OPENMW_CFG, "encoding", prefs.getString("pref_encoding", R.string.pref_encoding_default.toString())!!)

                var src = File(Constants.RESOURCES)
                var dst = File(Constants.USER_FILE_STORAGE + "/resources/")
                val resourcesDirCreated :Boolean = dst.mkdirs()

                if(resourcesDirCreated)
                    src.copyRecursively(dst, false) 

                obtainFixedScreenResolution()
               
                configureDefaultsBin(getConfigDefaults(scaling))

		writeUserSettings()

                runOnUiThread {
                    obtainFixedScreenResolution()
                    dialog.hide()
                    runGame()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write config files.", e)
            }
        }
        th.start()
    }

    protected open fun getConfigDefaults(scaling: Float): Map<String, String> {
        return mapOf(
            "scaling factor" to "%.2f".format(Locale.ROOT, scaling),
            // android-specific defaults
            "viewing distance" to "2048.0",
            "camera sensitivity" to "0.4",
            // and a bunch of windows positioning
            "stats x" to "0.0",
            "stats y" to "0.0",
            "stats w" to "0.375",
            "stats h" to "0.4275",
            "spells x" to "0.625",
            "spells y" to "0.5725",
            "spells w" to "0.375",
            "spells h" to "0.4275",
            "map x" to "0.625",
            "map y" to "0.0",
            "map w" to "0.375",
            "map h" to "0.5725",
            "inventory y" to "0.4275",
            "inventory w" to "0.6225",
            "inventory h" to "0.5725",
            "inventory container x" to "0.0",
            "inventory container y" to "0.4275",
            "inventory container w" to "0.6225",
            "inventory container h" to "0.5725",
            "inventory barter x" to "0.0",
            "inventory barter y" to "0.4275",
            "inventory barter w" to "0.6225",
            "inventory barter h" to "0.5725",
            "inventory companion x" to "0.0",
            "inventory companion y" to "0.4275",
            "inventory companion w" to "0.6225",
            "inventory companion h" to "0.5725",
            "dialogue x" to "0.095",
            "dialogue y" to "0.095",
            "dialogue w" to "0.810",
            "dialogue h" to "0.890",
            "console x" to "0.0",
            "console y" to "0.0",
            "container x" to "0.25",
            "container y" to "0.0",
            "container w" to "0.75",
            "container h" to "0.375",
            "barter x" to "0.25",
            "barter y" to "0.0",
            "barter w" to "0.75",
            "barter h" to "0.375",
            "companion x" to "0.25",
            "companion y" to "0.0",
            "companion w" to "0.75",
            "companion h" to "0.375"
        )
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_settings, menu)

        if (!MyApp.haveBugsnagApiKey)
            menu.findItem(R.id.action_bugsnag_consent).setVisible(false)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reset_user_config -> {
                AlertDialog.Builder(this)
                    .setTitle("Confirmation")
                    .setMessage("Are you sure you want to reset user configuration?")
                    .setPositiveButton("Yes") { _, _ ->
                        removeUserConfig()
                        Toast.makeText(this, getString(R.string.user_config_was_reset), Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("No", null)
                    .show()
                true
            }

            R.id.action_reset_user_resources -> {
                AlertDialog.Builder(this)
                    .setTitle("Confirmation")
                    .setMessage("Are you sure you want to reset user resources?")
                    .setPositiveButton("Yes") { _, _ ->
                        removeStaticFiles()
                        removeResourceFiles()
                        Toast.makeText(this, getString(R.string.user_resources_was_reset), Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("No", null)
                    .show()
                true
            }

            R.id.action_theme_system -> {
                with (prefs.edit()) {
                    putInt(getString(R.string.theme), 0)
                    apply()
                }

                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

                Toast.makeText(this, "Theme set to system", Toast.LENGTH_SHORT).show()
                true
            }

            R.id.action_theme_light -> {
                with (prefs.edit()) {
                    putInt(getString(R.string.theme), 1)
                    apply()
                }

                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

                Toast.makeText(this, "Theme set to light", Toast.LENGTH_SHORT).show()
                true
            }

            R.id.action_theme_dark -> {
                with (prefs.edit()) {
                    putInt(getString(R.string.theme), 2)
                    apply()
                }

                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

                Toast.makeText(this, "Theme set to dark", Toast.LENGTH_SHORT).show()
                true
            }

            R.id.action_generate_navmesh -> {
                Os.setenv("OPENMW_GENERATE_NAVMESH_CACHE", "1", true)
                checkStartGame()
                true
            }

            R.id.action_about -> {
                val text = assets.open("libopenmw/3rdparty-licenses.txt")
                    .bufferedReader()
                    .use { it.readText() }

                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.about_title))
                    .setMessage(text)
                    .show()

                true
            }

            R.id.action_bugsnag_consent -> {
                askBugsnagConsent()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val TAG = "OpenMW-Launcher"

        var resolutionX = 0
        var resolutionY = 0
    }

    class CaptureCrash : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            // Save crash log to a file
            saveCrashLog(throwable)

            // Terminate the app or perform any other necessary action
            android.os.Process.killProcess(android.os.Process.myPid());
            exitProcess(1)
        }

        private fun saveCrashLog(throwable: Throwable) {
            try {

                val logFile = File(Constants.USER_CONFIG + "/" + "crash.log")
                if (!logFile.exists()) {
                    logFile.createNewFile()
                }

                FileWriter(logFile, true).use { writer ->
                    writer.append("Device: ${Build.MODEL} (API ${Build.VERSION.SDK_INT})\n")
                    writer.append("${getCurrentDateTime()}:\t")
                    printFullStackTrace(throwable,PrintWriter(writer))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun printFullStackTrace(throwable: Throwable, printWriter: PrintWriter) {
            printWriter.println(throwable.toString())
            throwable.stackTrace.forEach { element ->
                printWriter.print("\t $element \n")
            }
            val cause = throwable.cause
            if (cause != null) {
                printWriter.print("Caused by:\t")
                printFullStackTrace(cause, printWriter)
            }
            printWriter.print("\n")
        }

        fun getCurrentDateTime(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return sdf.format(Date())
        }
    }
}
