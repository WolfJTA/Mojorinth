package com.example.modrinthforandroid.data.api

import com.example.modrinthforandroid.data.model.ModProject
import com.example.modrinthforandroid.data.model.ModVersion
import com.example.modrinthforandroid.data.model.SearchResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ModrinthApiService {
    @GET("search")
    suspend fun searchProjects(
        @Query("query")  query: String = "",
        @Query("limit")  limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("facets") facets: String? = null,
        @Query("index")  index: String = "relevance"
    ): SearchResponse

    @GET("project/{id}")
    suspend fun getProject(@Path("id") id: String): ModProject

    @GET("projects")
    suspend fun getProjects(@Query("ids") ids: String): List<ModProject>

    @GET("project/{id}/version")
    suspend fun getProjectVersions(@Path("id") id: String): List<ModVersion>

    /** Fetch a single version by its version ID */
    @GET("version/{id}")
    suspend fun getVersion(@Path("id") id: String): ModVersion
}