# Liquid LEAP SDK Notes

Based on decompiling and inspecting the following exact jar path from the Gradle cache:
`C:\Users\johnp\.gradle\caches\modules-2\files-2.1\ai.liquid.leap\leap-sdk-jvm\0.10.7\be48125fa381f445d1f3e468950d6e84ef882e0c\leap-sdk-jvm-0.10.7.jar`
and its sources jar `.../leap-sdk-jvm-0.10.7-sources.jar`, here are the true SDK APIs to interact with local Liquid models.

## 1. Loading a Model

Models are loaded asynchronously via the `LeapClient` singleton, which returns a `ModelRunner`.

```kotlin
import ai.liquid.leap.LeapClient
import ai.liquid.leap.ModelRunner

// Assumes modelPath is the local path to the downloaded bundle
val runner: ModelRunner = LeapClient.loadModel(modelPath)
```

## 2. Creating a Conversation

Once you have a `ModelRunner`, you can spawn a `Conversation` session. This manages the chat history context.

```kotlin
import ai.liquid.leap.Conversation

val conversation: Conversation = runner.createConversation()
```

## 3. Running Generation & Streaming Output

Instead of a simple synchronous `generate()` method, the SDK embraces Kotlin Coroutines flows. The `conversation.generateResponse(prompt)` method appends the user message and returns a `Flow<MessageResponse>`.

```kotlin
import ai.liquid.leap.message.MessageResponse
import kotlinx.coroutines.flow.Flow

val prompt = "Analyze the following UI elements..."
val flow: Flow<MessageResponse> = conversation.generateResponse(prompt)
```

## 4. Extracting the Text

The `MessageResponse` comes in chunks. To get the final text, you iterate over the flow and concatenate the `MessageResponse.Chunk` text properties. (Alternatively, wait for the terminal `MessageResponse.Complete` and read the full appended history).

```kotlin
var generatedText = ""

flow.collect { response ->
    when (response) {
        is MessageResponse.Chunk -> {
            generatedText += response.text
        }
        is MessageResponse.Complete -> {
            // Generation finished successfully.
            // You can also access response.fullMessage here.
        }
        is MessageResponse.ReasoningChunk -> {
            // Specialized chunk for reasoning models
        }
        is MessageResponse.FunctionCalls -> {
            // Function/tool calling outputs
        }
        else -> {}
    }
}

println("Final Output: $generatedText")
```

## Implementation Note

The `LiquidEngine.kt` file has already been successfully updated to use these exact classes and interfaces. The server compiles perfectly, and any further features should strictly rely on these Flow-based APIs.

## Model File Requirements

The recommended approach to loading models is via the `LeapDownloader`, which handles manifest resolution, downloading missing files into a local cache directory, and spinning up the `ModelRunner`.

* **Expected path type**: Local directory for the SDK to store/cache downloaded models.
* **Example Windows path**: `D:\ai-models\liquid-leap`
* **Env Vars**:
  - `LIQUID_MODEL_CACHE_DIR`: the local cache directory path.
  - `LIQUID_MODEL_ID`: the model ID (e.g. `LFM2.5-350M`).
  - `LIQUID_MODEL_QUANTIZATION`: the quantization format (e.g. `Q4_K_M`).

```kotlin
import ai.liquid.leap.manifest.LeapDownloader
import ai.liquid.leap.manifest.LeapDownloaderConfig

val downloader = LeapDownloader(LeapDownloaderConfig(saveDir = cacheDir))
val runner = downloader.loadModel(modelName = modelId, quantizationType = quantization)
```
