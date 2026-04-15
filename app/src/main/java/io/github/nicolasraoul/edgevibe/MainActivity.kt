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
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
    APP, PROMPT, LOG, HTML, ERRORS
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
    var skills by remember { mutableStateOf(listOf<Skill>()) }
    var selectedSkillIds by remember { mutableStateOf(setOf<String>()) }
    var showSkillsDialog by remember { mutableStateOf(false) }
    var fullPrompt by remember { mutableStateOf("") }
    var agents by remember { mutableStateOf(listOf<AgentState>()) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val mlkitModel = remember { Generation.getClient() }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        savedWebapps = loadSavedWebapps(context)
        skills = loadSkills(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("EdgeVibe: Vibe offline!", fontWeight = FontWeight.Bold)
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
            if (generatedHtml == null || isLoading) {
                PromptScreen(
                    prompt = prompt,
                    onPromptChange = { prompt = it },
                    selectedSkills = skills.filter { selectedSkillIds.contains(it.id) },
                    agents = agents,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onGenerate = {
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            webViewErrors = emptyList()



                            val selectedSkillsList = skills.filter { selectedSkillIds.contains(it.id) }
                            val skillsText = selectedSkillsList.joinToString("\n") { "${it.title}: ${it.content}" }

                            generateWebapp(
                                prompt = prompt,
                                skillsText = skillsText,
                                context = context,
                                selectedBackend = selectedBackend,
                                mlkitModel = mlkitModel,
                                scope = scope,
                                onAgentsChange = { updatedAgents ->
                                    agents = updatedAgents
                                },
                                onHtmlGenerated = { html ->
                                    generatedHtml = html
                                    currentViewMode = ViewMode.APP
                                    isLoading = false
                                },
                                onError = { error ->
                                    errorMessage = error
                                    isLoading = false
                                }
                            )
                        }
                    },
                    onSkillsClick = { showSkillsDialog = true },
                    onSkillRemove = { skill ->
                        selectedSkillIds = selectedSkillIds - skill.id
                    },
                    fullPrompt = fullPrompt,
                    generatedHtml = generatedHtml
                )
            } else {
                ResultScreen(
                    html = generatedHtml!!,
                    prompt = prompt,
                    agents = agents,
                    viewMode = currentViewMode,
                    errors = webViewErrors,
                    onViewModeChange = { currentViewMode = it },
                    onRetry = { 
                        generatedHtml = null 
                    },
                    onSave = {
                        saveWebapp(
                            context = context,
                            name = suggestedName.ifBlank { "Webapp ${savedWebapps.size + 1}" },
                            prompt = prompt,
                            html = generatedHtml!!,
                            onDone = {
                                savedWebapps = loadSavedWebapps(context)
                            }
                        )
                    },
                    onCopyContent = { text ->
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
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

        if (showSkillsDialog) {
            SkillsDialog(
                skills = skills,
                selectedSkillIds = selectedSkillIds,
                onDismiss = { showSkillsDialog = false },
                onSave = { newSelection ->
                    selectedSkillIds = newSelection
                    showSkillsDialog = false
                },
                onAddSkill = { title, content ->
                    val newSkill = Skill(id = System.currentTimeMillis().toString(), title = title, content = content)
                    saveSkill(context, newSkill)
                    skills = loadSkills(context)
                },
                onEditSkill = { skill ->
                    saveSkill(context, skill)
                    skills = loadSkills(context)
                }
            )
        }
    }
}

@Composable
fun PromptScreen(
    prompt: String,
    onPromptChange: (String) -> Unit,
    selectedSkills: List<Skill>,
    agents: List<AgentState>,
    isLoading: Boolean,
    errorMessage: String?,
    onGenerate: () -> Unit,
    onSkillsClick: () -> Unit,
    onSkillRemove: (Skill) -> Unit,
    fullPrompt: String,
    generatedHtml: String?
) {
    val scrollState = rememberScrollState()
    val isContentCentered = !isLoading && generatedHtml == null
    val keyboardController = LocalSoftwareKeyboardController.current
    
    val infiniteTransition = rememberInfiniteTransition()
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    val totalOutputLength = agents.sumOf { it.output.length }
    LaunchedEffect(totalOutputLength) {
        if (!isContentCentered) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (isContentCentered) Arrangement.Center else Arrangement.spacedBy(16.dp)
        ) {
        Text(
            "Describe your webapp",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Start)
        )
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            modifier = Modifier.fillMaxWidth().height(150.dp),
            placeholder = { Text("e.g. A random addition quiz with a check button") },
            shape = MaterialTheme.shapes.medium
        )
        
        // Tags Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Skills:", fontWeight = FontWeight.Bold)
            selectedSkills.forEach { skill ->
                SuggestionChip(
                    onClick = { onSkillRemove(skill) },
                    label = { Text(skill.title) }
                )
            }
            IconButton(onClick = onSkillsClick) {
                Icon(Icons.Default.Add, contentDescription = "Add Skill")
            }
        }
        
        Button(
            onClick = {
                keyboardController?.hide()
                onGenerate()
            },
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
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
        }

        if (!isContentCentered && agents.isNotEmpty()) {
            var selectedAgentIndex by remember { mutableStateOf(0) }
            
            // Automatically select the latest agent when the list grows
            LaunchedEffect(agents.size) {
                selectedAgentIndex = agents.size - 1
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Headers List
                agents.forEachIndexed { index, agent ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedAgentIndex = index }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🤖 ${agent.name}",
                            fontWeight = if (selectedAgentIndex == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedAgentIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            when (agent.status) {
                                AgentStatus.PENDING -> "⏳"
                                AgentStatus.RUNNING -> "⚙️"
                                AgentStatus.DONE -> "✔️"
                                AgentStatus.FAILED -> "❌"
                            },
                            fontSize = 16.sp,
                            modifier = if (agent.status == AgentStatus.RUNNING) {
                                Modifier.rotate(angle)
                            } else {
                                Modifier
                            }
                        )
                    }
                }
                
                // Content Area for Selected Agent
                val selectedAgent = agents.getOrNull(selectedAgentIndex)
                if (selectedAgent != null) {
                    val contentScrollState = rememberScrollState()
                    
                    // Auto-scroll content area to bottom when output changes
                    LaunchedEffect(selectedAgent.output.length) {
                        contentScrollState.animateScrollTo(contentScrollState.maxValue)
                    }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(contentScrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("LLM Input:", fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp)
                        ) {
                            Text(selectedAgent.input, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("LLM Output:", fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp)
                        ) {
                            Text(selectedAgent.output, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ResultScreen(
    html: String,
    prompt: String,
    agents: List<AgentState>,
    viewMode: ViewMode,
    errors: List<String>,
    onViewModeChange: (ViewMode) -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onCopyContent: (String) -> Unit,
    onAddError: (String) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

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
            Tab(selected = viewMode == ViewMode.LOG, onClick = { onViewModeChange(ViewMode.LOG) }) {
                Text("Log", modifier = Modifier.padding(12.dp))
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
                ViewMode.LOG -> {
                    var selectedAgentIndex by remember { mutableStateOf(0) }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Headers List
                        agents.forEachIndexed { index, agent ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedAgentIndex = index }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "🤖 ${agent.name}",
                                    fontWeight = if (selectedAgentIndex == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedAgentIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    when (agent.status) {
                                        AgentStatus.PENDING -> "⏳"
                                        AgentStatus.RUNNING -> "⚙️"
                                        AgentStatus.DONE -> "✔️"
                                        AgentStatus.FAILED -> "❌"
                                    },
                                    fontSize = 16.sp,
                                    modifier = if (agent.status == AgentStatus.RUNNING) {
                                        Modifier.rotate(angle)
                                    } else {
                                        Modifier
                                    }
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Content Area for Selected Agent
                        val selectedAgent = agents.getOrNull(selectedAgentIndex)
                        if (selectedAgent != null) {
                            val contentScrollState = rememberScrollState()
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(contentScrollState),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("LLM Input:", fontWeight = FontWeight.Bold)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(8.dp)
                                ) {
                                    Text(selectedAgent.input, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text("LLM Output:", fontWeight = FontWeight.Bold)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(8.dp)
                                ) {
                                    Text(selectedAgent.output, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                }
                            }
                        }
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

@Composable
fun SkillsDialog(
    skills: List<Skill>,
    selectedSkillIds: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
    onAddSkill: (String, String) -> Unit,
    onEditSkill: (Skill) -> Unit
) {
    var currentSelection by remember { mutableStateOf(selectedSkillIds) }
    var showAddDialog by remember { mutableStateOf(false) }
    var skillToEdit by remember { mutableStateOf<Skill?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Skills") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Add Skill")
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(skills) { skill ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = currentSelection.contains(skill.id),
                                onCheckedChange = { checked ->
                                    currentSelection = if (checked) {
                                        currentSelection + skill.id
                                    } else {
                                        currentSelection - skill.id
                                    }
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(skill.title, fontWeight = FontWeight.Bold)
                                Text(skill.content, fontSize = 12.sp, maxLines = 1)
                            }
                            IconButton(onClick = { skillToEdit = skill }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(currentSelection) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Skill") },
            text = {
                Column {
                    TextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(value = content, onValueChange = { content = it }, label = { Text("Content") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        onAddSkill(title, content)
                        showAddDialog = false
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (skillToEdit != null) {
        var title by remember { mutableStateOf(skillToEdit!!.title) }
        var content by remember { mutableStateOf(skillToEdit!!.content) }
        AlertDialog(
            onDismissRequest = { skillToEdit = null },
            title = { Text("Edit Skill") },
            text = {
                Column {
                    TextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(value = content, onValueChange = { content = it }, label = { Text("Content") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        onEditSkill(Skill(skillToEdit!!.id, title, content))
                        skillToEdit = null
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { skillToEdit = null }) { Text("Cancel") }
            }
        )
    }
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


