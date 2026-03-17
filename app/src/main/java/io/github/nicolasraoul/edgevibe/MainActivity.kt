package io.github.nicolasraoul.edgevibe

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.TextPart
import com.google.ai.edge.aicore.generationConfig
import com.google.ai.edge.aicore.GenerativeModel as EdgeGenerativeModel
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EdgeVibeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun EdgeVibeTheme(content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = Color(0xFF6200EE),
        onPrimary = Color.White,
        secondary = Color(0xFF03DAC6),
        onSecondary = Color.Black,
        background = Color(0xFFF5F5F5)
    )
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

data class SavedWebapp(val name: String, val prompt: String, val html: String)

enum class ViewMode {
    APP, PROMPT, HTML, ERRORS
}

enum class AiBackend(val displayName: String) {
    MLKIT("MLKit (Gemini Nano)"),
    AICORE("AI Edge SDK (Gemini Nano)"),
    QWEN("Qwen 3.5 2B (MediaPipe)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    var prompt by remember { mutableStateOf("") }
    var generatedHtml by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showOpenDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var suggestedName by remember { mutableStateOf("") }
    var currentViewMode by remember { mutableStateOf(ViewMode.APP) }
    var savedWebapps by remember { mutableStateOf(listOf<SavedWebapp>()) }
    var webViewErrors by remember { mutableStateOf(listOf<String>()) }
    var selectedBackend by remember { mutableStateOf(AiBackend.AICORE) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val mlkitModel = remember { Generation.getClient() }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        savedWebapps = loadSavedWebapps(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("EdgeVibe", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    if (generatedHtml == null) {
                        IconButton(onClick = { showOpenDialog = true }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Open")
                        }
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (generatedHtml == null) {
                PromptScreen(
                    prompt = prompt,
                    onPromptChange = { prompt = it },
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onGenerate = {
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            webViewErrors = emptyList()
                            try {
                                val fullPrompt = """
                                    You are an expert HTML dev, writing a one-file HTML webapp that fit in less than 100kB. No <head> nor style= nor any CSS. All JavaScript must be in the same HTML file, not as separate .js file. Only output the HTML.
                                    # Webapp:
                                    $prompt
                                """.trimIndent()

                                val text = withContext(Dispatchers.IO) {
                                    when (selectedBackend) {
                                        AiBackend.MLKIT -> {
                                            val request = GenerateContentRequest.Builder(TextPart(fullPrompt)).apply {
                                                maxOutputTokens = 256
                                            }.build()
                                            mlkitModel.generateContent(request).candidates.firstOrNull()?.text ?: ""
                                        }
                                        AiBackend.AICORE -> {
                                            val edgeModel = EdgeGenerativeModel(
                                                generationConfig {
                                                    this.context = context.applicationContext
                                                }
                                            )
                                            val response = edgeModel.generateContent(fullPrompt)
                                            edgeModel.close()
                                            response.text ?: ""
                                        }
                                        AiBackend.QWEN -> {
                                            val modelFile = File(context.getExternalFilesDir(null), "qwen.bin")
                                            if (!modelFile.exists()) {
                                                throw Exception("Qwen model not found. Please download a MediaPipe-compatible .bin to ${modelFile.absolutePath}")
                                            }
                                            val options = LlmInference.LlmInferenceOptions.builder()
                                                .setModelPath(modelFile.absolutePath)
                                                .setMaxTokens(8192)
                                                .build()
                                            val qwenModel = LlmInference.createFromOptions(context, options)
                                            val response = qwenModel.generateResponse(fullPrompt)
                                            qwenModel.close()
                                            response ?: ""
                                        }
                                    }
                                }

                                Log.e("EdgeVibe", "Generation finished. Response length: ${text.length}")
                                generatedHtml = cleanHtml(text)
                                currentViewMode = ViewMode.APP
                            } catch (e: Exception) {
                                errorMessage = e.localizedMessage ?: "Unknown error"
                                Log.e("EdgeVibe", "Generation failed", e)
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                )
            } else {
                ResultScreen(
                    html = generatedHtml!!,
                    prompt = prompt,
                    viewMode = currentViewMode,
                    errors = webViewErrors,
                    onViewModeChange = { currentViewMode = it },
                    onRetry = {
                        generatedHtml = null
                        errorMessage = null
                        webViewErrors = emptyList()
                    },
                    onSave = {
                        scope.launch {
                            try {
                                val nameRequest = GenerateContentRequest.Builder(TextPart("Suggest a very short name (max 3 words) for a webapp described as: $prompt. Only output the name.")).build()
                                val nameResponse = mlkitModel.generateContent(nameRequest)
                                suggestedName = nameResponse.candidates.firstOrNull()?.text?.trim()?.replace("\"", "") ?: "My Webapp"
                                showSaveDialog = true
                            } catch (e: Exception) {
                                suggestedName = "My Webapp"
                                showSaveDialog = true
                            }
                        }
                    },
                    onCopyContent = { text ->
                        clipboardManager.setText(AnnotatedString(text))
                    },
                    onAddError = { error ->
                        webViewErrors = webViewErrors + error
                    }
                )
            }
        }

        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("AI Backend Settings") },
                text = {
                    Column {
                        Text("Select On-Device AI Model:", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        AiBackend.values().forEach { backend ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedBackend = backend }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = (selectedBackend == backend),
                                    onClick = { selectedBackend = backend }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(backend.displayName)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingsDialog = false }) { Text("Close") }
                }
            )
        }

        if (showOpenDialog) {
            AlertDialog(
                onDismissRequest = { showOpenDialog = false },
                title = { Text("Open Webapp") },
                text = {
                    if (savedWebapps.isEmpty()) {
                        Text("No saved webapps found.")
                    } else {
                        LazyColumn {
                            items(savedWebapps) { webapp ->
                                ListItem(
                                    headlineContent = { Text(webapp.name) },
                                    modifier = Modifier.clickable {
                                        generatedHtml = webapp.html
                                        prompt = webapp.prompt
                                        showOpenDialog = false
                                        currentViewMode = ViewMode.APP
                                        webViewErrors = emptyList()
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showOpenDialog = false }) { Text("Close") }
                }
            )
        }

        if (showSaveDialog) {
            var nameToSave by remember { mutableStateOf(suggestedName) }
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text("Save Webapp") },
                text = {
                    TextField(
                        value = nameToSave,
                        onValueChange = { nameToSave = it },
                        label = { Text("Webapp Name") }
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        saveWebapp(context, nameToSave, prompt, generatedHtml!!) {
                            scope.launch { savedWebapps = loadSavedWebapps(context) }
                        }
                        showSaveDialog = false
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun PromptScreen(
    prompt: String,
    onPromptChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onGenerate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Describe your webapp",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            modifier = Modifier.fillMaxWidth().height(150.dp),
            placeholder = { Text("e.g. A random addition quiz with a check button") },
            shape = MaterialTheme.shapes.medium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onGenerate,
            enabled = !isLoading && prompt.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Generate webapp", fontSize = 16.sp)
            }
        }
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ResultScreen(
    html: String,
    prompt: String,
    viewMode: ViewMode,
    errors: List<String>,
    onViewModeChange: (ViewMode) -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onCopyContent: (String) -> Unit,
    onAddError: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Navigation row
        ScrollableTabRow(
            selectedTabIndex = viewMode.ordinal,
            edgePadding = 8.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            divider = {}
        ) {
            Tab(selected = viewMode == ViewMode.APP, onClick = { onViewModeChange(ViewMode.APP) }) {
                Text("App", modifier = Modifier.padding(12.dp))
            }
            Tab(selected = viewMode == ViewMode.PROMPT, onClick = { onViewModeChange(ViewMode.PROMPT) }) {
                Text("Prompt", modifier = Modifier.padding(12.dp))
            }
            Tab(selected = viewMode == ViewMode.HTML, onClick = { onViewModeChange(ViewMode.HTML) }) {
                Text("HTML", modifier = Modifier.padding(12.dp))
            }
            Tab(selected = viewMode == ViewMode.ERRORS, onClick = { onViewModeChange(ViewMode.ERRORS) }) {
                BadgedBox(badge = { if (errors.isNotEmpty()) Badge { Text(errors.size.toString()) } }) {
                    Text("Errors", modifier = Modifier.padding(12.dp))
                }
            }
        }

        // Action row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onRetry) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Row {
                if (viewMode != ViewMode.APP) {
                    IconButton(onClick = {
                        val textToCopy = when(viewMode) {
                            ViewMode.PROMPT -> prompt
                            ViewMode.HTML -> html
                            ViewMode.ERRORS -> errors.joinToString("\n")
                            else -> ""
                        }
                        onCopyContent(textToCopy)
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                }
                IconButton(onClick = onSave) {
                    Icon(Icons.Default.Save, contentDescription = "Save")
                }
            }
        }
        
        Box(modifier = Modifier.fillMaxSize()) {
            when (viewMode) {
                ViewMode.APP -> {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                webViewClient = WebViewClient()
                                webChromeClient = object : WebChromeClient() {
                                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                        consoleMessage?.let {
                                            if (it.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                                onAddError("${it.message()} (at line ${it.lineNumber()})")
                                            }
                                        }
                                        return super.onConsoleMessage(consoleMessage)
                                    }
                                }
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                            }
                        },
                        update = { webView ->
                            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                        }
                    )
                }
                ViewMode.PROMPT -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                        Text(prompt)
                    }
                }
                ViewMode.HTML -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                        Text(html, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
                ViewMode.ERRORS -> {
                    if (errors.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No errors detected.")
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            items(errors) { error ->
                                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 4.dp))
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

fun cleanHtml(input: String): String {
    var cleaned = input
    val startIndex = cleaned.indexOf("```html", ignoreCase = true)
    if (startIndex != -1) {
        cleaned = cleaned.substring(startIndex + 7)
    } else {
        val genericStart = cleaned.indexOf("```")
        if (genericStart != -1) {
            cleaned = cleaned.substring(genericStart + 3)
        }
    }
    val endIndex = cleaned.lastIndexOf("```")
    if (endIndex != -1) {
        cleaned = cleaned.substring(0, endIndex)
    }
    return cleaned.trim()
}

fun saveWebapp(context: android.content.Context, name: String, prompt: String, html: String, onDone: () -> Unit) {
    try {
        val safeName = name.replace(Regex("[^a-zA-Z0-9]"), "_")
        val dir = File(context.getExternalFilesDir(null), "webapps/$safeName")
        dir.mkdirs()
        File(dir, "prompt.txt").writeText(prompt)
        File(dir, "app.html").writeText(html)
        File(dir, "name.txt").writeText(name)
        onDone()
    } catch (e: Exception) {
        Log.e("EdgeVibe", "Save failed", e)
    }
}

fun loadSavedWebapps(context: android.content.Context): List<SavedWebapp> {
    val list = mutableListOf<SavedWebapp>()
    try {
        val rootDir = File(context.getExternalFilesDir(null), "webapps")
        if (rootDir.exists()) {
            rootDir.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    val promptFile = File(dir, "prompt.txt")
                    val htmlFile = File(dir, "app.html")
                    val nameFile = File(dir, "name.txt")
                    if (promptFile.exists() && htmlFile.exists() && nameFile.exists()) {
                        list.add(SavedWebapp(nameFile.readText(), promptFile.readText(), htmlFile.readText()))
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e("EdgeVibe", "Load failed", e)
    }
    return list
}
