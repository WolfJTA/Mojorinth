package com.example.modrinthforandroid.data.model

import org.json.JSONObject

/**
 * Parsed representation of mojo_instance.json.
 *
 * Example file:
 * {
 *   "argsMode": 0,
 *   "icon": "default",
 *   "name": "Vulkan Chapter Of Potato PVP",
 *   "renderer": "vulkan_zink",
 *   "selectedRuntime": "Internal-21",
 *   "sharedData": false,
 *   "versionId": "fabric-loader-0.16.14-1.21.4"
 * }
 */
data class MojoInstance(
    val name: String,
    val renderer: String,
    val selectedRuntime: String,
    val versionId: String,           // raw e.g. "fabric-loader-0.16.14-1.21.4"
    val icon: String,
    val sharedData: Boolean,

    // Derived — parsed from versionId
    val loader: String,              // e.g. "Fabric", "Forge", "NeoForge", "Quilt", "Vanilla"
    val loaderVersion: String,       // e.g. "0.16.14"  (empty for vanilla)
    val mcVersion: String            // e.g. "1.21.4"
) {
    /** Human-friendly one-liner shown in the UI, e.g. "Fabric 0.16.14 • 1.21.4" */
    val summary: String get() = when {
        loader == "Vanilla" -> "Vanilla $mcVersion"
        loaderVersion.isNotEmpty() -> "$loader $loaderVersion • MC $mcVersion"
        else -> "$loader • MC $mcVersion"
    }

    /** Lowercase loader slug compatible with Modrinth API facets, e.g. "fabric" */
    val loaderSlug: String get() = loader.lowercase()

    /** Display name for the renderer */
    val rendererDisplay: String get() = when (renderer) {
        "vulkan_zink"  -> "Vulkan (Zink)"
        "ltw"          -> "LTW (GL4ES)"
        "gl4es"        -> "GL4ES"
        "virgl"        -> "VirGL"
        "freedreno"    -> "Freedreno"
        else           -> renderer
    }

    companion object {
        /**
         * Parse a mojo_instance.json string into a [MojoInstance].
         * Returns null if the JSON is malformed or missing required fields.
         */
        fun parse(json: String): MojoInstance? {
            return try {
                val obj = JSONObject(json)
                val versionId = obj.optString("versionId", "")
                val (loader, loaderVersion, mcVersion) = parseVersionId(versionId)

                MojoInstance(
                    name             = obj.optString("name", "Unknown"),
                    renderer         = obj.optString("renderer", "unknown"),
                    selectedRuntime  = obj.optString("selectedRuntime", ""),
                    versionId        = versionId,
                    icon             = obj.optString("icon", "default"),
                    sharedData       = obj.optBoolean("sharedData", false),
                    loader           = loader,
                    loaderVersion    = loaderVersion,
                    mcVersion        = mcVersion
                )
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Parses the versionId field into (loader, loaderVersion, mcVersion).
         *
         * Known formats:
         *   "fabric-loader-0.16.14-1.21.4"   → Fabric, 0.16.14, 1.21.4
         *   "forge-1.21.4-54.1.0"            → Forge,  54.1.0,  1.21.4
         *   "neoforge-21.4.0-beta"           → NeoForge, 21.4.0-beta, 1.21.4  (mc inferred)
         *   "quilt-loader-0.26.0-1.21.4"     → Quilt, 0.26.0, 1.21.4
         *   "1.21.4"                          → Vanilla, "", 1.21.4
         */
        private fun parseVersionId(versionId: String): Triple<String, String, String> {
            if (versionId.isBlank()) return Triple("Unknown", "", "")

            // Vanilla — just a version number like "1.21.4"
            if (versionId.matches(Regex("""\d+\.\d+(\.\d+)?"""))) {
                return Triple("Vanilla", "", versionId)
            }

            // fabric-loader-<loaderVer>-<mcVer>
            val fabricMatch = Regex("""^fabric-loader-([\d.]+)-([\d.]+)$""").find(versionId)
            if (fabricMatch != null) {
                return Triple("Fabric", fabricMatch.groupValues[1], fabricMatch.groupValues[2])
            }

            // quilt-loader-<loaderVer>-<mcVer>
            val quiltMatch = Regex("""^quilt-loader-([\d.]+)-([\d.]+)$""").find(versionId)
            if (quiltMatch != null) {
                return Triple("Quilt", quiltMatch.groupValues[1], quiltMatch.groupValues[2])
            }

            // forge-<mcVer>-<forgeVer>
            val forgeMatch = Regex("""^forge-([\d.]+)-([\d.]+(?:\.\d+)?)$""").find(versionId)
            if (forgeMatch != null) {
                return Triple("Forge", forgeMatch.groupValues[2], forgeMatch.groupValues[1])
            }

            // neoforge-<loaderVer>  (MC version embedded in loader ver major: 21.x → 1.21.x)
            val neoforgeMatch = Regex("""^neoforge-([\d.]+(?:-\w+)?)$""").find(versionId)
            if (neoforgeMatch != null) {
                val loaderVer = neoforgeMatch.groupValues[1]
                // NeoForge 21.4.x → MC 1.21.4
                val mcVer = loaderVer.split(".").take(2).let { parts ->
                    if (parts.size >= 2) "1.${parts[0]}.${parts[1]}" else "1.${parts[0]}"
                }
                return Triple("NeoForge", loaderVer, mcVer)
            }

            // Fallback — return the raw string as loader, unknown versions
            return Triple(versionId, "", "")
        }
    }
}