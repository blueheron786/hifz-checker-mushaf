# Mushaf Reader Implementation

This implementation creates an image-based Quran reader that can work with QPC (Quran Page Coordinates) data.

## Files Added

1. **MushafReaderFragment.kt** - Main fragment for image-based reading
2. **MushafPageAdapter.kt** - Adapter for handling image pages with touch interactions
3. **QpcDataManager.kt** - Utility for loading word boundaries from JSON/database
4. **DatabaseSchemaAnalyzer.kt** - Helper for analyzing database structure
5. **DatabaseDebugFragment.kt** - Debug interface for examining database
6. Layout files for the new fragments

## Expected Data Structure

### Database (qpc15linesv2.db)
The system expects either:
- **Table with word boundaries**: columns like `page`, `line`, `word`, `surah`, `ayah`, `x`, `y`, `width`, `height`, `text`
- **Table with page images**: columns containing image blob data
- **Multiple tables**: The analyzer will help identify the structure

### JSON (qpc-v2.json)
Expected format:
```json
[
  {
    "page": 1,
    "line": 1,
    "word": 1,
    "surah": 1,
    "ayah": 1,
    "x": 100.5,
    "y": 200.2,
    "width": 50.0,
    "height": 30.0,
    "text": "بِسْمِ"
  },
  ...
]
```

### Page Images
Expected locations (in order of preference):
1. `assets/pages/page_001.png` (or .jpg) - Local assets (highest priority)
2. `assets/pages/001.png` - Alternative local format  
3. Embedded in database as blob data
4. **Downloaded from URLs** - Multiple sources attempted:
   - `https://www.searchtruth.com/quran/images1/XXX.gif`
   - `https://quran.com/images/pages/page_XXX.png`
   - `https://images.quran.com/pages/page_XXX.png`
   - `https://www.searchtruth.com/quran/images2/large/page-XXX.gif`
   - `https://qurancomplex.gov.sa/api/v1/pages/X.png`

**Note**: The app now automatically downloads Quran page images from online sources if they're not found locally. Downloaded images are cached for future use.

## Setup Instructions

1. **Add the database file**:
   - Place `qpc15linesv2.db` in `app/src/main/res/raw/`
   - No renaming needed - use the exact filename

2. **Add the JSON file**:
   - Place `qpc-v2.json` in `app/src/main/res/raw/`
   - Rename to `qpc_v2.json`

3. **Add page images** (if not in database):
   - Create `app/src/main/assets/pages/` directory
   - Add images named `page_001.png`, `page_002.png`, etc.

4. **Test the implementation**:
   - Use DatabaseDebugFragment to analyze the database structure
   - Check logs for loading errors
   - Verify images display correctly

## Features

### Hidden Word Reveal System
- **Words are initially invisible** - hidden behind white masks
- **Tap to reveal** - touch any word area to make it visible
- **5-second display** - revealed words automatically hide after 5 seconds
- **Yellow highlight border** - revealed words show with subtle highlighting
- **Mode toggle** - tap header to switch between masked (🔒) and normal (👁️) modes

### Interactive Learning Experience
- **Memory training** - forces active recall by hiding text
- **Precise word detection** - uses coordinate data for accurate touch detection
- **Visual feedback** - clear indication of revealed words
- **Progressive disclosure** - reveal only what you're studying
- Uses coordinate data for precise word detection
- Temporary highlight with automatic removal

### Page Navigation
- Swipe between pages (RTL layout)
- Header shows current page and surah info
- Automatic progress tracking

### Fallback Support
- Works without word boundaries (basic page navigation)
- Graceful degradation if images not found
- Comprehensive error logging

## Navigation

Add navigation actions to access the new reader:
- `action_surahListFragment_to_mushafReaderFragment`
- `action_surahListFragment_to_databaseDebugFragment` (for debugging)

## Next Steps

1. **Add the data files** to `res/raw/`
2. **Run DatabaseDebugFragment** to understand the schema
3. **Update QpcDataManager** if schema doesn't match expectations
4. **Add page images** if they're not in the database
5. **Test and refine** the word detection accuracy

## Debugging

Use the DatabaseDebugFragment to:
- See all tables in the database
- Examine column names and types
- View sample data
- Identify the correct table structure

The system will log detailed information about:
- Database schema discovery
- Image loading attempts
- Word boundary loading
- Touch coordinate mapping

This implementation provides a solid foundation for an image-based Quran reader with word-level interactivity.

## Word Reveal Learning System

The **hidden word reveal feature** transforms the Mushaf reader into an interactive learning tool:

1. **Start in Masked Mode (🔒)**: All words are hidden behind white overlays
2. **Tap to Reveal**: Touch any word area to reveal it temporarily  
3. **5-Second Timer**: Revealed words automatically hide after 5 seconds
4. **Visual Feedback**: Revealed words show with yellow border highlighting
5. **Mode Toggle**: Tap the header to switch between masked and normal viewing modes

This system is perfect for:
- **Memory training and Hifz practice**
- **Reading comprehension exercises** 
- **Word-by-word study sessions**
- **Progressive learning with instant feedback**

The implementation uses advanced bitmap manipulation to create smooth, responsive word reveals while maintaining high image quality and performance.
