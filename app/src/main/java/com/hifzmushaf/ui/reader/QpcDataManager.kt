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
        private const val DATABASE_NAME = "qpc15linesv2.db"
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
            val boundaries = mutableListOf<WordBoundary>()
            
            // Try different possible table structures
            val queries = listOf(
                // Common QPC database structures
                "SELECT page, line, word, surah, ayah, x, y, width, height, text FROM word_boundaries ORDER BY page, line, word",
                "SELECT page_number, line_number, word_number, surah_number, ayah_number, x_coordinate, y_coordinate, word_width, word_height, word_text FROM words ORDER BY page_number, line_number, word_number",
                "SELECT * FROM coordinates ORDER BY page, line, word",
                "SELECT * FROM word_coords ORDER BY page_id, line_id, word_id"
            )
            
            for (query in queries) {
                try {
                    val cursor = db.rawQuery(query, null)
                    
                    while (cursor.moveToNext()) {
                        val boundary = WordBoundary(
                            page = cursor.getInt(0),
                            line = cursor.getInt(1),
                            word = cursor.getInt(2),
                            surah = cursor.getInt(3),
                            ayah = cursor.getInt(4),
                            x = cursor.getFloat(5),
                            y = cursor.getFloat(6),
                            width = cursor.getFloat(7),
                            height = cursor.getFloat(8),
                            text = if (cursor.columnCount > 9) cursor.getString(9) else null
                        )
                        boundaries.add(boundary)
                    }
                    
                    cursor.close()
                    
                    if (boundaries.isNotEmpty()) {
                        Log.d(TAG, "Loaded ${boundaries.size} word boundaries from database using query: $query")
                        break
                    }
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Query failed: $query", e)
                    // Try next query
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
    
    private fun copyDatabaseToInternal(): File {
        val dbFile = File(context.filesDir, DATABASE_NAME)
        
        if (!dbFile.exists()) {
            try {
                val resourceId = context.resources.getIdentifier(
                    "qpc15linesv2",
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
