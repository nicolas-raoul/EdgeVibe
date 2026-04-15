package io.github.nicolasraoul.edgevibe

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import android.util.Log

data class AgentState(
    val name: String,
    val input: String = "",
    val output: String = "",
    val isExpanded: Boolean = true,
    val status: AgentStatus = AgentStatus.PENDING
)

enum class AgentStatus { PENDING, RUNNING, DONE, FAILED }

suspend fun generateWebapp(
    prompt: String,
    skillsText: String,
    context: Context,
    selectedBackend: AiBackend,
    mlkitModel: Any,
    scope: CoroutineScope,
    onAgentsChange: (List<AgentState>) -> Unit,
    onHtmlGenerated: (String) -> Unit,
    onError: (String) -> Unit
) {
    val agents = mutableListOf<AgentState>()
    
    fun updateAgents(newList: List<AgentState>) {
        agents.clear()
        agents.addAll(newList)
        onAgentsChange(agents.toList())
    }

    try {
        // 1. Planner Agent
        val plannerInput = """
            You direct a team of skilled agents to build a webapp.
            You will receive a description of the webapp to build.
            Based on that webapp description, call the agents that are needed.
            Each agent has very specialized skills, only call the agents whose skills are needed.
            Here are the agents in the format `AGENT_NAME: Skills`:
            - ARCHITECT: Always required.
            - STYLIST: Use colors, fonts, themes, design.

            Example 1:

            Webapp: 10-sided dice
            Agents: ARCHITECT

            Example 2:

            Webapp: Math quizz with pink theme
            Agents: ARCHITECT STYLIST

            Current task:
            Webapp: $prompt
            Agents:
        """.trimIndent()
        
        updateAgents(listOf(AgentState(name = "Planner agent", input = plannerInput, status = AgentStatus.RUNNING)))

        val plannerOutput = runAgentLocal(context, selectedBackend, mlkitModel, scope, plannerInput) { chunk ->
            agents[0] = agents[0].copy(output = agents[0].output + chunk)
            onAgentsChange(agents.toList())
        }
        agents[0] = agents[0].copy(status = AgentStatus.DONE, isExpanded = false)
        onAgentsChange(agents.toList())

        val hasStyle = plannerOutput.contains("STYLIST", ignoreCase = true)

        // 2. Architect Agent
        val architectInput = """
            You are an expert HTML dev, writing a one-file HTML webapp that fits in less than 100kB.
            Create a one-file HTML webapp for:
            $prompt.
            ${if (skillsText.isNotEmpty()) "\nTips:\n$skillsText\n" else ""}
            Important:
            - No <head> nor <link> nor style= nor any CSS.
            - Only output the HTML structure and JavaScript.
            - All JavaScript must be in the same HTML file, not as separate .js file.
            - Do not retrieve anything from the Internet nor use external APIs, the webapp must work offline.
            - Only output the HTML.

            HTML:
        """.trimIndent()
        
        updateAgents(agents + AgentState(name = "Architect agent", input = architectInput, status = AgentStatus.RUNNING))

        val architectOutput = runAgentLocal(context, selectedBackend, mlkitModel, scope, architectInput) { chunk ->
            agents[1] = agents[1].copy(output = agents[1].output + chunk)
            onAgentsChange(agents.toList())
        }
        agents[1] = agents[1].copy(status = AgentStatus.DONE, isExpanded = false)
        onAgentsChange(agents.toList())

        var finalHtml = architectOutput

        // 3. Stylist Agent (Conditional)
        if (hasStyle) {
            val styleInput = """
                Create a CSS stylesheet for the webapp described below, focusing on the requested style.
                Only output pure CSS code. Do not include any other text or markdown formatting.
                
                <webapp_description>
                $prompt
                </webapp_description>
                
                <generated_html>
                $architectOutput
                </generated_html>

                CSS style to insert into the style section of the HTML above:
            """.trimIndent()
            
            updateAgents(agents + AgentState(name = "Stylist agent", input = styleInput, status = AgentStatus.RUNNING))

            val styleOutput = runAgentLocal(context, selectedBackend, mlkitModel, scope, styleInput) { chunk ->
                agents[2] = agents[2].copy(output = agents[2].output + chunk)
                onAgentsChange(agents.toList())
            }
            agents[2] = agents[2].copy(status = AgentStatus.DONE, isExpanded = false)
            onAgentsChange(agents.toList())

            // Inject CSS
            val cleanStyleOutput = styleOutput
                .replace("```css", "")
                .replace("```", "")
                .trim()
            val styleBlock = "<style>\n$cleanStyleOutput\n</style>"
            finalHtml = if (architectOutput.contains("</head>")) {
                architectOutput.replace("</head>", "$styleBlock\n</head>")
            } else if (architectOutput.contains("<body>")) {
                architectOutput.replace("<body>", "$styleBlock\n<body>")
            } else {
                "$styleBlock\n$architectOutput"
            }
        }

        onHtmlGenerated(cleanHtml(finalHtml))
        
    } catch (e: Exception) {
        Log.e("EdgeVibe", "Generation failed", e)
        onError(e.localizedMessage ?: "Unknown error")
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
