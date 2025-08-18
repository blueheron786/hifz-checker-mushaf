package com.hifzmushaf.ui.surah

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hifzmushaf.R

class PageListAdapter(
    private val pages: List<Page>,
    private val onPageClick: (Page) -> Unit
) : RecyclerView.Adapter<PageListAdapter.PageViewHolder>() {

    class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val pageNumberTextView: TextView = itemView.findViewById(R.id.pageNumberTextView)
        val pageInfoTextView: TextView = itemView.findViewById(R.id.pageInfoTextView)
        val surahInfoTextView: TextView = itemView.findViewById(R.id.surahInfoTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_page_list, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val page = pages[position]
        
        holder.pageNumberTextView.text = page.pageNumber.toString()
        holder.pageInfoTextView.text = "Page ${page.pageNumber}"
        holder.surahInfoTextView.text = page.surahInfo
        
        holder.itemView.setOnClickListener {
            onPageClick(page)
        }
    }

    override fun getItemCount(): Int = pages.size
}
