package com.jayeshsolanki.sentiance.geofenceexercise

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.media.RingtoneManager
import android.os.AsyncTask
import android.os.Bundle
import android.os.Looper
import android.support.v4.app.NotificationCompat
import android.util.Log
import com.firebase.jobdispatcher.JobParameters
import com.firebase.jobdispatcher.JobService
import com.jayeshsolanki.sentiance.geofenceexercise.utils.Constants
import com.jayeshsolanki.sentiance.geofenceexercise.utils.PreferenceHelper.defaultPrefs
import com.jayeshsolanki.sentiance.geofenceexercise.utils.PreferenceHelper.get
import com.jayeshsolanki.sentiance.geofenceexercise.utils.PreferenceHelper.set
import java.lang.ref.WeakReference

class LocationService : JobService() {

    companion object {

        private val TAG = LocationService::class.java.simpleName

        private val LOCATION_DISTANCE: Float = 10F // 10 meters

        private val LOCATION_INTERVAL = 1000 * 60 * 1L // 1 minute
    }

    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener

    var location: Location? = null

    private var isGPSEnabled = false
    private var isNetworkEnabled = false

    override fun onStopJob(job: JobParameters?): Boolean {
        return false
    }

    override fun onStartJob(job: JobParameters?): Boolean {
        LocationTask(this).execute(job)
        // Return true as the work is not complete and being done in the worked thread.
        return true
    }

    private class LocationTask(jobService: LocationService) :
            AsyncTask<JobParameters, Void, JobParameters>() {

        private var jobServiceReference: WeakReference<LocationService> = WeakReference(jobService)

        override fun doInBackground(vararg params: JobParameters): JobParameters {
            val jobService = jobServiceReference.get()
            jobService?.getLocation()
            return params[0]
        }

        override fun onPostExecute(jobParameters: JobParameters) {
            val jobService = jobServiceReference.get()
            jobService?.jobFinished(jobParameters, false)
        }

    }

