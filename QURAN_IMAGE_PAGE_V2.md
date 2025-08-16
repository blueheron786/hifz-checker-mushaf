# QuranImagePage - Enhanced Quran Page Reader

This is a new implementation of the Quran page reader that replaces the old QuranImagePageAdapter with enhanced features and better image sources.

## Features

### 🖼️ Enhanced Image Sources
- **Qpc14LinesV2**: High-quality page images from King Saud University
- **BlackImagesWordByWord**: Individual word images for precise reveal functionality
- **Intelligent Fallback**: Automatic fallback to alternative sources if primary fails

### 💾 Advanced Caching System
- **Memory Cache**: LRU cache for frequently accessed images
- **Disk Cache**: Persistent storage with automatic cleanup
- **Structured Storage**: 
  - Page images: `quran_pages/page_001.png`
  - Word images: `quran_words/surah/ayah/word.png` (e.g., `1/1/1.png` for first word of Fatiha)

### 🎯 Word-Level Interaction
- **Tap to Reveal**: Touch any word area to reveal it temporarily
- **Smart Coordinate Mapping**: Accurate touch-to-word mapping
- **Visual Feedback**: Yellow border highlighting for revealed words
- **Auto-Hide**: Words automatically hide after 5 seconds

### 🔄 Backward Compatibility
- **Migration System**: Seamless transition from old adapter
- **Fallback Support**: Can switch back to original adapter if needed
- **Cache Migration**: Preserves existing cached images

## Architecture

### Core Components

1. **QuranImagePage**: Custom ImageView component
   - Handles page display and word interaction
   - Manages image loading and caching
   - Provides gesture detection

2. **QuranImagePageAdapterV2**: RecyclerView adapter
   - Manages multiple page views
   - Handles view recycling efficiently
   - Coordinates masked mode updates

3. **QuranImageCache**: Intelligent caching system
   - Memory and disk caching
   - LRU eviction policy
   - Cache statistics and cleanup

4. **QuranImagePageMigration**: Migration helper
   - Manages adapter version selection
   - Handles cache migration
   - Provides debugging utilities

### Image Sources

#### Page Images (Qpc14LinesV2)
```
https://qpc.ksu.edu.sa/pages/qpc14linesv2/page{001-604}.png
```

#### Word Images (BlackImagesWordByWord)
```
https://api.qurancdn.com/api/qdc/words/{surah}/{ayah}/{word}.png
```

### Cache Structure
```
app_cache/
├── quran_pages/
│   ├── page_001.png
│   ├── page_002.png
│   └── ...
└── quran_words/
    ├── 1/1/1.png     # First word of Fatiha
    ├── 1/1/2.png     # Second word of Fatiha
    └── ...
```

## Usage

### Basic Usage
```kotlin
val quranImagePage = QuranImagePage(context)
quranImagePage.setPage(pageNumber, pageInfo)
quranImagePage.setMaskedMode(true)
quranImagePage.setOnWordClickListener { word ->
    Log.d("Word", "Clicked: ${word.text}")
}
```

### In Fragment
```kotlin
// The fragment automatically uses the new adapter by default
// Migration is handled automatically

// Force specific adapter version (for testing)
QuranImagePageMigration.enableV2Adapter(context)  // Use new adapter
QuranImagePageMigration.fallbackToV1Adapter(context)  // Use old adapter
```

## Configuration

### Enabling/Disabling V2 Adapter
```kotlin
// Enable new adapter (default)
QuranImagePageMigration.enableV2Adapter(context)

// Fallback to original adapter
QuranImagePageMigration.fallbackToV1Adapter(context)

// Check current setting
val isUsingV2 = QuranImagePageMigration.shouldUseV2Adapter(context)
```

### Cache Management
```kotlin
val imageCache = QuranImageCache(context)

// Get cache statistics
val stats = imageCache.getCacheStats()

// Clear specific caches
imageCache.clearMemoryCache()
imageCache.clearPageDiskCache()
imageCache.clearWordDiskCache()

// Clear all caches
imageCache.clearAllCaches()
```

## Debugging

### Debug Utilities
```kotlin
// Test connectivity to image sources
QuranImagePageDebugger.runFullConnectivityTest(context)

// Print cache statistics
QuranImagePageDebugger.printCacheStats(context)

// Force adapter version for testing
QuranImagePageDebugger.forceAdapterVersion(context, useV2 = true)

// Clear all caches for testing
QuranImagePageDebugger.clearAllCachesForTesting(context)
```

### Debug Mode
When `BuildConfig.DEBUG` is true, the app automatically:
- Tests connectivity to image sources on startup
- Logs detailed information about image loading
- Provides enhanced error messages

## Performance

### Memory Management
- **LRU Cache**: 50MB memory cache with automatic eviction
- **Disk Cache**: 500MB disk cache with cleanup when exceeded
- **Bitmap Recycling**: Proper cleanup to prevent memory leaks

### Network Optimization
- **Parallel Downloads**: Multiple image downloads can occur simultaneously
- **Timeout Settings**: Reasonable timeouts to prevent hanging
- **User-Agent**: Proper identification for server compatibility

### Loading Performance
- **Cache-First**: Always check cache before downloading
- **Background Loading**: Network operations on background threads
- **Placeholder Support**: Immediate feedback while loading

## Migration

### From Old Adapter
The migration is automatic:
1. App checks migration preference on startup
2. V2 adapter is used by default for new installations
3. Existing cache data is preserved and compatible
4. Fallback option available if issues occur

### Cache Migration
- Existing page cache is compatible with V2
- No data loss during migration
- Migration status is tracked to avoid repeat operations

## Error Handling

### Network Errors
- Graceful fallback to cached images
- Retry logic for failed downloads
- Placeholder display for unavailable images

### Cache Errors
- Automatic cleanup of corrupted cache files
- Memory pressure handling
- Disk space management

## Future Enhancements

### Planned Features
- [ ] Progressive image loading
- [ ] Preloading for adjacent pages
- [ ] Custom image quality settings
- [ ] Offline mode indicators
- [ ] Advanced word highlighting options

### API Improvements
- [ ] Batch word image downloads
- [ ] WebP image support
- [ ] CDN integration
- [ ] Image compression options

---

## Technical Notes

### Dependencies
- Kotlin Coroutines for async operations
- RecyclerView for efficient scrolling
- ViewPager2 for page navigation
- LruCache for memory management

### Compatibility
- Minimum Android API: Same as existing app
- Backward compatible with existing data
- Graceful degradation on older devices

### Testing
- Unit tests for cache operations
- Integration tests for image loading
- Performance tests for memory usage
- Network connectivity tests
