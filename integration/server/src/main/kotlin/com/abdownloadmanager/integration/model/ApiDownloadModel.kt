package com.abdownloadmanager.integration.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiDownloadModel(
    val id: Long,
    val name: String,
    val folder: String,
    val size: Long,
    val progress: Long,
    val percent: Int?,
    val speed: Long,
    val eta: Long?,
    val status: String,
    val downloadLink: String,
    val dateAdded: Long,
    val startTime: Long,
    val completeTime: Long,
)
