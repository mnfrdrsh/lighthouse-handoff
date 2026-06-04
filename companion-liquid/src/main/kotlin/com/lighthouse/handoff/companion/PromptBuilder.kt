package com.lighthouse.handoff.companion

object PromptBuilder {

    fun buildPrompt(request: AnalyzeRequest): String {
        return """
            You are generating a Lighthouse Handoff analysis.

            Return ONLY valid JSON.
            
            Do not include markdown.
            
            Do not include prose outside JSON.
            
            Use this exact schema:
            {
              "executiveSummary": "string",
              "quickWins": ["string"],
              "priorityFixes": [
                {
                  "title": "string",
                  "reasoning": "string",
                  "instructions": ["string"]
                }
              ],
              "acceptanceCriteria": ["string"],
              "limitations": ["string"]
            }
            
            Analyze the following report and issues to generate the JSON response:
            
            REPORT:
            ${request.report}
            
            RANKED ISSUES:
            ${request.rankedIssues}
            
            OUTPUT MODE: ${request.outputMode}
        """.trimIndent()
    }
}
