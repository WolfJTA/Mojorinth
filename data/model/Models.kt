package com.example.modrinthforandroid.data.model

import com.google.gson.annotations.SerializedName

data class ModProject(
    val id: String, val slug: String, val title: String, val description: String,
    val body: String?,
    @SerializedName("icon_url") val iconUrl: String?,
    val downloads: Int, val followers: Int,
    val categories: List<String>,
    val loaders: List<String>?,
    @SerializedName("game_versions") val gameVersions: List<String>,
    @SerializedName("project_type") val projectType: String,
    @SerializedName("updated") val dateModified: String?,
    @SerializedName("published") val dateCreated: String?,
    @SerializedName("issues_url") val issuesUrl: String?,
    @SerializedName("source_url") val sourceUrl: String?,
    @SerializedName("wiki_url") val wikiUrl: String?,
    @SerializedName("discord_url") val discordUrl: String?,
    @SerializedName("client_side") val clientSide: String?,
    @SerializedName("server_side") val serverSide: String?,
    val license: License?,
    val versions: List<String>?,
    val gallery: List<GalleryImage>?
)

data class License(val id: String, val name: String, val url: String?)

data class GalleryImage(val url: String, val featured: Boolean, val title: String?, val description: String?)

data class SearchResult(
    @SerializedName("project_id") val projectId: String,
    val slug: String, val title: String, val description: String,
    @SerializedName("icon_url") val iconUrl: String?,
    val downloads: Int, val follows: Int,
    val categories: List<String>,
    @SerializedName("versions") val versions: List<String>,
    @SerializedName("project_type") val projectType: String,
    @SerializedName("date_modified") val dateModified: String
)

data class SearchResponse(val hits: List<SearchResult>, val offset: Int, val limit: Int, val total_hits: Int)

data class ModVersion(
    val id: String,
    val name: String,
    @SerializedName("version_number") val versionNumber: String,
    @SerializedName("version_type") val versionType: String,
    @SerializedName("game_versions") val gameVersions: List<String>,
    val loaders: List<String>,
    val changelog: String?,
    @SerializedName("date_published") val datePublished: String,
    val downloads: Int,
    val files: List<ModVersionFile>
)

data class ModVersionFile(
    val url: String,
    val filename: String,
    val primary: Boolean,
    val size: Long
)