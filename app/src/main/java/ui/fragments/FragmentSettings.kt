/*
    Copyright (C) 2016 sandstranger
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

package ui.fragments

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.EditTextPreference
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.PreferenceGroup
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager

import com.codekidlabs.storagechooser.StorageChooser
import com.libopenmw.openmw.R
import file.GameInstaller

import ui.activity.ConfigureControls
import ui.activity.MainActivity
import ui.activity.ModsActivity
import ui.activity.SettingsActivity
import utils.MyApp
import java.util.*
import java.io.File
import constants.Constants
import permission.PermissionHelper
import ui.activity.MainActivity.Companion.TAG
import java.io.IOException

class FragmentSettings : PreferenceFragment(), OnSharedPreferenceChangeListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addPreferencesFromResource(R.xml.settings)
        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        findPreference("pref_controls").setOnPreferenceClickListener {
            val intent = Intent(activity, ConfigureControls::class.java)
            this.startActivity(intent)
            true
        }

        findPreference("pref_game_settings").setOnPreferenceClickListener {
            val intent = Intent(activity, SettingsActivity::class.java)
            this.startActivity(intent)
            true
        }

        findPreference("pref_mods").setOnPreferenceClickListener {
            // Just prevent crash here if data files are not selected
            val sharedPref = preferenceScreen.sharedPreferences
            val inst = GameInstaller(sharedPref.getString("game_files", "")!!)
            if (!inst.check()) {
            AlertDialog.Builder(getActivity())
                .setTitle(R.string.no_data_files_title)
                .setMessage(R.string.no_data_files_message)
                .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int -> }
                .show()

                false
            }
            else
            {
                val intent = Intent(activity, ModsActivity::class.java)
                this.startActivity(intent)
                true
            }
        }

        findPreference("game_files").setOnPreferenceClickListener {
            if (ContextCompat.checkSelfPermission(activity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                showError(R.string.permissions_error_title, R.string.permissions_error_message)
                PermissionHelper.getWriteExternalStoragePermission(activity)
            } else {
                val chooser = StorageChooser.Builder()
                    .withActivity(activity)
                    .withFragmentManager(fragmentManager)
                    .withMemoryBar(true)
                    .allowCustomPath(true)
                    .setType(StorageChooser.DIRECTORY_CHOOSER)
                    .build()

                chooser.show()

                chooser.setOnSelectListener { path -> setupData(path) }
            }
            true
        }

        updateVisiblePreferences()
    }

    private fun updateVisiblePreferences() {
        val sharedPref = preferenceScreen.sharedPreferences
        val isGameFilesSet = !sharedPref.getString("game_files", "").isNullOrEmpty()

        // List of keys that should always be enabled
        val whiteList = listOf("game_files", "pref_encoding")

        for (i in 0 until preferenceScreen.preferenceCount) {
            val pref = preferenceScreen.getPreference(i)
            if (pref is PreferenceGroup) {
                for (j in 0 until pref.preferenceCount) {
                    val singlePref = pref.getPreference(j)
                    if (!whiteList.contains(singlePref.key)) {
                        singlePref.isEnabled = isGameFilesSet
                    }
                }
            } else {
                if (!whiteList.contains(pref.key)) {
                    pref.isEnabled = isGameFilesSet
                }
            }
        }
    }

    /**
     * Checks the specified path for a valid morrowind installation, generates config files
     * and saves the path to shared prefs if it's valid.
     * If it isn't, an error is displayed to the user.
     */
    private fun setupData(path: String) {
        val sharedPref = preferenceScreen.sharedPreferences

        // create user dirs
        File(Constants.USER_CONFIG).mkdirs()
        File(Constants.USER_FILE_STORAGE + "/launcher/icons").mkdirs()
        File(Constants.USER_FILE_STORAGE + "/launcher/delta").mkdirs()
        File(Constants.USER_FILE_STORAGE + "/launcher/ModCollections").mkdirs()

        if (!File(Constants.USER_OPENMW_CFG).exists())
            File(Constants.USER_OPENMW_CFG).writeText("# This is the user openmw.cfg. Feel free to modify it as you wish.\n")
        generateOpenmwCfg()

        val currentPreset = sharedPref.getString("modCollection", "Default")!!
        if (!File(Constants.USER_FILE_STORAGE + "/launcher/ModCollections/" + currentPreset).exists())
            File(Constants.USER_FILE_STORAGE + "/launcher/ModCollections/" + currentPreset).writeText("# This is the user openmw.cfg. Feel free to modify it as you wish.\n")

        if (!File(Constants.USER_FILE_STORAGE + "/launcher/ModCollections/Default").exists())
            File(Constants.USER_FILE_STORAGE + "/launcher/ModCollections/Default").writeText("")

        // create icons files hint
        if (!File(Constants.USER_FILE_STORAGE + "/launcher/icons/paste custom icons here.txt").exists())
            File(Constants.USER_FILE_STORAGE + "/launcher/icons/paste custom icons here.txt").writeText(
                "attack.png \ninventory.png \njournal.png \njump.png \nkeyboard.png \nmouse.png \npause.png \npointer_arrow.png \nrun.png \nsave.png \nsneak.png \nthird_person.png \ntoggle_magic.png \ntoggle_weapon.png \ntoggle.png \nuse.png \nwait.png \nscroll_wheel.png \npostprocessing.png \nstats.png")

        // reset the setting so that it's erased on error instead of keeping
        // possibly stale value
        var gameFiles = ""

        val inst = GameInstaller(path)
        if (inst.check()) {
            inst.setNomedia()
            if (!inst.convertIni(sharedPref.getString("pref_encoding", R.string.pref_encoding_default.toString())!!)) {
                showError(R.string.data_error_title, R.string.ini_error_message)
            } else {
                gameFiles = path
            }
        } else {
            showError(R.string.data_error_title, R.string.data_error_message,
                    "https://omw.xyz.is/game.html")
        }

        with(sharedPref.edit()) {
            putString("game_files", gameFiles)
            if (sharedPref.getString("mods_dir", "")!! == "")
                putString("mods_dir", gameFiles + "/")
            apply()
        }

        updateVisiblePreferences()
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
            val gameDir = PreferenceManager.getDefaultSharedPreferences(activity).getString("game_files", "")
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
     * Shows an alert dialog displaying a specific error
     * @param title Title string resource
     * @param message Message string resource
     */
    private fun showError(title: Int, message: Int, url: String? = null) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int -> }

        if (url != null) {
            dialog.setNeutralButton(R.string.dialog_howto) { _, _ ->
                (activity as MainActivity).openUrl(url)
            }
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        for (i in 0 until preferenceScreen.preferenceCount) {
            val preference = preferenceScreen.getPreference(i)
            if (preference is PreferenceGroup) {
                for (j in 0 until preference.preferenceCount) {
                    val singlePref = preference.getPreference(j)
                    updatePreference(singlePref, singlePref.key)
                }
            } else {
                updatePreference(preference, preference.key)
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        updatePreference(findPreference(key), key)
    }

    private fun updatePreference(preference: Preference?, key: String) {
        if (preference == null)
            return
        if (preference is EditTextPreference) {
            if (key == "pref_uiScaling" && (preference.text == null || preference.text.isEmpty()))
                // Show "Auto (1.23)"
                preference.summary = MyApp.app.getString(R.string.uiScaling_auto)
                    .format(Locale.ROOT, MyApp.app.defaultScaling)
            else
                preference.summary = preference.text
        }
        // Show selected value as a summary for game_files
        if (key == "game_files") {
            preference.summary = preference.sharedPreferences.getString("game_files", "")
        }
    }
}
