package com.brouken.player.utils

import java.util.regex.Pattern

object NameCleaner {

    // 1. Season/Episode patterns (capture title group 1, S/E group 2, 3)
    // S01E01, S01 E01, s1e1
    private val PATTERN_S_E = Pattern.compile("(.+?)[\\s._-]+[Ss](\\d+)[\\s._-]*[Ee](\\d+)", Pattern.CASE_INSENSITIVE)
    // 1x01, 1x1
    private val PATTERN_X = Pattern.compile("(.+?)[\\s._-]+(\\d+)[xX](\\d+)")
    // Episode 1, Ep 1, E1
    private val PATTERN_EP = Pattern.compile("(.+?)[\\s._-]+(?:Episode|Ep|E)[\\s._-]*(\\d+)", Pattern.CASE_INSENSITIVE)
    
    // Anime Absolute: "Show - 01" or "Show - 01 - Title"
    // Capture group 1: Title, group 2: Episode
    private val PATTERN_ANIME_ABS = Pattern.compile("^(.+?)\\s*-\\s*(\\d{1,4})(?:\\s*-|\\s*\\[|\\s*\\(|$)", Pattern.CASE_INSENSITIVE)

    // Loose Absolute: "Show.1080.1999" (Show, Ep, Year) or "Show 100"
    // Finds a separated number that is followed by Year or specific tags, or end of string
    // Negative lookahead (?![pPi]) prevents matching "1080p" as episode 1080
    // Group 1: Title, Group 2: Episode
    private val PATTERN_LOOSE_ABSOLUTE = Pattern.compile("^(.+?)[\\s._-]+(\\d{1,4})(?![pPi\\d])(?:[\\s._-]+|$)", Pattern.CASE_INSENSITIVE)

    // Year pattern: "Movie Title (2023)" or "Movie.Title.2023."
    private val PATTERN_YEAR = Pattern.compile("(.+?)[\\s._\\-(]+(\\d{4})[)\\s._-]")

    // Junk Tags to strip (Case Insensitive)
    // CRITICAL: All patterns must be word-boundary or separator-based to prevent matching inside words!
    private val JUNK_REGEX = listOf(
        // Resolutions - must be followed by space or separator or end
        "\\b(2160|1080|720|480|576)[pP]\\b",
        "\\b(4|8)[kK]\\b", "\\b(UHD|HD|SD)\\b",
        
        // Sources - must be whole words with boundaries
        "\\b(BluRay|BDRip|BRRip|BD|DVD|DVDRip|DVDScr|R5)\\b",
        "\\b(WEB-DL|WEBRip|WEB|HDTV|PDTV|CAM|TS|TC|REMUX)\\b",
        
        // Codecs - must be whole words with boundaries
        "\\b((x|h)\\.?264|(x|h)\\.?265|HEVC|AVC|DivX|XviD|MPEG)\\b",
        
        // Audio - must be whole words with boundaries
        "\\b(TrueHD|DTS-HD|DTS|Atmos|DD(\\+|P)?\\s*5\\.1|DD|AAC|AC3|EAC3|FLAC|MP3)\\b",
        "\\b(5\\.1|7\\.1|2\\.0)\\b",
        
        // HDR / Video specs - must be whole words with boundaries
        "\\b(HDR(10)?(\\+)?|Dolby\\s*Vision|DV|10bit|12bit|Hi10P|SDR)\\b",
        "\\b(AI\\s*Upscale|Upscaled)\\b",
        
        // Release Types / Misc - must be whole words with boundaries
        "\\b(REPACK|PROPER|REAL|INTERNAL|FESTIVAL|STV|LIMITED|UNRATED|DC|EXTENDED|REMASTERED|COMPLETE|RESTORED|UNCUT|DIRECTOR'?S\\s*CUT)\\b",
        
        // Languages - must be whole words with boundaries
        "\\b(MULTI|DUAL|LATINO|FRENCH|GERMAN|SPANISH|ITA|RUS|JAP|ENG|SUB|DUB)\\b",
        
        // Groups / Hash - must be in brackets at boundaries
        "[\\s._-]\\[[^\\]]+\\]",  // [HorribleSubs] with separator before
        "[\\s._-]\\([^\\)]+\\)",   // (Source) with separator before
        "-[\\w\\d]+$"        // -Group at end (alphanumeric only)
    ).joinToString("|") // Combine with OR

    private val JUNK_PATTERN = Pattern.compile(JUNK_REGEX, Pattern.CASE_INSENSITIVE)

    data class CleanResult(
        val showName: String,
        val season: Int,
        val episode: Int,
        val year: Int? = null,
        val isAnime: Boolean = false
    )

