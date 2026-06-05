package com.lighthouse.handoff.companion

import kotlinx.serialization.json.*

object PromptBuilder {

    fun buildPrompt(request: AnalyzeRequest): String {
        val reportObj = request.report.jsonObject
        val url = reportObj["url"] ?: reportObj["finalUrl"] ?: JsonNull
        val strategy = reportObj["strategy"] ?: reportObj["configSettings"]?.jsonObject?.get("formFactor") ?: JsonNull
        val scores = reportObj["scores"] ?: reportObj["categories"] ?: JsonNull
        val metrics = reportObj["metrics"] ?: JsonNull
        
        val topIssues = request.rankedIssues.take(3)

        return """
            You are generating a Lighthouse Handoff analysis.

            Return ONLY valid JSON. Do not include markdown or prose.
            
            Use this exact schema:
            {
              "executiveSummary": "string",
              "quickWins": ["action 1", "action 2"],
              "priorityFixes": [
                {
                  "title": "string",
                  "reasoning": "string",
                  "instructions": ["concrete step 1", "concrete step 2", "validation step"]
                }
              ],
              "acceptanceCriteria": ["criteria 1", "criteria 2"],
              "limitations": ["limitation 1", "limitation 2"]
            }
            
            CRITICAL SCHEMA RULES:
            - Arrays `quickWins`, `acceptanceCriteria`, and `limitations` MUST contain STRINGS ONLY. Do NOT place objects inside these arrays.
            - Only `priorityFixes` may contain objects.
            - Each `instructions` value MUST be an array of strings.
            
            OUTPUT QUALITY MINIMUMS:
            - Each priority fix MUST include at least 3 instruction strings.
            - Each priority fix MUST include at least 1 validation instruction.
            - NO placeholder wording (e.g. no "step 1", "fix the issue").
            
            INSTRUCTION EXAMPLES:
            Bad: "step 1", "fix the issue", "optimize performance", "test on mobile devices"
            Good: "Inspect the Lighthouse audit ID and affected resources listed.", "If the LCP element is an image, ensure it is not lazy-loaded.", "Use Chrome DevTools Coverage to identify unused JavaScript.", "Rerun Lighthouse mobile after the change and confirm the metric improves."
            
            ISSUE-SPECIFIC TEMPLATES:
            - LCP: identify LCP element, check image priority, avoid lazy-loading LCP image, set dimensions, consider preload/fetchpriority.
            - CLS: inspect layout shift elements, reserve space for images/embeds, avoid injecting content above existing content, verify CLS after rerun.
            - Unused JS: inspect listed script URLs, use DevTools Coverage, defer/split/remove only after confirming usage, avoid breaking interactive behavior.
            - Render-blocking: identify blocking CSS/JS, inline critical CSS if appropriate, defer non-critical JS, verify first render/LCP impact.
            - Images: inspect listed image URLs, use responsive sizes, compress/convert where appropriate, verify visual quality.
            
            CRITICAL RULES:
            1. Do not invent file paths, URLs, or code components that are not explicitly present in the data below.
            2. Base all fixes directly on the provided ranked issues.
            
            Analyze the following report and issues:
            
            URL: $url
            STRATEGY: $strategy
            SCORES: $scores
            METRICS: $metrics
            
            TOP RANKED ISSUES (Max 3):
            $topIssues
            
            OUTPUT MODE: ${request.outputMode}
        """.trimIndent()
    }
}
