# Liquid Local Companion Provider

## Overview
Lighthouse Handoff is a vanilla Chrome extension (ES modules, no build step). The Liquid LEAP SDK, which powers our AI engine, is a native/JVM/desktop library. To avoid bloating the extension and to take advantage of native model execution, the `liquid-local` provider delegates AI analysis to a local companion app running on the user's machine.

## Why a Local Companion?
1. **Extension Size & Constraints**: Chrome extensions have strict memory, WASM, and CSP constraints that complicate running a full LLM in the browser.
2. **Native Performance**: The Liquid LEAP SDK is designed to run efficiently on native targets (iOS, macOS, Android, Windows) and JVM.
3. **Clean Architecture**: The extension only needs to send normalized Lighthouse data and receive structured JSON back.

## Required Endpoint Contract
The local companion app must expose an HTTP endpoint (default: `http://localhost:31313/analyze`) that accepts a POST request.

### Request Body
```json
{
  "report": { /* Normalized LighthouseSummary */ },
  "rankedIssues": [ /* Array of RankedIssue */ ],
  "outputMode": "cursor",
  "schemaVersion": "1.0"
}
```

### Expected Response
The endpoint must return a JSON object matching the `AIAnalysis` schema:
```json
{
  "executiveSummary": "...",
  "quickWins": ["...", "..."],
  "priorityFixes": [
    {
      "id": "audit-id",
      "title": "Fix title",
      "reasoning": "...",
      "instructions": ["Step 1", "Step 2"]
    }
  ],
  "acceptanceCriteria": ["..."]
}
```

## Health Check
The provider checks `GET http://localhost:31313/health` to verify the companion is running.
Expected Response:
```json
{
  "status": "ok",
  "provider": "liquid",
  "model": "LFM2.5-350M",
  "ready": true
}
```

## Future Companion App Requirements
- Provide a simple GUI or CLI to start/stop the service.
- Handle downloading and caching Liquid models using the LEAP SDK.
- Enforce constrained generation to ensure the output matches the `AIAnalysis` JSON schema.
- Gracefully handle CORS requests from `chrome-extension://*`.
