package com.abdownloadmanager.integration.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiQueueModel(
    val id: Long,
    val name: String,
    val active: Int,
    val queued: Int,
    val total: Int,
    val running: Boolean,
)
