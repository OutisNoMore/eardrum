package com.miguelrochefort.eardrum

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.listPreference
import me.zhanghai.compose.preference.preference
import me.zhanghai.compose.preference.sliderPreference
import me.zhanghai.compose.preference.switchPreference
import me.zhanghai.compose.preference.textFieldPreference
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.util.UUID


class MainActivity : ComponentActivity() {

    private val listener: OnSharedPreferenceChangeListener? = null
    private var sharedPreferences: SharedPreferences? = null

    class Constants {
        companion object {
            const val RECORDING_PERMISSIONS = 1
            const val UUID_PREF_KEY = "uuid"
        }
    }

    private lateinit var sensorID: String

    override fun onCreate(savedInstanceState: Bundle?) {
        sensorID = getSensorID()
        super.onCreate(savedInstanceState)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        setContent {
            Scaffold_Main()
        }
    }

    private fun getSensorID(): String {
        var uuid = sharedPreferences?.getString(Constants.UUID_PREF_KEY, null)
        if (uuid == null) {
            uuid = UUID.randomUUID().toString()
            sharedPreferences?.edit()?.putString(Constants.UUID_PREF_KEY, uuid)?.apply()
        }
        return uuid
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Preview
    @Composable
    fun Scaffold_Main() {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = {
                        Text("Eardrum")
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        val enabled = sharedPreferences?.getBoolean("enable_recording", true)
                        val recording_length = sharedPreferences?.getFloat("recording_length", 1.0F)
                        val recording_interval = sharedPreferences?.getFloat("recording_interval", 10.0f)
                        val media_format = sharedPreferences?.getString("media_format", "mp4")
                        val api_endpoint = sharedPreferences?.getString("api_endpoint", "http://10.0.2.2/")

                        if (enabled == true) {
                            startAudioRecorderService();
                        } else {
                            stopAudioRecorderService();
                        }
                    }) {
                    Icon(Icons.Default.Check, contentDescription = "Add")
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SettingsPage()
            }
        }
    }

    @Composable
    fun SettingsPage() {
        ProvidePreferenceLocals {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                preference(
                    key = "preference",
                    title = { Text(text = "Preference") },
                    summary = { Text(text = "Summary") }
                ) {}
                switchPreference(
                    key = "enable_recording",
                    defaultValue = true,
                    title = { Text(text = "Enable recording") },
                    summary = { Text(text = if (it) "On" else "Off") }
                )
                sliderPreference(
                    key = "recording_length",
                    defaultValue = 1f,
                    title = { Text(text = "Recording length") },
                    valueRange = 0f..5f,
                    valueSteps = 9,
                    summary = { Text(text = "Minutes") },
                    valueText = { Text(text = "%.1f".format(it)) }
                )
                sliderPreference(
                    key = "recording_interval",
                    defaultValue = 10f,
                    title = { Text(text = "Recording interval") },
                    valueRange = 10f..60f,
                    valueSteps = 4,
                    summary = { Text(text = "Minutes") },
                    valueText = { Text(text = "%.1f".format(it)) }
                )
                listPreference(
                    key = "media_format",
                    defaultValue = "mp4",
                    values = listOf("opus", "mp4", "3gpp"),
                    title = { Text(text = "Media format") },
                    summary = { Text(text = it) },
                    type = ListPreferenceType.DROPDOWN_MENU
                )
                textFieldPreference(
                    key = "api_endpoint",
                    defaultValue = "http://10.0.2.2/",
                    title = { Text(text = "API endpoint address") },
                    textToValue = { it },
                    summary = { Text(text = it) }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()

        startAudioRecorderServiceWithPermissions()
    }

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
        } else {
            EasyPermissions.requestPermissions(
                this,
                getString(R.string.recording_permission_rationale),
                Constants.RECORDING_PERMISSIONS,
                *perms
            )
        }
    }

    private fun startAudioRecorderService() {
        val intent = Intent(this, AudioRecorderService::class.java)
        intent.putExtra("sensorID", sensorID)
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
}
