package com.abdownloadmanager.integration.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiBrowserItem(
    val name: String,
    val path: String,
    val directory: Boolean,
    val size: Long,
    val modified: Long,
)

@Serializable
data class ApiBrowserResponse(
    val path: String,
    val parent: String?,
    val items: List<ApiBrowserItem>,
)
