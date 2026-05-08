package com.snes19xx.einklauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// RecyclerView adapter for displaying books in the library
class BookAdapter(
    private val books: List<BookInfo>,
    private val onBookClicked: (BookInfo) -> Unit,
    private val onBookLongClicked: (BookInfo) -> Unit
) : RecyclerView.Adapter<BookAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val coverImage: ImageView = view.findViewById(R.id.book_cover_image)
        val textContainer: LinearLayout = view.findViewById(R.id.book_cover_text_container)
        val title: TextView = view.findViewById(R.id.book_title)
        val author: TextView = view.findViewById(R.id.book_author)
        val rootView: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_book, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val book = books[position]

        if (book.thumbnail != null) {
            holder.coverImage.setImageBitmap(book.thumbnail)
            holder.textContainer.visibility = View.GONE
        } else {
            holder.coverImage.setImageDrawable(null)
            holder.textContainer.visibility = View.VISIBLE
            holder.title.text = book.title
            holder.author.text = book.author
        }

        holder.rootView.setOnClickListener {
            onBookClicked(book)
        }

        holder.rootView.setOnLongClickListener {
            onBookLongClicked(book)
            true
        }
    }

    override fun getItemCount() = books.size
}
