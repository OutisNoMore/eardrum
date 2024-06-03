package com.miguelrochefort.eardrum

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
    Previous plan was to send serialized status, but currently send
    to the API as separate form data parts.
 */
@Serializable
@SerialName("Recording")
data class Status(
    @SerialName("sensor_id") val sensorID: String,
    val timestamp: String,
    val lat: Float,
    val lon: Float,
    val accuracy: Float,
    @SerialName("battery") val battery: Float,
    @SerialName("temperature") val temperature: Double,
)
