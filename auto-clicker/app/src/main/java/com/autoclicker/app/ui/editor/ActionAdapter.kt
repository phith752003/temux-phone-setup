package com.autoclicker.app.ui.editor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.autoclicker.app.databinding.ItemActionBinding
import com.autoclicker.app.macro.ActionType
import com.autoclicker.app.macro.MacroAction

/**
 * RecyclerView adapter for macro actions list.
 */
class ActionAdapter(
    private val actions: MutableList<MacroAction>,
    private val onEdit: (MacroAction) -> Unit,
    private val onRemove: (MacroAction) -> Unit
) : RecyclerView.Adapter<ActionAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemActionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemActionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val action = actions[position]
        holder.binding.apply {
            tvOrder.text = "${position + 1}"
            tvType.text = action.type.name

            var paramsText = when (action.type) {
                ActionType.TAP -> "x:${action.x.toInt()} y:${action.y.toInt()} • ${action.durationMs}ms"
                ActionType.SWIPE -> "(${action.x.toInt()},${action.y.toInt()})→(${action.x2.toInt()},${action.y2.toInt()}) • ${action.durationMs}ms"
                ActionType.WAIT -> "Wait ${action.durationMs}ms"
            }
            if (action.conditionType != com.autoclicker.app.macro.ConditionType.NONE) {
                val trueTarget = if (action.onTrueGoToIndex >= 0) "Jump #${action.onTrueGoToIndex + 1}" else "Execute"
                val falseTarget = if (action.onFalseGoToIndex >= 0) "Jump #${action.onFalseGoToIndex + 1}" else "Skip"
                paramsText += "\nCond: ${action.conditionType.name} | True: $trueTarget | False: $falseTarget"
            }
            tvParams.text = paramsText

            btnRemove.setOnClickListener { onRemove(action) }
            root.setOnClickListener { onEdit(action) }
        }
    }

    override fun getItemCount(): Int = actions.size

    fun updateActions(newActions: List<MacroAction>) {
        actions.clear()
        actions.addAll(newActions)
        notifyDataSetChanged()
    }
}
