package io.github.nicolasraoul.edgevibe

import android.content.Context
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.TextPart
import com.google.ai.edge.aicore.generationConfig
import com.google.ai.edge.aicore.GenerativeModel as EdgeGenerativeModel
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

enum class AiBackend(val displayName: String) {
    MLKIT("MLKit (Gemini Nano)"),
    AICORE("AI Edge SDK (Gemini Nano)"),
    QWEN("Qwen 3.5 2B (MediaPipe)")
}

suspend fun runAgentLocal(
    context: Context,
    selectedBackend: AiBackend,
    mlkitModel: Any,
    scope: CoroutineScope,
    promptText: String,
    onChunk: (String) -> Unit
): String {
    return withContext(Dispatchers.IO) {
        when (selectedBackend) {
            AiBackend.MLKIT -> {
                val client = mlkitModel as com.google.mlkit.genai.prompt.GenerativeModel
                val request = GenerateContentRequest.Builder(TextPart(promptText)).apply {
                    maxOutputTokens = 2048
                }.build()
                var accumulatedText = ""
                client.generateContentStream(request).collect { response ->
                    val chunk = response.candidates.firstOrNull()?.text ?: ""
                    accumulatedText += chunk
                    withContext(Dispatchers.Main) {
                        onChunk(chunk)
                    }
                }
                accumulatedText
            }
            AiBackend.AICORE -> {
                val edgeModel = EdgeGenerativeModel(
                    generationConfig {
                        this.context = context.applicationContext
                    }
                )
                var accumulatedText = ""
                edgeModel.generateContentStream(promptText).collect { response ->
                    val chunk = response.text ?: ""
                    accumulatedText += chunk
                    withContext(Dispatchers.Main) {
                        onChunk(chunk)
                    }
                }
                edgeModel.close()
                accumulatedText
            }
            AiBackend.QWEN -> {
                val modelFile = File(context.getExternalFilesDir(null), "qwen.bin")
                if (!modelFile.exists()) {
                    throw Exception("Qwen model not found.")
                }
                val deferredResult = CompletableDeferred<String>()
                var accumulatedText = ""
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(8192)
                    .setResultListener { partialResult, done ->
                        accumulatedText += partialResult
                        scope.launch(Dispatchers.Main) {
                            onChunk(partialResult)
                        }
                        if (done) {
                            deferredResult.complete(accumulatedText)
                        }
                    }
                    .build()
                val qwenModel = LlmInference.createFromOptions(context, options)
                qwenModel.generateResponseAsync(promptText)
                val result = deferredResult.await()
                qwenModel.close()
                result
            }
        }
    }
}
