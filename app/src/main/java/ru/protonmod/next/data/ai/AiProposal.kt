/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.data.ai

import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import org.json.JSONArray
import org.json.JSONObject

data class AiProposedAction(
    val id: String,
    val type: String,
    val title: String,
    val description: String,
    val payload: String,
    val destructive: Boolean,
)

data class AiProposal(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val summary: String,
    val actions: List<AiProposedAction>,
) {
    fun asRefinementContext(): String = JSONObject().apply {
        put("title", title)
        put("summary", summary)
        put("actions", JSONArray(actions.map { JSONObject(it.payload) }))
    }.toString()
}

internal object AiProposalParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): AiProposal {
        val trimmed = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root = json.parseToJsonElement(trimmed)
        val rootObject = root as? JsonObject
        val actionsJson = when (root) {
            is JsonArray -> root
            is JsonObject -> root["actions"] as? JsonArray ?: JsonArray(listOf(root))
            else -> throw IllegalArgumentException("AI response is not a JSON object or array")
        }
        require(actionsJson.isNotEmpty()) { "AI returned an empty proposal" }

        val actions = actionsJson.map { element ->
            val item = element as? JsonObject ?: throw IllegalArgumentException("Proposal action is not an object")
            val type = item.text("action")
            require(type.isNotEmpty()) { "Proposal action is missing" }
            AiProposedAction(
                id = UUID.randomUUID().toString(),
                type = type,
                title = actionTitle(type),
                description = actionDescription(type, item),
                payload = item.toString(),
                destructive = type in setOf("delete_profile", "disconnect", "reset_settings"),
            )
        }

        return AiProposal(
            title = rootObject?.text("title")?.takeIf(String::isNotBlank) ?: defaultTitle(actions),
            summary = rootObject?.text("summary")?.takeIf(String::isNotBlank)
                ?: actions.joinToString(" · ") { it.description },
            actions = actions,
        )
    }

    private fun defaultTitle(actions: List<AiProposedAction>): String = when {
        actions.size == 1 && actions.first().type == "create_profile" -> "New VPN profile"
        actions.size == 1 && actions.first().type == "update_profile" -> "Profile update"
        actions.size == 1 -> actions.first().title
        else -> "AI changes"
    }

    private fun actionTitle(type: String): String = when (type) {
        "create_profile" -> "Create profile"
        "update_profile" -> "Edit profile"
        "delete_profile" -> "Delete profile"
        "set_setting" -> "Change setting"
        "set_obfuscation", "set_awg_params" -> "Update obfuscation"
        "refresh_servers" -> "Refresh servers"
        "connect" -> "Connect"
        "disconnect" -> "Disconnect"
        else -> type.replace('_', ' ').replaceFirstChar(Char::uppercase)
    }

    private fun actionDescription(type: String, item: JsonObject): String = when (type) {
        "create_profile" -> item.text("name").ifBlank { "Create a generated VPN profile" }
        "update_profile" -> "Update ${item.profileReference()}"
        "delete_profile" -> "Delete ${item.profileReference()}"
        "set_setting" -> "${item.text("key")} → ${item["value"]?.displayValue().orEmpty()}"
        "set_obfuscation" -> if (item.boolean("enabled")) "Enable and configure AmneziaWG" else "Disable obfuscation"
        "set_awg_params" -> "Apply advanced AmneziaWG parameters"
        "refresh_servers" -> "Download the latest country list and server loads"
        "connect" -> listOf(item.text("serverName"), item.text("city"), item.text("country"))
            .firstOrNull(String::isNotBlank)?.let { "Connect to $it" } ?: "Connect to the best server"
        else -> actionTitle(type)
    }

    private fun JsonObject.profileReference(): String =
        text("profileName").takeIf(String::isNotBlank)
            ?: text("profileId").takeIf(String::isNotBlank)
            ?: "the selected profile"

    private fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.boolean(key: String): Boolean = (get(key) as? JsonPrimitive)?.booleanOrNull ?: false
    private fun JsonElement.displayValue(): String = (this as? JsonPrimitive)?.contentOrNull ?: toString()
}
