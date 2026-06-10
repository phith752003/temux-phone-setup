package com.autoclicker.app.storage

import android.content.Context
import android.util.Log
import com.autoclicker.app.macro.MacroProfile
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * JSON file-based profile storage.
 * Profiles are stored as individual JSON files in the app's internal storage.
 * Simple, lightweight, no Room dependency needed for MVP.
 */
class ProfileStorage(private val context: Context) {

    companion object {
        private const val TAG = "ProfileStorage"
        private const val PROFILES_DIR = "profiles"
    }

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private val profilesDir: File
        get() {
            val dir = File(context.filesDir, PROFILES_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    /**
     * Save a profile to storage.
     */
    fun saveProfile(profile: MacroProfile) {
        try {
            val file = File(profilesDir, "${profile.id}.json")
            file.writeText(gson.toJson(profile))
            Log.d(TAG, "Profile saved: ${profile.name} (${profile.id})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save profile: ${e.message}", e)
        }
    }

    /**
     * Load a profile by ID.
     */
    fun getProfile(id: String): MacroProfile? {
        return try {
            val file = File(profilesDir, "$id.json")
            if (!file.exists()) return null
            gson.fromJson(file.readText(), MacroProfile::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load profile: ${e.message}", e)
            null
        }
    }

    /**
     * Get all saved profiles.
     */
    fun getAllProfiles(): List<MacroProfile> {
        return try {
            profilesDir.listFiles { file -> file.extension == "json" }
                ?.mapNotNull { file ->
                    try {
                        gson.fromJson(file.readText(), MacroProfile::class.java)
                    } catch (e: Exception) {
                        Log.w(TAG, "Skipping corrupt profile: ${file.name}")
                        null
                    }
                }
                ?.sortedByDescending { it.updatedAt }
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list profiles: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Delete a profile by ID.
     */
    fun deleteProfile(id: String): Boolean {
        return try {
            val file = File(profilesDir, "$id.json")
            val deleted = file.delete()
            if (deleted) Log.d(TAG, "Profile deleted: $id")
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete profile: ${e.message}", e)
            false
        }
    }

    /**
     * Export a profile to a JSON string for sharing.
     */
    fun exportProfile(profile: MacroProfile): String {
        return gson.toJson(profile)
    }

    /**
     * Import a profile from a JSON string.
     */
    fun importProfile(json: String): MacroProfile? {
        return try {
            val profile = gson.fromJson(json, MacroProfile::class.java)
            saveProfile(profile)
            Log.i(TAG, "Profile imported: ${profile.name}")
            profile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import profile: ${e.message}", e)
            null
        }
    }

    /**
     * Export a profile to external storage file for sharing.
     */
    fun exportToFile(profile: MacroProfile, outputDir: File): File? {
        return try {
            val fileName = "${profile.name.replace(" ", "_")}_${System.currentTimeMillis()}.json"
            val file = File(outputDir, fileName)
            file.writeText(gson.toJson(profile))
            Log.i(TAG, "Profile exported to: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export profile to file: ${e.message}", e)
            null
        }
    }

    /**
     * Import a profile from a file.
     */
    fun importFromFile(file: File): MacroProfile? {
        return try {
            val json = file.readText()
            importProfile(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import from file: ${e.message}", e)
            null
        }
    }

    /**
     * Ensure the default test profile exists.
     */
    fun ensureDefaultProfile() {
        if (getAllProfiles().isEmpty()) {
            val default = MacroProfile.createDefaultTestProfile()
            saveProfile(default)
            Log.i(TAG, "Created default test profile")
        }
    }
}
