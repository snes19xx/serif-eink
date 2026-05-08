package com.snes19xx.einklauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// RecyclerView adapter for displaying recently used PDF files
class PdfAdapter(
    private val pdfs: List<PdfInfo>,
    private val onPdfClicked: (PdfInfo) -> Unit
) : RecyclerView.Adapter<PdfAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.pdf_thumbnail)
        val placeholder: View = view.findViewById(R.id.pdf_thumbnail_placeholder)
        val name: TextView = view.findViewById(R.id.pdf_name)
        val date: TextView = view.findViewById(R.id.pdf_date)
        val rootView: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pdf = pdfs[position]
        holder.name.text = pdf.name
        holder.date.text = pdf.date

        if (pdf.thumbnail != null) {
            holder.thumbnail.setImageBitmap(pdf.thumbnail)
            holder.thumbnail.visibility = View.VISIBLE
            holder.placeholder.visibility = View.GONE
        } else {
            holder.thumbnail.setImageDrawable(null)
            holder.thumbnail.visibility = View.GONE
            holder.placeholder.visibility = View.VISIBLE
        }

        holder.rootView.setOnClickListener {
            onPdfClicked(pdf)
        }
    }

    override fun getItemCount() = pdfs.size
}
