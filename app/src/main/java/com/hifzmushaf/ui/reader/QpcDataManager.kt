package com.hifzmushaf.ui.reader

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class QpcDataManager(private val context: Context) {
    
    companion object {
        private const val TAG = "QpcDataManager"
        private const val DATABASE_NAME = "quranwordbounds.sqlite"  // Use the correct database file with word coordinates
        private const val JSON_FILE = "qpc-v2.json"
    }
    
    /**
     * Load word boundaries from JSON file
     */
    fun loadWordBoundaries(): List<WordBoundary> {
        return try {
            val json = loadJsonFromRaw()
            if (json.isNotEmpty()) {
                val boundaries = Gson().fromJson<List<WordBoundary>>(
                    json,
                    object : TypeToken<List<WordBoundary>>() {}.type
                ) ?: emptyList()
                Log.d(TAG, "Loaded ${boundaries.size} word boundaries from JSON")
                boundaries
            } else {
                Log.w(TAG, "JSON file not found or empty")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading word boundaries from JSON", e)
            emptyList()
        }
    }
    
    /**
     * Load word boundaries from database
     */
    fun loadWordBoundariesFromDatabase(): List<WordBoundary> {
        return try {
            val dbFile = copyDatabaseToInternal()
            if (!dbFile.exists()) {
                Log.w(TAG, "Database file not found")
                return emptyList()
            }
            
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            
            // First, check what tables exist in the database and show sample data
            try {
                val tablesQuery = "SELECT name FROM sqlite_master WHERE type='table'"
                val tablesCursor = db.rawQuery(tablesQuery, null)
                Log.d(TAG, "Available tables in database:")
                while (tablesCursor.moveToNext()) {
                    val tableName = tablesCursor.getString(0)
                    Log.d(TAG, "  - $tableName")
                    
                    // For each table, show its schema
                    try {
                        val schemaCursor = db.rawQuery("PRAGMA table_info($tableName)", null)
                        Log.d(TAG, "    Columns:")
                        while (schemaCursor.moveToNext()) {
                            val columnName = schemaCursor.getString(1)
                            val columnType = schemaCursor.getString(2)
                            Log.d(TAG, "      $columnName ($columnType)")
                        }
                        schemaCursor.close()
                        
                        // Show sample data from table
                        if (tableName != "sqlite_sequence") {
                            try {
                                val dataCursor = db.rawQuery("SELECT * FROM $tableName LIMIT 5", null)
                                Log.d(TAG, "    Sample data:")
                                var rowCount = 0
                                while (dataCursor.moveToNext() && rowCount < 3) {
                                    val row = StringBuilder("      Row $rowCount: ")
                                    for (i in 0 until dataCursor.columnCount) {
                                        if (i > 0) row.append(", ")
                                        row.append("${dataCursor.getColumnName(i)}=")
                                        when (dataCursor.getType(i)) {
                                            android.database.Cursor.FIELD_TYPE_INTEGER -> row.append(dataCursor.getInt(i))
                                            android.database.Cursor.FIELD_TYPE_FLOAT -> row.append(dataCursor.getFloat(i))
                                            android.database.Cursor.FIELD_TYPE_STRING -> row.append("'${dataCursor.getString(i)}'")
                                            android.database.Cursor.FIELD_TYPE_NULL -> row.append("null")
                                            else -> row.append(dataCursor.getString(i))
                                        }
                                    }
                                    Log.d(TAG, row.toString())
                                    rowCount++
                                }
                                dataCursor.close()
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to sample data from $tableName: $e")
                            }
                        }
                        
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get schema for table $tableName: $e")
                    }
                }
                tablesCursor.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to list tables: $e")
            }
            
            val boundaries = mutableListOf<WordBoundary>()
            
            // Query the quranwordbounds.sqlite database structure using actual column names
            val queries = listOf(
                // Use the actual column names from quranwordbounds.sqlite
                "SELECT page_number, line_number, word_position, sura_number, ayah_number, min_x, min_y, (max_x - min_x) as width, (max_y - min_y) as height FROM word_bounds ORDER BY page_number, line_number, word_position",
                // Fallback with all columns
                "SELECT page_number, line_number, line_position, sura_number, ayah_number, min_x, min_y, max_x, max_y FROM word_bounds ORDER BY page_number, line_number, line_position"
            )
            
            for (query in queries) {
                try {
                    val cursor = db.rawQuery(query, null)
                    
                    while (cursor.moveToNext()) {
                        // Handle different column counts and names flexibly
                        val columnCount = cursor.columnCount
                        val boundary = when {
                            columnCount >= 9 -> {
                                // Standard format: page_number, line_number, word_position, sura_number, ayah_number, min_x, min_y, width, height
                                WordBoundary(
                                    page = cursor.getInt(0),
                                    line = cursor.getInt(1),
                                    word = cursor.getInt(2),
                                    surah = cursor.getInt(3),
                                    ayah = cursor.getInt(4),
                                    x = cursor.getFloat(5),
                                    y = cursor.getFloat(6),
                                    width = cursor.getFloat(7),
                                    height = cursor.getFloat(8),
                                    text = if (columnCount > 9) cursor.getString(9) else null
                                )
                            }
                            columnCount >= 8 -> {
                                // Format with min/max coordinates: page_number, line_number, line_position, sura_number, ayah_number, min_x, min_y, max_x, max_y
                                val minX = cursor.getFloat(5)
                                val minY = cursor.getFloat(6)
                                val maxX = cursor.getFloat(7)
                                val maxY = cursor.getFloat(8)
                                WordBoundary(
                                    page = cursor.getInt(0),
                                    line = cursor.getInt(1),
                                    word = cursor.getInt(2), // Use line_position as word index
                                    surah = cursor.getInt(3),
                                    ayah = cursor.getInt(4),
                                    x = minX,
                                    y = minY,
                                    width = maxX - minX,
                                    height = maxY - minY,
                                    text = null
                                )
                            }
                            else -> null
                        }
                        
                        boundary?.let { boundaries.add(it) }
                    }
                    
                    cursor.close()
                    
                    if (boundaries.isNotEmpty()) {
                        Log.d(TAG, "Loaded ${boundaries.size} word boundaries from database using query: $query")
                        break
                    }
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Query failed: $query")
                    Log.w(TAG, e.toString())
                }
            }
            
            db.close()
            boundaries
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading word boundaries from database", e)
            emptyList()
        }
    }
    
    /**
     * Get database schema information for debugging
     */
    fun getDatabaseSchema(): String {
        return try {
            val dbFile = copyDatabaseToInternal()
            if (!dbFile.exists()) return "Database file not found"
            
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val schema = StringBuilder()
            
            // Get all tables
            val tablesCursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
            while (tablesCursor.moveToNext()) {
                val tableName = tablesCursor.getString(0)
                schema.append("Table: $tableName\n")
                
                // Get table info
                val infoCursor = db.rawQuery("PRAGMA table_info($tableName)", null)
                while (infoCursor.moveToNext()) {
                    val columnName = infoCursor.getString(1)
                    val columnType = infoCursor.getString(2)
                    schema.append("  - $columnName ($columnType)\n")
                }
                infoCursor.close()
                schema.append("\n")
            }
            tablesCursor.close()
            
            db.close()
            schema.toString()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting database schema", e)
            "Error reading database schema: ${e.message}"
        }
    }
    
    private fun loadJsonFromRaw(): String {
        return try {
            val resourceId = context.resources.getIdentifier(
                JSON_FILE.substringBeforeLast('.'),
                "raw",
                context.packageName
            )
            
            if (resourceId != 0) {
                context.resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
            } else {
                Log.w(TAG, "JSON resource not found: $JSON_FILE")
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading JSON from raw resources", e)
            ""
        }
    }
    
    /**
     * Debug database structure and content
     */
    fun debugDatabaseStructure(): Unit {
        try {
            Log.d(TAG, "🔧 Starting database debugging...")
            val dbFile = copyDatabaseToInternal()
            if (!dbFile.exists()) {
                Log.w(TAG, "❌ Database file not found at: ${dbFile.absolutePath}")
                return
            }
            
            Log.d(TAG, "✅ Database file found at: ${dbFile.absolutePath}, size: ${dbFile.length()} bytes")
            
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            
            // Get all tables
            val tablesCursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
            Log.d(TAG, "📊 Tables in database:")
            while (tablesCursor.moveToNext()) {
                val tableName = tablesCursor.getString(0)
                Log.d(TAG, "  - $tableName")
                
                // Get table schema
                val schemaCursor = db.rawQuery("PRAGMA table_info($tableName)", null)
                Log.d(TAG, "    Columns:")
                while (schemaCursor.moveToNext()) {
                    val columnName = schemaCursor.getString(1)
                    val columnType = schemaCursor.getString(2)
                    Log.d(TAG, "      $columnName ($columnType)")
                }
                schemaCursor.close()
                
                // Get row count
                try {
                    val countCursor = db.rawQuery("SELECT COUNT(*) FROM $tableName", null)
                    if (countCursor.moveToFirst()) {
                        val rowCount = countCursor.getInt(0)
                        Log.d(TAG, "    Row count: $rowCount")
                    }
                    countCursor.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to count rows in $tableName: $e")
                }
                
                // Show sample data
                if (tableName != "sqlite_sequence") {
                    try {
                        val dataCursor = db.rawQuery("SELECT * FROM $tableName LIMIT 3", null)
                        Log.d(TAG, "    Sample data:")
                        var rowCount = 0
                        while (dataCursor.moveToNext() && rowCount < 3) {
                            val row = StringBuilder("      Row $rowCount: ")
                            for (i in 0 until dataCursor.columnCount) {
                                if (i > 0) row.append(", ")
                                row.append("${dataCursor.getColumnName(i)}=")
                                when (dataCursor.getType(i)) {
                                    android.database.Cursor.FIELD_TYPE_INTEGER -> row.append(dataCursor.getInt(i))
                                    android.database.Cursor.FIELD_TYPE_FLOAT -> row.append(dataCursor.getFloat(i))
                                    android.database.Cursor.FIELD_TYPE_STRING -> row.append("'${dataCursor.getString(i)}'")
                                    android.database.Cursor.FIELD_TYPE_NULL -> row.append("null")
                                    else -> row.append(dataCursor.getString(i))
                                }
                            }
                            Log.d(TAG, row.toString())
                            rowCount++
                        }
                        dataCursor.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to sample data from $tableName: $e")
                    }
                }
            }
            tablesCursor.close()
            
            db.close()
            Log.d(TAG, "🔧 Database debugging completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error debugging database structure", e)
        }
    }

    /**
     * Get all words for a specific page from the database
     */
    fun getWordsForPage(pageNumber: Int): List<WordBoundary> {
        return try {
            Log.d(TAG, "🔍 Querying words for page $pageNumber")
            
            // Run database debugging on first call
            if (pageNumber == 1) {
                debugDatabaseStructure()
            }
            
            val dbFile = copyDatabaseToInternal()
            if (!dbFile.exists()) {
                Log.w(TAG, "Database file not found")
                return emptyList()
            }
            
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val words = mutableListOf<WordBoundary>()
            
            // First, let's check what page numbers actually exist in the database
            val pageCheckCursor = db.rawQuery("SELECT DISTINCT page_number FROM word_bounds ORDER BY page_number LIMIT 10", null)
            val availablePages = mutableListOf<Int>()
            while (pageCheckCursor.moveToNext()) {
                availablePages.add(pageCheckCursor.getInt(0))
            }
            pageCheckCursor.close()
            Log.d(TAG, "📊 Available pages in database: ${availablePages.joinToString(", ")}")
            
            // Also check total count
            val countCursor = db.rawQuery("SELECT COUNT(*) FROM word_bounds", null)
            if (countCursor.moveToFirst()) {
                val totalWords = countCursor.getInt(0)
                Log.d(TAG, "📊 Total words in database: $totalWords")
            }
            countCursor.close()
            
            // Try the correct query first based on the actual database schema
            val queries = listOf(
                "SELECT page_number, line_number, word_position, sura_number, ayah_number, min_x, min_y, (max_x - min_x) as width, (max_y - min_y) as height, arabic_word FROM word_bounds WHERE page_number = ? ORDER BY line_number, word_position",
                "SELECT page_number, line_number, line_position, sura_number, ayah_number, min_x, min_y, max_x, max_y FROM word_bounds WHERE page_number = ? ORDER BY line_number, line_position"
            )
            
            for (query in queries) {
                Log.d(TAG, "🎯 Trying query for page $pageNumber: ${query.substring(0, 50)}...")
                try {
                    val cursor = db.rawQuery(query, arrayOf(pageNumber.toString()))
                    
                    while (cursor.moveToNext()) {
                        // Handle different column counts and names flexibly
                        val columnCount = cursor.columnCount
                        val boundary = when {
                            columnCount >= 9 -> {
                                // Standard format: page_number, line_number, word_position, sura_number, ayah_number, min_x, min_y, width, height
                                WordBoundary(
                                    page = cursor.getInt(0),
                                    line = cursor.getInt(1),
                                    word = cursor.getInt(2),
                                    surah = cursor.getInt(3),
                                    ayah = cursor.getInt(4),
                                    x = cursor.getFloat(5),
                                    y = cursor.getFloat(6),
                                    width = cursor.getFloat(7),
                                    height = cursor.getFloat(8),
                                    text = if (columnCount > 9) cursor.getString(9) else null
                                )
                            }
                            columnCount >= 8 -> {
                                // Format with min/max coordinates: page_number, line_number, line_position, sura_number, ayah_number, min_x, min_y, max_x, max_y
                                val minX = cursor.getFloat(5)
                                val minY = cursor.getFloat(6)
                                val maxX = cursor.getFloat(7)
                                val maxY = cursor.getFloat(8)
                                WordBoundary(
                                    page = cursor.getInt(0),
                                    line = cursor.getInt(1),
                                    word = cursor.getInt(2), // Use line_position as word index
                                    surah = cursor.getInt(3),
                                    ayah = cursor.getInt(4),
                                    x = minX,
                                    y = minY,
                                    width = maxX - minX,
                                    height = maxY - minY,
                                    text = null
                                )
                            }
                            else -> null
                        }
                        
                        boundary?.let { words.add(it) }
                    }
                    
                    cursor.close()
                    
                    if (words.isNotEmpty()) {
                        Log.d(TAG, "✅ Found ${words.size} words for page $pageNumber using query: ${query.substring(0, 50)}...")
                        break
                    } else {
                        Log.d(TAG, "⚪ No words found with this query for page $pageNumber")
                    }
                    
                } catch (e: Exception) {
                    Log.w(TAG, "❌ Query failed for page $pageNumber: ${query.substring(0, 50)}...", e)
                    // Try next query
                }
            }
            
            db.close()
            Log.d(TAG, "📊 Final result: ${words.size} words for page $pageNumber")
            words
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting words for page $pageNumber from database", e)
            emptyList()
        }
    }

    private fun copyDatabaseToInternal(): File {
        val dbFile = File(context.filesDir, DATABASE_NAME)
        
        if (!dbFile.exists()) {
            try {
                val resourceId = context.resources.getIdentifier(
                    "quranwordbounds",
                    "raw",
                    context.packageName
                )
                
                if (resourceId == 0) {
                    Log.w(TAG, "Database resource not found: $DATABASE_NAME")
                    return dbFile
                }
                
                val inputStream: InputStream = context.resources.openRawResource(resourceId)
                val outputStream = FileOutputStream(dbFile)
                
                val buffer = ByteArray(1024)
                var length: Int
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }
                
                outputStream.flush()
                outputStream.close()
                inputStream.close()
                
                Log.d(TAG, "Database copied to internal storage: ${dbFile.absolutePath}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error copying database to internal storage", e)
            }
        }
        
        return dbFile
    }
}
