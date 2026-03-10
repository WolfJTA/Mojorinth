package com.example.modrinthforandroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

// ViewModels that need constructor arguments require a Factory.
// This tells Compose how to create a BrowseViewModel with a projectType.
class BrowseViewModelFactory(private val projectType: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BrowseViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BrowseViewModel(projectType) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}