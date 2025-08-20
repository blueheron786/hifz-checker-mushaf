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
import kotlin.math.sqrt
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
    private var revealedAyahs: MutableSet<String> = mutableSetOf() // Track revealed ayahs
    private var onWordClickListener: ((WordBoundary) -> Unit)? = null
    private var debugTouchPoints: MutableList<Pair<Float, Float>> = mutableListOf() // Store touch points for debugging
    private val isDebugMode = false // Feature toggle for debug touch circles
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
        revealedAyahs.clear() // Clear revealed ayahs when changing pages
        loadPageImage()
    }

    /**
     * Enable or disable masked mode
     */
    fun setMaskedMode(masked: Boolean) {
        Log.d(TAG, "🔒 setMaskedMode called: $masked (current: $isMaskedMode) for page $currentPageNumber")
        
        if (isMaskedMode != masked) {
            isMaskedMode = masked
            Log.d(TAG, "🔄 Masking mode changed from ${!masked} to $masked for page $currentPageNumber")
            
            // If enabling masked mode, create masked bitmap
            if (masked && originalBitmap != null && !originalBitmap!!.isRecycled && wordBoundaries.isNotEmpty()) {
                Log.d(TAG, "🎭 Creating masked bitmap for page $currentPageNumber with ${wordBoundaries.size} words")
                
                // Safely recycle old masked bitmap first
                maskedBitmap?.let { oldMasked ->
                    if (!oldMasked.isRecycled) {
                        oldMasked.recycle()
                    }
                }
                maskedBitmap = createMaskedBitmap(originalBitmap!!, wordBoundaries)
                Log.d(TAG, "✅ Masked bitmap created for page $currentPageNumber")
            } else if (masked) {
                Log.w(TAG, "⚠️ Cannot create masked bitmap: originalBitmap=${originalBitmap != null}, isRecycled=${originalBitmap?.isRecycled}, wordBoundaries=${wordBoundaries.size}")
            }
            
            updateDisplayedImage()
        } else {
            Log.d(TAG, "🔄 Masking mode unchanged ($masked) for page $currentPageNumber")
        }
    }

    /**
     * Set listener for word click events
     */
    fun setOnWordClickListener(listener: (WordBoundary) -> Unit) {
        onWordClickListener = listener
    }

    /**
     * Temporarily reveal a single word for 5 seconds with fade animation
     */
    fun revealWordTemporarily(word: WordBoundary) {
        val wordKey = "${word.surah}_${word.ayah}_${word.word}"
        
        // Immediately reveal the word (fade out mask)
        revealedWords.add(wordKey)
        
        // Recreate masked bitmap if in masked mode
        if (isMaskedMode && originalBitmap != null && !originalBitmap!!.isRecycled) {
            maskedBitmap?.let { oldMasked ->
                if (!oldMasked.isRecycled) {
                    oldMasked.recycle()
                }
            }
            maskedBitmap = createMaskedBitmap(originalBitmap!!, wordBoundaries)
        }
        
        // Quick fade out animation (100ms)
        animate()
            .alpha(0.8f)
            .setDuration(100)
            .withEndAction {
                updateDisplayedImage()
                // Quick fade back in
                animate()
                    .alpha(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()
        
        // Hide the word again after 5 seconds
        imageScope.launch {
            delay(5000)
            revealedWords.remove(wordKey)
            withContext(Dispatchers.Main) {
                // Only update if the view is still attached and bitmaps are not recycled
                if (isAttachedToWindow && originalBitmap != null && !originalBitmap!!.isRecycled) {
                    if (isMaskedMode) {
                        maskedBitmap?.let { oldMasked ->
                            if (!oldMasked.isRecycled) {
                                oldMasked.recycle()
                            }
                        }
                        maskedBitmap = createMaskedBitmap(originalBitmap!!, wordBoundaries)
                    }
                    
                    // Fade animation when mask reappears
                    animate()
                        .alpha(0.8f)
                        .setDuration(100)
                        .withEndAction {
                            updateDisplayedImage()
                            // Fade back to full visibility
                            animate()
                                .alpha(1.0f)
                                .setDuration(100)
                                .start()
                        }
                        .start()
                }
            }
        }
    }

    /**
     * Temporarily reveal an entire ayah for 5 seconds
     */
    fun revealAyahTemporarily(surahNumber: Int, ayahNumber: Int) {
        // Find all words in this ayah
        val ayahWords = wordBoundaries.filter { it.surah == surahNumber && it.ayah == ayahNumber }
        val ayahWordKeys = ayahWords.map { "${it.surah}_${it.ayah}_${it.word}" }
        
        // Add all words of this ayah to revealed words
        revealedWords.addAll(ayahWordKeys)
        
        // Recreate masked bitmap if in masked mode
        if (isMaskedMode && originalBitmap != null && !originalBitmap!!.isRecycled) {
            maskedBitmap?.let { oldMasked ->
                if (!oldMasked.isRecycled) {
                    oldMasked.recycle()
                }
            }
            maskedBitmap = createMaskedBitmap(originalBitmap!!, wordBoundaries)
        }
        
        updateDisplayedImage()
        
        // Hide the ayah again after 5 seconds
        imageScope.launch {
            delay(5000)
            revealedWords.removeAll(ayahWordKeys.toSet())
            withContext(Dispatchers.Main) {
                // Only update if the view is still attached and bitmaps are not recycled
                if (isAttachedToWindow && originalBitmap != null && !originalBitmap!!.isRecycled) {
                    if (isMaskedMode) {
                        maskedBitmap?.let { oldMasked ->
                            if (!oldMasked.isRecycled) {
                                oldMasked.recycle()
                            }
                        }
                        maskedBitmap = createMaskedBitmap(originalBitmap!!, wordBoundaries)
                    }
                    updateDisplayedImage()
                }
            }
        }
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
            revealedAyahs.clear() // Clear revealed ayahs when changing pages
            loadPageImage()
        }
    }

    /**
     * Clean up resources when view is recycled
     */
    fun cleanup() {
        imageScope.cancel()
        
        // Clear the ImageView first to prevent drawing recycled bitmaps
        setImageBitmap(null)
        
        // Safely recycle bitmaps
        originalBitmap?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        originalBitmap = null
        
        maskedBitmap?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        maskedBitmap = null
        
        wordBoundaries = emptyList()
        revealedWords.clear()
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
        Log.d(TAG, "🖼️ setOriginalBitmap called for page $currentPageNumber, isMaskedMode: $isMaskedMode")
        
        // Safety check: ensure new bitmap is valid
        if (bitmap.isRecycled) {
            Log.e(TAG, "❌ Cannot set recycled bitmap as original")
            return
        }
        
        // Clear ImageView first to prevent drawing issues
        setImageBitmap(null)
        
        // Safely recycle old bitmaps
        originalBitmap?.let { oldBitmap ->
            if (!oldBitmap.isRecycled) {
                oldBitmap.recycle()
            }
        }
        maskedBitmap?.let { oldMasked ->
            if (!oldMasked.isRecycled) {
                oldMasked.recycle()
            }
        }
        maskedBitmap = null
        
        originalBitmap = bitmap
        
        // Create masked version if needed
        if (isMaskedMode) {
            if (wordBoundaries.isNotEmpty()) {
                Log.d(TAG, "🎭 Creating masked bitmap in setOriginalBitmap for page $currentPageNumber")
                maskedBitmap = createMaskedBitmap(bitmap, wordBoundaries)
            } else {
                Log.d(TAG, "🎭 Creating masked indicator for page $currentPageNumber (no word boundaries)")
                maskedBitmap = createMaskedIndicator(bitmap)
            }
        } else {
            Log.d(TAG, "🔍 Not creating masked bitmap: isMaskedMode=$isMaskedMode, wordBoundaries.size=${wordBoundaries.size}")
        }
        
        updateDisplayedImage()
        
        Log.d(TAG, "✅ Page $currentPageNumber loaded successfully (${bitmap.width}x${bitmap.height})")
    }

    /**
     * Create a masked version of the bitmap with word placeholders
     */
    private fun createMaskedBitmap(originalBitmap: Bitmap, words: List<WordBoundary>): Bitmap? {
        // Safety check: ensure original bitmap is valid
        if (originalBitmap.isRecycled) {
            Log.e(TAG, "❌ Cannot create masked bitmap: original bitmap is recycled")
            return null
        }
        
        val maskedBitmap = try {
            originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating bitmap copy: ${e.message}")
            return null
        }
        
        if (maskedBitmap == null) {
            Log.e(TAG, "❌ Failed to create bitmap copy")
            return null
        }
        
        val canvas = Canvas(maskedBitmap)
        
        // Paint for masking words (white background)
        val maskPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        
        // Paint for underline placeholders
        val underlinePaint = Paint().apply {
            color = Color.parseColor("#BDBDBD")
            style = Paint.Style.STROKE
            strokeWidth = 4f
            pathEffect = DashPathEffect(floatArrayOf(8f, 4f), 0f) // Dashed line for better visibility
        }
        
        // Calculate scale factors based on actual coordinate ranges
        // First, find the actual coordinate bounds from the data
        if (words.isNotEmpty()) {
            val maxX = words.maxOf { it.x + it.width }
            val maxY = words.maxOf { it.y + it.height }
            val minX = words.minOf { it.x }
            val minY = words.minOf { it.y }
            
            Log.d(TAG, "Coordinate analysis for page $currentPageNumber:")
            Log.d(TAG, "  Bitmap size: ${originalBitmap.width}x${originalBitmap.height}")
            Log.d(TAG, "  X range: $minX to $maxX (span: ${maxX - minX})")
            Log.d(TAG, "  Y range: $minY to $maxY (span: ${maxY - minY})")
        } else {
            Log.w(TAG, "No word boundaries available for page $currentPageNumber")
            return maskedBitmap
        }
        
        // QPC coordinates use the actual image width from the database
        // The database shows img_width=1300, and bitmap is 1300x2103
        // This means QPC coordinates are in actual pixel space!
        val qpcWidth = 1300f  // From database img_width field
        val qpcHeight = 2103f  // From actual bitmap height - QPC should match bitmap dimensions
        
        // Since QPC coordinates are already in pixel space, scaling should be 1:1
        val scaleX = originalBitmap.width.toFloat() / qpcWidth
        val scaleY = originalBitmap.height.toFloat() / qpcHeight
        
        Log.d(TAG, "  📊 QPC coordinate space: ${qpcWidth}x${qpcHeight}")
        Log.d(TAG, "  📊 Bitmap dimensions: ${originalBitmap.width}x${originalBitmap.height}")
        Log.d(TAG, "  📊 Scale factors: scaleX=$scaleX, scaleY=$scaleY")
        
        // No offsets needed - coordinates should map directly
        val offsetX = 0f
        val offsetY = 0f
        
        // Sort words by page position (line then word position)
        val sortedWords = words.sortedWith(compareBy<WordBoundary> { it.line }.thenBy { it.word })
        
        for (word in sortedWords) {
            val wordKey = "${word.surah}_${word.ayah}_${word.word}"
            val shouldMask = !revealedWords.contains(wordKey)
            
            // Extra debugging for ayah reveals
            if (word.surah == 1 && word.ayah == 1) {
                Log.d(TAG, "🔍 Checking Fatiha word ${word.word}: key=$wordKey, shouldMask=$shouldMask")
                Log.d(TAG, "🔍 Revealed words contains this key: ${revealedWords.contains(wordKey)}")
                Log.d(TAG, "🔍 Current revealed words: ${revealedWords.take(10)}")
            }
            
            if (shouldMask) {
                // Log detailed info for first ayah of Fatiha (Surah 1, Ayah 1)
                if (word.surah == 1 && word.ayah == 1) {
                    Log.d(TAG, "📍 Fatiha Ayah 1, Word ${word.word}:")
                    Log.d(TAG, "  📏 Database coords: x=${word.x}, y=${word.y}, w=${word.width}, h=${word.height}")
                    Log.d(TAG, "  📊 Scale factors: scaleX=$scaleX, scaleY=$scaleY")
                    Log.d(TAG, "  📐 Offsets: offsetX=$offsetX, offsetY=$offsetY")
                    Log.d(TAG, "  🖼️ Bitmap size: ${originalBitmap.width}x${originalBitmap.height}")
                }
                
                // Calculate word rectangle with some padding
                // Apply coordinate transformation with position adjustments
                val rect = RectF(
                    word.x * scaleX + offsetX - 1f,
                    word.y * scaleY + offsetY - 1f,
                    (word.x + word.width) * scaleX + offsetX + 1f,
                    (word.y + word.height) * scaleY + offsetY + 1f
                )
                
                // Log transformed coordinates for first ayah of Fatiha
                if (word.surah == 1 && word.ayah == 1) {
                    Log.d(TAG, "  🎯 Final rect: left=${rect.left}, top=${rect.top}, right=${rect.right}, bottom=${rect.bottom}")
                    Log.d(TAG, "  📏 Final size: width=${rect.width()}, height=${rect.height()}")
                    Log.d(TAG, "  ═══════════════════════════════════════")
                }
                
                // Draw white background to mask the word
                canvas.drawRect(rect, maskPaint)
                
                // Draw underline placeholder - make it slightly narrower than the word
                val underlineY = rect.bottom - 3f
                val underlineLeft = rect.left + (rect.width() * 0.1f)
                val underlineRight = rect.right - (rect.width() * 0.1f)
                canvas.drawLine(underlineLeft, underlineY, underlineRight, underlineY, underlinePaint)
            }
        }
        
        // Draw debug touch points as red circles (only in debug mode)
        if (isDebugMode && debugTouchPoints.isNotEmpty()) {
            val touchPaint = Paint().apply {
                color = Color.RED
                style = Paint.Style.FILL
                alpha = 200 // Semi-transparent
            }
            
            val touchBorderPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            
            for (touchPoint in debugTouchPoints) {
                val circleRadius = 15f
                // Draw white border
                canvas.drawCircle(touchPoint.first, touchPoint.second, circleRadius + 1f, touchBorderPaint)
                // Draw red circle
                canvas.drawCircle(touchPoint.first, touchPoint.second, circleRadius, touchPaint)
            }
            
            Log.d(TAG, "🔴 Drew ${debugTouchPoints.size} debug touch circles")
        }
        
        // Summary logging
        val totalWords = words.size
        val revealedCount = revealedWords.size
        val maskedCount = words.count { word ->
            val wordKey = "${word.surah}_${word.ayah}_${word.word}"
            !revealedWords.contains(wordKey)
        }
        Log.d(TAG, "📊 Masking summary: $totalWords total words, $revealedCount revealed, $maskedCount masked")
        
        return maskedBitmap
    }
    
    /**
     * Create a masked indicator when masking is enabled but no word boundaries are available
     */
    private fun createMaskedIndicator(originalBitmap: Bitmap): Bitmap? {
        // Safety check: ensure original bitmap is valid
        if (originalBitmap.isRecycled) {
            Log.e(TAG, "❌ Cannot create masked indicator: original bitmap is recycled")
            return null
        }
        
        val maskedBitmap = try {
            originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating bitmap copy for indicator: ${e.message}")
            return null
        }
        
        if (maskedBitmap == null) {
            Log.e(TAG, "❌ Failed to create bitmap copy for indicator")
            return null
        }
        
        val canvas = Canvas(maskedBitmap)
        
        // Add a subtle overlay to indicate masking is enabled
        val overlayPaint = Paint().apply {
            color = Color.parseColor("#10000000") // Very light black overlay (6% opacity)
            style = Paint.Style.FILL
        }
        
        // Draw subtle overlay
        canvas.drawRect(0f, 0f, maskedBitmap.width.toFloat(), maskedBitmap.height.toFloat(), overlayPaint)
        
        // Add masking indicator text in top-right corner
        val textPaint = Paint().apply {
            color = Color.parseColor("#4CAF50")
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        
        val indicatorText = "🔒 Masked Mode"
        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds(indicatorText, 0, indicatorText.length, textBounds)
        
        // Position in top-right corner with some margin
        val x = maskedBitmap.width - textBounds.width() - 20f
        val y = textBounds.height() + 20f
        
        // Draw semi-transparent background
        val bgPaint = Paint().apply {
            color = Color.parseColor("#80FFFFFF")
            style = Paint.Style.FILL
        }
        
        val bgRect = RectF(
            x - 10f,
            y - textBounds.height() - 5f,
            x + textBounds.width() + 10f,
            y + 5f
        )
        canvas.drawRoundRect(bgRect, 8f, 8f, bgPaint)
        
        // Draw the text
        canvas.drawText(indicatorText, x, y, textPaint)
        
        Log.d(TAG, "✅ Created masked indicator for page $currentPageNumber")
        return maskedBitmap
    }
    
    /**
     * Update the displayed image based on current mode
     */
    private fun updateDisplayedImage() {
        Log.d(TAG, "🖼️ updateDisplayedImage() called for page $currentPageNumber - isMaskedMode: $isMaskedMode")
        
        // Safety check: ensure we don't try to display recycled bitmaps
        val bitmapToShow = when {
            isMaskedMode && maskedBitmap != null && !maskedBitmap!!.isRecycled -> {
                Log.d(TAG, "📋 Showing MASKED bitmap for page $currentPageNumber")
                maskedBitmap
            }
            originalBitmap != null && !originalBitmap!!.isRecycled -> {
                Log.d(TAG, "📄 Showing ORIGINAL bitmap for page $currentPageNumber")
                // Create a copy with debug touch circles if we have touch points and debug mode is enabled
                if (isDebugMode && debugTouchPoints.isNotEmpty()) {
                    addDebugTouchCircles(originalBitmap!!)
                } else {
                    originalBitmap
                }
            }
            else -> {
                Log.w(TAG, "⚠️ No valid bitmap available for page $currentPageNumber")
                null
            }
        }
        
        if (bitmapToShow != null) {
            try {
                setImageBitmap(bitmapToShow)
                Log.d(TAG, "✅ Bitmap set successfully for page $currentPageNumber")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error setting bitmap: ${e.message}")
                // Clear the ImageView if bitmap setting fails
                setImageBitmap(null)
            }
        } else {
            Log.w(TAG, "⚠️ No valid bitmap to display")
            setImageBitmap(null)
        }
    }
    
    /**
     * Add debug touch circles to a bitmap (for non-masked mode)
     */
    private fun addDebugTouchCircles(originalBitmap: Bitmap): Bitmap {
        if (!isDebugMode || debugTouchPoints.isEmpty()) return originalBitmap
        
        val bitmapWithCircles = try {
            originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating bitmap copy for touch circles: ${e.message}")
            return originalBitmap
        }
        
        val canvas = Canvas(bitmapWithCircles)
        
        val touchPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
            alpha = 200 // Semi-transparent
        }
        
        val touchBorderPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        
        for (touchPoint in debugTouchPoints) {
            val circleRadius = 15f
            // Draw white border
            canvas.drawCircle(touchPoint.first, touchPoint.second, circleRadius + 1f, touchBorderPaint)
            // Draw red circle
            canvas.drawCircle(touchPoint.first, touchPoint.second, circleRadius, touchPaint)
        }
        
        Log.d(TAG, "🔴 Added ${debugTouchPoints.size} debug touch circles to original bitmap")
        return bitmapWithCircles
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
        
        val bitmap = originalBitmap ?: return
        
        // Get the drawable bounds and matrix to properly transform coordinates
        val drawable = this.drawable ?: return
        val matrix = imageMatrix
        val viewBounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val drawableBounds = RectF(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        
        // Apply the image matrix transformation to map view coordinates to bitmap coordinates
        val inverseMatrix = Matrix()
        if (matrix.invert(inverseMatrix)) {
            val touchPoint = floatArrayOf(x, y)
            inverseMatrix.mapPoints(touchPoint)
            
            val bitmapX = touchPoint[0]
            val bitmapY = touchPoint[1]
            
            // Store touch point for debugging (only if debug mode is enabled)
            if (isDebugMode) {
                debugTouchPoints.add(Pair(bitmapX, bitmapY))
                if (debugTouchPoints.size > 5) {
                    debugTouchPoints.removeAt(0)
                }
            }
            
            // Use the SAME coordinate transformation as createMaskedBitmap
            val qpcWidth = 1300f  // From database img_width field
            val qpcHeight = 2103f  // From actual bitmap height - QPC should match bitmap dimensions
            
            // Same scale factors as masking
            val scaleX = bitmap.width.toFloat() / qpcWidth
            val scaleY = bitmap.height.toFloat() / qpcHeight
            val offsetX = 0f
            val offsetY = 0f
            
            Log.d(TAG, "Touch: view($x,$y) -> bitmap($bitmapX,$bitmapY)")
            Log.d(TAG, "Matrix transformation applied, scale factors: scaleX=$scaleX, scaleY=$scaleY")
            Log.d(TAG, "Checking ${wordBoundaries.size} word boundaries...")
            
            // Check for word clicks by applying the SAME transformation as masking
            var foundWord: WordBoundary? = null
            for (word in wordBoundaries) {
                // Transform word coordinates to bitmap space (same as masking)
                val wordLeft = word.x * scaleX + offsetX
                val wordTop = word.y * scaleY + offsetY
                val wordRight = (word.x + word.width) * scaleX + offsetX
                val wordBottom = (word.y + word.height) * scaleY + offsetY
                
                if (bitmapX >= wordLeft && bitmapX <= wordRight &&
                    bitmapY >= wordTop && bitmapY <= wordBottom) {
                    
                    Log.d(TAG, "✅ Word clicked: ${word.surah}:${word.ayah}:${word.word}")
                    Log.d(TAG, "   QPC coords: (${word.x},${word.y},${word.width},${word.height})")
                    Log.d(TAG, "   Bitmap rect: ($wordLeft,$wordTop,$wordRight,$wordBottom)")
                    foundWord = word
                    onWordClickListener?.invoke(word)
                    
                    // Always reveal word temporarily (no mode check needed)
                    revealWordTemporarily(word)
                    break
                }
            }
            
            if (foundWord == null) {
                Log.d(TAG, "❌ No word found at bitmap($bitmapX,$bitmapY)")
                
                // Check if this is a click within an ayah area (not on a specific word)
                checkForAyahAreaClick(bitmapX, bitmapY, scaleX, scaleY, offsetX, offsetY)
            }
            
            // Update display to show the new touch circle
            updateDisplayedImage()
        } else {
            Log.e(TAG, "❌ Failed to invert image matrix for coordinate transformation")
        }
    }
    
    /**
     * Check if the touch is within an ayah area and reveal/hide the entire ayah
     */
    private fun checkForAyahAreaClick(bitmapX: Float, bitmapY: Float, scaleX: Float, scaleY: Float, offsetX: Float, offsetY: Float) {
        Log.d(TAG, "🔍 checkForAyahAreaClick called at ($bitmapX, $bitmapY)")
        
        // Find which ayah the touch point falls within based on word boundaries
        val touchedAyah = findAyahAtPosition(bitmapX, bitmapY, scaleX, scaleY, offsetX, offsetY)
        
        if (touchedAyah != null) {
            val ayahKey = "${touchedAyah.first}_${touchedAyah.second}"
            val surah = touchedAyah.first
            val ayah = touchedAyah.second
            
            Log.d(TAG, "🎯 AYAH AREA clicked for Surah $surah, Ayah $ayah")
            
            // Show immediate feedback
            android.widget.Toast.makeText(context, "Ayah $surah:$ayah", android.widget.Toast.LENGTH_SHORT).show()
            
            // Toggle ayah visibility
            if (revealedAyahs.contains(ayahKey)) {
                hideAyah(surah, ayah)
            } else {
                revealAyah(surah, ayah)
            }
        } else {
            Log.d(TAG, "🔍 No ayah area found at ($bitmapX, $bitmapY)")
        }
    }
    
    /**
     * Find which ayah contains the given position based on word boundaries
     */
    private fun findAyahAtPosition(bitmapX: Float, bitmapY: Float, scaleX: Float, scaleY: Float, offsetX: Float, offsetY: Float): Pair<Int, Int>? {
        // Find the closest words to determine which ayah this position belongs to
        val wordsWithDistance = wordBoundaries.mapNotNull { word ->
            val wordLeft = word.x * scaleX + offsetX
            val wordTop = word.y * scaleY + offsetY
            val wordRight = (word.x + word.width) * scaleX + offsetX
            val wordBottom = (word.y + word.height) * scaleY + offsetY
            
            // Check if the touch is roughly within the line height and horizontal span of this ayah
            val verticalDistance = when {
                bitmapY < wordTop -> wordTop - bitmapY
                bitmapY > wordBottom -> bitmapY - wordBottom
                else -> 0f // Within the line
            }
            
            // Only consider words that are on the same line or very close vertically
            if (verticalDistance <= 30f) { // Allow some tolerance for line spacing
                val wordCenterX = (wordLeft + wordRight) / 2f
                val horizontalDistance = kotlin.math.abs(bitmapX - wordCenterX)
                
                Triple(word, verticalDistance + horizontalDistance, Pair(word.surah, word.ayah))
            } else {
                null
            }
        }.sortedBy { it.second }
        
        // Return the ayah of the closest word if found
        return wordsWithDistance.firstOrNull()?.third
    }
    
    /**
     * Reveal an entire ayah
     */
    private fun revealAyah(surahNumber: Int, ayahNumber: Int) {
        val ayahKey = "${surahNumber}_${ayahNumber}"
        revealedAyahs.add(ayahKey)
        
        // Find all words in this ayah
        val ayahWords = wordBoundaries.filter { it.surah == surahNumber && it.ayah == ayahNumber }
        val ayahWordKeys = ayahWords.map { "${it.surah}_${it.ayah}_${it.word}" }
        
        // Add all words of this ayah to revealed words
        revealedWords.addAll(ayahWordKeys)
        
        Log.d(TAG, "👁️ Revealed ayah $surahNumber:$ayahNumber (${ayahWords.size} words)")
        Log.d(TAG, "👁️ Total revealed words now: ${revealedWords.size}")
        Log.d(TAG, "👁️ Ayah words added: $ayahWordKeys")
        
        // Force immediate update without animation
        if (isMaskedMode && originalBitmap != null && !originalBitmap!!.isRecycled) {
            maskedBitmap?.let { oldMasked ->
                if (!oldMasked.isRecycled) {
                    oldMasked.recycle()
                }
            }
            maskedBitmap = createMaskedBitmap(originalBitmap!!, wordBoundaries)
            
            // Ensure UI update happens on main thread
            post {
                updateDisplayedImage()
                invalidate() // Force view to redraw
            }
        } else {
            // Ensure UI update happens on main thread
            post {
                updateDisplayedImage()
                invalidate() // Force view to redraw
            }
        }
    }
    
    /**
     * Hide an entire ayah
     */
    private fun hideAyah(surahNumber: Int, ayahNumber: Int) {
        val ayahKey = "${surahNumber}_${ayahNumber}"
        revealedAyahs.remove(ayahKey)
        
        // Find all words in this ayah
        val ayahWords = wordBoundaries.filter { it.surah == surahNumber && it.ayah == ayahNumber }
        val ayahWordKeys = ayahWords.map { "${it.surah}_${it.ayah}_${it.word}" }
        
        // Remove all words of this ayah from revealed words
        revealedWords.removeAll(ayahWordKeys.toSet())
        
        Log.d(TAG, "🙈 Hidden ayah $surahNumber:$ayahNumber (${ayahWords.size} words)")
        Log.d(TAG, "🙈 Total revealed words now: ${revealedWords.size}")
        Log.d(TAG, "🙈 Ayah words removed: $ayahWordKeys")
        
        // Force immediate update with animation
        if (isMaskedMode && originalBitmap != null && !originalBitmap!!.isRecycled) {
            maskedBitmap?.let { oldMasked ->
                if (!oldMasked.isRecycled) {
                    oldMasked.recycle()
                }
            }
            maskedBitmap = createMaskedBitmap(originalBitmap!!, wordBoundaries)
            
            // Quick animation to show the change
            animate()
                .alpha(0.8f)
                .setDuration(100)
                .withEndAction {
                    updateDisplayedImage()
                    animate()
                        .alpha(1.0f)
                        .setDuration(100)
                        .start()
                }
                .start()
        } else {
            updateDisplayedImage()
        }
    }
    
    /**
     * Update the masked display after ayah visibility changes
     */
    private fun updateMaskedDisplay() {
        if (isMaskedMode && originalBitmap != null && !originalBitmap!!.isRecycled) {
            maskedBitmap?.let { oldMasked ->
                if (!oldMasked.isRecycled) {
                    oldMasked.recycle()
                }
            }
            maskedBitmap = createMaskedBitmap(originalBitmap!!, wordBoundaries)
        }
        
        updateDisplayedImage()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        imageScope.cancel()
        
        // Clear the ImageView first
        setImageBitmap(null)
        
        // Safely recycle bitmaps
        originalBitmap?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        maskedBitmap?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
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
