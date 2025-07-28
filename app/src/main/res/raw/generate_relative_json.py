import json

def convert_to_absolute(pages_path, output_path, quran_src_path, quran_text_path):
    letters_with_sukoon = [
        ["اْ", "ا"], ["بْ", "ب"], ["تْ", "ت"], ["ثْ", "ث"], ["جْ", "ج"],
        ["حْ", "ح"], ["خْ", "خ"], ["دْ", "د"], ["ذْ", "ذ"], ["رْ", "ر"],
        ["زْ", "ز"], ["سْ", "س"], ["شْ", "ش"], ["صْ", "ص"], ["ضْ", "ض"],
        ["طْ", "ط"], ["ظْ", "ظ"], ["عْ", "ع"], ["غْ", "غ"], ["فْ", "ف"],
        ["قْ", "ق"], ["كْ", "ك"], ["لْ", "ل"], ["مْ", "م"], ["نْ", "ن"],
        ["هْ", "ه"], ["وْ", "و"], ["يْ", "ي"], ["ءْ", "ء"], ["ؤْ", "ؤ"],
        ["إْ", "إ"], ["ىْ", "ى"], ["أْ", "أ"], ["ئْ", "ئ"]
    ]

    ###
    # REMOVE stuff we can't RENDER correctly. This almost invisible tiny dot character
    # is big trouble. Can't see it unless you add a waw or alif...
    replace_these = [
        ["و۟", "و"], ["ا۟", "ا"],
    ]

    with open(quran_src_path, 'r', encoding='utf-8') as f:
        quran_lines = f.read()
    
    for glyph in replace_these:
        quran_lines = quran_lines.replace(glyph[0], glyph[1])

    with open(quran_text_path, 'w', encoding='utf-8') as f:
        quran_lines = f.write(quran_lines)

    # Load the Quran text to count total lines
    with open(quran_text_path, 'r', encoding='utf-8') as f:
        quran_lines = [line.strip() for line in f if line.strip()]
    
    total_lines = len(quran_lines)
    print(f"Total Quran lines: {total_lines}")

    # Load the existing pages.json
    with open(pages_path, 'r', encoding='utf-8') as f:
        pages = json.load(f)
    
    # Process each page to convert to absolute line numbers
    current_line = 0
    new_pages = []
    
    for page in pages:
        new_page = []
        # Each page is a list of lists containing surah ranges
        for surah_ranges in page:
            new_surah_ranges = []
            for surah_range in surah_ranges:
                surah = surah_range['surah']
                start_relative = surah_range['start']
                end_relative = surah_range['end']
                
                # Calculate absolute positions (1-based)
                start_absolute = current_line + 1
                end_absolute = current_line + (end_relative - start_relative + 1)
                
                new_surah_ranges.append({
                    'surah': surah,
                    'start': start_absolute,
                    'end': end_absolute
                })
                
                current_line = end_absolute
            
            new_page.append(new_surah_ranges)
        
        new_pages.append(new_page)
    
    # Save the converted file
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(new_pages, f, indent=2, ensure_ascii=False)
    
    print(f"Converted file saved to {output_path}")
    print(f"Final line number: {current_line} (should match total lines: {total_lines})")

# Usage:
convert_to_absolute(
    pages_path='pages.json',
    output_path='pages_absolute.json',
    quran_src_path='D:\\temp\\quran-uthmani.txt',
    quran_text_path='quran_text.txt',
)