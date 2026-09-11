package com.example.blocker

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.blocker.databinding.ItemAppBinding

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    var blocked: Boolean
)

class AppsAdapter(
    private val apps: MutableList<AppInfo>,
    private val onToggle: (AppInfo, Boolean) -> Unit
) : RecyclerView.Adapter<AppsAdapter.AppViewHolder>() {

    inner class AppViewHolder(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val item = apps[position]
        holder.binding.txtAppName.text = item.label
        holder.binding.imgIcon.setImageDrawable(item.icon)

        // جلوگیری از فراخوانی ناخواسته‌ی listener هنگام recycle شدن view
        holder.binding.checkBlock.setOnCheckedChangeListener(null)
        holder.binding.checkBlock.isChecked = item.blocked
        holder.binding.checkBlock.setOnCheckedChangeListener { _, isChecked ->
            item.blocked = isChecked
            onToggle(item, isChecked)
        }
    }

    override fun getItemCount(): Int = apps.size
}
