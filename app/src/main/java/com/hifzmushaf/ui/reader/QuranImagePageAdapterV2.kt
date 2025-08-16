package com.hifzmushaf.ui.reader

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.hifzmushaf.databinding.ItemQuranImagePageV2Binding

/**
 * New adapter for QuranImagePage that uses the enhanced QuranImagePage component
 * with Qpc14LinesV2 and BlackImagesWordByWord support
 */
class QuranImagePageAdapterV2(
    private val fragment: QuranImageReaderFragment,
    private val totalPages: Int,
    private val pageInfoMap: Map<Int, PageInfo>
) : RecyclerView.Adapter<QuranImagePageAdapterV2.QuranImagePageViewHolder>() {

    companion object {
        private const val TAG = "QuranImagePageAdapterV2"
    }
    
    // Cache for QuranImagePage views to reuse them efficiently
    private val pageViewCache = mutableMapOf<Int, QuranImagePage>()

    inner class QuranImagePageViewHolder(val binding: ItemQuranImagePageV2Binding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        var quranImagePage: QuranImagePage? = null
        
        fun bind(pageNumber: Int) {
            Log.d(TAG, "🔗 Binding page $pageNumber to ViewHolder")
            
            // Remove any existing page view
            binding.pageContainer.removeAllViews()
            
            // Get or create QuranImagePage
            quranImagePage = pageViewCache[pageNumber] ?: QuranImagePage(binding.root.context).also {
                Log.d(TAG, "🆕 Creating new QuranImagePage for page $pageNumber")
                pageViewCache[pageNumber] = it
            }
            
            // Configure the page view
            quranImagePage?.let { pageView ->
                Log.d(TAG, "⚙️ Configuring QuranImagePage for page $pageNumber")
                
                // Remove from any previous parent
                (pageView.parent as? ViewGroup)?.removeView(pageView)
                
                // Set layout parameters
                val layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                pageView.layoutParams = layoutParams
                
                // Add to container
                binding.pageContainer.addView(pageView)
                
                // Set page data
                val pageInfo = pageInfoMap[pageNumber]
                Log.d(TAG, "📄 Setting page data for page $pageNumber, calling setPage()")
                pageView.setPage(pageNumber, pageInfo)
                pageView.setMaskedMode(fragment.isMaskedMode())
                
                // Set word click listener
                pageView.setOnWordClickListener { word ->
                    fragment.onWordClicked(word)
                }
                
                Log.d(TAG, "✅ Page $pageNumber binding complete")
            }
        }
        
        fun updateMaskedMode(masked: Boolean) {
            quranImagePage?.setMaskedMode(masked)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuranImagePageViewHolder {
        Log.d(TAG, "🏗️ Creating ViewHolder for V2 adapter")
        val binding = ItemQuranImagePageV2Binding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QuranImagePageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QuranImagePageViewHolder, position: Int) {
        val pageNumber = position + 1
        Log.d(TAG, "🔗 Binding ViewHolder for page $pageNumber (position $position)")
        holder.bind(pageNumber)
    }

    override fun onViewRecycled(holder: QuranImagePageViewHolder) {
        // Clean up the page view but don't remove from cache
        holder.quranImagePage?.cleanup()
        super.onViewRecycled(holder)
    }

    override fun getItemCount() = totalPages
    
    /**
     * Updates masked mode for all currently bound views
     */
    fun updateMaskedMode(masked: Boolean) {
        // Update all cached page views
        pageViewCache.values.forEach { pageView ->
            pageView.setMaskedMode(masked)
        }
    }
    
    /**
     * Reveals a word on a specific page
     */
    fun revealWord(pageNumber: Int, word: WordBoundary) {
        pageViewCache[pageNumber]?.revealWord(word)
    }
    
    /**
     * Clears the page view cache and cleans up resources
     */
    fun clearCache() {
        pageViewCache.values.forEach { pageView ->
            pageView.cleanup()
        }
        pageViewCache.clear()
    }
    
    /**
     * Gets cache statistics from all page views
     */
    fun getCacheStats(): QuranImageCache.CacheStats? {
        return pageViewCache.values.firstOrNull()?.let { pageView ->
            // All page views share the same cache instance, so we can get stats from any one
            pageView.imageCache.getCacheStats()
        }
    }
}
