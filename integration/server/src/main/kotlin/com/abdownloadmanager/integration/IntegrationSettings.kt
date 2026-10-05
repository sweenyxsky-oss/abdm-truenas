package com.abdownloadmanager.integration


data class IntegrationSettings(
    val port: Int,
    val apiKey: String?,
    val host: String = System.getenv("ABDM_API_HOST")?.takeIf { it.isNotBlank() } ?: "0.0.0.0",
)
