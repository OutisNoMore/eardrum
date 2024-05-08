package com.miguelrochefort.eardrum

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("Recording")
data class Recording(
  val filePath: String,
  @SerialName("sensor_id") val sensorID: String,
  val lat: Float,
  val lon: Float,
  val accuracy: Float,
  @SerialName("battery") val batteryPercentage: Float,
  @SerialName("temperature") val batteryTemperature: Double,
)
