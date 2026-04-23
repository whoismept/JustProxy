package com.whoismept.justproxy.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whoismept.justproxy.data.ConnectionLog
import com.whoismept.justproxy.data.ConnectionLogStore
import com.whoismept.justproxy.data.ProxyProfile
import com.whoismept.justproxy.data.ProxyRepository
import com.whoismept.justproxy.utils.ProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean
)

class ProxyViewModel(private val repository: ProxyRepository) : ViewModel() {

    val allProfiles: StateFlow<List<ProxyProfile>> = repository.allProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectionLogs: StateFlow<List<ConnectionLog>> = ConnectionLogStore.logs

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    // Cache key: avoid re-scanning the package list unless showSystemApps changes.
    private var lastShowSystemApps: Boolean? = null

    @SuppressLint("QueryPermissionsNeeded")
    fun loadInstalledApps(context: Context, showSystemApps: Boolean) {
        if (showSystemApps == lastShowSystemApps && _installedApps.value.isNotEmpty()) return
        lastShowSystemApps = showSystemApps
        viewModelScope.launch {
            _installedApps.value = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.getInstalledApplications(PackageManager.GET_META_DATA).map { app ->
                    val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                                   (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                    AppInfo(
                        name        = app.loadLabel(pm).toString(),
                        packageName = app.packageName,
                        icon        = app.loadIcon(pm),
                        isSystemApp = isSystem
                    )
                }
                .filter { showSystemApps || !it.isSystemApp }
                .sortedBy { it.name }
            }
        }
    }

    fun resetNetwork() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            ProxyManager.stopProxy()
        }
        repository.deactivateAll()
    }

    fun insert(profile: ProxyProfile) = viewModelScope.launch {
        repository.insert(profile)
    }

    fun delete(profile: ProxyProfile) = viewModelScope.launch {
        if (profile.isActive) {
            ProxyManager.stopProxy()
        }
        repository.delete(profile)
    }

    fun update(profile: ProxyProfile) = viewModelScope.launch {
        repository.update(profile)
    }

    fun clearLogs() = ConnectionLogStore.clear()

    fun toggleProfile(profile: ProxyProfile) = viewModelScope.launch {
        if (profile.isActive) {
            repository.deactivateAll()
            withContext(Dispatchers.IO) {
                ProxyManager.stopProxy()
            }
        } else {
            repository.activateProfile(profile)
        }
    }
}

class ProxyViewModelFactory(private val repository: ProxyRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProxyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProxyViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
