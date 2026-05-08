package com.snes19xx.einklauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Scanner

data class WeatherState(val temp: Int, val description: String, val location: String)

// Client for fetching weather data from OpenWeatherMap
class WeatherClient {
    private val apiKey = "a94cc2ea0d1aefcf894181a074768d0a"

    suspend fun fetchWeather(): WeatherState? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.openweathermap.org/data/2.5/weather?q=Toronto,CA&units=metric&appid=$apiKey")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val stream = connection.inputStream
                val scanner = Scanner(stream).useDelimiter("\\A")
                val response = if (scanner.hasNext()) scanner.next() else ""
                val jsonObject = JSONObject(response)

                val main = jsonObject.getJSONObject("main")
                val temp = Math.round(main.getDouble("temp")).toInt()

                val weatherArray = jsonObject.getJSONArray("weather")
                val desc = if (weatherArray.length() > 0) {
                    weatherArray.getJSONObject(0).getString("description")
                } else {
                    "unknown"
                }

                val wind = jsonObject.getJSONObject("wind")
                val speed = wind.getDouble("speed")
                val windDesc = when {
                    speed < 1.5 -> "calm"
                    speed < 3.3 -> "light air"
                    speed < 5.4 -> "light wind"
                    speed < 7.9 -> "gentle breeze"
                    speed < 10.7 -> "moderate breeze"
                    else -> "strong wind"
                }

                val finalDesc = "${desc.replaceFirstChar { it.uppercase() }}, $windDesc"
                return@withContext WeatherState(temp, finalDesc, "TORONTO, ON")
            }
            connection.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}
