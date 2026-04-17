package com.tapweb.ui.addsite

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.tapweb.R
import com.tapweb.data.db.AppDatabase
import com.tapweb.data.db.WebSiteEntity
import com.tapweb.data.model.IconType
import com.tapweb.data.model.OpenMode
import com.tapweb.data.repository.WebSiteRepository
import com.tapweb.databinding.ActivityAddSiteBinding
import com.tapweb.shortcut.ShortcutHelper
import com.tapweb.util.DeviceHelper
import com.tapweb.util.FaviconFetcher
import com.tapweb.util.FaviconManager
import com.tapweb.util.UrlUtils
import kotlinx.coroutines.launch
import java.io.File

class AddSiteActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WEBSITE_ID = "website_id"
        private const val PREFS_NAME = "shortcut_prefs"
        private const val KEY_HINT_DISMISSED = "hint_dismissed"
    }

    private lateinit var binding: ActivityAddSiteBinding
    private lateinit var repository: WebSiteRepository
    private var editingId: Long = 0
    private var editingEntity: WebSiteEntity? = null
    private var fetchedFavicon: String? = null
    private var selectedIconType: IconType = IconType.AUTO
    private var customIconUri: Uri? = null
    private var existingCustomPath: String? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            customIconUri = it
            showIconPreviewFromUri(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddSiteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val dao = AppDatabase.getInstance(this).webSiteDao()
        repository = WebSiteRepository(dao)

        setupToolbar()
        setupUrlWatcher()
        setupIconSection()
        setupSave()

        editingId = intent.getLongExtra(EXTRA_WEBSITE_ID, 0)
        if (editingId > 0) loadForEditing()
    }

    private fun setupToolbar() {
        binding.toolbar.setTitle(
            if (editingId > 0) R.string.edit_site else R.string.add_site
        )
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupUrlWatcher() {
        binding.etUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) autoFetchMeta()
        }
    }

    private fun setupIconSection() {
        binding.chipGroupIcon.setOnCheckedStateChangeListener { _, _ ->
            when {
                binding.chipIconAuto.isChecked -> {
                    selectedIconType = IconType.AUTO
                    binding.btnPickIcon.visibility = View.GONE
                    refreshIconPreview()
                }
                binding.chipIconCustom.isChecked -> {
                    selectedIconType = IconType.CUSTOM
                    binding.btnPickIcon.visibility = View.VISIBLE
                    if (customIconUri != null) {
                        showIconPreviewFromUri(customIconUri!!)
                    } else if (existingCustomPath != null) {
                        showIconPreviewFromFile(existingCustomPath!!)
                    }
                    if (customIconUri == null && existingCustomPath == null) {
                        launchImagePicker()
                    }
                }
            }
        }

        binding.btnPickIcon.setOnClickListener { launchImagePicker() }
    }

    private fun launchImagePicker() {
        try {
            pickImageLauncher.launch(arrayOf("image/*"))
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.no_app_to_handle), Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshIconPreview() {
        val url = UrlUtils.normalize(binding.etUrl.text.toString())
        when (selectedIconType) {
            IconType.AUTO -> {
                if (fetchedFavicon != null) {
                    showIconPreviewFromUrl(fetchedFavicon)
                } else if (url.isNotBlank()) {
                    showIconPreviewFromUrl(FaviconManager.buildDefaultFaviconUrl(url))
                } else {
                    binding.ivIconPreview.setImageResource(R.drawable.ic_globe)
                }
            }
            IconType.DEFAULT_FAVICON -> {
                if (url.isNotBlank()) {
                    showIconPreviewFromUrl(FaviconManager.buildDefaultFaviconUrl(url))
                } else {
                    binding.ivIconPreview.setImageResource(R.drawable.ic_globe)
                }
            }
            IconType.CUSTOM -> {
                // Keep current state — either uri or file or globe
            }
        }
    }

    private fun showIconPreviewFromUrl(url: String?) {
        if (url.isNullOrBlank()) {
            binding.ivIconPreview.setImageResource(R.drawable.ic_globe)
            return
        }
        Glide.with(this)
            .load(url)
            .placeholder(R.drawable.ic_globe)
            .error(R.drawable.ic_globe)
            .circleCrop()
            .into(binding.ivIconPreview)
    }

    private fun showIconPreviewFromUri(uri: Uri) {
        Glide.with(this)
            .load(uri)
            .placeholder(R.drawable.ic_globe)
            .error(R.drawable.ic_globe)
            .circleCrop()
            .into(binding.ivIconPreview)
    }

    private fun showIconPreviewFromFile(path: String) {
        Glide.with(this)
            .load(File(path))
            .placeholder(R.drawable.ic_globe)
            .error(R.drawable.ic_globe)
            .circleCrop()
            .into(binding.ivIconPreview)
    }

    private fun autoFetchMeta() {
        val url = UrlUtils.normalize(binding.etUrl.text.toString())
        if (!UrlUtils.isValid(url)) return
        if (binding.etTitle.text.isNullOrBlank()) {
            lifecycleScope.launch {
                val meta = FaviconFetcher.fetch(url)
                meta.title?.let { binding.etTitle.setText(it) }
                fetchedFavicon = meta.faviconUrl
                if (selectedIconType == IconType.AUTO) {
                    refreshIconPreview()
                }
            }
        } else {
            lifecycleScope.launch {
                val meta = FaviconFetcher.fetch(url)
                fetchedFavicon = meta.faviconUrl
                if (selectedIconType == IconType.AUTO) {
                    refreshIconPreview()
                }
            }
        }
    }

    private fun loadForEditing() {
        lifecycleScope.launch {
            editingEntity = repository.getById(editingId) ?: return@launch
            val entity = editingEntity!!
            binding.etUrl.setText(entity.url)
            binding.etTitle.setText(entity.title)
            binding.switchShortcut.isChecked = entity.hasShortcut
            when (entity.openMode) {
                OpenMode.WEBVIEW -> binding.chipWebview.isChecked = true
                OpenMode.BROWSER -> binding.chipBrowser.isChecked = true
            }
            fetchedFavicon = entity.faviconUrl
            selectedIconType = entity.iconType
            existingCustomPath = entity.customIconPath

            when (entity.iconType) {
                IconType.AUTO, IconType.DEFAULT_FAVICON -> binding.chipIconAuto.isChecked = true
                IconType.CUSTOM -> {
                    binding.chipIconCustom.isChecked = true
                    binding.btnPickIcon.visibility = View.VISIBLE
                    if (entity.customIconPath != null) {
                        showIconPreviewFromFile(entity.customIconPath)
                    }
                }
            }

            binding.toolbar.setTitle(R.string.edit_site)
        }
    }

    private fun setupSave() {
        binding.btnSave.setOnClickListener { saveWebsite() }
    }

    private fun saveWebsite() {
        val rawUrl = binding.etUrl.text.toString().trim()
        val url = UrlUtils.normalize(rawUrl)

        if (!UrlUtils.isValid(url)) {
            binding.tilUrl.error = getString(R.string.error_invalid_url)
            return
        }
        binding.tilUrl.error = null

        val title = binding.etTitle.text.toString().trim().ifBlank {
            UrlUtils.extractDomain(url)
        }
        val openMode = if (binding.chipBrowser.isChecked) OpenMode.BROWSER else OpenMode.WEBVIEW
        val createShortcut = binding.switchShortcut.isChecked

        binding.btnSave.isEnabled = false
        binding.btnSave.text = getString(R.string.saving)

        lifecycleScope.launch {
            if (fetchedFavicon == null) {
                val meta = FaviconFetcher.fetch(url)
                fetchedFavicon = meta.faviconUrl
            }

            val resolvedFaviconUrl: String? = when (selectedIconType) {
                IconType.AUTO -> fetchedFavicon ?: FaviconManager.buildDefaultFaviconUrl(url)
                IconType.DEFAULT_FAVICON -> FaviconManager.buildDefaultFaviconUrl(url)
                IconType.CUSTOM -> fetchedFavicon
            }

            if (editingEntity != null) {
                // --- EDITING ---
                // Handle custom icon
                val customPath = when {
                    selectedIconType == IconType.CUSTOM && customIconUri != null -> {
                        FaviconManager.saveCustomIcon(
                            this@AddSiteActivity, editingEntity!!.id, customIconUri!!
                        ) ?: existingCustomPath
                    }
                    selectedIconType != IconType.CUSTOM -> {
                        existingCustomPath?.let {
                            FaviconManager.deleteCustomIcon(this@AddSiteActivity, editingEntity!!.id)
                        }
                        null
                    }
                    else -> existingCustomPath
                }

                val entity = editingEntity!!.copy(
                    url = url,
                    title = title,
                    faviconUrl = resolvedFaviconUrl,
                    openMode = openMode,
                    hasShortcut = createShortcut,
                    iconType = selectedIconType,
                    customIconPath = customPath
                )
                repository.update(entity)

                if (createShortcut) {
                    ShortcutHelper.update(this@AddSiteActivity, entity.id, title, url, resolvedFaviconUrl, customPath)
                } else if (editingEntity!!.hasShortcut) {
                    ShortcutHelper.remove(this@AddSiteActivity, entity.id)
                }
            } else {
                // --- NEW ---
                val id = repository.addWithIcon(
                    url, title, resolvedFaviconUrl, openMode,
                    selectedIconType, null
                )

                // Save custom icon with real id
                var savedCustomPath: String? = null
                if (selectedIconType == IconType.CUSTOM && customIconUri != null) {
                    val realPath = FaviconManager.saveCustomIcon(
                        this@AddSiteActivity, id, customIconUri!!
                    )
                    if (realPath != null) {
                        savedCustomPath = realPath
                        val saved = repository.getById(id)
                        if (saved != null) {
                            repository.update(saved.copy(customIconPath = realPath))
                        }
                    }
                }

                if (createShortcut) {
                    runOnUiThread {
                        Toast.makeText(
                            this@AddSiteActivity,
                            getString(R.string.saved),
                            Toast.LENGTH_SHORT
                        ).show()

                        if (DeviceHelper.isChineseRom() && !isShortcutHintDismissed()) {
                            showChineseRomHint(id, title, url, resolvedFaviconUrl, savedCustomPath)
                        } else {
                            ShortcutHelper.create(
                                this@AddSiteActivity, id, title, url, resolvedFaviconUrl,
                                savedCustomPath
                            )
                            Handler(Looper.getMainLooper())
                                .postDelayed({ finish() }, 1500)
                        }
                    }
                    return@launch
                }
            }

            runOnUiThread {
                Toast.makeText(
                    this@AddSiteActivity,
                    getString(R.string.saved),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun showChineseRomHint(
        id: Long, title: String, url: String,
        faviconUrl: String?, customIconPath: String?
    ) {
        if (isFinishing) return
        val settingsIntent = DeviceHelper.getShortcutPermissionIntent(this)

        val content = View.inflate(this, R.layout.dialog_shortcut_hint, null)
        val checkBox = content.findViewById<CheckBox>(R.id.checkbox_dont_show)

        AlertDialog.Builder(this)
            .setTitle(R.string.shortcut_hint_title)
            .setView(content)
            .setPositiveButton(R.string.shortcut_hint_open_settings) { _, _ ->
                if (checkBox.isChecked) markShortcutHintDismissed()
                ShortcutHelper.create(this, id, title, url, faviconUrl, customIconPath)
                if (settingsIntent != null) startActivity(settingsIntent)
                finish()
            }
            .setNegativeButton(R.string.shortcut_hint_later) { _, _ ->
                if (checkBox.isChecked) markShortcutHintDismissed()
                ShortcutHelper.create(this, id, title, url, faviconUrl, customIconPath)
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun isShortcutHintDismissed(): Boolean {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HINT_DISMISSED, false)
    }

    private fun markShortcutHintDismissed() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HINT_DISMISSED, true)
            .apply()
    }
}
