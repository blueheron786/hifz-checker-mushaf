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
        private const val BLACK_IMAGES_WORD_BY_WORD_BASE_URL = "https://api.qurancdn.com/api/qdc/words"
        
        // Cache directories
        private const val PAGE_IMAGES_CACHE_DIR = "quran_pages"
        private const val WORD_IMAGES_CACHE_DIR = "quran_words"
        
        // Image dimensions
        private const val PAGE_IMAGE_WIDTH = 1800
        private const val PAGE_IMAGE_HEIGHT = 2700
    }

    // Properties
    private var pageNumber: Int = 1
    private var pageInfo: PageInfo? = null
    private var isMaskedMode: Boolean = false
    private var onWordClickListener: ((WordBoundary) -> Unit)? = null
    
    // Gesture detection
    private val gestureDetector = GestureDetector(context, PageGestureListener())
    
    // Image caching
    val imageCache = QuranImageCache(context)
    
    // Current bitmaps
    private var originalPageBitmap: Bitmap? = null
    private var maskedPageBitmap: Bitmap? = null
    private var currentRevealedBitmap: Bitmap? = null
    
    init {
        scaleType = ScaleType.FIT_CENTER
        adjustViewBounds = true
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
     * Loads the page image from cache or downloads it
     */
    private fun loadPageImage() {
        Log.d(TAG, "🖼️ Loading page image for page: $pageNumber")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Immediately show placeholder to indicate loading
                withContext(Dispatchers.Main) {
                    setOriginalBitmap(createPlaceholderBitmap())
                }
                
                // First try to load from cache
                val cachedBitmap = imageCache.getPageImage(pageNumber)
                if (cachedBitmap != null) {
                    Log.d(TAG, "✅ Found cached image for page $pageNumber")
                    withContext(Dispatchers.Main) {
                        setOriginalBitmap(cachedBitmap)
                    }
                    return@launch
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
        val pageNumberPadded = pageNumber.toString().padStart(3, '0')
        
        // Try multiple sources in order of preference
        val imageSources = listOf(
            "$QPC_BASE_URL/$pageNumberPadded.gif",
            "$QPC_ALT_URL/page_$pageNumberPadded.png",
            "https://images.quran.com/pages/page_$pageNumberPadded.png",
            "https://www.searchtruth.com/quran/images2/large/page-$pageNumberPadded.gif"
        )
        
        for (imageUrl in imageSources) {
            try {
                Log.d(TAG, "Trying to download page image from: $imageUrl")
                
                val url = URL(imageUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connectTimeout = 10000
                connection.readTimeout = 20000
                connection.setRequestProperty("User-Agent", "QuranImageReader/2.0")
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    connection.disconnect()
                    
                    if (bitmap != null) {
                        Log.d(TAG, "Successfully downloaded page image for page $pageNumber from: $imageUrl")
                        return bitmap
                    }
                } else {
                    Log.w(TAG, "HTTP ${connection.responseCode} for URL: $imageUrl")
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to download from $imageUrl: ${e.message}")
                continue
            }
        }
        
        Log.e(TAG, "Failed to download page $pageNumber from all sources")
        return null
    }

    /**
     * Sets the original bitmap and creates masked version if needed
     */
    private fun setOriginalBitmap(bitmap: Bitmap) {
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
        val bitmap = Bitmap.createBitmap(PAGE_IMAGE_WIDTH, PAGE_IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Background - light cream color
        canvas.drawColor(Color.parseColor("#FFF8DC"))
        
        // Border
        val borderPaint = Paint().apply {
            color = Color.parseColor("#DDDDDD")
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        canvas.drawRect(20f, 20f, bitmap.width - 20f, bitmap.height - 20f, borderPaint)
        
        // Text paint
        val textPaint = Paint().apply {
            color = Color.parseColor("#666666")
            textSize = 72f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        
        // Draw page number in Arabic
        canvas.drawText(
            "صفحة $pageNumber",
            bitmap.width / 2f,
            bitmap.height / 2f - 150f,
            textPaint
        )
        
        // Draw page number in English
        textPaint.textSize = 48f
        canvas.drawText(
            "Page $pageNumber",
            bitmap.width / 2f,
            bitmap.height / 2f - 50f,
            textPaint
        )
        
        // Draw status message
        textPaint.textSize = 36f
        textPaint.color = Color.parseColor("#999999")
        canvas.drawText(
            "Loading Quran image...",
            bitmap.width / 2f,
            bitmap.height / 2f + 100f,
            textPaint
        )
        
        // Draw debugging info
        textPaint.textSize = 24f
        textPaint.color = Color.parseColor("#AAAAAA")
        canvas.drawText(
            "If this persists, check network connection",
            bitmap.width / 2f,
            bitmap.height / 2f + 200f,
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
                    wordBitmap = downloadWordImage(word)
                    if (wordBitmap != null) {
                        imageCache.cacheWordImage(wordImageKey, wordBitmap)
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
     * Downloads word image from BlackImagesWordByWord
     */
    private suspend fun downloadWordImage(word: WordBoundary): Bitmap? {
        return try {
            val imageUrl = "$BLACK_IMAGES_WORD_BY_WORD_BASE_URL/${word.surah}/${word.ayah}/${word.word}.png"
            
            Log.d(TAG, "Downloading word image from: $imageUrl")
            
            val url = URL(imageUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 20000
            connection.setRequestProperty("User-Agent", "QuranImageReader/2.0")
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                connection.disconnect()
                
                Log.d(TAG, "Successfully downloaded word image: ${word.surah}/${word.ayah}/${word.word}")
                bitmap
            } else {
                Log.w(TAG, "HTTP ${connection.responseCode} when downloading word image")
                connection.disconnect()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading word image", e)
            null
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
        currentRevealedBitmap?.recycle()
        currentRevealedBitmap = null
        
        // Don't recycle original bitmaps as they might be cached
        removeCallbacks(null)
    }
}
