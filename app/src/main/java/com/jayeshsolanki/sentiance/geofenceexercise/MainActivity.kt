package com.jayeshsolanki.sentiance.geofenceexercise

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Toast
import com.firebase.jobdispatcher.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.jakewharton.rxbinding2.widget.RxTextView
import com.jayeshsolanki.sentiance.geofenceexercise.utils.Constants
import com.jayeshsolanki.sentiance.geofenceexercise.utils.PreferenceHelper
import com.jayeshsolanki.sentiance.geofenceexercise.utils.PreferenceHelper.get
import com.jayeshsolanki.sentiance.geofenceexercise.utils.PreferenceHelper.set
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private var marker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val prefs = PreferenceHelper.defaultPrefs(this)
        val radius = prefs[Constants.PREFS_GEOFENCE_RADIUS, Constants.DEFAULT_GEOFENCE_RADIUS]
                ?: Constants.DEFAULT_GEOFENCE_RADIUS

        if (radius != Constants.DEFAULT_GEOFENCE_RADIUS) {
            radius_distance.setText((radius / 1000).toString())
        }

        RxTextView.textChanges(radius_distance)
                .skip(1)
                .debounce(500, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    if (!it.isNullOrBlank()) {
                        val newRadius = it.toString().toInt() * 1000 // kms to metres
                        prefs[Constants.PREFS_GEOFENCE_RADIUS] = newRadius
                        showSnackbar(R.string.radius_change_msg, android.R.string.ok,
                                View.OnClickListener {})
                    }
                }
    }

    override fun onStart() {
        super.onStart()

        if (!checkPermissions()) {
            requestPermissions()
        } else {
            startLocationService()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        setPreviousSelectedPosition(googleMap)
        setPositionSelectionClickListener(googleMap)

        if (checkPermissions()) {
            map.isMyLocationEnabled = true // Get blue dot on current location.
        }
    }

    private fun setPreviousSelectedPosition(googleMap: GoogleMap) {
        val prefs = PreferenceHelper.defaultPrefs(this)
        val latitude = prefs[Constants.PREFS_LOCATION_CENTER_LAT, 0.toString()]
        val longitude = prefs[Constants.PREFS_LOCATION_CENTER_LNG, 0.toString()]
        if (latitude != null && longitude != null) {
            if (latitude.toDouble() != 0.0 && longitude.toDouble() != 0.0) {
                val latLng = LatLng(latitude.toDouble(), longitude.toDouble())
                moveToCurrentSelectedPosition(googleMap, latLng)
            }
        }
    }

    private fun moveToCurrentSelectedPosition(googleMap: GoogleMap, latLng: LatLng) {
        val snippet = String.format(Locale.getDefault(), "Lat: %1$.5f, Long: %2$.5f",
                latLng.latitude, latLng.longitude)
        marker = googleMap.addMarker(MarkerOptions()
                .position(latLng)
                .title(getString(R.string.new_location))
                .snippet(snippet))
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
    }

    private fun setPositionSelectionClickListener(googleMap: GoogleMap) {
        googleMap.setOnMapClickListener {
            marker?.remove()

            moveToCurrentSelectedPosition(googleMap, it)

            Toast.makeText(this, getString(R.string.new_location), Toast.LENGTH_SHORT).show()

            val prefs = PreferenceHelper.defaultPrefs(this)
            prefs[Constants.PREFS_LOCATION_CENTER_LAT] = it.latitude.toString()
            prefs[Constants.PREFS_LOCATION_CENTER_LNG] = it.longitude.toString()
        }
    }

    private fun startLocationService() {
        val dispatcher = FirebaseJobDispatcher(GooglePlayDriver(applicationContext))
        val job = dispatcher.newJobBuilder()
                .setService(LocationService::class.java)
                .setTag(LOCATION_SERVICE_TAG)
                .setRecurring(true)
                .setTrigger(Trigger.executionWindow(
                        LOCATION_SERVICE_START_INTERVAL, LOCATION_SERVICE_INTERVAL_TOLERANCE))
                .setRetryStrategy(RetryStrategy.DEFAULT_EXPONENTIAL)
                .setLifetime(Lifetime.FOREVER)
                .setReplaceCurrent(true)
                .build()
        dispatcher.mustSchedule(job)
    }

    private fun checkPermissions() = ActivityCompat.checkSelfPermission(this,
            ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        val shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                this, ACCESS_FINE_LOCATION)

        if (shouldProvideRationale) {
            showSnackbar(R.string.permission_rationale, android.R.string.ok,
                    View.OnClickListener { startLocationPermissionRequest() })
        } else {
            startLocationPermissionRequest()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_PERMISSIONS_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PERMISSION_GRANTED) {
                    map.isMyLocationEnabled = true
                    startLocationService()
                } else {
                    showSnackbar(R.string.permission_rationale, android.R.string.ok,
                            View.OnClickListener { startLocationPermissionRequest() })
                }
            }
        }
    }

    private fun startLocationPermissionRequest() {
        ActivityCompat.requestPermissions(
                this,
                arrayOf(ACCESS_FINE_LOCATION), REQUEST_PERMISSIONS_REQUEST_CODE)
    }

    private fun showSnackbar(mainTextStringId: Int, actionStringId: Int,
                             listener: View.OnClickListener?) {
        Snackbar.make(findViewById<View>(android.R.id.content),
                getString(mainTextStringId),
                Snackbar.LENGTH_INDEFINITE)
                .setAction(getString(actionStringId), listener).show()
    }

    companion object {

        const val LOCATION_SERVICE_TAG = "SENTIANCE_LOCATION_SERVICE"
        const val LOCATION_SERVICE_START_INTERVAL = 10
        const val LOCATION_SERVICE_INTERVAL_TOLERANCE = 20

        const val REQUEST_PERMISSIONS_REQUEST_CODE = 100
    }

}
