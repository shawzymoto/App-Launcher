package com.example.applauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class AppDrawerAdapter(
    private val onAppClicked: (AppInfo) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_APP = 1
    }

    sealed class DrawerItem {
        data class Header(val label: String) : DrawerItem()
        data class App(val appInfo: AppInfo) : DrawerItem()
    }

    private val items = mutableListOf<DrawerItem>()

    fun submitList(appList: List<AppInfo>) {
        items.clear()

        var currentSection: String? = null
        for (app in appList) {
            val firstChar = app.name.trim().firstOrNull()?.toString()?.uppercase(Locale.getDefault()) ?: "#"
            val section = if (firstChar.first().isLetterOrDigit()) firstChar else "#"

            if (section != currentSection) {
                currentSection = section
                items.add(DrawerItem.Header(section))
            }
            items.add(DrawerItem.App(app))
        }

        notifyDataSetChanged()
    }

    fun isHeader(position: Int): Boolean {
        return getItemViewType(position) == VIEW_TYPE_HEADER
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is DrawerItem.Header -> VIEW_TYPE_HEADER
            is DrawerItem.App -> VIEW_TYPE_APP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_app_drawer_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_app_drawer_app, parent, false)
            AppViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is DrawerItem.Header -> (holder as HeaderViewHolder).bind(item.label)
            is DrawerItem.App -> (holder as AppViewHolder).bind(item.appInfo)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val headerTextView: TextView = itemView.findViewById(R.id.app_drawer_header_text_view)

        fun bind(label: String) {
            headerTextView.text = label
        }
    }

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
