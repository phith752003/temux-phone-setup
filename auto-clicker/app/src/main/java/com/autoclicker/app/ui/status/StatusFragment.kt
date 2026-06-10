package com.autoclicker.app.ui.status

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.autoclicker.app.R
import com.autoclicker.app.databinding.FragmentStatusBinding
import com.autoclicker.app.macro.MacroState
import com.autoclicker.app.monitor.BatteryMonitor
import com.autoclicker.app.monitor.ThermalMonitor
import com.autoclicker.app.service.OverlayService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Status fragment showing macro state, battery, thermal, and service status.
 * Updates at most once per second to conserve resources.
 */
class StatusFragment : Fragment() {

    private var _binding: FragmentStatusBinding? = null
    private val binding get() = _binding!!
    private lateinit var batteryMonitor: BatteryMonitor
    private lateinit var thermalMonitor: ThermalMonitor

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        batteryMonitor = BatteryMonitor(requireContext())
        thermalMonitor = ThermalMonitor(requireContext())
        batteryMonitor.start()
        thermalMonitor.start()

        // Update status at most once per second
        viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                updateStatus()
                delay(1000)
            }
        }
    }

    private fun updateStatus() {
        val ctx = context ?: return

        // Macro state - read from overlay service if available
        val macroStateText = "IDLE"  // Default when no overlay
        binding.tvMacroState.text = macroStateText
        binding.tvMacroState.setTextColor(ContextCompat.getColor(ctx, R.color.status_stopped))

        // Battery
        binding.tvBattery.text = batteryMonitor.getStatusString()

        // Thermal
        binding.tvThermal.text = thermalMonitor.getStatusString()

        // Foreground service
        binding.tvForeground.text = if (OverlayService.isRunning) "Active" else "Inactive"

        // Progress (placeholder when not running)
        binding.tvCurrentLoop.text = "0 / 0"
        binding.tvCurrentAction.text = "—"
        binding.tvActionCount.text = "0"
        binding.tvRuntime.text = "00:00"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        batteryMonitor.stop()
        thermalMonitor.stop()
        _binding = null
    }
}
