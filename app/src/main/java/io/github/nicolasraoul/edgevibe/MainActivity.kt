package io.github.nicolasraoul.edgevibe

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.TextPart
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    var prompt by remember { mutableStateOf("") }
    var generatedHtml by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showOpenDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var suggestedName by remember { mutableStateOf("") }
    var viewPromptInResult by remember { mutableStateOf(false) }
    var savedWebapps by remember { mutableStateOf(listOf<SavedWebapp>()) }

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
                            try {
                                val fullPrompt = """
                                    You are an expert HTML dev, writing a one-file HTML webapp that fit in less than 100kB. No <head> nor style= nor any CSS. All JavaScript must be in the same HTML file, not as separate .js file. Only output the HTML.
                                    # Webapp:
                                    $prompt
                                """.trimIndent()

                                val request = GenerateContentRequest.Builder(TextPart(fullPrompt)).build()
                                val response = mlkitModel.generateContent(request)
                                val text = response.candidates.firstOrNull()?.text ?: ""
                                generatedHtml = cleanHtml(text)
                                viewPromptInResult = false
                            } catch (e: Exception) {
                                errorMessage = e.localizedMessage ?: "Unknown error"
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
                    viewPrompt = viewPromptInResult,
                    onToggleView = { viewPromptInResult = !viewPromptInResult },
                    onRetry = {
                        generatedHtml = null
                        errorMessage = null
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
                    onCopyPrompt = {
                        clipboardManager.setText(AnnotatedString(prompt))
                    }
                )
            }
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
                                        viewPromptInResult = false
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
    viewPrompt: Boolean,
    onToggleView: () -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onCopyPrompt: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                TextButton(onClick = onToggleView) {
                    Text(if (viewPrompt) "View App" else "View Prompt")
                }
            }
            Row {
                if (viewPrompt) {
                    IconButton(onClick = onCopyPrompt) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Prompt")
                    }
                }
                IconButton(onClick = onSave) {
                    Icon(Icons.Default.Save, contentDescription = "Save")
                }
            }
        }
        
        if (viewPrompt) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("Prompt:", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(prompt)
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        webViewClient = WebViewClient()
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
                    val prompt = File(dir, "prompt.txt").readText()
                    val html = File(dir, "app.html").readText()
                    val name = File(dir, "name.txt").readText()
                    list.add(SavedWebapp(name, prompt, html))
                }
            }
        }
    } catch (e: Exception) {
        Log.e("EdgeVibe", "Load failed", e)
    }
    return list
}
