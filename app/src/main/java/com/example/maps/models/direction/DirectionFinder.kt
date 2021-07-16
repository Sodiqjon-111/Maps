package com.example.maps.models.direction

import android.annotation.SuppressLint
import android.os.AsyncTask
import android.provider.ContactsContract.CommonDataKinds.Website.URL
import com.example.maps.utils.Constants
import com.google.android.gms.common.internal.ImagesContract.URL
import com.google.android.gms.maps.model.LatLng
import com.google.gson.internal.bind.TypeAdapters.URL
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder

class DirectionFinder(private val listener: DirectionFinderListener, private val origin: String, private val destination: String) {

    fun execute(google_api_key: String) {
        listener.onDirectionFinderStart()
        DownloadRawData().execute(createUrl(google_api_key))
    }

    private fun createUrl(google_api_key: String): String {
        val urlOrigin = URLEncoder.encode(origin, "utf-8")
        val urlDestination = URLEncoder.encode(destination, "utf-8")

        return Constants.DIRECTION_URL_API + "origin=" + urlOrigin + "&destination=" + urlDestination + "&key=" + google_api_key
    }

    @SuppressLint("StaticFieldLeak")
    private inner class DownloadRawData : AsyncTask<String, Void, String>() {

        override fun doInBackground(vararg params: String): String? {
            val link = params[0]
            try {
                val url = URL(link)
                val `is` = url.openConnection().getInputStream()
                val buffer = StringBuilder()
                val reader = BufferedReader(InputStreamReader(`is`))

                while (reader.readLine() != null) {
                    buffer.append(reader.readLine()).append("\n")
                }

                return buffer.toString()

            } catch (e: MalformedURLException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            return null
        }

        override fun onPostExecute(res: String) {
            try {
                parseJSon(res)
            } catch (e: JSONException) {
                e.printStackTrace()
            }

        }
    }

    private fun parseJSon(data: String?) {
        if (data == null)
            return

        val routes = ArrayList<Route>()
        val jsonData = JSONObject(data)
        val jsonRoutes = jsonData.getJSONArray("routes")
        for (i in 0 until jsonRoutes.length()) {
            val jsonRoute = jsonRoutes.getJSONObject(i)
            val route = Route()

            val overview_polylineJson = jsonRoute.getJSONObject("overview_polyline")
            val jsonLegs = jsonRoute.getJSONArray("legs")
            val jsonLeg = jsonLegs.getJSONObject(0)
            val jsonDistance = jsonLeg.getJSONObject("distance")
            val jsonDuration = jsonLeg.getJSONObject("duration")
            val jsonEndLocation = jsonLeg.getJSONObject("end_location")
            val jsonStartLocation = jsonLeg.getJSONObject("start_location")

            route.distance = Distance(jsonDistance.getString("text"), jsonDistance.getInt("value"))
            route.duration = Duration(jsonDuration.getString("text"), jsonDuration.getInt("value"))
            route.endAddress = jsonLeg.getString("end_address")
            route.startAddress = jsonLeg.getString("start_address")
            route.startLocation = LatLng(jsonStartLocation.getDouble("lat"), jsonStartLocation.getDouble("lng"))
            route.endLocation = LatLng(jsonEndLocation.getDouble("lat"), jsonEndLocation.getDouble("lng"))
            route.points = decodePolyLine(overview_polylineJson.getString("points"))
            routes.add(route)
        }
        listener.onDirectionFinderSuccess(routes)

    }
    private fun decodePolyLine(poly: String): List<LatLng> {
        val len = poly.length
        var index = 0
        val decoded = ArrayList<LatLng>()
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = poly[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            shift = 0
            result = 0
            do {
                b = poly[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            decoded.add(LatLng(lat / 100000.0, lng / 100000.0))
        }
        return decoded
    }
}