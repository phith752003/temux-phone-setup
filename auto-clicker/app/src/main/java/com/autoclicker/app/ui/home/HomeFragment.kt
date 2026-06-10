package com.autoclicker.app.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.autoclicker.app.R
import com.autoclicker.app.databinding.FragmentHomeBinding
import com.autoclicker.app.service.AutoClickerService
import com.autoclicker.app.service.OverlayService
import com.autoclicker.app.macro.ScreenCaptureHelper
import android.media.projection.MediaProjectionManager
import android.app.Activity

/**
 * Home screen showing permission status and overlay toggle.
 * Checks and guides user to enable required permissions.
 * Does NOT crash if permissions are missing.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val projectionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenCaptureHelper.projectionResultCode = result.resultCode
            ScreenCaptureHelper.projectionIntentData = result.data
            updatePermissionStatus()
            Toast.makeText(context, "Screen capture permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAccessibility.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open Accessibility Settings", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnOverlay.setOnClickListener {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}")
                )
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open Overlay Settings", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnBatteryOpt.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open Battery Settings", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnScreenCapture.setOnClickListener {
            val ctx = requireContext()
            val mediaProjectionManager = ctx.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            try {
                projectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
            } catch (e: Exception) {
                Toast.makeText(ctx, "Failed to launch screen capture request", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnToggleOverlay.setOnClickListener {
            val ctx = requireContext()
            if (!AutoClickerService.isServiceEnabled()) {
                Toast.makeText(ctx, getString(R.string.error_no_accessibility), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!Settings.canDrawOverlays(ctx)) {
                Toast.makeText(ctx, getString(R.string.error_no_overlay), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (OverlayService.isRunning) {
                OverlayService.stop(ctx)
                binding.btnToggleOverlay.text = "Show Overlay"
            } else {
                OverlayService.start(ctx)
                binding.btnToggleOverlay.text = "Hide Overlay"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        val ctx = context ?: return

        // Accessibility
        val accessibilityEnabled = AutoClickerService.isServiceEnabled()
        binding.tvAccessibilityStatus.text = if (accessibilityEnabled)
            getString(R.string.status_enabled) else getString(R.string.status_disabled)
        binding.tvAccessibilityStatus.setTextColor(
            ContextCompat.getColor(ctx, if (accessibilityEnabled) R.color.success else R.color.error)
        )

        // Overlay
        val overlayGranted = Settings.canDrawOverlays(ctx)
        binding.tvOverlayStatus.text = if (overlayGranted)
            getString(R.string.status_granted) else getString(R.string.status_not_granted)
        binding.tvOverlayStatus.setTextColor(
            ContextCompat.getColor(ctx, if (overlayGranted) R.color.success else R.color.error)
        )

        // Battery optimization
        val pm = ctx.getSystemService(PowerManager::class.java)
        val isIgnoring = pm?.isIgnoringBatteryOptimizations(ctx.packageName) ?: false
        binding.tvBatteryOptStatus.text = if (isIgnoring)
            getString(R.string.status_ignored) else getString(R.string.status_optimized)
        binding.tvBatteryOptStatus.setTextColor(
            ContextCompat.getColor(ctx, if (isIgnoring) R.color.success else R.color.warning)
        )

        // Screen Capture
        val hasProjection = ScreenCaptureHelper.isAuthorized
        binding.tvScreenCaptureStatus.text = if (hasProjection)
            getString(R.string.status_granted_capture) else getString(R.string.status_not_granted)
        binding.tvScreenCaptureStatus.setTextColor(
            ContextCompat.getColor(ctx, if (hasProjection) R.color.success else R.color.error)
        )

        // Overlay toggle button
        binding.btnToggleOverlay.text = if (OverlayService.isRunning) "Hide Overlay" else "Show Overlay"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
