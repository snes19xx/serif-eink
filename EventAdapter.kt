package com.snes19xx.einklauncher

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// RecyclerView adapter for displaying calendar events
class EventAdapter(
    private var events: List<EventInfo>,
    private val onConnectGoogleCalendar: () -> Unit
) : RecyclerView.Adapter<EventAdapter.ViewHolder>() {

    fun updateData(newEvents: List<EventInfo>) {
        events = newEvents
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val bar: View = view.findViewById(R.id.event_bar)
        val time: TextView = view.findViewById(R.id.event_time)
        val title: TextView = view.findViewById(R.id.event_title)
        val where: TextView = view.findViewById(R.id.event_where)
        val rootView: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = events[position]
        holder.time.text = event.time
        holder.title.text = event.title
        holder.where.text = event.location

        val barColor = if (event.isSoft) "#AAAAAA" else "#000000"
        holder.bar.setBackgroundColor(Color.parseColor(barColor))

        holder.rootView.setOnClickListener {
            if (event.title == "Google Calendar" && event.isSoft) {
                onConnectGoogleCalendar()
            }
        }
    }

    override fun getItemCount() = events.size
}
