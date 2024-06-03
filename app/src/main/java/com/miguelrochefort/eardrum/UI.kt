package com.miguelrochefort.eardrum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
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
import me.zhanghai.compose.preference.sliderPreference
import me.zhanghai.compose.preference.switchPreference
import me.zhanghai.compose.preference.textFieldPreference

class UI {
    /*
        Main page for UI. Simple Jetpack Compose window with a top
        bar and material colors. Settings options will be created inside.
        Can be previewed in Android Studio without building
     */
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

    /*
        All settings options are created with functions from the following project
        https://github.com/zhanghai/ComposePreference
        Settings get recorded to the default SharedPreferences for the app and
        can be accessed using getDefaultSharedPreferences
        More settings can easily be added and will be appended to the LazyColumn
        TODO: Look at AudioMoth and implement some settings from there
     */
    @Composable
    fun SettingsPage() {
        ProvidePreferenceLocals {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                switchPreference(
                    key = "enable_recording",
                    defaultValue = true,
                    title = { Text(text = "Enable recording") },
                    summary = { Text(text = if (it) "On" else "Off") }
                )
                sliderPreference(
                    key = "recording_length",
                    defaultValue = Constants.RECORDING_LENGTH,
                    title = { Text(text = "Recording length") },
                    valueRange = 0.5f..5f,
                    valueSteps = 8,
                    summary = { Text(text = "Minutes") },
                    valueText = { Text(text = "%.1f".format(it)) }
                )
                sliderPreference(
                    key = "recording_interval",
                    defaultValue = Constants.RECORDING_INTERVAL,
                    title = { Text(text = "Recording interval") },
                    valueRange = 1f..10f,
                    valueSteps = 8,
                    summary = { Text(text = "Minutes") },
                    valueText = { Text(text = "%.1f".format(it)) }
                )
                listPreference(
                    key = "media_format",
                    defaultValue = Constants.MEDIA_FORMAT,
                    values = listOf("opus", "mp4", "3gpp"),
                    title = { Text(text = "Media format") },
                    summary = { Text(text = it) },
                    type = ListPreferenceType.DROPDOWN_MENU
                )
                textFieldPreference(
                    key = "api_endpoint",
                    defaultValue = Constants.API_URL,
                    title = { Text(text = "API endpoint address") },
                    textToValue = { it },
                    summary = { Text(text = it) }
                )
            }
        }
    }
}
