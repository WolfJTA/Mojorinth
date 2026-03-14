package com.example.modrinthforandroid.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

// ─── Progress reporting ───────────────────────────────────────────────────────

data class InstallProgress(
    val step: String,
    val done: Int  = 0,
    val total: Int = 0   // 0 = indeterminate
)

data class InstallResult(
    val instanceName: String,
    val modsInstalled: Int,
    val modsFailed: List<String>
)

// ─── Installer ────────────────────────────────────────────────────────────────

object MrpackInstaller {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "MojorinthApp/1.0")
                    .build()
            )
        }
        .build()

    /**
     * Installs a .mrpack as a new Mojo Launcher instance.
     *
     * Handles two mod sources, used by different modpacks:
     *   A) modrinth.index.json "files" array  — mods hosted on Modrinth CDN,
     *      downloaded one by one.
     *   B) overrides/mods/ inside the zip     — mods bundled directly in the
     *      .mrpack (e.g. "embedded" dependency packs like SVC for Lunar Client).
     *      These are extracted straight from the zip bytes without a second download.
     *
     * Most modpacks use A, some use B, some use both.
     */
    suspend fun install(
        context: Context,
        rootUri: Uri,
        instanceName: String,
        mrpackUrl: String,
        iconUrl: String? = null,
        onProgress: (InstallProgress) -> Unit
    ): InstallResult = withContext(Dispatchers.IO) {

        // ── 1. Download .mrpack bytes ─────────────────────────────────────
        onProgress(InstallProgress("Downloading modpack…"))
        val mrpackBytes = http.newCall(Request.Builder().url(mrpackUrl).build())
            .execute().use { resp ->
                if (!resp.isSuccessful) error("Failed to download .mrpack (HTTP ${resp.code})")
                resp.body?.bytes() ?: error("Empty .mrpack response")
            }

        // ── 2. Parse zip — index + embedded overrides in one pass ─────────
        onProgress(InstallProgress("Reading modpack contents…"))

        var index: JSONObject? = null
        // filename → bytes for anything sitting in overrides/mods/
        val embeddedMods = mutableMapOf<String, ByteArray>()

        ZipInputStream(mrpackBytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                when {
                    entry.name == "modrinth.index.json" -> {
                        index = JSONObject(zis.readBytes().toString(Charsets.UTF_8))
                    }
                    !entry.isDirectory &&
                            (entry.name.startsWith("overrides/mods/") ||
                                    entry.name.startsWith("client-overrides/mods/")) -> {
                        val filename = entry.name.substringAfterLast('/')
                        if (filename.isNotBlank()) {
                            embeddedMods[filename] = zis.readBytes()
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val idx = index ?: error("modrinth.index.json not found in .mrpack")

        // ── 3. Parse loader metadata ──────────────────────────────────────
        // versionId = the PACK's version number (e.g. "0.0.1") — NOT the MC version
        // MC version lives in dependencies.minecraft
        val dependencies  = idx.optJSONObject("dependencies")
        val mcVersion     = dependencies?.optString("minecraft", "") ?: idx.optString("versionId", "")
        val loaderEntry   = dependencies?.keys()?.asSequence()
            ?.firstOrNull { it != "minecraft" }
        val loaderVersion = if (loaderEntry != null) dependencies.optString(loaderEntry, "") else ""

        val versionId = when {
            loaderEntry == null                -> mcVersion
            loaderEntry.startsWith("fabric")   -> "fabric-loader-$loaderVersion-$mcVersion"
            loaderEntry.startsWith("quilt")    -> "quilt-loader-$loaderVersion-$mcVersion"
            loaderEntry.startsWith("forge")    -> "forge-$mcVersion-$loaderVersion"
            loaderEntry.startsWith("neoforge") -> "neoforge-$loaderVersion"
            else                               -> mcVersion
        }

        // ── 4. Collect remote mod files from index ────────────────────────
        data class RemoteMod(val url: String, val filename: String)
        val remoteMods = mutableListOf<RemoteMod>()

        val filesArray = idx.optJSONArray("files")
        if (filesArray != null) {
            for (i in 0 until filesArray.length()) {
                val fileObj   = filesArray.getJSONObject(i)
                val path      = fileObj.optString("path", "")
                val downloads = fileObj.optJSONArray("downloads")
                val url       = downloads?.optString(0) ?: continue
                if ((path.startsWith("mods/") || path.startsWith("overrides/mods/")) && url.isNotBlank()) {
                    remoteMods += RemoteMod(url, path.substringAfterLast('/'))
                }
            }
        }

        val totalMods = remoteMods.size + embeddedMods.size

        // ── 5. Create instance folder via SAF ─────────────────────────────
        onProgress(InstallProgress("Creating instance folder…"))
        val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
            ?: error("Cannot open profiles root folder")

        val safeName = instanceName
            .replace(Regex("[/\\\\:*?\"<>|]"), "_")
            .trim()
            .ifBlank { "New_Modpack" }

        val finalName   = uniqueFolderName(rootDoc, safeName)
        val instanceDir = rootDoc.createDirectory(finalName)
            ?: error("Could not create instance folder '$finalName'")

        // ── 6. Write mojo_instance.json ───────────────────────────────────
        onProgress(InstallProgress("Writing instance config…"))
        val mojoConfig = JSONObject().apply {
            put("argsMode",        0)
            put("icon",            "default")
            put("name",            instanceName)
            put("renderer",        "vulkan_zink")
            put("selectedRuntime", "Internal-21")
            put("sharedData",      false)
            put("versionId",       versionId)
        }.toString(2)

        val configFile = instanceDir.createFile("application/json", "mojo_instance.json")
            ?: error("Could not create mojo_instance.json")
        context.contentResolver.openOutputStream(configFile.uri)?.use { out ->
            out.write(mojoConfig.toByteArray(Charsets.UTF_8))
        } ?: error("Could not write mojo_instance.json")

        // ── 7. Download icon.webp ─────────────────────────────────────────
        if (!iconUrl.isNullOrBlank()) {
            try {
                onProgress(InstallProgress("Downloading icon…"))
                val iconBytes = http.newCall(Request.Builder().url(iconUrl).build())
                    .execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.bytes() else null
                    }
                if (iconBytes != null) {
                    val iconFile = instanceDir.createFile("image/webp", "icon.webp")
                    iconFile?.let { f ->
                        context.contentResolver.openOutputStream(f.uri)?.use { it.write(iconBytes) }
                    }
                }
            } catch (_: Exception) {
                // Icon is non-critical — silently skip if it fails
            }
        }

        // ── 7. Install mods ───────────────────────────────────────────────
        if (totalMods == 0) {
            return@withContext InstallResult(
                instanceName  = finalName,
                modsInstalled = 0,
                modsFailed    = emptyList()
            )
        }

        val modsDir = instanceDir.createDirectory("mods")
            ?: error("Could not create mods/ folder")

        val failed    = mutableListOf<String>()
        var doneCount = 0

        // ── 7a. Write embedded mods straight from zip bytes ───────────────
        embeddedMods.forEach { (filename, bytes) ->
            onProgress(InstallProgress(
                step  = "Extracting ${filename.take(30)}…",
                done  = doneCount,
                total = totalMods
            ))
            try {
                val dest = modsDir.createFile("application/java-archive", filename)
                    ?: error("Could not create file")
                context.contentResolver.openOutputStream(dest.uri)?.use { it.write(bytes) }
                    ?: error("Could not open output stream")
            } catch (e: Exception) {
                failed += filename
            }
            doneCount++
        }

        // ── 7b. Download remote mods from Modrinth CDN ────────────────────
        remoteMods.forEach { (url, filename) ->
            onProgress(InstallProgress(
                step  = "Downloading ${filename.take(30)}…",
                done  = doneCount,
                total = totalMods
            ))
            try {
                val bytes = http.newCall(Request.Builder().url(url).build())
                    .execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        resp.body?.bytes() ?: error("Empty body")
                    }
                val dest = modsDir.createFile("application/java-archive", filename)
                    ?: error("Could not create file")
                context.contentResolver.openOutputStream(dest.uri)?.use { it.write(bytes) }
                    ?: error("Could not open output stream")
            } catch (e: Exception) {
                failed += filename
            }
            doneCount++
        }

        onProgress(InstallProgress("Done!", totalMods, totalMods))

        InstallResult(
            instanceName  = finalName,
            modsInstalled = totalMods - failed.size,
            modsFailed    = failed
        )
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun uniqueFolderName(parent: DocumentFile, desired: String): String {
        if (parent.findFile(desired) == null) return desired
        var i = 2
        while (parent.findFile("$desired ($i)") != null) i++
        return "$desired ($i)"
    }
}