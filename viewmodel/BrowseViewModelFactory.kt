package com.example.modrinthforandroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class BrowseViewModelFactory(
    private val projectType: String,
    private val initialFilters: BrowseFilters = BrowseFilters()
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BrowseViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BrowseViewModel(projectType, initialFilters) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}