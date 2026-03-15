package com.example.modrinthforandroid.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.modrinthforandroid.data.api.HashLookupRequest
import com.example.modrinthforandroid.data.api.RetrofitClient
import com.example.modrinthforandroid.data.model.MojoInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// ─── Result types ─────────────────────────────────────────────────────────────

data class ExportResult(
    val outputUri: Uri,
    val totalMods: Int,
    val resolvedMods: Int,
    val skippedMods: List<String>,
    val shadersExported: Int = 0,
    val resourcePacksExported: Int = 0
)

// ─── Exporter ─────────────────────────────────────────────────────────────────

object MrpackExporter {

    private val api = RetrofitClient.apiService

    /**
     * Scans the `mods/` subfolder of the active instance, hashes every `.jar`
     * (skipping `.disabled` files), resolves them against the Modrinth API,
     * and writes a `.mrpack` zip to [outputUri].
     *
     * @param context       App context for SAF access.
     * @param rootUri       The tree URI for the profiles root folder.
     * @param instanceName  The subfolder name of the active instance.
     * @param config        Parsed mojo_instance.json — used for pack metadata.
     * @param outputUri     A writable SAF URI (from ACTION_CREATE_DOCUMENT).
     */
    suspend fun export(
        context: Context,
        rootUri: Uri,
        instanceName: String,
        config: MojoInstance,
        outputUri: Uri
    ): ExportResult = withContext(Dispatchers.IO) {

        // ── 1. Collect files ──────────────────────────────────────────────
        val rootDoc     = DocumentFile.fromTreeUri(context, rootUri)
            ?: error("Cannot open root URI")
        val instanceDoc = rootDoc.findFile(instanceName)
            ?: error("Instance folder '$instanceName' not found")

        // Mods — resolve via Modrinth hash API
        val modsDir  = instanceDoc.findFile("mods")
        val jarFiles = modsDir?.listFiles()
            ?.filter { it.isFile }
            ?.filter { doc ->
                val n = doc.name ?: ""
                n.endsWith(".jar", ignoreCase = true) &&
                        !n.endsWith(".disabled", ignoreCase = true)
            } ?: emptyList()

        // Shaders + resource packs — bundled as overrides (zips, not resolvable by hash)
        data class OverrideFile(val doc: DocumentFile, val zipPath: String)
        val overrideFiles = mutableListOf<OverrideFile>()

        instanceDoc.findFile("shaderpacks")?.listFiles()
            ?.filter { it.isFile }
            ?.forEach { overrideFiles += OverrideFile(it, "overrides/shaderpacks/${it.name}") }

        instanceDoc.findFile("resourcepacks")?.listFiles()
            ?.filter { it.isFile }
            ?.forEach { overrideFiles += OverrideFile(it, "overrides/resourcepacks/${it.name}") }

        if (jarFiles.isEmpty() && overrideFiles.isEmpty())
            error("Nothing to export — no mods, shaders, or resource packs found")

        // ── 2. SHA-512 hash every mod jar ─────────────────────────────────
        val hashToDoc = mutableMapOf<String, DocumentFile>()
        for (doc in jarFiles) {
            val hash = context.contentResolver.openInputStream(doc.uri)
                ?.use { sha512(it) } ?: continue
            hashToDoc[hash] = doc
        }

        // ── 3. Bulk resolve hashes via Modrinth API ────────────────────────
        val resolved = mutableMapOf<String, com.example.modrinthforandroid.data.model.ModVersion>()
        hashToDoc.keys.chunked(100).forEach { batch ->
            try {
                val result = api.getVersionsByHashes(HashLookupRequest(batch))
                resolved.putAll(result)
            } catch (_: Exception) { /* partial failure — continue */ }
        }

        val skipped = hashToDoc.entries
            .filter { (hash, _) -> hash !in resolved }
            .map { (_, doc) -> doc.name ?: "unknown" }

        // ── 4. Build modrinth.index.json ──────────────────────────────────
        val filesArray = JSONArray()
        for ((hash, version) in resolved) {
            val doc  = hashToDoc[hash] ?: continue
            val file = version.files.firstOrNull { it.primary } ?: version.files.firstOrNull() ?: continue

            val fileObj = JSONObject().apply {
                put("path",      "mods/${doc.name}")
                put("downloads", JSONArray().apply { put(file.url) })
                put("hashes",    JSONObject().apply { put("sha512", hash) })
                put("env", JSONObject().apply {
                    put("client", "required")
                    put("server", "required")
                })
                put("fileSize", file.size)
            }
            filesArray.put(fileObj)
        }

        val index = JSONObject().apply {
            put("formatVersion", 1)
            put("game",          "minecraft")
            put("versionId",     config.mcVersion)
            put("name",          config.name)
            put("summary",       "Exported from Mojorinth — ${config.summary}")
            put("files",         filesArray)
            put("dependencies",  JSONObject().apply {
                put("minecraft", config.mcVersion)
                when (config.loader.lowercase()) {
                    "fabric"   -> put("fabric-loader",  config.loaderVersion)
                    "forge"    -> put("forge",           config.loaderVersion)
                    "quilt"    -> put("quilt-loader",    config.loaderVersion)
                    "neoforge" -> put("neoforge",        config.loaderVersion)
                }
            })
        }

        // ── 5. Write .mrpack zip ──────────────────────────────────────────
        context.contentResolver.openOutputStream(outputUri)?.use { out ->
            ZipOutputStream(out.buffered()).use { zip ->

                // modrinth.index.json
                zip.putNextEntry(ZipEntry("modrinth.index.json"))
                zip.write(index.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // overrides/ stub
                zip.putNextEntry(ZipEntry("overrides/"))
                zip.closeEntry()

                // Shaders + resource packs as overrides — copied directly into zip
                for ((doc, zipPath) in overrideFiles) {
                    context.contentResolver.openInputStream(doc.uri)?.use { input ->
                        zip.putNextEntry(ZipEntry(zipPath))
                        input.copyTo(zip)
                        zip.closeEntry()
                    }
                }
            }
        } ?: error("Could not open output stream for export")

        ExportResult(
            outputUri             = outputUri,
            totalMods             = jarFiles.size,
            resolvedMods          = resolved.size,
            skippedMods           = skipped,
            shadersExported       = overrideFiles.count { it.zipPath.startsWith("overrides/shaderpacks/") },
            resourcePacksExported = overrideFiles.count { it.zipPath.startsWith("overrides/resourcepacks/") }
        )
    }

    // ─── SHA-512 helper ───────────────────────────────────────────────────────

    private fun sha512(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-512")
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}