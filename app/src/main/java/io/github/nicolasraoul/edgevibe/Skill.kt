package io.github.nicolasraoul.edgevibe

import java.io.File
import android.util.Log

data class Skill(val id: String, val title: String, val content: String)

fun saveSkill(context: android.content.Context, skill: Skill) {
    try {
        val dir = File(context.getExternalFilesDir(null), "skills/${skill.id}")
        dir.mkdirs()
        File(dir, "title.txt").writeText(skill.title)
        File(dir, "content.txt").writeText(skill.content)
    } catch (e: Exception) {
        Log.e("EdgeVibe", "Save skill failed", e)
    }
}

fun loadSkills(context: android.content.Context): List<Skill> {
    val list = mutableListOf<Skill>()
    try {
        val rootDir = File(context.getExternalFilesDir(null), "skills")
        if (rootDir.exists()) {
            rootDir.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    val titleFile = File(dir, "title.txt")
                    val contentFile = File(dir, "content.txt")
                    if (titleFile.exists() && contentFile.exists()) {
                        list.add(Skill(dir.name, titleFile.readText(), contentFile.readText()))
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e("EdgeVibe", "Load skills failed", e)
    }
    return list
}
