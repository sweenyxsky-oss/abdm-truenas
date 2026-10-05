package com.abdownloadmanager.integration.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiSettingsModel(
    val downloadFolder: String,
    val maxConcurrentDownloads: Int,
    val threadCount: Int,
    val speedLimit: Long,
    val maxDownloadRetryCount: Int,
    val dynamicPartCreation: Boolean,
    val sparseFileAllocation: Boolean,
    val useAverageSpeed: Boolean,
    val autoStartOnBoot: Boolean,
    val useCategoryByDefault: Boolean,
    val apiEnabled: Boolean,
    val apiPort: Int,
    val apiAuthEnabled: Boolean,
    val trackDeletedFilesOnDisk: Boolean,
    val deletePartialFileOnDownloadCancellation: Boolean,
)
