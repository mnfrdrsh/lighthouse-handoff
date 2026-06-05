package com.lighthouse.handoff.companion

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class HealthResponse(
    val status: String,
    val provider: String,
    val model: String?,
    val ready: Boolean
)

@Serializable
data class AnalyzeRequest(
    val report: JsonElement,
    val rankedIssues: List<JsonElement>,
    val outputMode: String,
    val schemaVersion: String,
    val modelId: String? = null
)

@Serializable
data class PriorityFix(
    val id: String? = null,
    val title: String,
    val reasoning: String,
    val instructions: List<String>
)

@Serializable
data class AIAnalysis(
    val executiveSummary: String,
    val quickWins: List<String>,
    val priorityFixes: List<PriorityFix>,
    val acceptanceCriteria: List<String>,
    val limitations: List<String>? = null,
    val modelId: String? = null
)

@Serializable
data class ErrorResponse(
    val error: String,
    val code: String
)
