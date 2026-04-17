package com.tapweb.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tapweb.R
import com.tapweb.databinding.ActivitySettingsBinding
import com.tapweb.databinding.DialogAboutBinding

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_THEME = "theme"

        const val LANG_SYSTEM = "system"
        const val LANG_ZH = "zh"
        const val LANG_EN = "en"

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        fun getLanguage(context: Context): String {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, LANG_SYSTEM) ?: LANG_SYSTEM
        }

        fun getThemeMode(context: Context): String {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        }
    }

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupLanguage()
        setupTheme()
        setupAbout()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupLanguage() {
        val current = getLanguage(this)
        when (current) {
            LANG_ZH -> binding.chipLangZh.isChecked = true
            LANG_EN -> binding.chipLangEn.isChecked = true
            else -> binding.chipLangSystem.isChecked = true
        }

        binding.chipGroupLanguage.setOnCheckedStateChangeListener { _, _ ->
            val lang = when {
                binding.chipLangZh.isChecked -> LANG_ZH
                binding.chipLangEn.isChecked -> LANG_EN
                else -> LANG_SYSTEM
            }

            if (lang != current) {
                saveLanguage(lang)
                applyLanguage(lang)
                Toast.makeText(this, getString(R.string.settings_restart_hint), Toast.LENGTH_LONG).show()
                recreate()
            }
        }
    }

    private fun setupTheme() {
        val current = getThemeMode(this)
        when (current) {
            THEME_LIGHT -> binding.chipThemeLight.isChecked = true
            THEME_DARK -> binding.chipThemeDark.isChecked = true
            else -> binding.chipThemeSystem.isChecked = true
        }

        binding.chipGroupTheme.setOnCheckedStateChangeListener { _, _ ->
            val mode = when {
                binding.chipThemeLight.isChecked -> THEME_LIGHT
                binding.chipThemeDark.isChecked -> THEME_DARK
                else -> THEME_SYSTEM
            }

            if (mode != current) {
                saveTheme(mode)
                applyTheme(mode)
            }
        }
    }

    private fun setupAbout() {
        binding.cardAbout.setOnClickListener { showAboutDialog() }
    }

    private fun showAboutDialog() {
        val dialogBinding = DialogAboutBinding.inflate(LayoutInflater.from(this))

        dialogBinding.tvGithub.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Joker-chichi/TapWeb")))
            } catch (_: Exception) {}
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun saveLanguage(lang: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, lang)
            .apply()
    }

    private fun saveTheme(mode: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, mode)
            .apply()
    }

    private fun applyLanguage(lang: String) {
        val localeList = when (lang) {
            LANG_ZH -> LocaleListCompat.forLanguageTags("zh")
            LANG_EN -> LocaleListCompat.forLanguageTags("en")
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    private fun applyTheme(mode: String) {
        val nightMode = when (mode) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}
