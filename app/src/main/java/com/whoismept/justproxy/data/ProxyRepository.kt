package com.whoismept.justproxy.data

import kotlinx.coroutines.flow.Flow

class ProxyRepository(private val proxyDao: ProxyDao) {
    val allProfiles: Flow<List<ProxyProfile>> = proxyDao.getAllProfiles()

    suspend fun insert(profile: ProxyProfile) {
        proxyDao.insertProfile(profile)
    }

    suspend fun delete(profile: ProxyProfile) {
        proxyDao.deleteProfile(profile)
    }

    suspend fun update(profile: ProxyProfile) {
        proxyDao.updateProfile(profile)
    }

    suspend fun activateProfile(profile: ProxyProfile) {
        proxyDao.deactivateAll()
        proxyDao.updateProfile(profile.copy(isActive = true))
    }

    suspend fun deactivateAll() {
        proxyDao.deactivateAll()
    }
}
