package com.example.modrinthforandroid.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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
    val modsFailed: List<String>,
    val rendererPatched: Boolean = false   // true when LTW safeguard fired
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
        mrpackBytes: ByteArray? = null,   // pass pre-read bytes to skip the download step
        onProgress: (InstallProgress) -> Unit
    ): InstallResult = withContext(Dispatchers.IO) {

        // ── 1. Obtain .mrpack bytes ───────────────────────────────────────
        onProgress(InstallProgress("Downloading modpack…"))
        val mrpackBytes = mrpackBytes ?: http.newCall(Request.Builder().url(mrpackUrl).build())
            .execute().use { resp ->
                if (!resp.isSuccessful) error("Failed to download .mrpack (HTTP ${resp.code})")
                resp.body?.bytes() ?: error("Empty .mrpack response")
            }

        // ── 2. Parse zip — index + embedded overrides in one pass ─────────
        onProgress(InstallProgress("Reading modpack contents…"))

        var index: JSONObject? = null
        // filename → bytes for anything sitting in overrides/mods/
        val embeddedMods = mutableMapOf<String, ByteArray>()
        var iconBytes: ByteArray? = null

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
                    // Extract bundled icon — check common mrpack icon locations
                    !entry.isDirectory && (
                            entry.name == "icon.png"  ||
                                    entry.name == "icon.webp" ||
                                    entry.name == "icon.jpg"  ||
                                    entry.name == "icon.jpeg" ||
                                    entry.name.equals("icon.png",  ignoreCase = true) ||
                                    entry.name.equals("icon.webp", ignoreCase = true)
                            ) -> {
                        iconBytes = zis.readBytes()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val idx = index ?: error("modrinth.index.json not found in .mrpack")

        // Use the pack's own name from the index; fall back to caller-supplied name
        val packName = idx.optString("name", "").trim().ifBlank { instanceName }

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

        val safeName = packName
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
            put("icon",            if (iconBytes != null || !iconUrl.isNullOrBlank()) "icon" else "default")
            put("name",            packName)
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

        // ── 7. Write icon.webp ────────────────────────────────────────────
        // Source priority: (a) icon bundled inside the .mrpack zip,
        //                  (b) remote iconUrl passed by the caller.
        // Mojo Launcher always looks for "icon.webp" regardless of original format.
        val iconBytesToWrite: ByteArray? = when {
            iconBytes != null -> toWebP(iconBytes)
            !iconUrl.isNullOrBlank() -> try {
                onProgress(InstallProgress("Downloading icon…"))
                val raw = http.newCall(Request.Builder().url(iconUrl).build())
                    .execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.bytes() else null
                    }
                raw?.let { toWebP(it) } ?: raw
            } catch (_: Exception) { null }
            else -> null
        }
        if (iconBytesToWrite != null) {
            try {
                // Use octet-stream so SAF doesn't append or mangle the extension.
                // The filename "icon.webp" must be exact — Mojo Launcher hardcodes it.
                val iconFile = instanceDir.createFile("application/octet-stream", "icon.webp")
                iconFile?.let { f ->
                    context.contentResolver.openOutputStream(f.uri)?.use { it.write(iconBytesToWrite) }
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

        // ── 8. LTW renderer safeguard ─────────────────────────────────────
        // If the modpack contains Sodium, Iris, or Indium the default
        // vulkan_zink renderer will break the game.  Detect by filename and
        // patch mojo_instance.json to opengles3_ltw automatically.
        val ltwTriggerPatterns = listOf("sodium", "iris", "indium")
        val allModFilenames = (remoteMods.map { it.filename } + embeddedMods.keys)
            .map { it.lowercase() }
        val needsLtw = allModFilenames.any { name ->
            ltwTriggerPatterns.any { trigger -> name.contains(trigger) }
        }

        if (needsLtw) {
            try {
                onProgress(InstallProgress("Applying LTW renderer safeguard…"))
                val instanceDir2 = DocumentFile.fromTreeUri(context, rootUri)
                    ?.findFile(finalName)
                val configFile2  = instanceDir2?.findFile("mojo_instance.json")
                if (configFile2 != null) {
                    val rawJson = context.contentResolver
                        .openInputStream(configFile2.uri)?.use { it.bufferedReader().readText() }
                    if (rawJson != null) {
                        val patched = JSONObject(rawJson)
                        patched.put("renderer", "opengles3_ltw")
                        context.contentResolver
                            .openOutputStream(configFile2.uri, "wt")?.use { out ->
                                out.write(patched.toString(2).toByteArray(Charsets.UTF_8))
                            }
                    }
                }
            } catch (_: Exception) {
                // Non-fatal — renderer can be changed manually in Mojo Launcher
            }
        }

        InstallResult(
            instanceName    = finalName,
            modsInstalled   = totalMods - failed.size,
            modsFailed      = failed,
            rendererPatched = needsLtw
        )
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun uniqueFolderName(parent: DocumentFile, desired: String): String {
        if (parent.findFile(desired) == null) return desired
        var i = 2
        while (parent.findFile("$desired ($i)") != null) i++
        return "$desired ($i)"
    }

    /**
     * Decodes [bytes] as any image format and re-encodes as WebP.
     * Returns null if decoding fails (bytes aren't a valid image).
     */
    private fun toWebP(bytes: ByteArray): ByteArray? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val out = ByteArrayOutputStream()
        @Suppress("DEPRECATION")
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Bitmap.CompressFormat.WEBP_LOSSLESS
        else
            Bitmap.CompressFormat.WEBP
        bitmap.compress(format, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }
}