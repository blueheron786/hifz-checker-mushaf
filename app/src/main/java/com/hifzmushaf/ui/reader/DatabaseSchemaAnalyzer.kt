package com.hifzmushaf.ui.reader

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class DatabaseSchemaAnalyzer(private val context: Context) {
    
    companion object {
        private const val TAG = "DatabaseAnalyzer"
    }
    
    fun analyzeDatabase(): DatabaseAnalysisResult {
        val result = DatabaseAnalysisResult()
        
        try {
            val dbFile = copyDatabaseToInternal()
            if (!dbFile.exists()) {
                result.error = "Database file not found"
                return result
            }
            
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            
            // Get all tables
            val tablesCursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
            while (tablesCursor.moveToNext()) {
                val tableName = tablesCursor.getString(0)
                if (tableName != "android_metadata" && tableName != "sqlite_sequence") {
                    result.tables.add(tableName)
                    
                    // Get table structure
                    val tableInfo = TableInfo(tableName)
                    val infoCursor = db.rawQuery("PRAGMA table_info($tableName)", null)
                    while (infoCursor.moveToNext()) {
                        val columnName = infoCursor.getString(1)
                        val columnType = infoCursor.getString(2)
                        tableInfo.columns.add(ColumnInfo(columnName, columnType))
                    }
                    infoCursor.close()
                    
                    // Get row count
                    try {
                        val countCursor = db.rawQuery("SELECT COUNT(*) FROM $tableName", null)
                        if (countCursor.moveToFirst()) {
                            tableInfo.rowCount = countCursor.getInt(0)
                        }
                        countCursor.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not get row count for $tableName", e)
                    }
                    
                    // Sample first few rows
                    try {
                        val sampleCursor = db.rawQuery("SELECT * FROM $tableName LIMIT 3", null)
                        while (sampleCursor.moveToNext()) {
                            val row = mutableListOf<String>()
                            for (i in 0 until sampleCursor.columnCount) {
                                val value = when (sampleCursor.getType(i)) {
                                    android.database.Cursor.FIELD_TYPE_NULL -> "NULL"
                                    android.database.Cursor.FIELD_TYPE_INTEGER -> sampleCursor.getInt(i).toString()
                                    android.database.Cursor.FIELD_TYPE_FLOAT -> sampleCursor.getFloat(i).toString()
                                    android.database.Cursor.FIELD_TYPE_STRING -> sampleCursor.getString(i)
                                    android.database.Cursor.FIELD_TYPE_BLOB -> "[BLOB ${sampleCursor.getBlob(i)?.size ?: 0} bytes]"
                                    else -> sampleCursor.getString(i) ?: "NULL"
                                }
                                row.add(value)
                            }
                            tableInfo.sampleRows.add(row)
                        }
                        sampleCursor.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not get sample data for $tableName", e)
                    }
                    
                    result.tableInfos[tableName] = tableInfo
                }
            }
            tablesCursor.close()
            
            db.close()
            
        } catch (e: Exception) {
            result.error = "Error analyzing database: ${e.message}"
            Log.e(TAG, "Error analyzing database", e)
        }
        
        return result
    }
    
    private fun copyDatabaseToInternal(): File {
        val dbFile = File(context.filesDir, "qpc15linesv2.db")
        
        if (!dbFile.exists()) {
            try {
                val resourceId = context.resources.getIdentifier(
                    "qpc15linesv2",
                    "raw",
                    context.packageName
                )
                
                if (resourceId != 0) {
                    val inputStream = context.resources.openRawResource(resourceId)
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
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error copying database", e)
            }
        }
        
        return dbFile
    }
}

data class DatabaseAnalysisResult(
    var error: String? = null,
    val tables: MutableList<String> = mutableListOf(),
    val tableInfos: MutableMap<String, TableInfo> = mutableMapOf()
)

data class TableInfo(
    val name: String,
    val columns: MutableList<ColumnInfo> = mutableListOf(),
    var rowCount: Int = 0,
    val sampleRows: MutableList<List<String>> = mutableListOf()
)

data class ColumnInfo(
    val name: String,
    val type: String
)
