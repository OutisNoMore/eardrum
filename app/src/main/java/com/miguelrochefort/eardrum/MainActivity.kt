package com.miguelrochefort.eardrum

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.util.UUID
import java.util.concurrent.TimeUnit


class MainActivity : ComponentActivity() {
    // Create variables for sharedPreferences reference and listener
    private var sharedPreferences: SharedPreferences? = null
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            // Currently when one preference changes this gets called for all
            // of them, so make sure to start or stop only once
            // TODO: look into why this is happening
            if (key == "enable_recording") {
                val enabled = sharedPreferences?.getBoolean("enable_recording", true)
                if (enabled == true) {
                    // Restart service to pass in new settings
                    stopAudioRecorderService();
                    startAudioRecorderService();
                } else {
                    stopAudioRecorderService();
                }
            }
        }
    // Create variable for sensorID (UUID)
    private lateinit var sensorID: String


    /*
        Generate a new UUID for this phone if it doesn't exist already and
        store it in the shared preferences
     */
    private fun getSensorID(): String {
        var uuid = sharedPreferences?.getString(Constants.UUID_PREF_KEY, null)
        if (uuid == null) {
            uuid = UUID.randomUUID().toString()
            sharedPreferences?.edit()?.putString(Constants.UUID_PREF_KEY, uuid)?.apply()
        }
        return uuid
    }


    /*
        Standard onCreate, get ID, Preferences, and start ui
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorID = getSensorID()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val ui = UI()

        setContent {
            ui.Scaffold_Main()
        }
    }


    /*
        On start, check if permissions are granted, else don't do anything
     */
    override fun onStart() {
        super.onStart()
        val enabled = sharedPreferences?.getBoolean("enable_recording", true)
        if (enabled == true)
            startAudioRecorderServiceWithPermissions()
    }


    /*
        Previous application used EasyPermissions, possibly find new library or do it
        native in the future
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }


    @AfterPermissionGranted(Constants.RECORDING_PERMISSIONS)
    private fun startAudioRecorderServiceWithPermissions() {
        var perms = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )

        /*
            Android Q and above require background location permission, currently
            this shows as a link in the permission dialog
            Possibly change to directly go to that settings page in the future.
            Also, we currently save data in application data folder, but if we want
            to store somewhere else we need some other permissions and setup
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            perms = arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            )
        }

        if (EasyPermissions.hasPermissions(this, *perms)) {
            startAudioRecorderService()
            runWorkers()
        } else {
            EasyPermissions.requestPermissions(
                this,
                getString(R.string.recording_permission_rationale),
                Constants.RECORDING_PERMISSIONS,
                *perms
            )
        }
    }

    /*
        This starts the background service (actually a foreground service to handle audio recording)
        and passes in all the settings in the intent. Additional settings can be passed in this way
     */
    private fun startAudioRecorderService() {
        val intent = Intent(this, AudioRecorderService::class.java)
        val recordingLength = sharedPreferences?.getFloat("recording_length", Constants.RECORDING_LENGTH)
        val recordingInterval = sharedPreferences?.getFloat("recording_interval", Constants.RECORDING_INTERVAL)
        val mediaFormat = sharedPreferences?.getString("media_format", Constants.MEDIA_FORMAT)
        val apiEndpoint = sharedPreferences?.getString("api_endpoint", Constants.API_URL)
        val onDevice = sharedPreferences?.getBoolean("on_device_processing", false)
        intent.putExtra("sensorID", sensorID)
        intent.putExtra("recordingLength", recordingLength)
        intent.putExtra("recordingInterval", recordingInterval)
        intent.putExtra("mediaFormat", mediaFormat)
        intent.putExtra("apiEndpoint", apiEndpoint)
        intent.putExtra("onDeviceProcessing", onDevice)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }


    private fun stopAudioRecorderService() {
        val intent = Intent(this, AudioRecorderService::class.java)
        this.stopService(intent)
    }


    override fun onResume() {
        super.onResume()
        // Set up a listener whenever a key changes
        sharedPreferences?.registerOnSharedPreferenceChangeListener(listener)
    }


    override fun onPause() {
        super.onPause()
        // Unregister the listener whenever a key changes
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(listener)
    }

    /*
     * runWorkers
     * --------------------------------
     * Create a worker that does on device processing of recorded audio files
     * in batches every interval.
     */
    private fun runWorkers() {
        val onDeviceProcessingWorkRequest =
            PeriodicWorkRequestBuilder<AudioProcessingWorker>(3, TimeUnit.HOURS)
        val data = Data.Builder()
        data.putString("api_url", sharedPreferences?.getString("api_endpoint", Constants.API_URL))
        data.putString("media_format", sharedPreferences?.getString("media_format", Constants.MEDIA_FORMAT))
        onDeviceProcessingWorkRequest.setInputData(data.build())

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "EXECUTE_ON_DEVICE_PROCESSING",
            ExistingPeriodicWorkPolicy.KEEP, // If there is an overlap in scheduled jobs, keep the running one
            onDeviceProcessingWorkRequest.build()
        )
    }
}