    fun clean(filename: String): CleanResult {
        var name = filename
        var season = 1
        var episode = 1
        var year: Int? = null
        var isAnime = false

        DebugLogger.log("NameCleaner", "========================================")
        DebugLogger.log("NameCleaner", "CLEANING FILENAME: '$filename'")
        DebugLogger.log("NameCleaner", "========================================")

        // 1. Try S/E Patterns first (Strongest signal for TV)
        var matcher = PATTERN_S_E.matcher(name)
        if (matcher.find()) {
            name = matcher.group(1).trim()
            season = matcher.group(2).toInt()
            episode = matcher.group(3).toInt()
            DebugLogger.log("NameCleaner", " Matched S/E pattern: '$name' S$season E$episode")
        } else {
            matcher = PATTERN_X.matcher(name)
            if (matcher.find()) {
                name = matcher.group(1).trim()
                season = matcher.group(2).toInt()
                episode = matcher.group(3).toInt()
                DebugLogger.log("NameCleaner", " Matched X pattern: '$name' S$season E$episode")
            } else {
                // Try Anime format (Show - 01)
                matcher = PATTERN_ANIME_ABS.matcher(name)
                if (matcher.find()) {
                    name = matcher.group(1).trim()
                    episode = matcher.group(2).toInt()
                    season = 1 // Anime typically uses absolute episode numbers
                    isAnime = true
                    DebugLogger.log("NameCleaner", " Matched Anime pattern: '$name' E$episode")
                } else {
                    // Try simple Episode pattern
                    matcher = PATTERN_EP.matcher(name)
                    if (matcher.find()) {
                        name = matcher.group(1).trim()
                        episode = matcher.group(2).toInt()
                        DebugLogger.log("NameCleaner", " Matched Ep pattern: '$name' E$episode")
                    } else {
                        // Fallback: Loose Absolute Number (Show 1080)
                        // Useful for "One.Piece.1080.WEBRip"
                        matcher = PATTERN_LOOSE_ABSOLUTE.matcher(name)
                        if (matcher.find()) {
                            // Only accept if it looks safe
                            val potentialName = matcher.group(1).trim()
                            val potentialEp = matcher.group(2).toInt()
                            
                            // Safety Check: Don't treat "Movie 2024" as Ep 2024 if it looks like a Year
                            // Heuristic: If we find a Year elsewhere, OR if number is NOT 19xx/20xx, accept it.
                            val yearMatcher = PATTERN_YEAR.matcher(filename)
                            val hasYear = yearMatcher.find()
                            val isLikelyYear = (potentialEp in 1900..2100)
                            
                            if (hasYear && yearMatcher.group(2).toInt() == potentialEp) {
                                // The number matched is actually the year (duplicated or primary)
                                // e.g. "Movie.2000.mkv" -> 2000 is the year, not Ep.
                                DebugLogger.log("NameCleaner", " Loose match $potentialEp skipped (appears to be Year)")
                            } else if (!isLikelyYear || hasYear) {
                                // Accept as Episode if it's NOT a year-like number, OR if valid year covers it
                                // "One.Piece.1080" (1080 is fine as Ep if 1999 is Year)
                                
                                name = potentialName
                                episode = potentialEp
                                season = 1
                                isAnime = true
                                DebugLogger.log("NameCleaner", " Matched Loose Absolute pattern: '$name' E$episode")
                            }
                        }
                    }
                }
            }
        }

        // 2. Extract Year if present (and not already part of title)
        DebugLogger.log("NameCleaner", "Step 2: Checking for year pattern...")
        matcher = PATTERN_YEAR.matcher(name)
        if (matcher.find()) {
            val potentialTitle = matcher.group(1).trim()
            // Only accept if title isn't empty (e.g. "2024.mkv" -> empty)
            if (potentialTitle.length > 1) {
                name = potentialTitle
                year = matcher.group(2).toInt()
                DebugLogger.log("NameCleaner", " Extracted Year: '$name' ($year)")
            }
        }

        // 3. Bruteforce sanitize "Scene Tags"
        DebugLogger.log("NameCleaner", "Step 3: Applying junk regex patterns...")
        DebugLogger.log("NameCleaner", "  Before junk removal: '$name'")
        val beforeJunk = name
        // remove anything matching junk regex
        name = JUNK_PATTERN.matcher(name).replaceAll(" ")
        DebugLogger.log("NameCleaner", "  After junk removal: '$name' (changed: ${beforeJunk != name})")
        
        // 4. Final Cleanup
        DebugLogger.log("NameCleaner", "Step 4: Final cleanup steps...")
        
        // Remove file extensions
        val beforeExt = name
        name = name.replace(Regex("\\.(mkv|mp4|avi|webm|mov)$", RegexOption.IGNORE_CASE), "")
        DebugLogger.log("NameCleaner", "  4a. Removed extension: '$beforeExt' ’ '$name'")
        
        // Replace dots/underscores with spaces
        val beforeDots = name
        name = name.replace(".", " ").replace("_", " ")
        DebugLogger.log("NameCleaner", "  4b. Dots/underscores ’ spaces: '$beforeDots' ’ '$name'")
        
        // Collapse multiple spaces
        val beforeSpaces = name
        name = name.replace(Regex("\\s+"), " ").trim()
        DebugLogger.log("NameCleaner", "  4c. Collapsed spaces: '$beforeSpaces' ’ '$name'")
        
        // Remove trailing hyphens
        val beforeHyphens = name
        name = name.replace(Regex("[-]+$"), "").trim()
        DebugLogger.log("NameCleaner", "  4d. Removed trailing hyphens: '$beforeHyphens' ’ '$name'")
        
        DebugLogger.log("NameCleaner", "========================================")
        DebugLogger.log("NameCleaner", "FINAL RESULT: '$name' S$season E$episode")
        DebugLogger.log("NameCleaner", "  Year: $year, IsAnime: $isAnime")
        DebugLogger.log("NameCleaner", "========================================")
        
        return CleanResult(name, season, episode, year, isAnime)
    }
}