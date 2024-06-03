package com.miguelrochefort.eardrum

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone


/*
    This is the service that does most of the work. Attaches to a notification
    to allow running in foreground. This service takes care of all the sensor
    data and uploading to the server
 */
class AudioRecorderService : Service() {
    // Variables for running upload in coroutines as per best practices
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    // Variable to store the intent for the scheduled service to allow
    // cancelling the alarm
    private var pendingIntent: PendingIntent? = null
    // Variables for the media recorder
    private var mediaRecorderStarted = false
    private var recorder: MediaRecorder? = null
    // Variables for GPS, both using GNSS and network
    // Network provider hasn't really been tested
    private var currentLocationGPS: Location? = null
    private var currentLocationNet: Location? = null
    private var locationManagerGPS: LocationManager? = null
    private var locationManagerNet: LocationManager? = null
    private val gpsLocationListener: LocationListener =
        LocationListener { location -> currentLocationGPS = location }
    private val netLocationListener: LocationListener =
        LocationListener { location -> currentLocationNet = location }
    // Variables to store ID and settings
    private var sensorID: String? = null
    private var outputPath: String? = null
    private var timeStamp: String? = null
    private var recordingLength: Float? = null
    private var recordingInterval: Float? = null
    private var mediaFormat: String? = null
    private var mediaString: String? = null
    private var apiURL: String? = null


    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }


    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            startForeground()
        } else {
            startForeground(1, Notification())
        }
        createRecorder()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Parse all passed in settings and store to local variables
        // TODO: ensure that sensorID is passed in the intent
        sensorID = intent?.getStringExtra("sensorID")
        recordingLength = intent?.getFloatExtra("recordingLength", Constants.RECORDING_LENGTH)
        recordingInterval = intent?.getFloatExtra("recordingInterval", Constants.RECORDING_INTERVAL)
        mediaFormat = intent?.getStringExtra("mediaFormat")
        apiURL = intent?.getStringExtra("apiEndpoint")

        // Start or stop recording and upload if stopping
        // Set next alarm to start or stop recording
        if (mediaRecorderStarted) {
            stopRecorder()
            uploadData(outputPath!!)
        } else {
            startRecorder()
        }
        setNextAlarm()
        return START_STICKY
    }


    /*
        When this service is destroyed from MainActivity, make sure to cancel
        any scheduled alarms using the stored intent. Also make sure to cancel
        any jobs (from uploading)
     */
    override fun onDestroy() {
        super.onDestroy()
        job.cancelChildren()
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        pendingIntent?.let { alarmManager.cancel(it) }
    }


    // Set notification to tie service to
    @RequiresApi(Build.VERSION_CODES.O)
    private fun startForeground() {
        val channelId = "com.miguelrochefort.channel.audiorecorderservice"
        val channelName = "AudioRecorderService Channel"
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_NONE
        )
        val manager =  (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        manager.createNotificationChannel(channel)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
        val notification: Notification = notificationBuilder.setOngoing(true)
            .setContentTitle(getString(R.string.notification_title))
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setSmallIcon(R.drawable.ic_stat)
            .build()
        startForeground(2, notification)
    }


    /*
        Create recorder and gps sensor objects
     */
    private fun createRecorder() {
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaRecorder(this)
        else
            MediaRecorder()
        locationManagerGPS = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManagerNet = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }


    /*
        Sets up and starts recording. Different output formats and encoders are
        available depending on Android version and hardware.
        TODO: Check what's available to populate settings page
        TODO: Enable changing more settings such as bitrate and sampling rate
        TODO: Possibly use AudioRecorder instead for more control over recording
     */
    private fun startRecorder() {
        if (mediaRecorderStarted) {
            recorder?.stop()
            recorder?.reset()
        }

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaRecorder(this)
        else
            MediaRecorder()
        // There are some other ones you can try and use but MediaRecorder doesn't
        // provide much granularity
        recorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        // These combinations are just what examples or what experiment has
        // worked and may not be optimal or available on some configurations
        // TODO: Look into best configurations or make it configurable
        try {
            if (mediaFormat == "mp4") {
                recorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder?.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC)
                mediaString = "video/mpeg"
            } else if (mediaFormat == "3gpp") {
                recorder?.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                recorder?.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
                mediaString = "audio/3gpp"
            } else if (mediaFormat == "opus" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                recorder?.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                recorder?.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                mediaString = "audio/opus"
            } else {
                recorder?.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
                recorder?.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
                mediaString = "audio/3gpp"
            }
        } catch (e: Exception) {
            // TODO: Possibly alert user that default is going to be used
            recorder?.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
            recorder?.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
            mediaString = "audio/3gpp"
        }
        // Try to set stereo (seems to be default using MediaRecorder)
        try {
            recorder?.setAudioChannels(2)
        } catch (e: Exception) {
            Log.e("test", "Unable to set stereo recording")
        }
        recorder?.setAudioEncodingBitRate(128000)
        recorder?.setAudioSamplingRate(48000)
        outputPath = getFilePath()
        recorder?.setOutputFile(outputPath)
        // Prepare has to be called before start (see docs)
        recorder?.prepare()
        recorder?.start()
        mediaRecorderStarted = true
    }


    /*
        Stop recorder, reset settings, and release
     */
    private fun stopRecorder() {
        if (mediaRecorderStarted) {
            recorder?.stop()
            recorder?.reset()
            recorder?.release()
        }
        mediaRecorderStarted = false
    }


    /*
        Simple function to get formatted timestamp for filename
     */
    private fun getTimeStamp() : String {
        val tz = TimeZone.getTimeZone("UTC")
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        df.timeZone = tz
        timeStamp = df.format(Date())
        return df.format(Date())
    }


    /*
        Gets the file path from the application data directory.
        This needs to be changed if using custom directory in the future
     */
    private fun getFilePath() : String {
        return getExternalFilesDir(null)?.absolutePath + "/${getFileName()}"
    }


    /*
        Set filename based on the media format and timestamp
     */
    private fun getFileName() : String {
        val nowAsISO = getTimeStamp()
        var fileName = "${nowAsISO}.3gpp"
        if (mediaString == "video/mpeg") {
            fileName = "${nowAsISO}.mp4"
        } else if (mediaString == "audio/3gpp") {
            fileName = "${nowAsISO}.3gpp"
        } else if (mediaString == "audio/opus") {
            fileName = "${nowAsISO}.ogg"
        }
        return fileName
    }


    /*
        Handles uploading the data to the server. Creates the request using OkHttp
        and launches the upload in a coroutine.
        TODO: Handle errors better, add retries and other optimizations
     */
    private fun uploadData(audioFilePath: String) {
        val stat: Status = getStatus()
        // Old json metadata format
        val statusJson: String = Json.encodeToString(Status.serializer(), stat)

        val client = OkHttpClient()
        val url = apiURL

        // Create the request body
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("sensor_id", stat.sensorID)
            .addFormDataPart("timestamp", stat.timestamp)
            .addFormDataPart("lat", stat.lat.toString())
            .addFormDataPart("lon", stat.lon.toString())
            .addFormDataPart("accuracy", stat.accuracy.toString())
            .addFormDataPart("battery", stat.battery.toString())
            .addFormDataPart("temperature", stat.temperature.toString())
            .addFormDataPart(
                "audio_file",
                outputPath,
                File(audioFilePath).asRequestBody(mediaString?.toMediaType())
            )
            .build()

        // Add the headers and build request
        val request = url?.let {
            Request.Builder()
                .url(it)
                .addHeader("accept", "application/json")
                .addHeader("Accept-Encoding", "identity")
                .post(requestBody)
                .build()
        }

        // Launch request in coroutine, look for response
        scope.launch {
            val response: Response? = request?.let {
                try {
                    client.newCall(it).execute()
                } catch (e: Exception) {
                    null
                }
            }

            if (response != null) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.i("test", responseBody.toString())
                } else {
                    Log.e("test", "Failed to upload data ${response.code}")
                }
            }
        }
    }


    /*
        Function to get status from the various sensors. Currently, GPS and Battery
        stats are checked. GPS status happens through listeners while battery
        happens through intents.
        TODO: Look into using FusedProvider (requires play services though)
        TODO: Look into other sensors such as cpu usage
     */
    @SuppressLint("MissingPermission")
    private fun getStatus(): Status {
        val lastKnownLocationByGps =
            locationManagerGPS?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        lastKnownLocationByGps?.let {
            currentLocationGPS = lastKnownLocationByGps
        }
        val lastKnownLocationByNet =
            locationManagerNet?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        lastKnownLocationByNet?.let {
            currentLocationNet = lastKnownLocationByNet
        }

        var lat: Double? = 0.0
        var lon: Double? = 0.0
        var accuracy = 0f
        val accuracyGPS: Float? = currentLocationGPS?.accuracy
        val accuracyNet: Float? = currentLocationNet?.accuracy

        if (accuracyNet != null && accuracyNet > accuracyGPS!!) {
            lat = currentLocationNet?.latitude
            lon = currentLocationNet?.longitude
            accuracy = accuracyNet
        } else if (accuracyGPS != null) {
            lat = currentLocationGPS?.latitude
            lon = currentLocationGPS?.longitude
            accuracy = accuracyGPS
        }

        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            this.registerReceiver(null, ifilter)
        }
        val battery: Float? = batteryStatus?.let { intent ->
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale.toFloat()
        }
        val temperature: Double? =
            batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)?.div(10.0)

        Log.i(
            "test",
            "Current Location GPS = [lat : ${currentLocationGPS?.latitude}, " +
                "lng : ${currentLocationGPS?.longitude}, " +
                "acc : ${currentLocationGPS?.accuracy}]",
        )
        Log.i(
            "test",
            "Current Location Net = [lat : ${currentLocationNet?.latitude}, " +
                "lng : ${currentLocationNet?.longitude}, " +
                "acc : ${currentLocationNet?.accuracy}]",
        )
        Log.i(
            "test",
            "Battery Percentage : $battery",
        )
        Log.i(
            "test",
            "Battery Temperature : $temperature",
        )

        val status = Status(
            sensorID = sensorID!!,
            timestamp = timeStamp!!,
            lat = lat!!.toFloat(),
            lon = lon!!.toFloat(),
            accuracy = accuracy,
            battery = battery!!,
            temperature = temperature!!
        )
        return status
    }


    /*
        Sets the next alarm to start or stop recording. Makes sure to save intent
        if service needs to be destroyed. Register and unregister listeners to make sure
        we're not constantly listening to sensors
     */
    @SuppressLint("MissingPermission")
    private fun setNextAlarm() {
        var interval = recordingLength?.times(60000) // Stop a recording after some time

        if (!mediaRecorderStarted) {
            interval =
                recordingInterval?.times(60000) // Start a new recording session every interval

            locationManagerGPS?.removeUpdates(gpsLocationListener)
            locationManagerNet?.removeUpdates(netLocationListener)
        } else {
            if (locationManagerGPS?.isProviderEnabled("GPS_PROVIDER") == true) {
                locationManagerGPS?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    5000,
                    0F,
                    gpsLocationListener
                )
            }
            if (locationManagerGPS?.isProviderEnabled("NETWORK_PROVIDER") == true) {
                locationManagerNet?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    5000,
                    0F,
                    netLocationListener
                )
            }
        }

        // Create new service with settings for alarm to call
        val service: PendingIntent?
        val intent = Intent(applicationContext, AudioRecorderService::class.java)
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        intent.putExtra("sensorID", sensorID)
        intent.putExtra("recordingLength", recordingLength)
        intent.putExtra("recordingInterval", recordingInterval)
        intent.putExtra("mediaFormat", mediaFormat)
        intent.putExtra("apiEndpoint", apiURL)

        service = PendingIntent.getService(this, 0, intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        pendingIntent = service

        // There's a few different settings you can use, but we chose to use
        // the precise realtime with wakeup
        alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + (interval!!).toLong(),
            service)
    }
}
