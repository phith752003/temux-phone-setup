package com.autoclicker.app.ui.profile

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
import androidx.recyclerview.widget.RecyclerView
import com.autoclicker.app.R
import com.autoclicker.app.databinding.FragmentProfileBinding
import com.autoclicker.app.macro.MacroProfile
import com.autoclicker.app.storage.ProfileStorage
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context


/**
 * Fragment showing profile list with create/edit/delete functionality.
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var storage: ProfileStorage
    private val profiles = mutableListOf<MacroProfile>()
    private lateinit var adapter: ProfileAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        storage = ProfileStorage(requireContext())

        adapter = ProfileAdapter(
            profiles = profiles,
            onSelect = { /* profile selected - could navigate to editor */ },
            onEdit = { showEditDialog(it) },
            onDelete = { confirmDelete(it) },
            onExport = { exportProfileToClipboard(it) }
        )


        binding.rvProfiles.layoutManager = LinearLayoutManager(context)
        binding.rvProfiles.adapter = adapter

        binding.btnCreateProfile.setOnClickListener { showCreateDialog() }
        binding.btnImport.setOnClickListener { showImportDialog() }

        loadProfiles()
    }

    private fun loadProfiles() {
        profiles.clear()
        profiles.addAll(storage.getAllProfiles())
        adapter.notifyDataSetChanged()

        binding.tvEmpty.visibility = if (profiles.isEmpty()) View.VISIBLE else View.GONE
        binding.rvProfiles.visibility = if (profiles.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showCreateDialog() {
        val ctx = context ?: return
        val layout = createProfileFormLayout()

        AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.dialog_new_profile))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val nameEdit = layout.findViewWithTag<EditText>("name")
                val loopEdit = layout.findViewWithTag<EditText>("loops")
                val delayEdit = layout.findViewWithTag<EditText>("delay")

                val name = nameEdit.text.toString().ifBlank { "Untitled" }
                val loops = loopEdit.text.toString().toIntOrNull() ?: 20
                val delay = delayEdit.text.toString().toLongOrNull() ?: 1000

                val profile = MacroProfile(
                    name = name,
                    loopCount = loops,
                    delayBetweenActionsMs = delay
                )
                storage.saveProfile(profile)
                loadProfiles()
                Toast.makeText(ctx, "Profile created: $name", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showEditDialog(profile: MacroProfile) {
        val ctx = context ?: return
        val layout = createProfileFormLayout()

        layout.findViewWithTag<EditText>("name").setText(profile.name)
        layout.findViewWithTag<EditText>("loops").setText(profile.loopCount.toString())
        layout.findViewWithTag<EditText>("delay").setText(profile.delayBetweenActionsMs.toString())

        AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.dialog_edit_profile))
            .setView(layout)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val nameEdit = layout.findViewWithTag<EditText>("name")
                val loopEdit = layout.findViewWithTag<EditText>("loops")
                val delayEdit = layout.findViewWithTag<EditText>("delay")

                val updated = profile.copy(
                    name = nameEdit.text.toString().ifBlank { profile.name },
                    loopCount = loopEdit.text.toString().toIntOrNull() ?: profile.loopCount,
                    delayBetweenActionsMs = delayEdit.text.toString().toLongOrNull() ?: profile.delayBetweenActionsMs,
                    updatedAt = System.currentTimeMillis()
                )
                storage.saveProfile(updated)
                loadProfiles()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun confirmDelete(profile: MacroProfile) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.btn_delete))
            .setMessage(getString(R.string.confirm_delete))
            .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                storage.deleteProfile(profile.id)
                loadProfiles()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun exportProfileToClipboard(profile: MacroProfile) {
        val ctx = context ?: return
        val json = storage.exportProfile(profile)
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AutoClickerProfile", json)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(ctx, "Đã sao chép Profile ${profile.name} vào Clipboard!", Toast.LENGTH_SHORT).show()
    }

    private fun showImportDialog() {
        val ctx = context ?: return
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipboardText = if (clipboard.hasPrimaryClip()) {
            clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        } else {
            ""
        }

        val isJson = clipboardText.trim().startsWith("{") && clipboardText.trim().endsWith("}")

        if (isJson) {
            AlertDialog.Builder(ctx)
                .setTitle("Nhập cấu hình Profile")
                .setMessage("Phát hiện một cấu hình JSON trong Clipboard. Bạn có muốn nhập trực tiếp không?")
                .setPositiveButton("Nhập trực tiếp") { _, _ ->
                    val imported = storage.importProfile(clipboardText)
                    if (imported != null) {
                        loadProfiles()
                        Toast.makeText(ctx, "Đã nhập: ${imported.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, "Lỗi khi nhập cấu hình", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Nhập thủ công") { _, _ ->
                    showManualImportDialog("")
                }
                .setNeutralButton("Hủy", null)
                .show()
        } else {
            showManualImportDialog("")
        }
    }

    private fun showManualImportDialog(initialText: String) {
        val ctx = context ?: return
        val editText = EditText(ctx).apply {
            hint = "Dán mã JSON của Profile tại đây"
            setText(initialText)
            setPadding(48, 24, 48, 24)
        }

        AlertDialog.Builder(ctx)
            .setTitle("Nhập cấu hình thủ công")
            .setView(editText)
            .setPositiveButton("Nhập") { _, _ ->
                val json = editText.text.toString()
                val imported = storage.importProfile(json)
                if (imported != null) {
                    loadProfiles()
                    Toast.makeText(ctx, "Đã nhập: ${imported.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(ctx, "Lỗi: JSON không hợp lệ!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }


    private fun createProfileFormLayout(): LinearLayout {
        val ctx = requireContext()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)

            addView(EditText(ctx).apply {
                tag = "name"
                hint = getString(R.string.hint_profile_name)
            })
            addView(EditText(ctx).apply {
                tag = "loops"
                hint = getString(R.string.hint_loop_count)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
            })
            addView(EditText(ctx).apply {
                tag = "delay"
                hint = getString(R.string.hint_delay_between)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
            })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
