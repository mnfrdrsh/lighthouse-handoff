# Liquid Local Companion (Real SDK Spike)

This is a Kotlin Multiplatform / JVM Desktop companion app designed to run the real Liquid LEAP SDK.

## Purpose

The Chrome extension sends normalized Lighthouse reports to `http://localhost:31313`.
This app listens on that port, loads the `LFM2.5-350M` model using the Liquid LEAP SDK, formats the report into a prompt, executes constrained JSON generation, and returns a valid `AIAnalysis` back to the extension.

## Endpoints

* `GET /health` - Returns the health status, provider, model name, and readiness of the loaded model.
* `POST /analyze` - Takes the normalized report + ranked issues, and returns an `AIAnalysis` JSON.

## Environment Config

You can configure the server using environment variables:
```bash
LIQUID_MODEL_ID=LFM2.5-350M
PORT=31313
```

## Setup & Running

**Required Java version**: JDK 21 LTS
* Java 25 is not recommended for this project right now
* Java 8 is too old
* Confirm Java with:

```powershell
java -version
echo $env:JAVA_HOME
```

Expected:

```txt
java version "21..."
JAVA_HOME points to JDK 21
```

**Prerequisite:** If you do not have Gradle installed globally, you must install it to generate the wrapper (`gradle wrapper`) or run the project for the first time.

Run using Gradle:

**macOS / Linux:**
```bash
cd companion-liquid
./gradlew run
```

**Windows:**
```powershell
cd companion-liquid
.\gradlew.bat run
```

## Testing the API

Check health (even if model is not loaded):
```bash
curl http://localhost:31313/health
```

Analyze a payload:
```bash
curl -X POST http://localhost:31313/analyze \
  -H "Content-Type: application/json" \
  -d @sample-analyze-request.json
```

_Note: This is an experimental spike. The actual Liquid LEAP SDK APIs may vary based on SDK version and platform constraints._
