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
    val maxConcurrent: Int,
    val items: List<Long>,
    val schedulerEnabled: Boolean,
    val activeDays: List<String>,
    val autoStartEnabled: Boolean,
    val startTime: String,
    val autoStopEnabled: Boolean,
    val endTime: String,
    val stopQueueOnEmpty: Boolean,
)
