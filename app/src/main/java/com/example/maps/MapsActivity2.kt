package com.example.maps

import android.Manifest
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.os.ResultReceiver
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.maps.models.direction.DirectionFinder
import com.example.maps.models.direction.DirectionFinderListener
import com.example.maps.models.direction.Route
import com.example.maps.service.FetchAddressIntentService
import com.example.maps.utils.Connections
import com.example.maps.utils.Constants
import com.example.maps.utils.PermissionGPS
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.*
import com.google.android.gms.location.places.Place
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment
import com.google.android.gms.location.places.ui.PlaceSelectionListener
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar

class MapsActivity2 :AppCompatActivity(), GoogleMap.OnMarkerClickListener, GoogleMap.OnMapClickListener, OnMapReadyCallback,
    DirectionFinderListener {

    private val LocationA = LatLng(-8.594848, 116.105390)

    private var map: GoogleMap? = null
    private var fusedLocationProvider: FusedLocationProviderClient? = null
    private var locationRequest: LocationRequest? = null
    private var locationCallback: LocationCallback? = null
    private var locationgps: Location? = null
    private var resultReceiver: ResultReceiver? = null
    private var selectedMarker: Marker? = null
    private var searchLocation: LatLng? = null

    private var originMarkers: List<Marker>? = ArrayList()
    private var destinationMarker: MutableList<Marker>? = ArrayList()
    private var polyLinePaths: MutableList<Polyline>? = ArrayList()

    private var progressDialog: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps2)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkLocationPermission()
        }

        if (!Connections.checkConnection(this)) {
            Toast.makeText(this, "Kesalahan jaringan periksa koneksi anda", Toast.LENGTH_SHORT).show()
            finish()
        }

        init()

        fusedLocationProvider = LocationServices.getFusedLocationProviderClient(this)
        resultReceiver = object : ResultReceiver(Handler()) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle) {
                val addressOutput = resultData.getString(Constants.RESULT_DATA_KEY)
                Toast.makeText(applicationContext, addressOutput, Toast.LENGTH_SHORT).show()
            }
        }

        locationgps = Location("Point A")
    }

    @SuppressLint("SetTextI18n")
    private fun init() {

        setupAutoCompleteFragment()

        val fa = findViewById<FloatingActionButton>(R.id.fb_direction)
        fa.setOnClickListener {
            try {
                val origin = locationgps!!.latitude.toString() + "," + locationgps!!.longitude
                DirectionFinder(this@MapsActivity2, origin, searchLocation!!.latitude.toString() + "," + searchLocation!!.longitude).execute(getString(R.string.google_maps_key))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val ft = findViewById<FloatingActionButton>(R.id.fb_satillete)
        ft.setOnClickListener {
            if (map != null) {
                val MapType = map!!.mapType
                if (MapType == 1) {
                    ft.setImageResource(R.drawable.ic_satellite_off)
                    map!!.mapType = GoogleMap.MAP_TYPE_SATELLITE
                } else {
                    ft.setImageResource(R.drawable.ic_satellite_on)
                    map!!.mapType = GoogleMap.MAP_TYPE_NORMAL
                }
            }
        }

        val fm = findViewById<FloatingActionButton>(R.id.fb_gps)
        fm.setOnClickListener {
            getDeviceLocation(true)
            if (!Geocoder.isPresent()) {
                Toast.makeText(this, R.string.no_geocoder_available, Toast.LENGTH_SHORT).show()
            } else {
                showAddress()
            }
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                if (locationResult == null)
                    return

                for (locationUpdate in locationResult.locations) {
                    locationgps = locationUpdate
                    if (gpsFirstOn) {
                        gpsFirstOn = false
                        getDeviceLocation(true)
                    }
                }
            }
        }

        locationRequest = LocationRequest()
        locationRequest!!.interval = UPDATE_INTERVAL
        locationRequest!!.fastestInterval = FASTEST_UPDATE_INTERVAL
        locationRequest!!.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as MapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setupAutoCompleteFragment() {
        val autocompleteFragment = supportFragmentManager.findFragmentById(R.id.place_autocompleate_fragment) as PlaceAutocompleteFragment
        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                searchLocation = place.latLng
            }

            override fun onError(status: Status) {
                status.statusMessage?.let { Log.e("Error", it) }
            }
        })
    }

    override fun onMapReady(gMap: GoogleMap) {
        map = gMap

        map!!.setOnMapClickListener(this)
        map!!.setOnMarkerClickListener(this)

        map!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LocationA, DEFAULT_ZOOM))

        map!!.uiSettings.isMapToolbarEnabled = false
        map!!.uiSettings.isMyLocationButtonEnabled = false
        //  map.getUiSettings().setCompassEnabled(false);

        // TODO : location
        map!!.projection.visibleRegion

        if (!checkPermission())
            requestPermission()

        getDeviceLocation(false)
    }

    override fun onDirectionFinderStart() {
        progressDialog = ProgressDialog.show(this, "Tunggu sebentar", "Mencari lokasi terdekat..", true)
        if (originMarkers != null) {
            for (marker in originMarkers!!) {
                marker.remove()
            }
        }
        if (destinationMarker != null) {
            for (marker in destinationMarker!!) {
                marker.remove()
            }
        }
        if (polyLinePaths != null) {
            for (polylinePath in polyLinePaths!!) {
                polylinePath.remove()
            }
        }
    }

    override fun onDirectionFinderSuccess(routes: List<Route>) {
        progressDialog!!.dismiss()
        polyLinePaths = ArrayList()
        originMarkers = ArrayList()
        destinationMarker = ArrayList()

        for (route in routes) {
            map!!.moveCamera(CameraUpdateFactory.newLatLngZoom(route.startLocation, 15.5f))
            (findViewById<View>(R.id.tv_distance) as TextView).text = route.distance!!.text
            (findViewById<View>(R.id.tv_time) as TextView).text = route.duration!!.text

            destinationMarker!!.add(map!!.addMarker(MarkerOptions()
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                .title(route.endAddress)
                .position(route.endLocation!!)))

            val polylineOptions = PolylineOptions()
                .geodesic(true)
                .color(resources.getColor(R.color.colorPrimary))
                .width(10f)

            for (i in route.points!!.indices) {
                polylineOptions.add(route.points!![i])
            }

            polyLinePaths!!.add(map!!.addPolyline(polylineOptions))
        }
    }

    private fun getDeviceLocation(MyLocation: Boolean) {
        if (!MyLocation)

            if (checkPermission()) {
                if (map != null)
                    map!!.isMyLocationEnabled = true

                val locationResult = fusedLocationProvider!!.lastLocation
                locationResult.addOnCompleteListener(this) { task ->
                    if (task.isSuccessful && task.result != null) {
                        // lastKnownLocation = task.getResult();
                    } else {
                        Log.w(TAG, "getLastLocation:exception", task.exception)
                        Toast.makeText(this, R.string.no_location_detected, Toast.LENGTH_SHORT).show()
                    }
                }
            } else
                Log.d(TAG, "Current location is null. Permission Denied.")
    }

    override fun onMapClick(point: LatLng) {
        selectedMarker = null
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        if (marker == selectedMarker) {
            selectedMarker = null
            return true
        }

        Toast.makeText(this, marker.title, Toast.LENGTH_SHORT).show()
        selectedMarker = marker
        return false
    }

    private fun showAddress() {
        val intent = Intent(this, FetchAddressIntentService::class.java)
        intent.putExtra(Constants.RECEIVER, resultReceiver)
        intent.putExtra(Constants.LOCATION_DATA_EXTRA, locationgps)
        startService(intent)
    }

    override fun onStart() {
        super.onStart()
        if (Connections.checkConnection(this)) {
            PermissionGPS(this)
        }
    }

    override fun onRestart() {
        super.onRestart()
        if (Connections.checkConnection(this)) {
            PermissionGPS(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (Connections.checkConnection(this)) {
            if (checkPermission())
                fusedLocationProvider!!.requestLocationUpdates(locationRequest, locationCallback!!, Looper.myLooper())
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        Log.i(TAG, "onRequestPermissionResult")
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            when {
                grantResults.isEmpty() -> Log.i(TAG, "User interaction was cancelled.") // grantResults.length > 0
                grantResults[0] == PackageManager.PERMISSION_GRANTED -> getDeviceLocation(false)
                else -> showSnackbar(R.string.permission_rationale, Snackbar.LENGTH_INDEFINITE, android.R.string.ok
                ) { requestPermission() }
            }
        }
    }

    private fun showSnackbar(textStringId: Int, length: Int, actionStringId: Int, listener: (Any) -> Unit) {
        val snackbar = Snackbar.make(findViewById(android.R.id.content), textStringId, length)
        snackbar.setAction(actionStringId, listener)
        snackbar.show()
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)

    }

    private fun checkPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkLocationPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    MY_PERMISSIONS_REQUEST_LOCATION)

            } else {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    MY_PERMISSIONS_REQUEST_LOCATION)
            }
            return false
        } else {
            return true
        }
    }

    companion object {

        private val TAG = MapsActivity2::class.java.simpleName

        private const val DEFAULT_ZOOM = 9.5f

        private const val UPDATE_INTERVAL: Long = 500
        private const val FASTEST_UPDATE_INTERVAL = UPDATE_INTERVAL / 5
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private var gpsFirstOn = true

        const val MY_PERMISSIONS_REQUEST_LOCATION = 99
    }
}