    /**
     *
     * Note: This should be called only if the permission is granted.
     */
    @SuppressLint("MissingPermission")
    private fun getLocation() {
        locationManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        isGPSEnabled = locationManager
                .isProviderEnabled(LocationManager.GPS_PROVIDER)
        isNetworkEnabled = locationManager
                .isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGPSEnabled && !isNetworkEnabled) {
            // no network provider is enabled
        } else {

            // First get location from Network Provider
            if (isNetworkEnabled) {
                locationListener = LocationListener(applicationContext)
                locationManager.
                        requestLocationUpdates(
                                LocationManager.NETWORK_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                                locationListener, Looper.getMainLooper())
                Log.d(TAG, "Network is enabled")
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }
            // if GPS Enabled get lat/long using GPS Services
            if (isGPSEnabled) {
                if (location == null) {
                    locationListener = LocationListener(applicationContext)
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                            locationListener, Looper.getMainLooper())
                    Log.d(TAG, "GPS is enabled")
                    location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                }
            }
        }
    }

    inner class LocationListener(private val context: Context) : android.location.LocationListener {

        override fun onLocationChanged(newLocation: Location?) {
            Log.d(TAG, "onLocationChanged: $newLocation")
            handleLocationChanged(newLocation)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            Log.d(TAG, "onStatusChanged: $provider")
        }

        override fun onProviderEnabled(provider: String?) {
            Log.d(TAG, "onProviderEnabled: $provider")
        }

        override fun onProviderDisabled(provider: String?) {
            Log.d(TAG, "onProviderDisabled: $provider")
        }

        private fun handleLocationChanged(newLocation: Location?) {
            newLocation?.let {
                if (isBetterLocation(newLocation, location)) {
                    location = newLocation
                }

                val prefs = defaultPrefs(context)

                val wasInGeoFence = prefs[Constants.PREFS_IS_IN_GEOFENCE, false] ?: false

                val distance = distanceFromCentreLocation(newLocation, prefs)
                // Default to 10 kms
                val radius = prefs[Constants.PREFS_GEOFENCE_RADIUS, Constants.DEFAULT_GEOFENCE_RADIUS]
                        ?: Constants.DEFAULT_GEOFENCE_RADIUS
                val isInGeoFence = distance < radius

                // If state is changed, show a notification.
                if (wasInGeoFence != isInGeoFence) {
                    val message = if (isInGeoFence) {
                        "You have entered your geofence"
                    } else {
                        "You have exited your geofence"
                    }
                    showNotification(message)
                }

                // Always save the state.
                prefs[Constants.PREFS_IS_IN_GEOFENCE] = isInGeoFence
            }
        }

        private fun showNotification(message: String) {
            val channelId = "SENTIANCE_FUN_NOTIF_CHANNEL"
            val notification = NotificationCompat.Builder(context, channelId)
                    .setContentText(message)
                    .setSmallIcon(R.drawable.navigation_empty_icon)
                    .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                    .build()
            val notificationManager =
                    context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(1, notification)
        }

        private fun distanceFromCentreLocation(newLocation: Location,
                                               prefs: SharedPreferences): Float {
            val centreLocation = Location("")
            centreLocation.latitude = prefs[Constants.PREFS_LOCATION_CENTER_LAT, 0.toString()]!!
                    .toDouble()
            centreLocation.longitude = prefs[Constants.PREFS_LOCATION_CENTER_LNG, 0.toString()]!!
                    .toDouble()
            return distanceBetweenLocations(centreLocation, newLocation)
        }

        private fun distanceBetweenLocations(fromLocation: Location, toLocation: Location): Float {
            return if (fromLocation.latitude == toLocation.latitude &&
                    fromLocation.longitude == toLocation.longitude) {
                0F
            } else {
                fromLocation.distanceTo(toLocation)
            }
        }

        /**
         * Determines whether one Location reading is better than the current Location fix
         * @param location  The new Location that you want to evaluate
         * @param currentBestLocation  The current Location fix, to which you want to compare the new one
         * NOTE: This code is from Android Developer's Guide
         *  https://developer.android.com/guide/topics/location/strategies.html#BestPerformance
         */
        private fun isBetterLocation(location: Location, currentBestLocation: Location?): Boolean {
            if (currentBestLocation == null) {
                // A new location is always better than no location
                return true
            }

            // Check whether the new location fix is newer or older
            val timeDelta = location.time - currentBestLocation.time
            val isSignificantlyNewer = timeDelta > Constants.TWO_MINUTES
            val isSignificantlyOlder = timeDelta < -Constants.TWO_MINUTES
            val isNewer = timeDelta > 0

            // If it's been more than two minutes since the current location, use the new location
            // because the user has likely moved
            if (isSignificantlyNewer) {
                return true
                // If the new location is more than two minutes older, it must be worse
            } else if (isSignificantlyOlder) {
                return false
            }

            // Check whether the new location fix is more or less accurate
            val accuracyDelta = (location.accuracy - currentBestLocation.accuracy)
            val isLessAccurate = accuracyDelta > 0
            val isMoreAccurate = accuracyDelta < 0
            val isSignificantlyLessAccurate = accuracyDelta > 200

            // Check if the old and new location are from the same provider
            val isFromSameProvider = isSameProvider(location.provider,
                    currentBestLocation.provider)

            // Determine location quality using a combination of timeliness and accuracy
            if (isMoreAccurate) {
                return true
            } else if (isNewer && !isLessAccurate) {
                return true
            } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
                return true
            }
            return false
        }

        /**
         * Checks whether two providers are the same
         * NOTE: This code is from Android Developer's Guide
         *  https://developer.android.com/guide/topics/location/strategies.html#BestPerformance
         **/
        private fun isSameProvider(provider1: String?, provider2: String?): Boolean {
            return if (provider1 == null) false else provider1 == provider2
        }

    }

}
