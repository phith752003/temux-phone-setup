package com.autoclicker.app.ui.editor

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.autoclicker.app.R
import com.autoclicker.app.databinding.FragmentMacroEditorBinding
import com.autoclicker.app.macro.ActionType
import com.autoclicker.app.macro.MacroAction
import com.autoclicker.app.macro.MacroProfile
import com.autoclicker.app.storage.ProfileStorage

/**
 * Fragment for editing macro actions within a profile.
 * Supports adding tap/swipe/wait actions and setting loop/delay parameters.
 */
class MacroEditorFragment : Fragment() {

    private var _binding: FragmentMacroEditorBinding? = null
    private val binding get() = _binding!!
    private lateinit var storage: ProfileStorage
    private var currentProfile: MacroProfile? = null
    private lateinit var adapter: ActionAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMacroEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        storage = ProfileStorage(requireContext())

        adapter = ActionAdapter(
            actions = mutableListOf(),
            onEdit = { action -> showEditActionDialog(action) },
            onRemove = { action -> removeAction(action) }
        )

        binding.rvActions.layoutManager = LinearLayoutManager(context)
        binding.rvActions.adapter = adapter

        binding.btnAddTap.setOnClickListener { showAddTapDialog() }
        binding.btnAddSwipe.setOnClickListener { showAddSwipeDialog() }
        binding.btnAddWait.setOnClickListener { showAddWaitDialog() }

