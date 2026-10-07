package com.abdownloadmanager.integration.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiDownloadPart(
    val id: Long,
    val downloaded: Long,
    val size: Long?,
    val percent: Int?,
    val speed: Long,
    val status: String,
    val error: String? = null,
)