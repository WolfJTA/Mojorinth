package com.example.modrinthforandroid.data

import com.example.modrinthforandroid.data.api.RetrofitClient
import com.example.modrinthforandroid.data.model.ModDependency
import com.example.modrinthforandroid.data.model.ModProject
import com.example.modrinthforandroid.data.model.ModVersion
import com.example.modrinthforandroid.data.model.SearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class ModrinthRepository {
    private val api = RetrofitClient.apiService

    suspend fun searchMods(
        query: String = "", projectType: String = "mod",
        loader: String? = null, gameVersion: String? = null, category: String? = null,
        sortIndex: String = "relevance", limit: Int = 20, offset: Int = 0
    ): List<SearchResult> {
        val facetList = mutableListOf("""["project_type:$projectType"]""")
        loader?.let      { facetList.add("""["categories:$it"]""") }
        gameVersion?.let { facetList.add("""["versions:$it"]""") }
        category?.let    { facetList.add("""["categories:$it"]""") }
        val facets = "[${facetList.joinToString(",")}]"
        return api.searchProjects(
            query  = query,
            facets = facets,
            limit  = limit,
            offset = offset,
            index  = sortIndex
        ).hits
    }

    suspend fun getFeaturedMods(limit: Int = 20) =
        searchMods(query = "", projectType = "mod", sortIndex = "downloads", limit = limit)

    suspend fun getModDetails(id: String): ModProject = api.getProject(id)

    suspend fun getProjectVersions(id: String): List<ModVersion> = api.getProjectVersions(id)

    /**
     * Given a [ModVersion], resolves all REQUIRED dependencies to their best
     * matching [ModVersion] for the supplied [mcVersion] and [loaderSlug].
     *
     * Returns a list of [ResolvedDependency] — one per required dep, with a
     * non-null [ResolvedDependency.version] when a suitable version was found.
     *
     * Embedded and incompatible deps are ignored (callers handle them separately
     * if needed). Optional deps are included with [ResolvedDependency.isOptional]
     * set to true so callers can decide whether to surface them.
     */
    suspend fun resolveRequiredDependencies(
        version: ModVersion,
        mcVersion: String,
        loaderSlug: String
    ): List<ResolvedDependency> = coroutineScope {
        val deps = version.dependencies
            .filter { it.isRequired || it.isOptional }
            .filter { !it.isEmbedded }

        deps.map { dep ->
            async {
                try {
                    when {
                        // Dep pins a specific version ID — fetch it directly
                        dep.versionId != null -> {
                            val depVer = api.getVersion(dep.versionId)
                            ResolvedDependency(
                                dependency = dep,
                                projectId  = dep.projectId ?: depVer.id,
                                version    = depVer,
                                isOptional = dep.isOptional
                            )
                        }
                        // Dep only specifies a project — find the best version for our instance
                        dep.projectId != null -> {
                            val allVersions = api.getProjectVersions(dep.projectId)
                            val best = allVersions.bestMatchFor(mcVersion, loaderSlug)
                            ResolvedDependency(
                                dependency = dep,
                                projectId  = dep.projectId,
                                version    = best,
                                isOptional = dep.isOptional
                            )
                        }
                        else -> null
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }.mapNotNull { it.await() }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * From a list of versions, pick the best one for [mcVersion] + [loaderSlug].
     * Priority: MC+loader match → MC-only match → null.
     * Within each group: release > beta > alpha.
     */
    private fun List<ModVersion>.bestMatchFor(
        mcVersion: String,
        loaderSlug: String
    ): ModVersion? {
        fun List<ModVersion>.bestRelease() =
            firstOrNull { it.versionType == "release" }
                ?: firstOrNull { it.versionType == "beta" }
                ?: firstOrNull()

        val mcAndLoader = filter { v ->
            v.gameVersions.contains(mcVersion) &&
                    v.loaders.any { it.equals(loaderSlug, ignoreCase = true) }
        }
        val mcOnly = filter { v -> v.gameVersions.contains(mcVersion) }

        return mcAndLoader.bestRelease() ?: mcOnly.bestRelease()
    }
}

/**
 * A dependency whose version has been resolved (or attempted).
 *
 * @param dependency  The raw [ModDependency] from the parent version.
 * @param projectId   The Modrinth project ID of the dependency.
 * @param version     The best matching [ModVersion], or null if none was found.
 * @param isOptional  True if this is an optional (recommended) dependency.
 */
data class ResolvedDependency(
    val dependency: ModDependency,
    val projectId: String,
    val version: ModVersion?,
    val isOptional: Boolean
)