        loadFirstProfile()
    }

    private fun loadFirstProfile() {
        val profiles = storage.getAllProfiles()
        currentProfile = if (profiles.isNotEmpty()) {
            profiles.first()
        } else {
            storage.ensureDefaultProfile()
            storage.getAllProfiles().firstOrNull()
        }
        updateUI()
    }

    private fun updateUI() {
        val profile = currentProfile ?: return

        binding.tvProfileName.text = profile.name
        binding.tvLoopCount.text = "Loops: ${profile.loopCountDisplay}"
        binding.tvDelay.text = "Delay: ${profile.delayBetweenActionsMs}ms"

        adapter.updateActions(profile.actions.sortedBy { it.orderIndex })

        binding.tvNoActions.visibility = if (profile.actions.isEmpty()) View.VISIBLE else View.GONE
        binding.rvActions.visibility = if (profile.actions.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showAddTapDialog() {
        val ctx = context ?: return
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
            addView(EditText(ctx).apply { tag = "x"; hint = getString(R.string.hint_x); setText("540"); inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL })
            addView(EditText(ctx).apply { tag = "y"; hint = getString(R.string.hint_y); setText("960"); inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL })
            addView(EditText(ctx).apply { tag = "dur"; hint = getString(R.string.hint_duration); setText("100"); inputType = android.text.InputType.TYPE_CLASS_NUMBER })
            addView(EditText(ctx).apply { tag = "delay"; hint = getString(R.string.hint_delay_after); setText("1000"); inputType = android.text.InputType.TYPE_CLASS_NUMBER })
        }

        AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.dialog_add_tap))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val profile = currentProfile ?: return@setPositiveButton
                val action = MacroAction(
                    type = ActionType.TAP,
                    x = layout.findViewWithTag<EditText>("x").text.toString().toFloatOrNull() ?: 540f,
                    y = layout.findViewWithTag<EditText>("y").text.toString().toFloatOrNull() ?: 960f,
                    durationMs = layout.findViewWithTag<EditText>("dur").text.toString().toLongOrNull() ?: 100,
                    delayAfterMs = layout.findViewWithTag<EditText>("delay").text.toString().toLongOrNull() ?: 1000,
                    orderIndex = profile.actions.size
                )
                profile.actions.add(action)
                profile.updatedAt = System.currentTimeMillis()
                storage.saveProfile(profile)
                updateUI()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showAddSwipeDialog() {
        val ctx = context ?: return
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
            addView(EditText(ctx).apply { tag = "x1"; hint = "Start X"; setText("540"); inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL })
            addView(EditText(ctx).apply { tag = "y1"; hint = "Start Y"; setText("1200"); inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL })
            addView(EditText(ctx).apply { tag = "x2"; hint = getString(R.string.hint_x2); setText("540"); inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL })
            addView(EditText(ctx).apply { tag = "y2"; hint = getString(R.string.hint_y2); setText("400"); inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL })
            addView(EditText(ctx).apply { tag = "dur"; hint = getString(R.string.hint_duration); setText("300"); inputType = android.text.InputType.TYPE_CLASS_NUMBER })
            addView(EditText(ctx).apply { tag = "delay"; hint = getString(R.string.hint_delay_after); setText("1000"); inputType = android.text.InputType.TYPE_CLASS_NUMBER })
        }

        AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.dialog_add_swipe))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val profile = currentProfile ?: return@setPositiveButton
                val action = MacroAction(
                    type = ActionType.SWIPE,
                    x = layout.findViewWithTag<EditText>("x1").text.toString().toFloatOrNull() ?: 540f,
                    y = layout.findViewWithTag<EditText>("y1").text.toString().toFloatOrNull() ?: 1200f,
                    x2 = layout.findViewWithTag<EditText>("x2").text.toString().toFloatOrNull() ?: 540f,
                    y2 = layout.findViewWithTag<EditText>("y2").text.toString().toFloatOrNull() ?: 400f,
                    durationMs = layout.findViewWithTag<EditText>("dur").text.toString().toLongOrNull() ?: 300,
                    delayAfterMs = layout.findViewWithTag<EditText>("delay").text.toString().toLongOrNull() ?: 1000,
                    orderIndex = profile.actions.size
                )
                profile.actions.add(action)
                profile.updatedAt = System.currentTimeMillis()
                storage.saveProfile(profile)
                updateUI()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showAddWaitDialog() {
        val ctx = context ?: return
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
            addView(EditText(ctx).apply { tag = "dur"; hint = "Wait duration (ms)"; setText("2000"); inputType = android.text.InputType.TYPE_CLASS_NUMBER })
        }

        AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.dialog_add_wait))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val profile = currentProfile ?: return@setPositiveButton
                val action = MacroAction(
                    type = ActionType.WAIT,
                    durationMs = layout.findViewWithTag<EditText>("dur").text.toString().toLongOrNull() ?: 2000,
                    delayAfterMs = 0,
                    orderIndex = profile.actions.size
                )
                profile.actions.add(action)
                profile.updatedAt = System.currentTimeMillis()
                storage.saveProfile(profile)
                updateUI()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun removeAction(action: MacroAction) {
        val profile = currentProfile ?: return
        profile.actions.removeAll { it.id == action.id }
        // Re-index
        profile.actions.forEachIndexed { index, a ->
            profile.actions[index] = a.copy(orderIndex = index)
        }
        profile.updatedAt = System.currentTimeMillis()
        storage.saveProfile(profile)
        updateUI()
    }

    private fun showEditActionDialog(action: MacroAction) {
        val ctx = context ?: return
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        
        val scroll = android.widget.ScrollView(ctx).apply {
            addView(layout)
        }

        // Add standard fields based on type
        val etX = EditText(ctx).apply { hint = "X coordinate"; setText(action.x.toString()); inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val etY = EditText(ctx).apply { hint = "Y coordinate"; setText(action.y.toString()); inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val etX2 = EditText(ctx).apply { hint = "End X (for Swipe)"; setText(action.x2.toString()); inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val etY2 = EditText(ctx).apply { hint = "End Y (for Swipe)"; setText(action.y2.toString()); inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val etDur = EditText(ctx).apply { hint = "Duration (ms)"; setText(action.durationMs.toString()); inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val etDelay = EditText(ctx).apply { hint = "Delay after (ms)"; setText(action.delayAfterMs.toString()); inputType = android.text.InputType.TYPE_CLASS_NUMBER }

        if (action.type == ActionType.TAP || action.type == ActionType.SWIPE) {
            layout.addView(android.widget.TextView(ctx).apply { text = "Coordinates & Duration" })
            layout.addView(etX)
            layout.addView(etY)
            if (action.type == ActionType.SWIPE) {
                layout.addView(etX2)
                layout.addView(etY2)
            }
        }
        layout.addView(android.widget.TextView(ctx).apply { text = "Timing" })
        layout.addView(etDur)
        layout.addView(etDelay)

        // Condition Fields
        layout.addView(android.widget.TextView(ctx).apply { text = "\nImage Recognition Condition"; setTypeface(null, android.graphics.Typeface.BOLD) })
        
        val spCond = android.widget.Spinner(ctx)
        val condOptions = com.autoclicker.app.macro.ConditionType.values().map { it.name }
        spCond.adapter = android.widget.ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, condOptions)
        spCond.setSelection(action.conditionType.ordinal)
        layout.addView(spCond)

        layout.addView(android.widget.TextView(ctx).apply { text = "\nSearch Region (Left, Top, Right, Bottom %)" })
        val etRegL = EditText(ctx).apply { hint = "Left %"; setText(action.searchRegionLeft.toString()); inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val etRegT = EditText(ctx).apply { hint = "Top %"; setText(action.searchRegionTop.toString()); inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val etRegR = EditText(ctx).apply { hint = "Right %"; setText(action.searchRegionRight.toString()); inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val etRegB = EditText(ctx).apply { hint = "Bottom %"; setText(action.searchRegionBottom.toString()); inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        
        val regLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(etRegL.apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
            addView(etRegT.apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
            addView(etRegR.apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
            addView(etRegB.apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        }
        layout.addView(regLayout)

        val etThreshold = EditText(ctx).apply { hint = "Threshold (0.1..1.0)"; setText(action.similarityThreshold.toString()); inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }
        layout.addView(android.widget.TextView(ctx).apply { text = "Similarity Threshold" })
        layout.addView(etThreshold)

        val etTrueIndex = EditText(ctx).apply { hint = "Jump index if True (Action #, 1-indexed. -1 to execute)"; setText(if (action.onTrueGoToIndex >= 0) (action.onTrueGoToIndex + 1).toString() else "-1"); inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED }
        val etFalseIndex = EditText(ctx).apply { hint = "Jump index if False (Action #, 1-indexed. -1 to skip)"; setText(if (action.onFalseGoToIndex >= 0) (action.onFalseGoToIndex + 1).toString() else "-1"); inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED }
        
        layout.addView(android.widget.TextView(ctx).apply { text = "Branching Jumps" })
        layout.addView(etTrueIndex)
        layout.addView(etFalseIndex)

        // Demo image button
        val btnDemoImage = android.widget.Button(ctx).apply {
            text = "Set Demo 5x5 Red Button Template"
            setOnClickListener {
                action.templateImageBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAAHElEQVQI12P4//8/w38GIAXDIBKE0DHxgljNBAAO9TXL0Y4OHwAAAABJRU5ErkJggg=="
                Toast.makeText(ctx, "Demo template image set", Toast.LENGTH_SHORT).show()
            }
        }
        layout.addView(btnDemoImage)

        AlertDialog.Builder(ctx)
            .setTitle("Edit Action #${action.orderIndex + 1}")
            .setView(scroll)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val profile = currentProfile ?: return@setPositiveButton
                
                // Save coordinates
                if (action.type == ActionType.TAP || action.type == ActionType.SWIPE) {
                    action.x = etX.text.toString().toFloatOrNull() ?: action.x
                    action.y = etY.text.toString().toFloatOrNull() ?: action.y
                    if (action.type == ActionType.SWIPE) {
                        action.x2 = etX2.text.toString().toFloatOrNull() ?: action.x2
                        action.y2 = etY2.text.toString().toFloatOrNull() ?: action.y2
                    }
                }
                action.durationMs = etDur.text.toString().toLongOrNull() ?: action.durationMs
                action.delayAfterMs = etDelay.text.toString().toLongOrNull() ?: action.delayAfterMs

                // Save condition
                action.conditionType = com.autoclicker.app.macro.ConditionType.values()[spCond.selectedItemPosition]
                action.searchRegionLeft = etRegL.text.toString().toIntOrNull() ?: action.searchRegionLeft
                action.searchRegionTop = etRegT.text.toString().toIntOrNull() ?: action.searchRegionTop
                action.searchRegionRight = etRegR.text.toString().toIntOrNull() ?: action.searchRegionRight
                action.searchRegionBottom = etRegB.text.toString().toIntOrNull() ?: action.searchRegionBottom
                action.similarityThreshold = etThreshold.text.toString().toFloatOrNull() ?: action.similarityThreshold

                val tIdx = etTrueIndex.text.toString().toIntOrNull() ?: -1
                action.onTrueGoToIndex = if (tIdx > 0) tIdx - 1 else -1

                val fIdx = etFalseIndex.text.toString().toIntOrNull() ?: -1
                action.onFalseGoToIndex = if (fIdx > 0) fIdx - 1 else -1

                // Save profile
                profile.updatedAt = System.currentTimeMillis()
                storage.saveProfile(profile)
                updateUI()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
