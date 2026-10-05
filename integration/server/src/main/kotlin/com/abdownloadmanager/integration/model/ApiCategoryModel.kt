package com.abdownloadmanager.integration.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiCategoryModel(
    val id: Long,
    val name: String,
    val path: String,
    val usePath: Boolean,
    val acceptedFileTypes: List<String>,
    val acceptedUrlPatterns: List<String>,
    val items: List<Long>,
    val defaultCategory: Boolean,
)
