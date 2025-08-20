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

    init {
        setHasStableIds(true)
    }

    inner class QuranImagePageViewHolder(val binding: ItemQuranImagePageV2Binding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        var quranImagePage: QuranImagePage? = null
        var currentPageNumber: Int = -1 // Track which page this holder is currently showing
        
        fun bind(pageNumber: Int) {
            Log.d(TAG, "🔗 Binding page $pageNumber to ViewHolder@${hashCode()}")
            
            // CRITICAL: Always clear container first
            binding.pageContainer.removeAllViews()
            
            // CRITICAL: Clean up previous page view if it was a different page
            if (currentPageNumber != -1 && currentPageNumber != pageNumber) {
                quranImagePage?.let { pageView ->
                    Log.d(TAG, "🧹 Cleaning up previous page $currentPageNumber before binding $pageNumber")
                    // Remove from container if still attached
                    (pageView.parent as? ViewGroup)?.removeView(pageView)
                }
            }
            
            // CRITICAL: Always get fresh page view and ensure it's properly configured
            val pageView = getOrCreatePageView(pageNumber)
            
            // CRITICAL: Remove from any previous parent to prevent attachment issues
            (pageView.parent as? ViewGroup)?.removeView(pageView)
            
            // CRITICAL: Always set the page content to ensure proper initialization
            // This is especially important for forward/backward navigation combinations
            val pageInfo = pageInfoMap[pageNumber]
            Log.d(TAG, "📄 Setting page $pageNumber (forcing refresh to prevent blank screens)")
            pageView.setPage(pageNumber, pageInfo)
            
            // Store references
            currentPageNumber = pageNumber
            quranImagePage = pageView
            
            // Add to container with proper layout
            val layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            pageView.layoutParams = layoutParams
            binding.pageContainer.addView(pageView)
            
            // Apply masked mode after page is set
            pageView.post {
                val isMasked = fragment.isMaskedMode()
                Log.d(TAG, "🔒 Setting masked mode to $isMasked for page $pageNumber")
                pageView.setMaskedMode(isMasked)
            }
            
            // Set word click listener
            pageView.setOnWordClickListener { word ->
                fragment.onWordClicked(word)
            }

            // Safety: if drawable is still null shortly after binding, force a display update
            pageView.postDelayed({
                if (pageView.drawable == null) {
                    Log.w(TAG, "🩺 Drawable null after bind; forcing updateDisplayedImage for page $pageNumber")
                    pageView.updateDisplayedImage()
                    // If still null, re-apply setPage as a last resort
                    pageView.postDelayed({
                        if (pageView.drawable == null) {
                            Log.w(TAG, "🛠️ Drawable still null; reapplying setPage for page $pageNumber")
                            val pi = pageInfoMap[pageNumber]
                            pageView.setPage(pageNumber, pi)
                        }
                    }, 120)
                }
            }, 120)

            // Extra: refresh on attach to window to recover after recycling
            pageView.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: android.view.View) {
                    if ((v as? QuranImagePage)?.drawable == null) {
                        Log.d(TAG, "🩺 onViewAttachedToWindow -> drawable null; refreshing page $pageNumber")
                        (v as QuranImagePage).updateDisplayedImage()
                    }
                    v.removeOnAttachStateChangeListener(this)
                }
                override fun onViewDetachedFromWindow(v: android.view.View) { /* no-op */ }
            })
            
            Log.d(TAG, "✅ Page $pageNumber binding complete")
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
        Log.d(TAG, "♻️ Recycling ViewHolder@${holder.hashCode()} (was showing page ${holder.currentPageNumber})")
        
        // Clean up the page view properly to prevent state issues
        holder.quranImagePage?.let { pageView ->
            // Cancel any ongoing loading operations
            try {
                // Don't call cleanup() as it recycles bitmaps that might be used by cache
                Log.d(TAG, "🧹 Preparing page view for recycling (keeping cache intact)")
            } catch (e: Exception) {
                Log.w(TAG, "Warning during page view cleanup: ${e.message}")
            }
        }
        
        // Clear the container to prevent view attachment issues
        holder.binding.pageContainer.removeAllViews()
        
        // Clear holder references but keep page views in cache for performance
        holder.currentPageNumber = -1
        holder.quranImagePage = null
        
        super.onViewRecycled(holder)
        Log.d(TAG, "✅ ViewHolder recycled (page views kept in cache)")
    }

    override fun getItemCount() = totalPages

    override fun getItemId(position: Int): Long {
        // Use page number as stable ID
        return (position + 1).toLong()
    }
    
    /**
     * Gets or creates a page view - no eviction until user exits reader
     */
    private fun getOrCreatePageView(pageNumber: Int): QuranImagePage {
        // Get existing or create new
        val pageView = pageViewCache[pageNumber] ?: run {
            Log.d(TAG, "🆕 Creating new QuranImagePage for page $pageNumber (cache size: ${pageViewCache.size})")
            
            val newPageView = QuranImagePage(fragment.requireContext()).apply {
                // Avoid any scale mismatch causing apparent blanks
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }
            pageViewCache[pageNumber] = newPageView
            newPageView
        }
        
        // Always ensure the view is properly configured for this page
        Log.d(TAG, "📋 Retrieved/created page view for page $pageNumber (cache size: ${pageViewCache.size})")
        
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
     * Clear the entire page cache - useful for debugging navigation issues
     */
    fun clearCache() {
        Log.d(TAG, "🗑️ Clearing all cached page views")
        
        // Clean up all cached views
        pageViewCache.forEach { (pageNumber, pageView) ->
            try {
                pageView.cleanup()
            } catch (e: Exception) {
                Log.w(TAG, "Warning during cache cleanup for page $pageNumber: ${e.message}")
            }
        }
        
        pageViewCache.clear()
        Log.d(TAG, "✅ Page cache cleared")
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
