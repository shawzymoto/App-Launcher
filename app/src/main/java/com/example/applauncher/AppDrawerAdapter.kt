package com.example.applauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppDrawerAdapter(
    private val onAppClicked: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppDrawerAdapter.AppViewHolder>() {

    private val apps = mutableListOf<AppInfo>()

    fun submitList(appList: List<AppInfo>) {
        apps.clear()
        apps.addAll(appList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_drawer_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    override fun getItemCount(): Int = apps.size

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconImageView: ImageView = itemView.findViewById(R.id.app_icon_image_view)
        private val nameTextView: TextView = itemView.findViewById(R.id.app_name_text_view)

        fun bind(appInfo: AppInfo) {
            nameTextView.text = appInfo.name
            iconImageView.setImageDrawable(appInfo.icon)
            itemView.setOnClickListener {
                onAppClicked(appInfo)
            }
        }
    }
}
