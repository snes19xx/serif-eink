package com.snes19xx.einklauncher

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// RecyclerView adapter for displaying installed apps
class AppAdapter(private val context: Context, private val appList: List<AppInfo>) :
    RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val name: TextView = view.findViewById(R.id.appName)

        init {
            view.setOnClickListener {
                val appInfo = appList[adapterPosition]
                val launchIntent = context.packageManager.getLaunchIntentForPackage(appInfo.packageName.toString())
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val appInfo = appList[position]

        val matrix = ColorMatrix()
        matrix.setSaturation(0f)
        val contrast = 1.3f
        val translate = (-128f * (contrast - 1f))
        matrix.postConcat(ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )))

        holder.icon.setImageDrawable(appInfo.icon)
        holder.icon.colorFilter = ColorMatrixColorFilter(matrix)
        holder.name.text = appInfo.label
    }

    override fun getItemCount(): Int {
        return appList.size
    }
}