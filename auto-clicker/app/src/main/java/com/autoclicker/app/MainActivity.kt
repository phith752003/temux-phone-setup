package com.autoclicker.app

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.autoclicker.app.databinding.ActivityMainBinding
import com.autoclicker.app.storage.ProfileStorage

/**
 * Main entry point for the Auto Clicker app.
 * Sets up bottom navigation with 4 fragments: Home, Profiles, Editor, Status.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AutoClickerApp"
    }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.i(TAG, "App started")

        // Setup navigation
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)

        // Ensure default profile exists
        val storage = ProfileStorage(this)
        storage.ensureDefaultProfile()

        Log.i(TAG, "Navigation setup complete")
    }
}
