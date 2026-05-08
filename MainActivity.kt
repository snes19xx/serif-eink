package com.snes19xx.einklauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.snes19xx.einklauncher.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import net.openid.appauth.*
import net.openid.appauth.browser.AnyBrowserMatcher
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil

data class EventInfo(val time: String, val title: String, val location: String, val isSoft: Boolean, val rawDate: Date? = null)
data class PdfInfo(val name: String, val date: String, val uri: Uri, val thumbnail: Bitmap?)
data class BookInfo(val title: String, val author: String, val type: String, val path: String, val thumbnail: Bitmap?)

// Main entry point and dashboard of the launcher
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var currentEventPage = 0
    private var eventsPerPage = 3
    private var realEvents = listOf<EventInfo>()
    private var calendarEventDays = setOf<Int>()

    private var selectedFilterDay: Int? = null
    private var filterResetJob: Job? = null

    private var pendingCoverBookPath: String? = null

    private lateinit var authService: AuthorizationService
    private var authState: AuthState? = null

    private lateinit var weatherClient: WeatherClient
    private lateinit var calendarClient: GoogleCalendarClient
    private lateinit var libraryManager: LibraryManager

    private val coverCropLauncher = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful && result.uriContent != null && pendingCoverBookPath != null) {
            libraryManager.saveCustomCover(pendingCoverBookPath!!, result.uriContent!!)
            loadLocalData()
        }
        pendingCoverBookPath = null
    }

    private val googleAuthLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val resp = AuthorizationResponse.fromIntent(result.data!!)
            val ex = AuthorizationException.fromIntent(result.data!!)
            if (resp != null) {
                authService.performTokenRequest(resp.createTokenExchangeRequest()) { tokenResp, tokenEx ->
                    val newState = AuthState(resp, tokenEx)
                    newState.update(tokenResp, tokenEx)
                    calendarClient.saveGoogleAuthState(newState)
                    authState = newState
                    fetchCalendarData()
                }
            }
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

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val appAuthConfig = AppAuthConfiguration.Builder()
            .setBrowserMatcher(AnyBrowserMatcher.INSTANCE)
            .build()
        authService = AuthorizationService(this, appAuthConfig)

        weatherClient = WeatherClient()
        calendarClient = GoogleCalendarClient(this, authService)
        libraryManager = LibraryManager(this)

        authState = calendarClient.loadGoogleAuthState()

        checkStoragePermissions()

        startClock()
        fetchWeather()
        setupCalendar()
        setupEvents()
        setupCalendarDrawing()

        binding.pdfGrid.layoutManager = GridLayoutManager(this, 3)
        binding.pdfGrid.itemAnimator = null
        binding.booksList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.booksList.itemAnimator = null
    }

    override fun onResume() {
        super.onResume()
        syncPreferences()
        if (hasStoragePermission()) {
            loadLocalData()
        }
        fetchCalendarData()
    }

    private fun applyTheme() {
        val prefs = getSharedPreferences("folio_prefs", MODE_PRIVATE)
        val mode = prefs.getString("theme_mode", "auto") ?: "auto"

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

    override fun onStop() {
        super.onStop()
        clearEventFilter()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (selectedFilterDay != null) {
            startFilterTimer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        authService.dispose()
    }

    private fun startGoogleAuth() {
        val prefs = getSharedPreferences("folio_prefs", MODE_PRIVATE)
        val clientId = prefs.getString("google_client_id", "") ?: ""

        if (clientId.isEmpty()) {
            Toast.makeText(this, "Please configure Google Client ID in Settings", Toast.LENGTH_SHORT).show()
            return
        }

        val authIntent = calendarClient.getAuthorizationRequestIntent(clientId)
        googleAuthLauncher.launch(authIntent)
    }

    private fun fetchCalendarData() {
        val currentAuthState = authState
        if (currentAuthState == null || !currentAuthState.isAuthorized) {
            showCalendarSignIn()
            return
        }

        scope.launch {
            val result = calendarClient.fetchCalendarEvents(currentAuthState)
            if (result != null) {
                realEvents = result.first
                calendarEventDays = result.second

                if (selectedFilterDay == null) {
                    val nowDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                    val futureDays = calendarEventDays.filter { it >= nowDay }.sorted()
                    selectedFilterDay = futureDays.firstOrNull() ?: nowDay
                    currentEventPage = 0
                }

                renderEventPage()
                setupCalendar()
            } else {
                showCalendarSignIn()
            }
        }
    }

    private fun showCalendarSignIn() {
        realEvents = listOf(EventInfo("TAP TO CONNECT", "Google Calendar", "Requires OAuth2 login", true))
        calendarEventDays = setOf()
        currentEventPage = 0
        renderEventPage()
        setupCalendar()
    }

    private fun startFilterTimer() {
        filterResetJob?.cancel()
        filterResetJob = scope.launch {
            delay(60_000)
            clearEventFilter()
        }
    }

    private fun clearEventFilter() {
        val nowDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        val futureDays = calendarEventDays.filter { it >= nowDay }.sorted()
        selectedFilterDay = futureDays.firstOrNull() ?: nowDay
        currentEventPage = 0
        renderEventPage()
        binding.calGrid.adapter?.notifyDataSetChanged()
    }

    private fun onDayClicked(day: Int) {
        if (selectedFilterDay == day) {
            clearEventFilter()
        } else {
            selectedFilterDay = day
            currentEventPage = 0
            renderEventPage()
            binding.calGrid.adapter?.notifyDataSetChanged()
            startFilterTimer()
        }
    }

    private fun getFilteredEvents(): List<EventInfo> {
        if (selectedFilterDay == null) return realEvents
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)

        return realEvents.filter { event ->
            if (event.rawDate == null) return@filter true
            cal.time = event.rawDate
            cal.get(Calendar.DAY_OF_MONTH) == selectedFilterDay &&
                    cal.get(Calendar.MONTH) == currentMonth &&
                    cal.get(Calendar.YEAR) == currentYear
        }
    }

    private fun setupEvents() {
        binding.eventsList.layoutManager = LinearLayoutManager(this)
        binding.eventsList.itemAnimator = null

        binding.btnEvPrev.setOnClickListener {
            if (currentEventPage > 0) {
                currentEventPage--
                renderEventPage()
            }
        }

        binding.btnEvNext.setOnClickListener {
            val eventsToRender = getFilteredEvents()
            val totalPages = ceil(eventsToRender.size.toDouble() / eventsPerPage).toInt()
            if (currentEventPage < totalPages - 1) {
                currentEventPage++
                renderEventPage()
            }
        }
    }

    private fun renderEventPage() {
        val eventsToRender = getFilteredEvents()
        val totalPages = ceil(eventsToRender.size.toDouble() / eventsPerPage).toInt()
        val start = currentEventPage * eventsPerPage
        val end = minOf(start + eventsPerPage, eventsToRender.size)

        val pageEvents = if (eventsToRender.isEmpty()) emptyList() else eventsToRender.subList(start, end)

        if (binding.eventsList.adapter == null) {
            binding.eventsList.adapter = EventAdapter(pageEvents) { startGoogleAuth() }
        } else {
            (binding.eventsList.adapter as EventAdapter).updateData(pageEvents)
        }

        val displayTotal = maxOf(1, totalPages)
        binding.evIndicator.text = "${currentEventPage + 1} / $displayTotal"
        binding.evPager.visibility = if (totalPages > 1) View.VISIBLE else View.INVISIBLE

        if (eventsToRender.isEmpty() && selectedFilterDay != null) {
            val emptyEvents = listOf(EventInfo("", "No events on this day", "", true))
            if (binding.eventsList.adapter == null) {
                binding.eventsList.adapter = EventAdapter(emptyEvents) { startGoogleAuth() }
            } else {
                (binding.eventsList.adapter as EventAdapter).updateData(emptyEvents)
            }
            binding.evIndicator.text = "1 / 1"
            binding.evPager.visibility = View.INVISIBLE
        }
    }

    private fun setupCalendar() {
        val cal = Calendar.getInstance()
        val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
        binding.calMonth.text = monthFormat.format(cal.time)
        binding.calYear.text = cal.get(Calendar.YEAR).toString()

        val today = cal.get(Calendar.DAY_OF_MONTH)

        val days = mutableListOf<String>()
        val dayHeaders = listOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")
        days.addAll(dayHeaders)

        val firstDayCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDayOfWeek = firstDayCal.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = firstDayCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        for (i in 0 until firstDayOfWeek) {
            days.add("")
        }
        for (i in 1..daysInMonth) {
            days.add(i.toString())
        }

        binding.calGrid.layoutManager = GridLayoutManager(this, 7)
        binding.calGrid.itemAnimator = null
        binding.calGrid.adapter = CalendarAdapter(days, today, selectedFilterDay, calendarEventDays) { day ->
            onDayClicked(day)
        }
    }

    private fun checkStoragePermissions() {
        if (!hasStoragePermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            } else {
                requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 100)
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun loadLocalData() {
        scope.launch(Dispatchers.IO) {
            val recentPdfs = libraryManager.fetchRecentPdfs().take(6)
            val libraryBooks = libraryManager.fetchLibraryBooks()

            withContext(Dispatchers.Main) {
                binding.pdfGrid.adapter = PdfAdapter(recentPdfs) { pdf ->
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(pdf.uri, "application/pdf")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    try { startActivity(intent) } catch (e: Exception) { }
                }
                binding.booksList.adapter = BookAdapter(libraryBooks, { book ->
                    val file = File(book.path)
                    val uri = FileProvider.getUriForFile(this@MainActivity, "${packageName}.fileprovider", file)
                    val mimeType = if (book.type == "EPUB") "application/epub+zip" else "application/pdf"

                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(uri, mimeType)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    try { startActivity(intent) } catch (e: Exception) { }
                }, { book ->
                    pendingCoverBookPath = book.path
                    coverCropLauncher.launch(
                        CropImageContractOptions(
                            uri = null,
                            cropImageOptions = CropImageOptions(
                                aspectRatioX = 56,
                                aspectRatioY = 80,
                                fixAspectRatio = true,
                                imageSourceIncludeCamera = false
                            )
                        )
                    )
                })
            }
        }
    }

    private fun syncPreferences() {
        val prefs = getSharedPreferences("folio_prefs", MODE_PRIVATE)

        val wpBase64 = prefs.getString("header_wallpaper_data", null)
        if (wpBase64 != null) {
            try {
                val bytes = Base64.decode(wpBase64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                binding.headerWallpaper.setImageBitmap(bmp)
                binding.headerWallpaper.visibility = View.VISIBLE
            } catch (e: Exception) {
                binding.headerWallpaper.setImageDrawable(null)
                binding.headerWallpaper.visibility = View.GONE
            }
        } else {
            binding.headerWallpaper.setImageDrawable(null)
            binding.headerWallpaper.visibility = View.GONE
        }

        val showPdfs = prefs.getBoolean("show_pdfs", true)
        val showPhoto = prefs.getBoolean("show_photo", true)

        binding.pdfWidgetContainer.visibility = if (showPdfs) View.VISIBLE else View.GONE
        binding.photoWidgetContainer.visibility = if (showPhoto) View.VISIBLE else View.GONE

        eventsPerPage = 3

        val evParams = binding.eventsList.layoutParams
        evParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        binding.eventsList.layoutParams = evParams

        binding.mainGrid.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        renderEventPage()

        val photoBase64 = prefs.getString("widget_photo_data", null)
        if (photoBase64 != null) {
            try {
                val bytes = Base64.decode(photoBase64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                binding.photoWidget.setImageBitmap(bmp)
            } catch (e: Exception) {
                binding.photoWidget.setImageDrawable(null)
            }
        } else {
            binding.photoWidget.setImageDrawable(null)
        }

        binding.wxBlock.visibility = if (prefs.getBoolean("show_weather", true)) View.VISIBLE else View.GONE

        val compactDock = prefs.getBoolean("compact_dock", true)
        val dockParams = binding.dockItemsContainer.layoutParams
        dockParams.height = dpToPx(if (compactDock) 45 else 65)
        binding.dockItemsContainer.layoutParams = dockParams

        updateClockUI()
        buildDock()
        adjustLayoutForScreenSize()
    }

    private fun adjustLayoutForScreenSize() {
        val displayMetrics = resources.displayMetrics
        val dpWidth = displayMetrics.widthPixels / displayMetrics.density

        val calendarFrame = binding.mainGrid.getChildAt(0)
        val widgetCol = binding.widgetCol

        val calParams = calendarFrame.layoutParams as LinearLayout.LayoutParams
        val widgetParams = widgetCol.layoutParams as LinearLayout.LayoutParams

        if (dpWidth >= 600) {
            binding.mainGrid.orientation = LinearLayout.HORIZONTAL

            calParams.width = 0
            calParams.weight = 1f
            calParams.marginEnd = dpToPx(12)

            widgetParams.width = 0
            widgetParams.weight = 1f
            widgetParams.marginStart = dpToPx(12)
        } else {
            binding.mainGrid.orientation = LinearLayout.VERTICAL

            calParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            calParams.weight = 0f
            calParams.marginEnd = 0

            widgetParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            widgetParams.weight = 0f
            widgetParams.marginStart = 0
        }

        calendarFrame.layoutParams = calParams
        widgetCol.layoutParams = widgetParams
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun startClock() {
        scope.launch {
            while (isActive) {
                updateClockUI()
                val delayMillis = 60000L - (System.currentTimeMillis() % 60000L)
                delay(delayMillis)
            }
        }
    }

    private fun updateClockUI() {
        val now = Date()
        val calendar = Calendar.getInstance()
        calendar.time = now
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        val prefs = getSharedPreferences("folio_prefs", MODE_PRIVATE)
        val is24h = prefs.getBoolean("clock_24h", false)
        val greetingName = prefs.getString("greeting_name", "snes") ?: "snes"

        val timeFormat = SimpleDateFormat(if (is24h) "HH:mm" else "h:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())

        binding.timeDisplay.text = timeFormat.format(now)
        binding.dateLine.text = dateFormat.format(now)

        binding.greetingLine.text = when {
            hour < 5 -> "Still up, $greetingName?"
            hour < 12 -> "Good morning, $greetingName."
            hour < 18 -> "Good afternoon, $greetingName."
            else -> "Good evening, $greetingName."
        }
    }

    private fun fetchWeather() {
        scope.launch {
            val state = weatherClient.fetchWeather()
            if (state != null) {
                binding.wxTemp.text = "${state.temp}°C"
                binding.wxCond.text = state.description
                binding.wxLoc.text = state.location
            } else {
                binding.wxTemp.text = "--°C"
                binding.wxCond.text = "Weather unavailable"
            }
        }
    }

    private fun buildDock() {
        val container = binding.dockItemsContainer
        container.removeAllViews()

        val pm = packageManager
        val prefs = getSharedPreferences("folio_prefs", MODE_PRIVATE)
        val dockedStr = prefs.getString("docked_apps", "") ?: ""
        val dockedPkgs = dockedStr.split(",").filter { it.isNotEmpty() }
        val showLabels = prefs.getBoolean("show_dock_labels", false)

        val inkColor = ContextCompat.getColor(this, R.color.ink)

        val einkMatrix = ColorMatrix()
        einkMatrix.setSaturation(0f)
        val contrast = 1.3f
        val translate = (-128f * (contrast - 1f))
        einkMatrix.postConcat(ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )))

        val typeface = try { ResourcesCompat.getFont(this, R.font.inter) } catch (e: Exception) { null }

        for (pkg in dockedPkgs) {
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                val launchIntent = pm.getLaunchIntentForPackage(pkg)

                val view = layoutInflater.inflate(R.layout.item_dock_button, container, false)
                val iconView = view.findViewById<ImageView>(R.id.dock_icon)
                val labelView = view.findViewById<TextView>(R.id.dock_label)

                if (typeface != null) labelView.typeface = typeface
                iconView.setImageDrawable(icon)
                iconView.colorFilter = ColorMatrixColorFilter(einkMatrix)

                if (showLabels) {
                    labelView.text = label
                    labelView.setTextColor(inkColor)
                    labelView.visibility = View.VISIBLE
                } else {
                    labelView.visibility = View.GONE
                }

                view.setOnClickListener {
                    launchIntent?.let { startActivity(it) }
                }

                container.addView(view)
            } catch (e: Exception) { }
        }

        val settingsView = layoutInflater.inflate(R.layout.item_dock_button, container, false)
        val settingsIcon = settingsView.findViewById<ImageView>(R.id.dock_icon)
        val settingsLabel = settingsView.findViewById<TextView>(R.id.dock_label)

        if (typeface != null) settingsLabel.typeface = typeface
        settingsIcon.setImageResource(R.drawable.ic_settings_modern)
        settingsIcon.colorFilter = ColorMatrixColorFilter(einkMatrix)

        if (showLabels) {
            settingsLabel.text = "SETTINGS"
            settingsLabel.setTextColor(inkColor)
            settingsLabel.visibility = View.VISIBLE
        } else {
            settingsLabel.visibility = View.GONE
        }

        settingsView.setOnClickListener {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }

        container.addView(settingsView)

        val allAppsView = layoutInflater.inflate(R.layout.item_dock_button, container, false)
        val allIcon = allAppsView.findViewById<ImageView>(R.id.dock_icon)
        val allLabel = allAppsView.findViewById<TextView>(R.id.dock_label)

        if (typeface != null) allLabel.typeface = typeface
        allIcon.setImageResource(android.R.drawable.ic_dialog_dialer)
        allIcon.colorFilter = ColorMatrixColorFilter(einkMatrix)

        if (showLabels) {
            allLabel.text = "ALL APPS"
            allLabel.setTextColor(inkColor)
            allLabel.visibility = View.VISIBLE
        } else {
            allLabel.visibility = View.GONE
        }

        allAppsView.setOnClickListener {
            startActivity(Intent(this@MainActivity, AllAppsActivity::class.java))
        }

        container.addView(allAppsView)
    }

    private fun setupCalendarDrawing() {
        binding.btnDrawToggle.setOnClickListener {
            binding.drawingView.isDrawingEnabled = true
            binding.btnDrawSave.visibility = View.VISIBLE
            binding.btnDrawClear.visibility = View.VISIBLE
            binding.btnDrawToggle.visibility = View.GONE

            binding.mainScrollView.setOnTouchListener { _, _ -> true }
        }

        binding.btnDrawClear.setOnClickListener {
            binding.drawingView.clearCanvas()
            binding.drawingView.isDrawingEnabled = false
            binding.btnDrawSave.visibility = View.GONE
            binding.btnDrawClear.visibility = View.GONE
            binding.btnDrawToggle.visibility = View.VISIBLE

            binding.mainScrollView.setOnTouchListener(null)
        }

        binding.btnDrawSave.setOnClickListener {
            binding.drawingView.saveDrawing()
            binding.drawingView.isDrawingEnabled = false
            binding.btnDrawSave.visibility = View.GONE
            binding.btnDrawClear.visibility = View.GONE
            binding.btnDrawToggle.visibility = View.VISIBLE

            binding.mainScrollView.setOnTouchListener(null)
        }
    }
}