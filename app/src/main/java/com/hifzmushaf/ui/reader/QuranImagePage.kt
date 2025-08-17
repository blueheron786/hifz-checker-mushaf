package com.hifzmushaf.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import com.hifzmushaf.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * A custom ImageView component for displaying Quran pages with word-by-word interaction.
 * Supports both Qpc14LinesV2 (page images) and BlackImagesWordByWord (individual word images).
 */
class QuranImagePage @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "QuranImagePage"
        
        // Image sources - Updated with working URLs
        private const val QPC_BASE_URL = "https://www.searchtruth.com/quran/images1"
        private const val QPC_ALT_URL = "https://quran.com/images/pages"
        private const val QPC_FULL_PAGE_URL = "https://qpc.ksu.edu.sa/pages/qpc14linesv2"
        private const val BLACK_IMAGES_WORD_BY_WORD_BASE_URL = "https://api.qurancdn.com/api/qdc/words"
        
        // Cache directories
        private const val PAGE_IMAGES_CACHE_DIR = "quran_pages"
        private const val WORD_IMAGES_CACHE_DIR = "quran_words"
        
        // Display modes
        private const val MODE_WORD_BY_WORD = "word_by_word"
        private const val MODE_FULL_PAGE_MASK = "full_page_mask"
        
        // Reduced image dimensions to save memory
        private const val PAGE_IMAGE_WIDTH = 400  // Much smaller placeholders
        private const val PAGE_IMAGE_HEIGHT = 600
        
        // Shared JSON object for all instances to avoid loading multiple times
        @Volatile
        private var sharedWordImageUrls: JSONObject? = null
        
        // Lock for thread-safe JSON loading
        private val jsonLoadLock = Any()
        
        /**
         * Loads the JSON data once for all instances
         */
        private fun ensureJsonLoaded(context: Context) {
            if (sharedWordImageUrls != null) return
            
            synchronized(jsonLoadLock) {
                if (sharedWordImageUrls != null) return // Double-check
                
                try {
                    Log.d(TAG, "🔄 Loading shared word image URLs JSON...")
                    val inputStream = context.resources.openRawResource(R.raw.blackimageswordbyword)
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    sharedWordImageUrls = JSONObject(jsonString)
                    Log.d(TAG, "✅ Successfully loaded shared word image URLs JSON")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to load word image URLs JSON: ${e.message}")
                }
            }
        }
        
        /**
         * Gets the URL for a specific word image - thread-safe
         */
        fun getWordImageUrl(context: Context, surah: Int, ayah: Int, word: Int): String? {
            ensureJsonLoaded(context)
            return try {
                val key = "$surah:$ayah:$word"
                val wordData = sharedWordImageUrls?.getJSONObject(key)
                wordData?.getString("text")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to get URL for word $surah:$ayah:$word: ${e.message}")
                null
            }
        }
    }

    // Properties
    private var pageNumber: Int = 1
    private var pageInfo: PageInfo? = null
    private var isMaskedMode: Boolean = false
    private var displayMode: String = MODE_FULL_PAGE_MASK // Use fast full-page masking by default
    private var revealedWords: MutableSet<String> = mutableSetOf() // Track revealed words
    private var onWordClickListener: ((WordBoundary) -> Unit)? = null
    
    // Full page image data
    private var fullPageBitmap: Bitmap? = null
    private var maskBitmap: Bitmap? = null
    
    // Gesture detection
    private val gestureDetector = GestureDetector(context, PageGestureListener())
    
    // Image caching
    val imageCache = QuranImageCache(context)
    
    // Current bitmaps
    private var originalPageBitmap: Bitmap? = null
    private var maskedPageBitmap: Bitmap? = null
    private var currentRevealedBitmap: Bitmap? = null
    
    // Coroutine job to cancel background operations
    private var downloadJob: kotlinx.coroutines.Job? = null
    
    init {
        // Configure ImageView for proper Quran page display
        scaleType = ScaleType.FIT_CENTER  // Better for fitting both width and height
        adjustViewBounds = true
        
        // Set minimum dimensions to prevent squishing
        minimumWidth = 400
        minimumHeight = 600
        
        Log.d(TAG, "📱 QuranImagePage initialized with center-fit scaling for height optimization")
    }

    /**
     * Sets the page number and loads the corresponding page image
     */
    fun setPage(pageNumber: Int, pageInfo: PageInfo? = null) {
        this.pageNumber = pageNumber
        this.pageInfo = pageInfo
        loadPageImage()
    }

    /**
     * Sets whether the page should be displayed in masked mode (words hidden)
     */
    fun setMaskedMode(masked: Boolean) {
        if (this.isMaskedMode != masked) {
            this.isMaskedMode = masked
            updateDisplayedImage()
        }
    }

    /**
     * Sets the word click listener
     */
    fun setOnWordClickListener(listener: (WordBoundary) -> Unit) {
        this.onWordClickListener = listener
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    /**
     * Public method to set page number and load the page
     */
    fun setPageNumber(pageNum: Int) {
        Log.d(TAG, "🔢 Setting page number to: $pageNum (previous was: $pageNumber)")
        pageNumber = pageNum
        
        // DIAGNOSTIC: Show a unique placeholder immediately for each page
        setImageBitmap(createLoadingPlaceholder("LOADING PAGE $pageNum"))
        
        loadPageImage()
    }

    /**
     * Loads the page image from cache or downloads it
     */
    private fun loadPageImage() {
        Log.d(TAG, "🖼️ Loading page image for page: $pageNumber")
        
        // Check memory pressure before starting
        imageCache.checkMemoryPressure()
        
        // Cancel any existing download
        downloadJob?.cancel()
        
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Immediately show placeholder to indicate loading
                withContext(Dispatchers.Main) {
                    setOriginalBitmap(createPlaceholderBitmap())
                }
                
                // Force regeneration for debugging lower pages to test database
                if (pageNumber <= 10) {
                    Log.d(TAG, "🔄 Forcing regeneration for debugging page $pageNumber - skipping cache")
                } else {
                    // First try to load from cache for higher pages
                    val cachedBitmap = imageCache.getPageImage(pageNumber)
                    if (cachedBitmap != null) {
                        Log.d(TAG, "✅ Found cached image for page $pageNumber")
                        withContext(Dispatchers.Main) {
                            setOriginalBitmap(cachedBitmap)
                        }
                        return@launch
                    }
                }

                Log.d(TAG, "⬇️ No cached image, downloading for page $pageNumber")
                
                // Download from Qpc14LinesV2
                val downloadedBitmap = downloadPageImage(pageNumber)
                if (downloadedBitmap != null) {
                    Log.d(TAG, "✅ Successfully downloaded image for page $pageNumber")
                    // Cache the downloaded image
                    imageCache.cachePageImage(pageNumber, downloadedBitmap)
                    
                    withContext(Dispatchers.Main) {
                        setOriginalBitmap(downloadedBitmap)
                    }
                } else {
                    Log.e(TAG, "❌ Failed to download image for page $pageNumber")
                    withContext(Dispatchers.Main) {
                        // Keep the placeholder with error indication
                        setOriginalBitmap(createPlaceholderBitmap())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading page image for page $pageNumber", e)
                withContext(Dispatchers.Main) {
                    showPlaceholder()
                }
            }
        }
    }

    /**
     * Downloads page image from multiple sources with fallback
     */
    private suspend fun downloadPageImage(pageNumber: Int): Bitmap? {
        Log.d(TAG, "🌐 Starting proper page assembly for page $pageNumber")
        
        try {
            // Show initial loading state
            withContext(Dispatchers.Main) {
                setImageBitmap(createLoadingPlaceholder("Getting word positions..."))
            }
            
            // 1. Get words for this page from database
            val qpcDataManager = QpcDataManager(context)
            val wordsForPage = qpcDataManager.getWordsForPage(pageNumber)
            
            if (wordsForPage.isEmpty()) {
                Log.w(TAG, "⚠️ No words found for page $pageNumber in database")
                return createPageReadyPlaceholder(pageNumber)
            }
            
            Log.d(TAG, "📊 Found ${wordsForPage.size} words for page $pageNumber")
            
            // Update loading status
            withContext(Dispatchers.Main) {
                setImageBitmap(createLoadingPlaceholder("Found ${wordsForPage.size} words, preparing canvas..."))
            }
            
            // 2. Calculate page dimensions based on word positions
            val maxX = wordsForPage.maxOfOrNull { it.x + it.width } ?: 1000f
            val maxY = wordsForPage.maxOfOrNull { it.y + it.height } ?: 1500f
            
            // Count approximate lines by grouping words by similar Y positions
            val lineYPositions = wordsForPage.map { it.y }.distinct().sorted()
            val approximateLines = lineYPositions.size
            
            Log.d(TAG, "📏 Page analysis - Max X: $maxX, Max Y: $maxY, Approximate lines: $approximateLines")
            
            // For pages with full content (more than 10 lines), optimize for height fitting
            val pageWidth: Int
            val pageHeight: Int
            
            if (approximateLines >= 10) {
                // Full pages - scale to fit screen height better
                // Use consistent dimensions for full pages to ensure proper scaling
                pageWidth = 1080  // Standard phone width
                pageHeight = 1800 // Taller to accommodate 15 lines properly
                Log.d(TAG, "📐 Full page detected ($approximateLines lines) - using height-optimized dimensions: ${pageWidth}x${pageHeight}")
            } else {
                // Shorter pages (like pages 1-2) - use content-based dimensions
                val baseWidth = (maxX + 100).toInt()  // Add padding
                val baseHeight = (maxY + 200).toInt()  // Extra padding for shorter pages
                
                pageWidth = if (baseWidth < 800) 800 else baseWidth
                pageHeight = if (baseHeight < 1000) 1000 else baseHeight
                Log.d(TAG, "📐 Short page detected ($approximateLines lines) - using content-based dimensions: ${pageWidth}x${pageHeight}")
            }
            
            // 3. Create a canvas to assemble the page
            val pageBitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(pageBitmap)
            
            // 4. Fill with background color (cream/white)
            canvas.drawColor(Color.parseColor("#FFF8DC"))
            
            // Update loading status
            withContext(Dispatchers.Main) {
                setImageBitmap(createLoadingPlaceholder("Canvas ready, downloading words..."))
            }
            
            // 5. Download and place each word at its correct position
            var successfulWords = 0
            var cachedWords = 0
            
            // Scale factor for height optimization on full pages
            val heightScaleFactor = if (approximateLines >= 10) {
                // For full pages, scale Y positions to better utilize the page height
                val contentHeight = maxY - (wordsForPage.minOfOrNull { it.y } ?: 0f)
                val availableHeight = pageHeight - 200f  // Leave some margin
                if (contentHeight > 0) (availableHeight / contentHeight).coerceAtMost(1.5f) else 1f
            } else {
                1f  // No scaling for shorter pages
            }
            
            val widthScaleFactor = if (approximateLines >= 10) {
                // Slight width scaling to maintain proportions
                val contentWidth = maxX - (wordsForPage.minOfOrNull { it.x } ?: 0f)
                val availableWidth = pageWidth - 100f
                if (contentWidth > 0) (availableWidth / contentWidth).coerceAtMost(1.2f) else 1f
            } else {
                1f
            }
            
            Log.d(TAG, "🎯 Scale factors - Height: $heightScaleFactor, Width: $widthScaleFactor")
            
            for ((index, word) in wordsForPage.withIndex()) {
                try {
                    // Update progress every 10 words or so
                    if (index % 10 == 0) {
                        withContext(Dispatchers.Main) {
                            setImageBitmap(createLoadingPlaceholder("Processing word ${index + 1}/${wordsForPage.size}..."))
                        }
                    }
                    
                    val wordKey = "${word.surah}/${word.ayah}/${word.word}"
                    
                    // Check if word is already cached
                    var wordBitmap = imageCache.getWordImage(wordKey)
                    if (wordBitmap != null) {
                        cachedWords++
                    } else {
                        // Get URL and download word image
                        val wordUrl = getWordImageUrl(context, word.surah, word.ayah, word.word)
                        if (wordUrl != null) {
                            wordBitmap = downloadWordImage(wordUrl, wordKey)
                            if (wordBitmap != null) {
                                // Cache the word for future use
                                imageCache.cacheWordImage(wordKey, wordBitmap)
                            }
                        }
                    }
                    
                    // Place word on page if we have the image
                    if (wordBitmap != null) {
                        // Apply scaling factors for height optimization
                        val scaledX = word.x * widthScaleFactor
                        val scaledY = word.y * heightScaleFactor
                        val scaledWidth = word.width * widthScaleFactor
                        val scaledHeight = word.height * heightScaleFactor
                        
                        val destRect = android.graphics.RectF(
                            scaledX,
                            scaledY, 
                            scaledX + scaledWidth,
                            scaledY + scaledHeight
                        )
                        canvas.drawBitmap(wordBitmap, null, destRect, null)
                        successfulWords++
                    } else {
                        Log.w(TAG, "⚠️ Could not get image for word ${word.surah}:${word.ayah}:${word.word}")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error processing word ${word.surah}:${word.ayah}:${word.word}: ${e.message}")
                }
            }
            
            Log.d(TAG, "✅ Page assembly complete: $successfulWords/${wordsForPage.size} words placed ($cachedWords from cache)")
            
            return if (successfulWords > 0) {
                pageBitmap
            } else {
                // If no words were placed, return a placeholder
                pageBitmap.recycle()
                createPageReadyPlaceholder(pageNumber)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error assembling page $pageNumber: ${e.message}", e)
            return createPlaceholderBitmap()
        }
    }
    
    /**
     * Downloads a single word image efficiently
     */
    private suspend fun downloadWordImage(url: String, wordKey: String): Bitmap? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 5000   // Shorter timeout for individual words
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "QuranImageReader/2.0")
            connection.setRequestProperty("Accept", "image/*")
            
            Log.d(TAG, "🔗 Downloading word $wordKey from: $url")
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                
                // Use efficient decoding for small word images
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888  // Keep quality for text
                    inPurgeable = true
                    inInputShareable = true
                }
                
                val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close()
                connection.disconnect()
                
                if (bitmap != null) {
                    Log.d(TAG, "✅ Downloaded word $wordKey (${bitmap.width}x${bitmap.height})")
                    return bitmap
                } else {
                    Log.e(TAG, "❌ Failed to decode word image: $wordKey")
                }
            } else {
                Log.w(TAG, "⚠️ HTTP ${connection.responseCode} for word: $wordKey")
            }
            connection.disconnect()
            null
        } catch (e: Exception) {
            Log.e(TAG, "💥 Exception downloading word $wordKey: ${e.message}")
            null
        }
    }

    /**
     * Sets the original bitmap and creates masked version if needed
     */
    private fun setOriginalBitmap(bitmap: Bitmap) {
        // Recycle old bitmaps to free memory
        if (originalPageBitmap != null && originalPageBitmap != bitmap) {
            originalPageBitmap?.recycle()
        }
        if (maskedPageBitmap != null) {
            maskedPageBitmap?.recycle()
            maskedPageBitmap = null
        }
        if (currentRevealedBitmap != null) {
            currentRevealedBitmap?.recycle()
            currentRevealedBitmap = null
        }
        
        originalPageBitmap = bitmap
        
        // Create masked version if we have word boundaries
        if (pageInfo != null && pageInfo!!.words.isNotEmpty()) {
            maskedPageBitmap = createMaskedBitmap(bitmap, pageInfo!!.words)
        }
        
        updateDisplayedImage()
    }

    /**
     * Creates a masked version of the bitmap with words hidden
     */
    private fun createMaskedBitmap(originalBitmap: Bitmap, words: List<WordBoundary>): Bitmap {
        val maskedBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(maskedBitmap)
        
        // Paint to mask words (white overlay)
        val maskPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            alpha = 255
        }
        
        // Mask each word
        words.forEach { word ->
            val wordRect = RectF(
                word.x - 2f,
                word.y - 2f,
                word.x + word.width + 2f,
                word.y + word.height + 2f
            )
            canvas.drawRect(wordRect, maskPaint)
        }
        
        return maskedBitmap
    }

    /**
     * Updates the displayed image based on current mode
     */
    private fun updateDisplayedImage() {
        val bitmapToShow = when {
            currentRevealedBitmap != null -> currentRevealedBitmap
            isMaskedMode && maskedPageBitmap != null -> maskedPageBitmap
            else -> originalPageBitmap
        }
        
        setImageBitmap(bitmapToShow)
    }

    /**
     * Shows a placeholder when image cannot be loaded
     */
    private fun showPlaceholder() {
        val placeholderBitmap = createPlaceholderBitmap()
        setImageBitmap(placeholderBitmap)
    }

    /**
     * Creates a placeholder bitmap with page information
     */
    private fun createPlaceholderBitmap(): Bitmap {
        // Create larger placeholder with proper aspect ratio for Quran pages
        val bitmap = Bitmap.createBitmap(
            800,  // Wider for better readability
            1200, // Taller to match typical Quran page proportions (2:3 ratio)
            Bitmap.Config.RGB_565
        )
        val canvas = Canvas(bitmap)
        
        // Background - light cream color
        canvas.drawColor(Color.parseColor("#FFF8DC"))
        
        // Border
        val borderPaint = Paint().apply {
            color = Color.parseColor("#DDDDDD")
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRect(20f, 20f, bitmap.width - 20f, bitmap.height - 20f, borderPaint)
        
        // Text paint
        val textPaint = Paint().apply {
            color = Color.parseColor("#333333")
            textSize = 48f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        
        // Draw page number in Arabic
        canvas.drawText(
            "صفحة $pageNumber",
            bitmap.width / 2f,
            bitmap.height / 2f - 150f,
            textPaint
        )
        
        // Draw page number in English
        textPaint.textSize = 36f
        textPaint.typeface = android.graphics.Typeface.DEFAULT
        canvas.drawText(
            "Page $pageNumber",
            bitmap.width / 2f,
            bitmap.height / 2f - 80f,
            textPaint
        )
        
        // Draw loading message - more prominent
        textPaint.textSize = 28f
        textPaint.color = Color.parseColor("#0066CC")
        canvas.drawText(
            "📥 Downloading page...",
            bitmap.width / 2f,
            bitmap.height / 2f + 20f,
            textPaint
        )
        
        // Draw status line
        textPaint.textSize = 20f
        textPaint.color = Color.parseColor("#666666")
        canvas.drawText(
            "Please wait while words are assembled",
            bitmap.width / 2f,
            bitmap.height / 2f + 80f,
            textPaint
        )
        
        return bitmap
    }
    
    /**
     * Creates a loading placeholder with custom message
     */
    private fun createLoadingPlaceholder(message: String): Bitmap {
        val bitmap = Bitmap.createBitmap(800, 1200, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        
        // Background - light cream color
        canvas.drawColor(Color.parseColor("#FFF8DC"))
        
        // Border
        val borderPaint = Paint().apply {
            color = Color.parseColor("#0066CC")
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        canvas.drawRect(20f, 20f, bitmap.width - 20f, bitmap.height - 20f, borderPaint)
        
        // Title paint
        val titlePaint = Paint().apply {
            color = Color.parseColor("#333333")
            textSize = 56f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        
        // Page number
        canvas.drawText(
            "صفحة $pageNumber",
            bitmap.width / 2f,
            bitmap.height / 2f - 200f,
            titlePaint
        )
        
        // Loading animation symbol
        val symbolPaint = Paint().apply {
            color = Color.parseColor("#0066CC")
            textSize = 72f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(
            "⏳",
            bitmap.width / 2f,
            bitmap.height / 2f - 100f,
            symbolPaint
        )
        
        // Progress message
        val messagePaint = Paint().apply {
            color = Color.parseColor("#0066CC")
            textSize = 32f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText(
            message,
            bitmap.width / 2f,
            bitmap.height / 2f + 20f,
            messagePaint
        )
        
        // Additional info
        val infoPaint = Paint().apply {
            color = Color.parseColor("#666666")
            textSize = 24f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(
            "Please wait...",
            bitmap.width / 2f,
            bitmap.height / 2f + 80f,
            infoPaint
        )
        
        return bitmap
    }
    
    /**
     * Creates a small placeholder indicating page is ready with cached words
     */
    private fun createPageReadyPlaceholder(pageNumber: Int): Bitmap {
        // Create very small placeholder for memory efficiency
        val bitmap = Bitmap.createBitmap(200, 300, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        
        // Background - light green indicating success
        canvas.drawColor(Color.parseColor("#F0FFF0"))
        
        // Border
        val borderPaint = Paint().apply {
            color = Color.parseColor("#90EE90")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(5f, 5f, bitmap.width - 5f, bitmap.height - 5f, borderPaint)
        
        // Text paint
        val textPaint = Paint().apply {
            color = Color.parseColor("#006400")
            textSize = 16f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        
        // Draw page number
        canvas.drawText(
            "Page $pageNumber",
            bitmap.width / 2f,
            bitmap.height / 2f - 10f,
            textPaint
        )
        
        // Draw ready status
        textPaint.textSize = 12f
        textPaint.color = Color.parseColor("#228B22")
        canvas.drawText(
            "Words Cached ✓",
            bitmap.width / 2f,
            bitmap.height / 2f + 15f,
            textPaint
        )
        
        return bitmap
    }

    /**
     * Reveals a specific word by downloading and overlaying its image
     */
    fun revealWord(word: WordBoundary) {
        if (!isMaskedMode || maskedPageBitmap == null) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get word image from cache or download
                val wordImageKey = "${word.surah}/${word.ayah}/${word.word}"
                var wordBitmap = imageCache.getWordImage(wordImageKey)
                
                if (wordBitmap == null) {
                    // Get URL from JSON and download using the new method
                    val wordUrl = getWordImageUrl(context, word.surah, word.ayah, word.word)
                    if (wordUrl != null) {
                        wordBitmap = downloadWordImage(wordUrl, wordImageKey)
                        if (wordBitmap != null) {
                            imageCache.cacheWordImage(wordImageKey, wordBitmap)
                        }
                    }
                }
                
                if (wordBitmap != null) {
                    withContext(Dispatchers.Main) {
                        showRevealedWord(word, wordBitmap)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error revealing word: $word", e)
            }
        }
    }

    /**
     * Shows the revealed word by overlaying its image
     */
    private fun showRevealedWord(word: WordBoundary, wordBitmap: Bitmap) {
        val baseBitmap = maskedPageBitmap ?: return
        
        // Create a copy to draw the revealed word on
        currentRevealedBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(currentRevealedBitmap!!)
        
        // Scale and position the word image
        val destRect = RectF(word.x, word.y, word.x + word.width, word.y + word.height)
        canvas.drawBitmap(wordBitmap, null, destRect, null)
        
        // Add highlight border
        val highlightPaint = Paint().apply {
            color = Color.YELLOW
            style = Paint.Style.STROKE
            strokeWidth = 4f
            alpha = 120
        }
        canvas.drawRect(destRect, highlightPaint)
        
        // Update display
        updateDisplayedImage()
        
        // Auto-hide after 5 seconds
        postDelayed({
            currentRevealedBitmap = null
            updateDisplayedImage()
        }, 5000)
        
        Log.d(TAG, "Revealed word: ${word.text} for 5 seconds")
    }

    /**
     * Gesture listener for handling touch events
     */
    private inner class PageGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            handleWordTap(e.x, e.y)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            // Could be used for additional interactions
            Log.d(TAG, "Long press detected at ${e.x}, ${e.y}")
        }
    }

    /**
     * Handles word tap by finding the tapped word and revealing it
     */
    private fun handleWordTap(x: Float, y: Float) {
        val words = pageInfo?.words ?: return
        if (words.isEmpty()) return
        
        // Convert screen coordinates to image coordinates
        val imageCoords = convertScreenToImageCoordinates(x, y) ?: return
        
        // Find tapped word
        val tappedWord = words.find { word ->
            imageCoords[0] >= word.x && imageCoords[0] <= word.x + word.width &&
            imageCoords[1] >= word.y && imageCoords[1] <= word.y + word.height
        }
        
        if (tappedWord != null) {
            if (isMaskedMode) {
                revealWord(tappedWord)
            }
            onWordClickListener?.invoke(tappedWord)
            Log.d(TAG, "Word tapped: ${tappedWord.text} at surah ${tappedWord.surah}, ayah ${tappedWord.ayah}")
        }
    }

    /**
     * Converts screen coordinates to image coordinates
     */
    private fun convertScreenToImageCoordinates(screenX: Float, screenY: Float): FloatArray? {
        val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return null
        
        val imageMatrix = imageMatrix
        val invertedMatrix = Matrix()
        
        return if (imageMatrix.invert(invertedMatrix)) {
            val imageCoords = floatArrayOf(screenX, screenY)
            invertedMatrix.mapPoints(imageCoords)
            imageCoords
        } else {
            null
        }
    }

    /**
     * Cleans up resources
     */
    fun cleanup() {
        // Cancel any ongoing downloads
        downloadJob?.cancel()
        
        // Recycle bitmaps to free memory
        currentRevealedBitmap?.recycle()
        currentRevealedBitmap = null
        
        maskedPageBitmap?.recycle()
        maskedPageBitmap = null
        
        // Don't recycle original bitmap if it might be cached
        if (originalPageBitmap != null) {
            val pageImageCached = imageCache.getPageImage(pageNumber) != null
            if (!pageImageCached) {
                originalPageBitmap?.recycle()
            }
            originalPageBitmap = null
        }
        
        // Remove any pending callbacks
        removeCallbacks(null)
        
        Log.d(TAG, "🧹 Cleaned up resources for page $pageNumber")
    }
    
    /**
     * Called when the view is detached from window
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cleanup()
    }
}
