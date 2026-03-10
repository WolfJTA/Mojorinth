package com.example.modrinthforandroid.data

import com.example.modrinthforandroid.data.api.RetrofitClient
import com.example.modrinthforandroid.data.model.ModProject
import com.example.modrinthforandroid.data.model.ModVersion
import com.example.modrinthforandroid.data.model.SearchResult

class ModrinthRepository {
    private val api = RetrofitClient.apiService

    suspend fun searchMods(
        query: String = "", projectType: String = "mod",
        loader: String? = null, gameVersion: String? = null, category: String? = null,
        sortIndex: String = "relevance", limit: Int = 20, offset: Int = 0
    ): List<SearchResult> {
        val facetList = mutableListOf("""["project_type:$projectType"]""")
        loader?.let { facetList.add("""["categories:$it"]""") }
        gameVersion?.let { facetList.add("""["versions:$it"]""") }
        category?.let { facetList.add("""["categories:$it"]""") }
        val facets = "[${facetList.joinToString(",")}]"
        return api.searchProjects(query = query, facets = facets, limit = limit, offset = offset, index = sortIndex).hits
    }

    suspend fun getFeaturedMods(limit: Int = 20) =
        searchMods(query = "", projectType = "mod", sortIndex = "downloads", limit = limit)

    suspend fun getModDetails(id: String): ModProject = api.getProject(id)

    suspend fun getProjectVersions(id: String): List<ModVersion> = api.getProjectVersions(id)
}