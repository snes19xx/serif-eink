package com.snes19xx.einklauncher

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView

// RecyclerView adapter for displaying the monthly calendar grid
class CalendarAdapter(
    private val days: List<String>,
    private val currentDay: Int,
    private val selectedFilterDay: Int?,
    private val eventDays: Set<Int>,
    private val onDayClicked: (Int) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.day_text)
        val dot: View = view.findViewById(R.id.event_dot)
        val rootView: View = view.findViewById(R.id.day_root) ?: view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val context = holder.itemView.context
        val text = days[position]
        holder.textView.text = text

        val inkColor = ContextCompat.getColor(context, R.color.ink)
        val whiteColor = ContextCompat.getColor(context, R.color.white)
        val mutedColor = ContextCompat.getColor(context, R.color.muted)
        val selectionColor = ContextCompat.getColor(context, R.color.fill_medium)

        try {
            holder.textView.typeface = ResourcesCompat.getFont(context, R.font.eb_garamond)
        } catch (e: Exception) {}

        holder.textView.setBackgroundColor(Color.TRANSPARENT)
        holder.dot.visibility = View.INVISIBLE

        if (position < 7) {
            holder.rootView.setOnClickListener(null)
            holder.textView.textSize = 11f
            holder.textView.setTextColor(mutedColor)
        } else {
            holder.textView.textSize = 12f
            val dayNum = text.toIntOrNull()

            if (dayNum != null) {
                if (dayNum == currentDay) {
                    holder.textView.setBackgroundColor(inkColor)
                    holder.textView.setTextColor(whiteColor)
                } else if (dayNum == selectedFilterDay) {
                    holder.textView.setBackgroundColor(selectionColor)
                    holder.textView.setTextColor(inkColor)
                } else {
                    holder.textView.setBackgroundColor(Color.TRANSPARENT)
                    holder.textView.setTextColor(inkColor)
                }

                if (eventDays.contains(dayNum)) {
                    holder.dot.visibility = View.VISIBLE
                    if (dayNum == currentDay) {
                        holder.dot.setBackgroundColor(whiteColor)
                    } else {
                        holder.dot.setBackgroundColor(inkColor)
                    }
                }

                holder.rootView.setOnClickListener {
                    onDayClicked(dayNum)
                }
            } else {
                holder.rootView.setOnClickListener(null)
            }
        }
    }

    override fun getItemCount() = days.size
}
