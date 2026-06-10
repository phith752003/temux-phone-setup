package com.autoclicker.app.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.autoclicker.app.databinding.ItemProfileBinding
import com.autoclicker.app.macro.MacroProfile

/**
 * RecyclerView adapter for profile list.
 */
class ProfileAdapter(
    private val profiles: List<MacroProfile>,
    private val onSelect: (MacroProfile) -> Unit,
    private val onEdit: (MacroProfile) -> Unit,
    private val onDelete: (MacroProfile) -> Unit,
    private val onExport: (MacroProfile) -> Unit
) : RecyclerView.Adapter<ProfileAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemProfileBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val profile = profiles[position]
        holder.binding.apply {
            tvName.text = profile.name
            tvDetails.text = "${profile.loopCountDisplay} loops • ${profile.actions.size} actions • ${profile.delayBetweenActionsMs}ms delay"
            btnSelect.setOnClickListener { onSelect(profile) }
            btnEdit.setOnClickListener { onEdit(profile) }
            btnDelete.setOnClickListener { onDelete(profile) }
            btnExport.setOnClickListener { onExport(profile) }
        }
    }

    override fun getItemCount(): Int = profiles.size
}
