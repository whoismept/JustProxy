package com.proxydegil.proxyapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProxyDao {
    @Query("SELECT * FROM proxy_profiles")
    fun getAllProfiles(): Flow<List<ProxyProfile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProxyProfile)

    @Delete
    suspend fun deleteProfile(profile: ProxyProfile)

    @Query("UPDATE proxy_profiles SET isActive = 0")
    suspend fun deactivateAll()

    @Update
    suspend fun updateProfile(profile: ProxyProfile)
}
