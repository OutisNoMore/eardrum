package com.miguelrochefort.eardrum

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.File

/*
    This is the worker that takes audio files and processes them using the birdnet model
    then it sends the resulting csv files to the server.
 */
class AudioProcessingWorker (appContext: Context, workerParams: WorkerParameters): Worker(appContext, workerParams) {
    private val ctx = appContext
    private val apiURL = inputData.getString("api_url")
    private val mediaFormat = inputData.getString("media_format")
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    /*
        Send csv file with outputs of birdnet to the server through network http request
     */
    private fun uploadData(filePath: String) {
        val client = OkHttpClient()
        val mediaString = filePath.substringAfterLast(".", "")

        // Create the request body
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("sensor_id", "foo")
            .addFormDataPart("timestamp", "foo")
            .addFormDataPart("lat", "foo")
            .addFormDataPart("lon", "foo")
            .addFormDataPart("accuracy", "foo")
            .addFormDataPart("battery", "foo")
            .addFormDataPart("temperature", "foo")
            .addFormDataPart(
                "csv_file",
                filePath,                   // why use outputfilepath instead of audio file path
                File(filePath).asRequestBody(mediaString?.toMediaType())
            )
            .build()

        // Add the headers and build request
        val request = apiURL?.let {
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
       Start worker that scans internal directory for audio files, then calls upload data
       to send the file through network.
     */
    override fun doWork(): Result {
        // Use media scanner to update folder to get all files
        MediaScannerConnection.scanFile(
            ctx, arrayOf(ctx.getExternalFilesDir(null)?.absolutePath), null
        ) { path, _ ->
            // Process each file - TODO: do asynchronously? in parallel/ optimize
            for (file in ctx.getExternalFilesDir(null)?.absolutePath?.let { File(it).listFiles() }!!) {
                if (file.extension == mediaFormat) {
                    // TODO: Process audio file using birdnet
                    uploadData(path)
                }
            }
        }
        return Result.success()
    }
}
