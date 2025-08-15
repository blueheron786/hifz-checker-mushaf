package com.hifzmushaf.ui.reader

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.*
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hifzmushaf.R
import com.hifzmushaf.databinding.ItemQuranImagePageBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class QuranImagePageAdapter(
    private val fragment: QuranImageReaderFragment,
    private val totalPages: Int,
    private val pageInfoMap: Map<Int, PageInfo>
) : RecyclerView.Adapter<QuranImagePageAdapter.QuranImagePageViewHolder>() {

    companion object {
        private const val TAG = "QuranImagePageAdapter"
    }
    
    // Cache for original page bitmaps and masked versions
    private val originalBitmaps = mutableMapOf<Int, Bitmap>()
    private val maskedBitmaps = mutableMapOf<Int, Bitmap>()

    inner class QuranImagePageViewHolder(val binding: ItemQuranImagePageBinding) : RecyclerView.ViewHolder(binding.root) {
        private val gestureDetector = GestureDetector(binding.root.context, PageGestureListener())
        
        init {
            binding.quranImagePageView.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                true
            }
        }
        
        private inner class PageGestureListener : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val pageNumber = bindingAdapterPosition + 1
                val pageInfo = pageInfoMap[pageNumber]
                
                if (pageInfo != null && pageInfo.words.isNotEmpty()) {
                    handleWordTap(e.x, e.y, pageInfo)
                }
                return true
            }
            
            override fun onLongPress(e: MotionEvent) {
                // Handle long press for word selection, bookmark, etc.
                val pageNumber = bindingAdapterPosition + 1
                Log.d(TAG, "Long press on page $pageNumber at ${e.x}, ${e.y}")
            }
        }
        
        private fun handleWordTap(x: Float, y: Float, pageInfo: PageInfo) {
            // Convert touch coordinates to image coordinates
            val imageView = binding.quranImagePageView
            val bitmap = (imageView.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            
            if (bitmap != null) {
                val imageMatrix = imageView.imageMatrix
                val invertedMatrix = Matrix()
                if (imageMatrix.invert(invertedMatrix)) {
                    val imageCoords = floatArrayOf(x, y)
                    invertedMatrix.mapPoints(imageCoords)
                    
                    val imageX = imageCoords[0]
                    val imageY = imageCoords[1]
                    
                    // Find word at these coordinates
                    val tappedWord = pageInfo.words.find { word ->
                        imageX >= word.x && imageX <= word.x + word.width &&
                        imageY >= word.y && imageY <= word.y + word.height
                    }
                    
                    if (tappedWord != null) {
                        revealWord(tappedWord)
                        Log.d(TAG, "Revealed word: ${tappedWord.text} at surah ${tappedWord.surah}, ayah ${tappedWord.ayah}")
                    }
                }
            }
        }
        
        private fun revealWord(word: WordBoundary) {
            val imageView = binding.quranImagePageView
            val pageNumber = bindingAdapterPosition + 1
            val originalBitmap = originalBitmaps[pageNumber]
            val maskedBitmap = maskedBitmaps[pageNumber]
            
            if (originalBitmap != null && maskedBitmap != null) {
                // Create a temporary bitmap showing the revealed word
                val revealedBitmap = maskedBitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(revealedBitmap)
                
                // Create paint to copy the original word area
                val paint = Paint().apply {
                    isAntiAlias = true
                }
                
                // Define the word area with some padding
                val padding = 4f
                val srcRect = Rect(
                    (word.x - padding).toInt().coerceAtLeast(0),
                    (word.y - padding).toInt().coerceAtLeast(0),
                    (word.x + word.width + padding).toInt().coerceAtMost(originalBitmap.width),
                    (word.y + word.height + padding).toInt().coerceAtMost(originalBitmap.height)
                )
                
                val destRect = RectF(srcRect)
                
                // Copy the original word area onto the masked bitmap
                canvas.drawBitmap(originalBitmap, srcRect, destRect, paint)
                
                // Add a subtle highlight around the revealed word
                val highlightPaint = Paint().apply {
                    color = Color.YELLOW
                    alpha = 60
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                }
                
                val highlightRect = RectF(
                    word.x - 2f,
                    word.y - 2f,
                    word.x + word.width + 2f,
                    word.y + word.height + 2f
                )
                
                canvas.drawRect(highlightRect, highlightPaint)
                
                // Show the revealed word
                imageView.setImageBitmap(revealedBitmap)
                
                // Hide the word again after 5 seconds
                imageView.postDelayed({
                    // Restore the masked version
                    maskedBitmaps[pageNumber]?.let { cached ->
                        imageView.setImageBitmap(cached)
                    }
                }, 5000)
                
                Log.d(TAG, "Revealed word for 5 seconds: ${word.text}")
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuranImagePageViewHolder {
        val binding = ItemQuranImagePageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QuranImagePageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QuranImagePageViewHolder, position: Int) {
        val pageNumber = position + 1
        loadPageWithMask(holder, pageNumber)
    }

    private fun loadPageWithMask(holder: QuranImagePageViewHolder, pageNumber: Int) {
        loadPageWithMask(pageNumber, holder.binding.quranImagePageView)
    }
    
    private fun loadPageWithMask(pageNumber: Int) {
        // This is called to reload the masked version after word reveal timeout
        val viewHolder = findViewHolderForPage(pageNumber - 1)
        viewHolder?.let { holder ->
            loadPageWithMask(pageNumber, holder.binding.quranImagePageView)
        }
    }
    
    private fun findViewHolderForPage(position: Int): QuranImagePageViewHolder? {
        // Try to find the view holder for the given position
        // This is a simplified approach - in practice you might need a more robust method
        return null // For now, we'll rely on the timeout mechanism
    }

    private fun loadPageWithMask(pageNumber: Int, imageView: android.widget.ImageView) {
        try {
            // Check if we're in masked mode
            if (!fragment.isMaskedMode()) {
                // Load normal image without masking
                loadOriginalPageImageAsync(pageNumber) { originalBitmap ->
                    if (originalBitmap != null) {
                        imageView.setImageBitmap(originalBitmap)
                        Log.d(TAG, "Loaded normal image for page $pageNumber")
                    } else {
                        // Show a simple placeholder instead of the green grid
                        val placeholderBitmap = createPlaceholderBitmap(pageNumber)
                        imageView.setImageBitmap(placeholderBitmap)
                        Log.w(TAG, "No image found for page $pageNumber, showing placeholder")
                    }
                }
                return
            }
            
            // Check if we already have a masked version cached
            maskedBitmaps[pageNumber]?.let { cachedMasked ->
                imageView.setImageBitmap(cachedMasked)
                return
            }
            
            // Load the original image first
            loadOriginalPageImageAsync(pageNumber) { originalBitmap ->
                if (originalBitmap != null) {
                    // Cache the original
                    originalBitmaps[pageNumber] = originalBitmap
                    
                    // Create masked version
                    val maskedBitmap = createMaskedImage(originalBitmap, pageNumber)
                    maskedBitmaps[pageNumber] = maskedBitmap
                    
                    imageView.setImageBitmap(maskedBitmap)
                    Log.d(TAG, "Loaded masked image for page $pageNumber")
                } else {
                    // Show placeholder if no image found
                    val placeholderBitmap = createPlaceholderBitmap(pageNumber)
                    imageView.setImageBitmap(placeholderBitmap)
                    Log.w(TAG, "No image found for page $pageNumber, showing placeholder")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading masked image for page $pageNumber", e)
            val placeholderBitmap = createPlaceholderBitmap(pageNumber)
            imageView.setImageBitmap(placeholderBitmap)
        }
    }
    
    private fun createMaskedImage(originalBitmap: Bitmap, pageNumber: Int): Bitmap {
        val pageInfo = pageInfoMap[pageNumber]
        
        // If no word boundaries, return original image
        if (pageInfo?.words?.isEmpty() != false) {
            return originalBitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        
        // Create a working copy
        val maskedBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(maskedBitmap)
        
        // Create paint for masking words (make them invisible)
        val maskPaint = Paint().apply {
            color = Color.WHITE // Use page background color
            style = Paint.Style.FILL
            alpha = 255 // Completely opaque to hide the text
        }
        
        // Create paint for subtle word boundaries (optional - for debugging)
        val boundaryPaint = Paint().apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 1f
            alpha = 30 // Very subtle
        }
        
        // Mask each word
        for (word in pageInfo.words) {
            val wordRect = RectF(
                word.x - 2f, // Add small padding
                word.y - 2f,
                word.x + word.width + 2f,
                word.y + word.height + 2f
            )
            
            // Draw mask over the word
            canvas.drawRect(wordRect, maskPaint)
            
            // Optionally draw subtle boundary for debugging
            // canvas.drawRect(wordRect, boundaryPaint)
        }
        
        return maskedBitmap
    }

    private fun loadOriginalPageImage(pageNumber: Int): Bitmap? {
        try {
            // Try multiple possible image formats and locations
            val possibleImageNames = listOf(
                "page_${pageNumber.toString().padStart(3, '0')}.png",
                "page_${pageNumber.toString().padStart(3, '0')}.jpg",
                "page_$pageNumber.png",
                "page_$pageNumber.jpg",
                "${pageNumber.toString().padStart(3, '0')}.png",
                "${pageNumber.toString().padStart(3, '0')}.jpg"
            )
            
            // Try loading from assets/pages/
            for (imageName in possibleImageNames) {
                try {
                    val inputStream = fragment.requireContext().assets.open("pages/$imageName")
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    
                    if (bitmap != null) {
                        Log.d(TAG, "Loaded original image: pages/$imageName")
                        return bitmap
                    }
                } catch (e: Exception) {
                    // Try next format
                }
            }
            
            // If no image found in assets, try to extract from database
            return extractImageFromDatabase(fragment.requireContext(), pageNumber)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading original image for page $pageNumber", e)
            return null
        }
    }

    private fun loadOriginalPageImageAsync(pageNumber: Int, callback: (Bitmap?) -> Unit) {
        // First try to load from cache
        originalBitmaps[pageNumber]?.let { cachedBitmap ->
            callback(cachedBitmap)
            return
        }
        
        // Try loading from local assets/database first
        val localBitmap = loadOriginalPageImage(pageNumber)
        if (localBitmap != null) {
            originalBitmaps[pageNumber] = localBitmap
            callback(localBitmap)
            return
        }
        
        // If not found locally, try downloading from URL
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val downloadedBitmap = downloadPageImage(pageNumber)
                withContext(Dispatchers.Main) {
                    if (downloadedBitmap != null) {
                        originalBitmaps[pageNumber] = downloadedBitmap
                        callback(downloadedBitmap)
                        Log.d(TAG, "Downloaded and loaded image for page $pageNumber")
                    } else {
                        callback(null)
                        Log.w(TAG, "Failed to download image for page $pageNumber")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(null)
                    Log.e(TAG, "Error downloading image for page $pageNumber", e)
                }
            }
        }
    }

    private fun downloadPageImage(pageNumber: Int): Bitmap? {
        return try {
            // Common Quran image sources - you can modify these URLs as needed
            val possibleUrls = listOf(
                // QPC images from common sources
                "https://www.searchtruth.com/quran/images1/${pageNumber.toString().padStart(3, '0')}.gif",
                "https://quran.com/images/pages/page_${pageNumber.toString().padStart(3, '0')}.png",
                "https://images.quran.com/pages/page_${pageNumber.toString().padStart(3, '0')}.png",
                // Alternative format
                "https://www.searchtruth.com/quran/images2/large/page-${pageNumber.toString().padStart(3, '0')}.gif",
                // King Fahd Complex images (if available)
                "https://qurancomplex.gov.sa/api/v1/pages/$pageNumber.png"
            )
            
            for (imageUrl in possibleUrls) {
                try {
                    Log.d(TAG, "Attempting to download from: $imageUrl")
                    val url = URL(imageUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.doInput = true
                    connection.connectTimeout = 10000 // 10 seconds
                    connection.readTimeout = 30000 // 30 seconds
                    connection.setRequestProperty("User-Agent", "QuranImageReader/1.0")
                    connection.connect()
                    
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val inputStream = connection.inputStream
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream.close()
                        connection.disconnect()
                        
                        if (bitmap != null) {
                            Log.d(TAG, "Successfully downloaded image from: $imageUrl")
                            // Save to cache directory for future use
                            saveBitmapToCache(bitmap, pageNumber)
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
            
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error in downloadPageImage for page $pageNumber", e)
            null
        }
    }

    private fun saveBitmapToCache(bitmap: Bitmap, pageNumber: Int) {
        try {
            val cacheDir = File(fragment.requireContext().cacheDir, "quran_pages")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            val file = File(cacheDir, "page_${pageNumber.toString().padStart(3, '0')}.png")
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
            outputStream.close()
            
            Log.d(TAG, "Cached image for page $pageNumber")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache image for page $pageNumber", e)
        }
    }

    private fun createPlaceholderBitmap(pageNumber: Int): Bitmap {
        // Create a simple placeholder with page number
        val width = 800
        val height = 1200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Fill with off-white background
        canvas.drawColor(Color.parseColor("#F8F8F8"))
        
        // Draw border
        val borderPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRect(10f, 10f, width - 10f, height - 10f, borderPaint)
        
        // Draw page number
        val textPaint = Paint().apply {
            color = Color.parseColor("#888888")
            textSize = 48f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        
        canvas.drawText(
            "صفحة $pageNumber",
            width / 2f,
            height / 2f - 50f,
            textPaint
        )
        
        canvas.drawText(
            "Page $pageNumber",
            width / 2f,
            height / 2f + 50f,
            textPaint
        )
        
        // Draw loading message
        textPaint.textSize = 24f
        canvas.drawText(
            "Loading from network...",
            width / 2f,
            height / 2f + 150f,
            textPaint
        )
        
        return bitmap
    }

    private fun extractImageFromDatabase(context: Context, pageNumber: Int): Bitmap? {
        return try {
            // Copy database to internal storage if needed
            val dbFile = copyDatabaseToInternal(context)
            
            if (dbFile.exists()) {
                val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                
                // Try different possible table and column names
                val queries = listOf(
                    "SELECT image FROM pages WHERE page_number = $pageNumber",
                    "SELECT page_image FROM pages WHERE id = $pageNumber",
                    "SELECT data FROM images WHERE page = $pageNumber",
                    "SELECT content FROM page_images WHERE page_id = $pageNumber"
                )
                
                for (query in queries) {
                    try {
                        val cursor = db.rawQuery(query, null)
                        if (cursor.moveToFirst()) {
                            val imageBytes = cursor.getBlob(0)
                            cursor.close()
                            
                            if (imageBytes != null && imageBytes.isNotEmpty()) {
                                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                db.close()
                                return bitmap
                            }
                        }
                        cursor.close()
                    } catch (e: Exception) {
                        // Try next query
                    }
                }
                
                db.close()
            }
            
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting image from database for page $pageNumber", e)
            null
        }
    }

    private fun copyDatabaseToInternal(context: Context): File {
        val dbFile = File(context.filesDir, "qpc15linesv2.db")
        
        if (!dbFile.exists()) {
            try {
                val inputStream: InputStream = context.resources.openRawResource(
                    context.resources.getIdentifier("qpc15linesv2", "raw", context.packageName)
                )
                val outputStream = FileOutputStream(dbFile)
                
                val buffer = ByteArray(1024)
                var length: Int
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }
                
                outputStream.flush()
                outputStream.close()
                inputStream.close()
                
                Log.d(TAG, "Database copied to internal storage")
            } catch (e: Exception) {
                Log.e(TAG, "Error copying database", e)
            }
        }
        
        return dbFile
    }

    override fun onViewRecycled(holder: QuranImagePageViewHolder) {
        val position = holder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION) {
            // Clear any pending reveal timeouts
            holder.binding.quranImagePageView.removeCallbacks(null)
            
            // Don't immediately clear cached bitmaps as they may be reused
            // We'll implement LRU cache management if memory becomes an issue
        }
        super.onViewRecycled(holder)
    }

    override fun getItemCount() = totalPages
    
    // Method to clear cache when memory is low
    fun clearCache() {
        originalBitmaps.values.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        maskedBitmaps.values.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        originalBitmaps.clear()
        maskedBitmaps.clear()
    }
}
