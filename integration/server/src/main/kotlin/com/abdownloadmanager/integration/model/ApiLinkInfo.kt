package com.abdownloadmanager.integration.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiLinkInfo(
    val url: String,
    val name: String,
    val size: Long?,
    val sizeText: String?,
    val status: String,
    val error: String?,
)
