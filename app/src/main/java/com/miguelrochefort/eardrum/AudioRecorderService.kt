package com.miguelrochefort.eardrum

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

class AudioRecorderService : Service() {

    private var mediaRecorderStarted = false
    private var recorder: MediaRecorder? = null

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
    }

    private fun startRecorder() {
        if (mediaRecorderStarted) {
            recorder?.stop()
            recorder?.reset()
        }

        recorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder?.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
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
        }
        mediaRecorderStarted = false
    }

    private fun getFilePath() : String {
        return getExternalFilesDir(null)?.absolutePath + "/${getFileName()}"
    }

    private fun getFileName() : String {
        val tz = TimeZone.getTimeZone("UTC")
        val df = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        df.timeZone = tz
        val nowAsISO = df.format(Date())
        val fileName = "eardrum-${nowAsISO}.mp3"
        return fileName
    }

    private fun setNextAlarm() {
        var interval = 10*1000 // Stop a recording after 10 seconds

        if (!mediaRecorderStarted) {
            interval = 60*1000 // Start a new recording session every minute
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
