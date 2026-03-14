package com.example.modrinthforandroid.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Stores the last [MAX_ENTRIES] non-blank search queries per project type.
 * Backed by SharedPreferences — survives app restarts.
 */
class SearchHistory private constructor(private val prefs: SharedPreferences) {

    companion object {
        private const val PREFS_NAME  = "search_history"
        private const val MAX_ENTRIES = 5
        private const val SEPARATOR   = "|||"

        @Volatile private var instance: SearchHistory? = null

        fun get(context: Context): SearchHistory =
            instance ?: synchronized(this) {
                instance ?: SearchHistory(
                    context.applicationContext
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ).also { instance = it }
            }
    }

    /** Returns the last [MAX_ENTRIES] queries for [projectType], newest first. */
    fun get(projectType: String): List<String> {
        val raw = prefs.getString(keyFor(projectType), "") ?: ""
        return if (raw.isBlank()) emptyList()
        else raw.split(SEPARATOR).filter { it.isNotBlank() }
    }

    /**
     * Adds [query] to the front of the history for [projectType].
     * Duplicate entries are removed before inserting so the list stays clean.
     * The list is capped at [MAX_ENTRIES].
     */
    fun add(projectType: String, query: String) {
        if (query.isBlank()) return
        val trimmed  = query.trim()
        val existing = get(projectType).toMutableList()
        existing.removeAll { it.equals(trimmed, ignoreCase = true) }
        existing.add(0, trimmed)
        val capped = existing.take(MAX_ENTRIES)
        prefs.edit { putString(keyFor(projectType), capped.joinToString(SEPARATOR)) }
    }

    /** Removes a single [query] from history for [projectType]. */
    fun remove(projectType: String, query: String) {
        val updated = get(projectType).filter { !it.equals(query, ignoreCase = true) }
        prefs.edit { putString(keyFor(projectType), updated.joinToString(SEPARATOR)) }
    }

    /** Clears all history for [projectType]. */
    fun clear(projectType: String) {
        prefs.edit { remove(keyFor(projectType)) }
    }

    private fun keyFor(projectType: String) = "history_$projectType"
}
