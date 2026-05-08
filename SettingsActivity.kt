package com.snes19xx.einklauncher

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.snes19xx.einklauncher.databinding.ActivitySettingsBinding
import java.io.ByteArrayOutputStream
import java.util.Calendar

// Activity for configuring launcher settings and preferences
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

    companion object {
        const val PREFS_NAME           = "folio_prefs"
        const val KEY_WIDGET_MODE      = "widget_mode"
        const val KEY_LAYOUT_MODE      = "layout_mode"
        const val KEY_SHOW_PDFS        = "show_pdfs"
        const val KEY_SHOW_PHOTO       = "show_photo"
        const val KEY_WALLPAPER        = "header_wallpaper_data"
        const val KEY_CLOCK_24H        = "clock_24h"
        const val KEY_SHOW_WEATHER     = "show_weather"
        const val KEY_INK_SAVING       = "ink_saving"
        const val KEY_COMPACT_DOCK     = "compact_dock"
        const val KEY_DOCK_LABELS      = "show_dock_labels"
        const val KEY_DOCKED_APPS      = "docked_apps"
        const val KEY_GREETING_NAME    = "greeting_name"
        const val KEY_GOOGLE_CLIENT_ID = "google_client_id"
        const val KEY_THEME_MODE       = "theme_mode"
    }

    private var activeTab = 0

    private val wallpaperCropLauncher = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful && result.uriContent != null) {
            handleWallpaperResult(result.uriContent!!)
        }
    }

    private val widgetPhotoCropLauncher = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful && result.uriContent != null) {
            handleWidgetPhotoResult(result.uriContent!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        window.setWindowAnimations(0)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        setupTabs()
        setupCloseButton()
        setupWallpaper()
        setupWidgetChoice()
        setupSwitches()
        setupThemeSelector()
        setupDockEditor()
        setupDefaultLauncherButton()
        setupCustomInputs()
        setupAboutLink()
    }

    private fun applyTheme() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val mode = prefs.getString(KEY_THEME_MODE, "auto") ?: "auto"
        
        when (mode) {
            "always_light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "always_dark"  -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                if (hour in 6..18) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                }
            }
        }
    }

    private fun setupTabs() {
        binding.tabAppearance.setOnClickListener { switchTab(0) }
        binding.tabDock.setOnClickListener       { switchTab(1) }
        binding.tabSystem.setOnClickListener     { switchTab(2) }
    }

    private fun switchTab(index: Int) {
        if (index == activeTab) return
        activeTab = index

        val inkColor   = resources.getColor(R.color.ink,   null)
        val mutedColor = resources.getColor(R.color.muted, null)

        listOf(
            Triple(binding.tabAppearanceText, binding.tabAppearanceIndicator, 0),
            Triple(binding.tabDockText,       binding.tabDockIndicator,       1),
            Triple(binding.tabSystemText,     binding.tabSystemIndicator,     2)
        ).forEach { (textView, indicator, tabIndex) ->
            val isActive = tabIndex == index
            textView.setTextColor(if (isActive) inkColor else mutedColor)
            indicator.setBackgroundColor(
                if (isActive) inkColor
                else resources.getColor(android.R.color.transparent, null)
            )
        }

        binding.panelAppearance.visibility = if (index == 0) View.VISIBLE else View.GONE
        binding.panelDock.visibility       = if (index == 1) View.VISIBLE else View.GONE
        binding.panelSystem.visibility     = if (index == 2) View.VISIBLE else View.GONE
    }

    private fun setupCloseButton() {
        binding.btnClose.setOnClickListener { finish() }
    }

    private fun setupDefaultLauncherButton() {
        binding.btnSetDefaultLauncher.setOnClickListener {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            startActivity(intent)
        }
    }

    private fun setupWallpaper() {
        val saved = prefs.getString(KEY_WALLPAPER, null)
        if (saved != null) {
            showWallpaperPreview(saved)
        }

        binding.btnWallpaper.setOnClickListener {
            wallpaperCropLauncher.launch(
                CropImageContractOptions(
                    uri = null,
                    cropImageOptions = CropImageOptions(
                        aspectRatioX = 165,
                        aspectRatioY = 52,
                        fixAspectRatio = true,
                        guidelines = CropImageView.Guidelines.ON,
                        imageSourceIncludeCamera = false
                    )
                )
            )
        }

        binding.btnRemoveWallpaper.setOnClickListener {
            prefs.edit().remove(KEY_WALLPAPER).apply()
            binding.wpPreview.setImageDrawable(null)
            binding.wpNoImageLabel.visibility     = View.VISIBLE
            binding.btnRemoveWallpaper.visibility = View.GONE
        }
    }

    private fun showWallpaperPreview(base64: String) {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val bmp   = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return

        val cm = ColorMatrix().also { it.setSaturation(0f) }
        binding.wpPreview.colorFilter = ColorMatrixColorFilter(cm)
        binding.wpPreview.setImageBitmap(bmp)
        binding.wpNoImageLabel.visibility     = View.GONE
        binding.btnRemoveWallpaper.visibility = View.VISIBLE
    }

    private fun setupWidgetChoice() {
        binding.btnChooseWidgetPhoto.setOnClickListener {
            widgetPhotoCropLauncher.launch(
                CropImageContractOptions(
                    uri = null,
                    cropImageOptions = CropImageOptions(
                        aspectRatioX = 4,
                        aspectRatioY = 3,
                        fixAspectRatio = true,
                        guidelines = CropImageView.Guidelines.ON,
                        imageSourceIncludeCamera = false
                    )
                )
            )
        }
    }

    private fun setupSwitches() {
        binding.sw24h.isChecked              = prefs.getBoolean(KEY_CLOCK_24H,    false)
        binding.swWeather.isChecked          = prefs.getBoolean(KEY_SHOW_WEATHER, true)
        binding.swInkSaving.isChecked        = prefs.getBoolean(KEY_INK_SAVING,   false)
        binding.swCompactDock.isChecked      = prefs.getBoolean(KEY_COMPACT_DOCK, true)
        binding.swDockLabels.isChecked       = prefs.getBoolean(KEY_DOCK_LABELS,  false)
        binding.swShowPdfs.isChecked         = prefs.getBoolean(KEY_SHOW_PDFS,    true)
        binding.swShowPhoto.isChecked        = prefs.getBoolean(KEY_SHOW_PHOTO,   true)

        (binding.sw24h.parent as View).setOnClickListener {
            binding.sw24h.isChecked = !binding.sw24h.isChecked
        }
        (binding.swWeather.parent as View).setOnClickListener {
            binding.swWeather.isChecked = !binding.swWeather.isChecked
        }
        (binding.swInkSaving.parent as View).setOnClickListener {
            binding.swInkSaving.isChecked = !binding.swInkSaving.isChecked
        }
        (binding.swCompactDock.parent as View).setOnClickListener {
            binding.swCompactDock.isChecked = !binding.swCompactDock.isChecked
        }
        (binding.swDockLabels.parent as View).setOnClickListener {
            binding.swDockLabels.isChecked = !binding.swDockLabels.isChecked
        }
        (binding.swShowPdfs.parent as View).setOnClickListener {
            binding.swShowPdfs.isChecked = !binding.swShowPdfs.isChecked
        }
        (binding.swShowPhoto.parent as View).setOnClickListener {
            binding.swShowPhoto.isChecked = !binding.swShowPhoto.isChecked
        }

        binding.sw24h.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_CLOCK_24H, checked).apply()
        }
        binding.swWeather.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_SHOW_WEATHER, checked).apply()
        }
        binding.swInkSaving.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_INK_SAVING, checked).apply()
        }
        binding.swCompactDock.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_COMPACT_DOCK, checked).apply()
        }
        binding.swDockLabels.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_DOCK_LABELS, checked).apply()
        }
        binding.swShowPdfs.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_SHOW_PDFS, checked).apply()
            Toast.makeText(this, "Layout updated", Toast.LENGTH_SHORT).show()
        }
        binding.swShowPhoto.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_SHOW_PHOTO, checked).apply()
            Toast.makeText(this, "Layout updated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupThemeSelector() {
        val radioGroup = binding.groupTheme
        val mode = prefs.getString(KEY_THEME_MODE, "auto") ?: "auto"
        
        when (mode) {
            "auto"         -> binding.radioAuto.isChecked   = true
            "always_light" -> binding.radioLight.isChecked  = true
            "always_dark"  -> binding.radioDark.isChecked   = true
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newMode = when (checkedId) {
                R.id.radioAuto  -> "auto"
                R.id.radioLight -> "always_light"
                R.id.radioDark  -> "always_dark"
                else -> "auto"
            }
            prefs.edit().putString(KEY_THEME_MODE, newMode).apply()
            applyTheme()
        }
    }

    private fun setupCustomInputs() {
        binding.inputGreetingName.setText(prefs.getString(KEY_GREETING_NAME, "snes"))
        binding.inputClientId.setText(prefs.getString(KEY_GOOGLE_CLIENT_ID, ""))

        binding.btnSaveGreeting.setOnClickListener {
            val name = binding.inputGreetingName.text.toString()
            prefs.edit().putString(KEY_GREETING_NAME, name).apply()
            Toast.makeText(this, "Greeting name saved", Toast.LENGTH_SHORT).show()
        }

        binding.btnSaveClientId.setOnClickListener {
            val clientId = binding.inputClientId.text.toString()
            prefs.edit().putString(KEY_GOOGLE_CLIENT_ID, clientId).apply()
            Toast.makeText(this, "Google Client ID saved", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupDockEditor() {
        binding.dockEditor.removeAllViews()
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val apps = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val dockedPrefs = prefs.getString(KEY_DOCKED_APPS, "") ?: ""
        val dockedSet = dockedPrefs.split(",").filter { it.isNotEmpty() }.toMutableSet()

        for (app in apps) {
            val pkg = app.activityInfo.packageName
            val view = layoutInflater.inflate(R.layout.item_dock_editor, binding.dockEditor, false)
            val icon = view.findViewById<ImageView>(R.id.app_icon)
            val name = view.findViewById<TextView>(R.id.app_name)
            val btn = view.findViewById<TextView>(R.id.btn_toggle)

            icon.setImageDrawable(app.loadIcon(pm))
            name.text = app.loadLabel(pm)

            val updateBtnState = {
                if (dockedSet.contains(pkg)) {
                    btn.setBackgroundResource(R.drawable.bg_settings_btn_filled)
                    btn.setTextColor(resources.getColor(R.color.white, null))
                    btn.text = "✓"
                } else {
                    btn.setBackgroundResource(R.drawable.bg_settings_btn)
                    btn.setTextColor(resources.getColor(R.color.ink, null))
                    btn.text = ""
                }
            }
            updateBtnState()

            view.setOnClickListener {
                if (dockedSet.contains(pkg)) {
                    dockedSet.remove(pkg)
                } else {
                    if (dockedSet.size >= 10) {
                        Toast.makeText(this@SettingsActivity, "Maximum 10 apps allowed in dock.", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    dockedSet.add(pkg)
                }
                prefs.edit().putString(KEY_DOCKED_APPS, dockedSet.joinToString(",")).apply()
                updateBtnState()
            }

            binding.dockEditor.addView(view)
        }
    }

    private fun handleWallpaperResult(uri: Uri) {
        val bmp = uriToBitmap(uri) ?: return
        val scaled = scaleBitmapDown(bmp, 1600)
        val grey = toGreyscaleBitmap(scaled)
        val b64  = bitmapToBase64(grey)

        prefs.edit().putString(KEY_WALLPAPER, b64).apply()
        showWallpaperPreview(b64)
        Toast.makeText(this, "Wallpaper updated successfully", Toast.LENGTH_SHORT).show()
    }

    private fun handleWidgetPhotoResult(uri: Uri) {
        val bmp = uriToBitmap(uri) ?: return
        val scaled = scaleBitmapDown(bmp, 1000)
        val grey = toGreyscaleBitmap(scaled)
        val b64  = bitmapToBase64(grey)

        prefs.edit().putString("widget_photo_data", b64).apply()
        Toast.makeText(this, "Widget photo updated successfully", Toast.LENGTH_SHORT).show()
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun scaleBitmapDown(bmp: Bitmap, maxDim: Int): Bitmap {
        if (bmp.width <= maxDim && bmp.height <= maxDim) return bmp
        val ratio = bmp.width.toFloat() / bmp.height.toFloat()
        val width = if (ratio > 1) maxDim else (maxDim / ratio).toInt()
        val height = if (ratio > 1) (maxDim / ratio).toInt() else maxDim
        return Bitmap.createScaledBitmap(bmp, width, height, true)
    }

    private fun toGreyscaleBitmap(src: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(dest)
        val paint  = android.graphics.Paint()
        val cm     = ColorMatrix().also { it.setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }

    private fun bitmapToBase64(bmp: Bitmap): String {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
    }

    private fun setupAboutLink() {
        binding.aboutSectionClickable.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/snes19xx/serif-eink"))
            startActivity(intent)
        }
    }
}
