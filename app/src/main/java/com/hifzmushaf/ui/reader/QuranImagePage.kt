package com.hifzmushaf.ui.reader

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView
import com.hifzmushaf.ui.reader.QpcDataManager
import com.hifzmushaf.ui.reader.PageInfo
import com.hifzmushaf.ui.reader.WordBoundary
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Custom ImageView for displaying Quran pages with word-level interaction capabilities.
 * Supports fast loading from assets and word-based masking for Hifz practice.
 */
class QuranImagePage @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "QuranImagePage"
        private var urlMappingJson: JSONObject? = null

        /**
         * Load and parse the URL mapping JSON file once
         */
        private fun ensureJsonLoaded(context: Context) {
            if (urlMappingJson == null) {
                try {
                    val inputStream = context.assets.open("url_mapping.json")
                    val json = inputStream.bufferedReader().use(BufferedReader::readText)
                    urlMappingJson = JSONObject(json)
                    Log.d(TAG, "✅ URL mapping JSON loaded successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to load URL mapping JSON", e)
                }
            }
        }

        /**
         * Get the image URL for a specific word
         */
        fun getWordImageUrl(context: Context, surah: Int, ayah: Int, word: Int): String? {
            ensureJsonLoaded(context)
            
            return try {
                val surahStr = surah.toString().padStart(3, '0')
                val ayahStr = ayah.toString().padStart(3, '0')
                val wordStr = word.toString().padStart(3, '0')
                
                val key = "${surahStr}_${ayahStr}_${wordStr}"
                
                val wordData = urlMappingJson?.getJSONObject(key)
                val fileName = wordData?.getString("file_name")
                
                if (fileName != null) {
                    "https://api.qurancdn.com/api/qdc/images/words/$fileName"
                } else {
                    Log.w(TAG, "⚠️ No URL found for word $surah:$ayah:$word")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error getting URL for word $surah:$ayah:$word", e)
                null
            }
        }
    }

    // Private properties
    private var currentPageNumber: Int = 1
    private var isMaskedMode: Boolean = false
    private var originalBitmap: Bitmap? = null
    private var maskedBitmap: Bitmap? = null
    private var wordBoundaries: List<WordBoundary> = emptyList()
    private var revealedWords: MutableSet<String> = mutableSetOf()
    private var onWordClickListener: ((WordBoundary) -> Unit)? = null
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            handleTouch(e.x, e.y)
            return true
        }
        
        override fun onLongPress(e: MotionEvent) {
            handleTouch(e.x, e.y)
        }
    })

    // Coroutine scope for image operations
    private val imageScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Image cache for word images (exposed for adapter access)
    val imageCache = ImageCache()

    /**
     * Set the page to display
     */
    fun setPage(pageNumber: Int, pageInfo: PageInfo? = null) {
        currentPageNumber = pageNumber
        revealedWords.clear()
        loadPageImage()
    }

    /**
     * Enable or disable masked mode
     */
    fun setMaskedMode(masked: Boolean) {
        if (isMaskedMode != masked) {
            isMaskedMode = masked
            updateDisplayedImage()
        }
    }

    /**
     * Set listener for word click events
     */
    fun setOnWordClickListener(listener: (WordBoundary) -> Unit) {
        onWordClickListener = listener
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    /**
     * Set the page number and trigger loading
     */
    fun setPageNumber(pageNum: Int) {
        if (currentPageNumber != pageNum) {
            currentPageNumber = pageNum
            revealedWords.clear()
            loadPageImage()
        }
    }

    /**
     * Clean up resources when view is recycled
     */
    fun cleanup() {
        imageScope.cancel()
        originalBitmap?.recycle()
        originalBitmap = null
        maskedBitmap?.recycle()
        maskedBitmap = null
        wordBoundaries = emptyList()
        revealedWords.clear()
        setImageBitmap(null)
    }

    /**
     * Load and display the page image
     */
    private fun loadPageImage() {
        imageScope.launch {
            try {
                showPlaceholder()
                
                // Load page using fast asset-based approach
                val downloadedBitmap = downloadPageImage(currentPageNumber)
                
                if (downloadedBitmap != null) {
                    withContext(Dispatchers.Main) {
                        setOriginalBitmap(downloadedBitmap)
                    }
                } else {
                    Log.e(TAG, "Failed to load page $currentPageNumber")
                    withContext(Dispatchers.Main) {
                        showPlaceholder()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading page image for page $currentPageNumber", e)
                withContext(Dispatchers.Main) {
                    showPlaceholder()
                }
            }
        }
    }

    /**
     * Fast page loading using full page images from assets
     */
    private suspend fun downloadPageImage(pageNumber: Int): Bitmap? {
        Log.d(TAG, "🚀 Loading full page image $pageNumber from assets")
        
        try {
            // Show initial loading state
            withContext(Dispatchers.Main) {
                setImageBitmap(createLoadingPlaceholder("Loading page $pageNumber..."))
            }
            
            // Load full page image from assets
            val assetPath = "quran_pages/${pageNumber}.png"
            val inputStream = context.assets.open(assetPath)
            val fullPageBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (fullPageBitmap == null) {
                Log.e(TAG, "❌ Failed to load page image from assets: $assetPath")
                return createPageReadyPlaceholder(pageNumber)
            }
            
            Log.d(TAG, "✅ Successfully loaded page $pageNumber (${fullPageBitmap.width}x${fullPageBitmap.height})")
            
            // Get word boundaries for touch handling (but don't use for rendering)
            val qpcDataManager = QpcDataManager(context)
            val wordsForPage = qpcDataManager.getWordsForPage(pageNumber)
            Log.d(TAG, "📊 Found ${wordsForPage.size} word boundaries for touch handling")
            
            // Store word boundaries for later use in touch events
            wordBoundaries = wordsForPage
            
            return fullPageBitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading page $pageNumber from assets: ${e.message}")
            return createPageReadyPlaceholder(pageNumber)
        }
    }

    /**
     * Download an individual word image from the API
     */
    private suspend fun downloadWordImage(url: String, wordKey: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.doInput = true
                connection.connect()

                if (connection.responseCode == 200) {
                    val inputStream = connection.inputStream
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    connection.disconnect()
                    
                    if (bitmap != null) {
                        Log.d(TAG, "✅ Downloaded word image: $wordKey")
                    } else {
                        Log.w(TAG, "⚠️ Failed to decode bitmap for: $wordKey")
                    }
                    
                    bitmap
                } else {
                    Log.w(TAG, "⚠️ HTTP ${connection.responseCode} for: $wordKey")
                    connection.disconnect()
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error downloading word $wordKey: ${e.message}")
                null
            }
        }
    }

    /**
     * Set the original bitmap and update display
     */
    private fun setOriginalBitmap(bitmap: Bitmap) {
        originalBitmap?.recycle()
        originalBitmap = bitmap
        
        // Create masked version if needed
        if (isMaskedMode && wordBoundaries.isNotEmpty()) {
            maskedBitmap?.recycle()
            maskedBitmap = createMaskedBitmap(bitmap, wordBoundaries)
        }
        
        updateDisplayedImage()
        
        Log.d(TAG, "✅ Page $currentPageNumber loaded successfully (${bitmap.width}x${bitmap.height})")
    }

    /**
     * Create a masked version of the bitmap
     */
    private fun createMaskedBitmap(originalBitmap: Bitmap, words: List<WordBoundary>): Bitmap {
        val maskedBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(maskedBitmap)
        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        
        // Calculate scale factors
        val scaleX = originalBitmap.width.toFloat() / 1000f  // Assuming coordinate system is 1000-based
        val scaleY = originalBitmap.height.toFloat() / 1000f
        
        for (word in words) {
            val wordKey = "${word.surah}_${word.ayah}_${word.word}"
            if (!revealedWords.contains(wordKey)) {
                val rect = RectF(
                    word.x * scaleX,
                    word.y * scaleY,
                    (word.x + word.width) * scaleX,
                    (word.y + word.height) * scaleY
                )
                canvas.drawRect(rect, paint)
            }
        }
        
        return maskedBitmap
    }

    /**
     * Update the displayed image based on current mode
     */
    private fun updateDisplayedImage() {
        val bitmapToShow = if (isMaskedMode && maskedBitmap != null) {
            maskedBitmap
        } else {
            originalBitmap
        }
        
        if (bitmapToShow != null) {
            setImageBitmap(bitmapToShow)
        }
    }

    /**
     * Show a placeholder while loading
     */
    private fun showPlaceholder() {
        setImageBitmap(createPlaceholderBitmap())
    }

    /**
     * Create a placeholder bitmap
     */
    private fun createPlaceholderBitmap(): Bitmap {
        val width = 800
        val height = 1200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Background
        canvas.drawColor(Color.parseColor("#F5F5DC"))
        
        // Paint for text
        val paint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        
        // Title
        paint.apply {
            color = Color.parseColor("#8B4513")
            textSize = 48f
            typeface = Typeface.DEFAULT_BOLD
        }
        
        canvas.drawText(
            "Hifz Checker Mushaf",
            width / 2f,
            height / 2f - 100,
            paint
        )
        
        // Subtitle
        paint.apply {
            color = Color.parseColor("#654321")
            textSize = 24f
            typeface = Typeface.DEFAULT
        }
        
        canvas.drawText(
            "Loading page $currentPageNumber...",
            width / 2f,
            height / 2f - 50,
            paint
        )
        
        // Decorative border
        val borderPaint = Paint().apply {
            color = Color.parseColor("#D2691E")
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        
        canvas.drawRect(
            20f, 20f,
            width - 20f, height - 20f,
            borderPaint
        )
        
        return bitmap
    }

    /**
     * Create a loading placeholder with custom message
     */
    private fun createLoadingPlaceholder(message: String): Bitmap {
        val width = 800
        val height = 1200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Background
        canvas.drawColor(Color.parseColor("#F8F8FF"))
        
        // Paint for text
        val paint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        
        // Title
        paint.apply {
            color = Color.parseColor("#4B0082")
            textSize = 36f
            typeface = Typeface.DEFAULT_BOLD
        }
        
        canvas.drawText(
            "Quran Page $currentPageNumber",
            width / 2f,
            height / 2f - 60,
            paint
        )
        
        // Loading message
        paint.apply {
            color = Color.parseColor("#696969")
            textSize = 20f
            typeface = Typeface.DEFAULT
        }
        
        canvas.drawText(
            message,
            width / 2f,
            height / 2f,
            paint
        )
        
        // Progress indicator (simple animation effect)
        val progressPaint = Paint().apply {
            color = Color.parseColor("#32CD32")
            style = Paint.Style.FILL
        }
        
        val progressWidth = 200f
        val progressHeight = 8f
        val progressX = (width - progressWidth) / 2f
        val progressY = height / 2f + 40f
        
        // Background bar
        val bgPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            style = Paint.Style.FILL
        }
        canvas.drawRect(progressX, progressY, progressX + progressWidth, progressY + progressHeight, bgPaint)
        
        // Animated progress (this would be better with actual animation)
        val progress = (System.currentTimeMillis() % 2000) / 2000f
        canvas.drawRect(progressX, progressY, progressX + (progressWidth * progress), progressY + progressHeight, progressPaint)
        
        return bitmap
    }

    /**
     * Create a page ready placeholder
     */
    private fun createPageReadyPlaceholder(pageNumber: Int): Bitmap {
        val width = 800
        val height = 1200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Background
        canvas.drawColor(Color.parseColor("#FFF8DC"))
        
        // Paint for text
        val paint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        
        // Title
        paint.apply {
            color = Color.parseColor("#8B4513")
            textSize = 42f
            typeface = Typeface.DEFAULT_BOLD
        }
        
        canvas.drawText(
            "Page $pageNumber",
            width / 2f,
            height / 2f - 80,
            paint
        )
        
        // Subtitle
        paint.apply {
            color = Color.parseColor("#A0522D")
            textSize = 18f
            typeface = Typeface.DEFAULT
        }
        
        canvas.drawText(
            "Ready for reading",
            width / 2f,
            height / 2f - 40,
            paint
        )
        
        return bitmap
    }

    /**
     * Reveal a specific word by removing its mask
     */
    fun revealWord(word: WordBoundary) {
        val wordKey = "${word.surah}_${word.ayah}_${word.word}"
        if (!revealedWords.contains(wordKey)) {
            revealedWords.add(wordKey)
            
            if (isMaskedMode) {
                // Download and show the specific word
                imageScope.launch {
                    val wordUrl = getWordImageUrl(context, word.surah, word.ayah, word.word)
                    if (wordUrl != null) {
                        val wordBitmap = downloadWordImage(wordUrl, wordKey)
                        if (wordBitmap != null) {
                            imageCache.cacheWordImage(wordKey, wordBitmap)
                            withContext(Dispatchers.Main) {
                                showRevealedWord(word, wordBitmap)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Show a revealed word on the masked image
     */
    private fun showRevealedWord(word: WordBoundary, wordBitmap: Bitmap) {
        maskedBitmap?.let { masked ->
            val canvas = Canvas(masked)
            
            // Calculate scale factors
            val scaleX = masked.width.toFloat() / 1000f
            val scaleY = masked.height.toFloat() / 1000f
            
            val destRect = RectF(
                word.x * scaleX,
                word.y * scaleY,
                (word.x + word.width) * scaleX,
                (word.y + word.height) * scaleY
            )
            
            canvas.drawBitmap(wordBitmap, null, destRect, null)
            updateDisplayedImage()
        }
    }

    /**
     * Handle touch events to detect word clicks
     */
    private fun handleTouch(x: Float, y: Float) {
        if (wordBoundaries.isEmpty() || drawable == null) return
        
        // Calculate scale factors based on current view size vs original bitmap size
        val bitmap = originalBitmap ?: return
        val scaleX = bitmap.width.toFloat() / width.toFloat()
        val scaleY = bitmap.height.toFloat() / height.toFloat()
        
        // Adjust coordinates to bitmap space
        val bitmapX = x * scaleX
        val bitmapY = y * scaleY
        
        // Convert to word coordinate space (assuming 1000-based coordinates)
        val wordX = (bitmapX / bitmap.width) * 1000f
        val wordY = (bitmapY / bitmap.height) * 1000f
        
        // Find clicked word
        for (word in wordBoundaries) {
            if (wordX >= word.x && wordX <= word.x + word.width &&
                wordY >= word.y && wordY <= word.y + word.height) {
                
                Log.d(TAG, "Word clicked: ${word.surah}:${word.ayah}:${word.word}")
                onWordClickListener?.invoke(word)
                
                if (isMaskedMode) {
                    revealWord(word)
                }
                break
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        imageScope.cancel()
        originalBitmap?.recycle()
        maskedBitmap?.recycle()
        imageCache.clear()
    }
}

/**
 * Simple image cache for word images with cache statistics
 */
class ImageCache {
    private val cache = mutableMapOf<String, Bitmap>()
    private val maxSize = 50 // Maximum number of cached images

    fun cacheWordImage(key: String, bitmap: Bitmap) {
        if (cache.size >= maxSize) {
            // Remove oldest entry
            val oldestKey = cache.keys.first()
            cache[oldestKey]?.recycle()
            cache.remove(oldestKey)
        }
        cache[key] = bitmap
    }

    fun getWordImage(key: String): Bitmap? = cache[key]

    fun clear() {
        cache.values.forEach { it.recycle() }
        cache.clear()
    }

    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            size = cache.size,
            maxSize = maxSize,
            hitRate = 0.0f // Would need tracking to calculate actual hit rate
        )
    }

    data class CacheStats(
        val size: Int,
        val maxSize: Int,
        val hitRate: Float
    )
}
