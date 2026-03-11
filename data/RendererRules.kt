package com.example.modrinthforandroid.data

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.modrinthforandroid.data.model.MojoInstance
import org.json.JSONObject

/**
 * Result returned when a renderer rule fires and the instance config is
 * successfully updated. Drives the post-download dialog in ModDetailScreen.
 */
data class RendererChangeResult(
    val modName: String,
    val oldRenderer: String,
    val newRenderer: String,
    val reason: String
) {
    val oldRendererDisplay: String get() = rendererDisplay(oldRenderer)
    val newRendererDisplay: String get() = rendererDisplay(newRenderer)

    private fun rendererDisplay(r: String) = when (r) {
        "vulkan_zink" -> "Vulkan (Zink)"
        "ltw"             -> "LTW (GL4ES)"
        "opengles3_ltw"  -> "OpenGL ES 3 + LTW"
        "gl4es"       -> "GL4ES"
        "virgl"       -> "VirGL"
        "freedreno"   -> "Freedreno"
        "default"     -> "Default"
        else          -> r
    }
}

/**
 * A single renderer rule.
 *
 * [slugs]          — Modrinth project slugs that trigger this rule (lowercase).
 * [requiredLoader] — If non-null, rule only fires when the instance uses this
 *                    loader (e.g. Sodium only matters on Fabric/Quilt).
 * [targetRenderer] — The renderer value to write into mojo_instance.json.
 * [reason]         — Human-readable explanation shown in the dialog.
 */
data class RendererRule(
    val slugs: Set<String>,
    val requiredLoader: String? = null,
    val targetRenderer: String,
    val reason: String
)

object RendererRules {

    private const val TAG = "RendererRules"

    /**
     * Rules are checked in order — first match wins.
     *
     * Add new rules here as needed. Keep slugs lowercase.
     */
    private val RULES = listOf(

        // Sodium uses OpenGL calls that are incompatible with Vulkan/Zink.
        // LTW (GL4ES) is the correct renderer for Sodium on MojoLauncher.
        RendererRule(
            slugs           = setOf("sodium"),
            requiredLoader  = null,   // applies regardless of loader
            targetRenderer  = "opengles3_ltw",
            reason          = "Sodium uses OpenGL and is incompatible with Vulkan (Zink). " +
                    "Your renderer has been switched to LTW (GL4ES) so Sodium " +
                    "will work correctly."
        ),

        // Iris Shaders also requires OpenGL — same fix as Sodium.
        RendererRule(
            slugs           = setOf("iris"),
            requiredLoader  = null,
            targetRenderer  = "opengles3_ltw",
            reason          = "Iris Shaders requires OpenGL and does not work with Vulkan " +
                    "(Zink). Your renderer has been switched to LTW (GL4ES)."
        ),

        // Indium is Sodium's rendering API — same requirement.
        RendererRule(
            slugs           = setOf("indium"),
            requiredLoader  = null,
            targetRenderer  = "opengles3_ltw",
            reason          = "Indium (Sodium rendering API) requires OpenGL. " +
                    "Your renderer has been switched to LTW (GL4ES)."
        ),

        // Lithium is purely a server-side optimisation mod and works on any renderer.
        // No rule needed — listed here as a comment for future reference.
    )

    /**
     * Checks [projectSlug] against all rules. If a rule matches and the
     * instance's current renderer is different from the target, writes the new
     * renderer value back to mojo_instance.json via SAF and returns a
     * [RendererChangeResult] describing what changed.
     *
     * Returns null if:
     *   - No rule matches the slug
     *   - No active instance is set
     *   - The renderer is already correct
     *   - The SAF write fails (logs the error silently)
     */
    fun applyIfNeeded(context: Context, projectSlug: String): RendererChangeResult? {
        val slug = projectSlug.lowercase().trim()

        val rule = RULES.firstOrNull { rule ->
            slug in rule.slugs
        } ?: return null.also {
            Log.d(TAG, "No rule for slug '$slug'")
        }

        val instanceName = InstanceManager.activeInstanceName ?: return null
        val config       = InstanceManager.activeInstanceConfig ?: return null
        val rootUri      = InstanceManager.rootUri ?: return null

        // Loader check
        if (rule.requiredLoader != null &&
            !config.loader.equals(rule.requiredLoader, ignoreCase = true)) {
            Log.d(TAG, "Rule for '$slug' skipped — loader ${config.loader} != ${rule.requiredLoader}")
            return null
        }

        // Already the right renderer — nothing to do
        if (config.renderer == rule.targetRenderer) {
            Log.d(TAG, "Rule for '$slug' — renderer already ${rule.targetRenderer}, skipping")
            return null
        }

        val oldRenderer = config.renderer

        // ── Write new renderer to mojo_instance.json via SAF ─────────────
        return try {
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
                ?: throw IllegalStateException("Cannot open root URI")

            val instanceDoc = rootDoc.findFile(instanceName)
                ?: throw IllegalStateException("Instance folder '$instanceName' not found")

            val jsonFile = instanceDoc.findFile("mojo_instance.json")
                ?: throw IllegalStateException("mojo_instance.json not found in '$instanceName'")

            // Read current JSON
            val rawJson = context.contentResolver.openInputStream(jsonFile.uri)?.use {
                it.bufferedReader().readText()
            } ?: throw IllegalStateException("Cannot read mojo_instance.json")

            // Patch the renderer field
            val json = JSONObject(rawJson)
            json.put("renderer", rule.targetRenderer)
            val updatedJson = json.toString(2)

            // Write back — "wt" truncates before writing
            context.contentResolver.openOutputStream(jsonFile.uri, "wt")?.use { out ->
                out.write(updatedJson.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("Cannot open output stream for mojo_instance.json")

            // Refresh the in-memory config so the UI immediately reflects the change
            InstanceManager.readInstanceConfig(context, instanceName)

            Log.d(TAG, "Renderer updated: $oldRenderer → ${rule.targetRenderer} for '$instanceName'")

            RendererChangeResult(
                modName     = slug,
                oldRenderer = oldRenderer,
                newRenderer = rule.targetRenderer,
                reason      = rule.reason
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to update renderer for '$slug'", e)
            null
        }
    }
}