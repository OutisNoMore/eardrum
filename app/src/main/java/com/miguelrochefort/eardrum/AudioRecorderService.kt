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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone


class AudioRecorderService : Service() {

    private var mediaRecorderStarted = false
    private var recorder: MediaRecorder? = null
    private lateinit var sensorManager: SensorManager
    private var currentTemperature: Float? = null
    private var temperature: Sensor? = null
    private var temperatureListener: SensorEventListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

        override fun onSensorChanged(event: SensorEvent) {
            currentTemperature = event.values[0]
        }
    }
    private var currentLocation: Location? = null
    private var locationManager: LocationManager? = null
    private val gpsLocationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentLocation = location
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

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
        if (mediaRecorderStarted) {
            stopRecorder()
        } else {
            startRecorder()
        }
        setNextAlarm()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }

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
            //.setContentText(getString(R.string.notification_text))
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setSmallIcon(R.drawable.ic_stat)
            .build()
        startForeground(2, notification)
    }

    private fun createRecorder() {
        recorder = MediaRecorder()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        temperature = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
    }

    private fun startRecorder() {
        if (mediaRecorderStarted) {
            recorder?.stop()
            recorder?.reset()
        }

        recorder = MediaRecorder()
        recorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            recorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder?.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC)
        } else {
            recorder?.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
            recorder?.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
        }
        recorder?.setAudioEncodingBitRate(128000)
        recorder?.setAudioSamplingRate(48000)
        recorder?.setOutputFile(getFilePath())
        recorder?.prepare()
        recorder?.start()
        mediaRecorderStarted = true
    }

    private fun stopRecorder() {
        if (mediaRecorderStarted) {
            recorder?.stop()
            recorder?.reset()
            recorder?.release()
        }
        mediaRecorderStarted = false
    }

    private fun getFilePath() : String {
        return getExternalFilesDir(null)?.absolutePath + "/${getFileName()}"
    }

    private fun getFileName() : String {
        val tz = TimeZone.getTimeZone("UTC")
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        df.timeZone = tz
        val nowAsISO = df.format(Date())
        var fileName = "${nowAsISO}.3gpp"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            fileName = "${nowAsISO}.mp4"
        return fileName
    }

    @SuppressLint("MissingPermission")
    private fun setNextAlarm() {
        var interval = 10*1000 // Stop a recording after 10 seconds

        if (!mediaRecorderStarted) {
            interval = 60*1000 // Start a new recording session every minute

            val lastKnownLocationByGps =
                locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            lastKnownLocationByGps?.let {
                currentLocation = lastKnownLocationByGps
            }

            Log.i(
                "test",
                "Current Location = [lat : ${currentLocation?.latitude}, " +
                        "lng : ${currentLocation?.longitude}, " +
                        "acc : ${currentLocation?.accuracy}]",
            )

            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                this.registerReceiver(null, ifilter)
            }
            val batteryPct: Float? = batteryStatus?.let { intent ->
                val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                level * 100 / scale.toFloat()
            }
            val batteryTemp: Double? =
                batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)?.div(10.0)
            Log.i(
                "test",
                "Battery Percentage : $batteryPct",
            )
            Log.i(
                "test",
                "Battery Temperature : $batteryTemp",
            )
            temperature?.let {
                Log.i(
                    "test",
                    "Temperature : $currentTemperature",
                )
            }
            temperature?.let {
                sensorManager.unregisterListener(temperatureListener)
            }
            locationManager?.removeUpdates(gpsLocationListener)
        } else {
            temperature?.let {
                sensorManager.registerListener(
                    temperatureListener,
                    temperature,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
            }
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000,
                0F,
                gpsLocationListener
            )
        }

        val service: PendingIntent?
        val intent = Intent(applicationContext, AudioRecorderService::class.java)
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        service = PendingIntent.getService(this, 0, intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + interval,
            service)
    }
}
