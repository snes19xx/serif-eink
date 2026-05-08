package com.snes19xx.einklauncher

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.openid.appauth.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume

// Client for interacting with Google Calendar API
class GoogleCalendarClient(private val context: Context, private val authService: AuthorizationService) {

    private val GOOGLE_REDIRECT_URI = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"

    fun saveGoogleAuthState(state: AuthState) {
        val prefs = context.getSharedPreferences("folio_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("google_auth_state", state.jsonSerializeString()).apply()
    }

    fun loadGoogleAuthState(): AuthState? {
        val prefs = context.getSharedPreferences("folio_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("google_auth_state", null) ?: return null
        return try { AuthState.jsonDeserialize(json) } catch (e: Exception) { null }
    }

    fun getAuthorizationRequestIntent(clientId: String): android.content.Intent {
        val config = AuthorizationServiceConfiguration(
            Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
            Uri.parse("https://oauth2.googleapis.com/token")
        )
        val req = AuthorizationRequest.Builder(
            config,
            clientId,
            ResponseTypeValues.CODE,
            Uri.parse(GOOGLE_REDIRECT_URI)
        ).setScopes("https://www.googleapis.com/auth/calendar.readonly").build()

        return authService.getAuthorizationRequestIntent(req)
    }

    suspend fun fetchCalendarEvents(authState: AuthState): Pair<List<EventInfo>, Set<Int>>? = withContext(Dispatchers.IO) {
        return@withContext suspendCoroutine<Pair<List<EventInfo>, Set<Int>>?> { continuation ->
            authState.performActionWithFreshTokens(authService) { accessToken, _, ex ->
                if (ex != null || accessToken == null) {
                    continuation.resume(null)
                    return@performActionWithFreshTokens
                }

                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }
                    val startOfMonth = Calendar.getInstance().apply {
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }.time
                    val timeMin = sdf.format(startOfMonth)

                    val url = URL("https://www.googleapis.com/calendar/v3/calendars/primary/events?timeMin=$timeMin&maxResults=120&singleEvents=true&orderBy=startTime")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("Authorization", "Bearer $accessToken")

                    if (conn.responseCode == 200) {
                        val stream = conn.inputStream
                        val scanner = Scanner(stream).useDelimiter("\\A")
                        val response = if (scanner.hasNext()) scanner.next() else ""
                        val jsonObject = JSONObject(response)
                        val items = jsonObject.getJSONArray("items")

                        val events = mutableListOf<EventInfo>()
                        val eventDays = mutableSetOf<Int>()

                        val inFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                        val allDayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

                        val now = Calendar.getInstance()
                        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

                        for (i in 0 until items.length()) {
                            val item = items.getJSONObject(i)
                            val summary = item.optString("summary", "Untitled Event")
                            val location = item.optString("location", "")
                            val startObj = item.optJSONObject("start") ?: continue
                            val endObj = item.optJSONObject("end") ?: continue

                            var timeString = ""
                            var isSoft = false
                            var rawDate: Date? = null

                            if (startObj.has("dateTime")) {
                                val startTime = inFormat.parse(startObj.getString("dateTime"))
                                val endTime = inFormat.parse(endObj.getString("dateTime"))
                                rawDate = startTime

                                val eventCal = Calendar.getInstance().apply { time = startTime }
                                if (eventCal.get(Calendar.MONTH) == now.get(Calendar.MONTH)) {
                                    eventDays.add(eventCal.get(Calendar.DAY_OF_MONTH))
                                }

                                val isToday = eventCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) && eventCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
                                val isTomorrow = eventCal.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) && eventCal.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR)

                                val startStr = timeFormat.format(startTime)
                                val endStr = timeFormat.format(endTime)

                                timeString = when {
                                    isToday -> "$startStr - $endStr"
                                    isTomorrow -> "Tomorrow, $startStr"
                                    else -> SimpleDateFormat("EEE, HH:mm", Locale.getDefault()).format(startTime)
                                }
                            } else if (startObj.has("date")) {
                                val startDate = allDayFormat.parse(startObj.getString("date"))
                                rawDate = startDate

                                val eventCal = Calendar.getInstance().apply { time = startDate }
                                if (eventCal.get(Calendar.MONTH) == now.get(Calendar.MONTH)) {
                                    eventDays.add(eventCal.get(Calendar.DAY_OF_MONTH))
                                }

                                val isToday = eventCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) && eventCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
                                val isTomorrow = eventCal.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) && eventCal.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR)

                                timeString = when {
                                    isToday -> "Today (All Day)"
                                    isTomorrow -> "Tomorrow (All Day)"
                                    else -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(startDate) + " (All Day)"
                                }
                                isSoft = true
                            }

                            events.add(EventInfo(timeString, summary, location, isSoft, rawDate))
                        }
                        continuation.resume(Pair(events, eventDays))
                    } else {
                        continuation.resume(null)
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    continuation.resume(null)
                }
            }
        }
    }
}
