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
        private const val MAX_CACHED_PAGES = 10  // Limit cache size to prevent OOM
    }
    
    // Cache for QuranImagePage views to reuse them efficiently
    private val pageViewCache = mutableMapOf<Int, QuranImagePage>()
    private val accessOrder = mutableListOf<Int>()  // Track access order for LRU

    inner class QuranImagePageViewHolder(val binding: ItemQuranImagePageV2Binding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        var quranImagePage: QuranImagePage? = null
        var currentPageNumber: Int = -1 // Track which page this holder is currently showing
        
        fun bind(pageNumber: Int) {
            Log.d(TAG, "🔗 Binding page $pageNumber to ViewHolder (was showing page $currentPageNumber)")
            
            // Remove any existing page view from container
            binding.pageContainer.removeAllViews()
            
            // If this ViewHolder is being reused for a different page, clear the reference
            if (currentPageNumber != pageNumber) {
                quranImagePage = null
                currentPageNumber = pageNumber
                Log.d(TAG, "🔄 ViewHolder page changed from $currentPageNumber to $pageNumber, clearing cached reference")
            }
            
            // Get or create QuranImagePage with cache management
            quranImagePage = getOrCreatePageView(pageNumber)
            
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
                
                // Set page data first - always call setPage to ensure proper state reset
                val pageInfo = pageInfoMap[pageNumber]
                Log.d(TAG, "📄 Setting page data for page $pageNumber, calling setPage()")
                Log.d(TAG, "📊 PageInfo for page $pageNumber: ${pageInfo?.words?.size ?: 0} words")
                pageView.setPage(pageNumber, pageInfo)
                
                // Force a slight delay to ensure setPage completes before setting masked mode
                pageView.post {
                    // Set masked mode after setPage has been processed
                    val isMasked = fragment.isMaskedMode()
                    Log.d(TAG, "🔒 Setting masked mode to $isMasked for page $pageNumber (post-setPage)")
                    pageView.setMaskedMode(isMasked)
                }
                
                // Also set masked mode with a longer delay to ensure it takes effect after async loading
                pageView.postDelayed({
                    val isMasked = fragment.isMaskedMode()
                    Log.d(TAG, "🔒 Re-setting masked mode to $isMasked for page $pageNumber (delayed)")
                    pageView.setMaskedMode(isMasked)
                }, 1500) // Increased delay to 1500ms to ensure asset loading completes
                
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
        // Clear the ViewHolder's page tracking so it can be reused properly
        holder.currentPageNumber = -1
        holder.quranImagePage = null
        
        // Remove from container to prevent multiple parent issues
        holder.binding.pageContainer.removeAllViews()
        super.onViewRecycled(holder)
        Log.d(TAG, "♻️ ViewHolder recycled and reset for reuse")
    }

    override fun getItemCount() = totalPages
    
    /**
     * Gets or creates a page view with LRU cache management
     */
    private fun getOrCreatePageView(pageNumber: Int): QuranImagePage {
        // Update access order
        accessOrder.remove(pageNumber)
        accessOrder.add(pageNumber)
        
        // Get existing or create new
        val pageView = pageViewCache[pageNumber] ?: run {
            Log.d(TAG, "🆕 Creating new QuranImagePage for page $pageNumber")
            
            // Check if we need to evict old pages
            if (pageViewCache.size >= MAX_CACHED_PAGES) {
                val oldestPage = accessOrder.removeAt(0)
                pageViewCache.remove(oldestPage)?.let { oldPageView ->
                    Log.d(TAG, "🗑️ Evicting old page $oldestPage from cache")
                    oldPageView.cleanup()
                }
            }
            
            val newPageView = QuranImagePage(fragment.requireContext())
            pageViewCache[pageNumber] = newPageView
            newPageView
        }
        
        // Always ensure the view is properly configured for this page
        Log.d(TAG, "📋 Retrieved/created page view for page $pageNumber")
        
        return pageView
    }
    
    /**
     * Updates masked mode for all currently bound views
     */
    fun updateMaskedMode(masked: Boolean) {
        Log.d(TAG, "🔄 updateMaskedMode called with: $masked")
        Log.d(TAG, "📋 Updating ${pageViewCache.size} cached page views")
        
        // Update all cached page views
        pageViewCache.forEach { (pageNumber, pageView) ->
            Log.d(TAG, "🔒 Setting masked mode $masked for cached page $pageNumber")
            pageView.setMaskedMode(masked)
        }
        
        Log.d(TAG, "✅ Finished updating masked mode for all cached pages")
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
    fun getCacheStats(): ImageCache.CacheStats? {
        return pageViewCache.values.firstOrNull()?.let { pageView ->
            // All page views share the same cache instance, so we can get stats from any one
            pageView.imageCache.getCacheStats()
        }
    }
}
