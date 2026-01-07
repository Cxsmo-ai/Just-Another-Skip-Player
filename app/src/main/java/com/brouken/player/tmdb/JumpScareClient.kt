package com.brouken.player.tmdb

import com.brouken.player.utils.DebugLogger
import com.brouken.player.utils.LogTags
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Client for scraping jump scare data from notscare.me
 * 
 * This runs entirely on the device - no external server needed.
 * Acts as an "in-app API" for jump scare timestamps.
 */
class JumpScareClient {
    
    companion object {
        private const val TAG = "JumpScareClient"
        private const val BASE_URL = "https://notscare.me"
        
        // Regex patterns for parsing
        private val SEARCH_RESULT_PATTERN = Regex(
            """<a\s+href="(/(?:movie|show|tv|series)/[^"]+)"[^>]*>([^<]+)</a>""",
            RegexOption.IGNORE_CASE
        )
        
        // Timestamp pattern: HH:MM:SS or H:MM:SS or MM:SS
        private val TIMESTAMP_PATTERN = Regex(
            """(\d{1,2}:\d{2}(?::\d{2})?)\s*[-–—]\s*(.+?)(?=\d{1,2}:\d{2}|$)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        
        // Alternative timestamp finder (fallback)
        private val TIME_ONLY_PATTERN = Regex("""(\d{1,2}):(\d{2}):(\d{2})""")

        // New layout pattern (2025): <span class="... font-mono ...">00:00:00</span>
        private val TIMESTAMP_SPAN_PATTERN = Regex(
            """<span[^>]*class="[^"]*font-mono[^"]*"[^>]*>\s*(\d{1,2}:\d{2}(?::\d{2})?)\s*</span>""",
            RegexOption.IGNORE_CASE
        )
        
        // Episode header pattern: <h3 ...>1. Pilot</h3>
        private val EPISODE_HEADER_PATTERN = Regex(
            """<h3[^>]*>\s*(\d+\.\s*[^<]+)\s*</h3>""",
            RegexOption.IGNORE_CASE
        )
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    
    @Serializable
    data class JumpScare(
        val timeMs: Long,
        val description: String = "",
        val intensity: Float = 0f
    )
    
    /**
     * Construct a direct URL path for a movie/show on notscare.me
     * No search API exists, so we construct the URL pattern directly:
     * /series/jump-scares-in-[title-slug-year]
     * /movies/jump-scares-in-[title-slug-year]
     */
    fun search(title: String, year: Int? = null): String? {
        DebugLogger.section(LogTags.API_NOTSCARE_URL, "NOTSCARE URL CONSTRUCTION")
        DebugLogger.i(LogTags.API_NOTSCARE_URL, "Building URL", mapOf(
            "title" to title,
            "year" to (year ?: "unknown"),
            "title_length" to title.length,
            "title_has_spaces" to title.contains(" ")
        ))
        
        return try {
            // Create slug from title: "Stranger Things" -> "stranger-things"
            val slug = title.lowercase()
                .replace(Regex("[^a-z0-9\\s-]"), "") // Remove special chars
                .replace(Regex("\\s+"), "-")         // Spaces to dashes
                .replace(Regex("-+"), "-")           // Collapse multiple dashes
                .trim('-')
            
            DebugLogger.t(LogTags.API_NOTSCARE_URL, "Slug generated", mapOf("slug" to slug))
            
            // Try series URL first (more common for TV shows)
            val yearSuffix = if (year != null) "-$year" else ""
            val seriesPath = "/series/jump-scares-in-$slug$yearSuffix"
            val moviePath = "/movies/jump-scares-in-$slug$yearSuffix"
            
            DebugLogger.step(LogTags.API_NOTSCARE_URL, 1, 5, "Try series with year: $seriesPath")
            if (tryUrl("$BASE_URL$seriesPath")) {
                DebugLogger.success(LogTags.API_NOTSCARE_URL, "Found series URL", mapOf("path" to seriesPath))
                return seriesPath
            }
            
            // Try without year for series
            if (year != null) {
                val seriesPathNoYear = "/series/jump-scares-in-$slug"
                DebugLogger.step(LogTags.API_NOTSCARE_URL, 2, 5, "Try series without year: $seriesPathNoYear")
                if (tryUrl("$BASE_URL$seriesPathNoYear")) {
                    DebugLogger.success(LogTags.API_NOTSCARE_URL, "Found series URL (no year)", mapOf("path" to seriesPathNoYear))
                    return seriesPathNoYear
                }
            }
            
            DebugLogger.step(LogTags.API_NOTSCARE_URL, 3, 5, "Try movie with year: $moviePath")
            if (tryUrl("$BASE_URL$moviePath")) {
                DebugLogger.success(LogTags.API_NOTSCARE_URL, "Found movie URL", mapOf("path" to moviePath))
                return moviePath
            }
            
            // Try movie without year
            if (year != null) {
                val moviePathNoYear = "/movies/jump-scares-in-$slug"
                DebugLogger.step(LogTags.API_NOTSCARE_URL, 4, 5, "Try movie without year: $moviePathNoYear")
                if (tryUrl("$BASE_URL$moviePathNoYear")) {
                    DebugLogger.success(LogTags.API_NOTSCARE_URL, "Found movie URL (no year)", mapOf("path" to moviePathNoYear))
                    return moviePathNoYear
                }
            }
            
            // Try common year variations if no year provided
            if (year == null) {
                DebugLogger.step(LogTags.API_NOTSCARE_URL, 5, 5, "Trying year variations...")
                for (testYear in listOf(2016, 2017, 2018, 2019, 2020, 2021, 2022, 2023, 2024, 2025, 2011, 2010)) {
                    val testSeriesPath = "/series/jump-scares-in-$slug-$testYear"
                    if (tryUrl("$BASE_URL$testSeriesPath")) {
                        DebugLogger.success(LogTags.API_NOTSCARE_URL, "Found with year probe", mapOf(
                            "path" to testSeriesPath,
                            "detected_year" to testYear
                        ))
                        return testSeriesPath
                    }
                }
            }
            
            DebugLogger.fail(LogTags.API_NOTSCARE_URL, "No valid URL found", mapOf("title" to title))
            null
        } catch (e: Exception) {
            DebugLogger.e(LogTags.API_NOTSCARE_ERR, "URL construction failed", e, mapOf("title" to title))
            null
        }
    }
    
    /**
     * Check if a URL exists (returns 200)
     */
    private fun tryUrl(url: String): Boolean {
        return try {
            DebugLogger.t(TAG, "tryUrl() attempt", mapOf(
                "url" to url.takeLast(60),
                "method" to "HEAD"
            ))
            
            val request = Request.Builder()
                .url(url)
                .head() // Use HEAD to save bandwidth
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                .build()
            
            client.newCall(request).execute().use { response ->
                val success = response.code == 200
                DebugLogger.d(TAG, "HTTP Response", mapOf(
                    "url" to url.takeLast(50),
                    "status_code" to response.code,
                    "success" to success,
                    "headers_count" to response.headers.size
                ))
                success
            }
        } catch (e: Exception) {
            DebugLogger.w(TAG, "HTTP Request Failed", mapOf(
                "url" to url.takeLast(50),
                "error_type" to e.javaClass.simpleName,
                "error_msg" to e.message?.take(100)
            ))
            false
        }
    }
    
    /**
     * Get jump scare timestamps from a notscare.me page
     */
    fun getJumpScares(pagePath: String): List<JumpScare> {
        DebugLogger.section(TAG, "GET JUMP SCARES")
        DebugLogger.i(TAG, "Request Details", mapOf(
            "pagePath" to pagePath,
            "full_url" to "$BASE_URL$pagePath"
        ))
        
        return try {
            val url = "$BASE_URL$pagePath"
            val opId = "get-jump-scares-${System.currentTimeMillis()}"
            DebugLogger.startTimer(opId)
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            
            DebugLogger.log(TAG, "Executing HTTP request for page...")
            
            client.newCall(request).execute().use { response ->
            val statusCode = response.code
            DebugLogger.httpResponse(url, statusCode, DebugLogger.endTimer(opId), null)
            
            if (response.isSuccessful) {
                val html = response.body?.string()
                if (html == null) {
                    DebugLogger.e(TAG, "Page body is NULL", null, mapOf("url" to url))
                    return emptyList()
                }
                
                DebugLogger.i(TAG, "HTML Retrieved", mapOf(
                    "length_chars" to html.length,
                    "length_kb" to (html.length / 1024),
                    "first_200_chars" to html.take(200).replace("\n", "\\n")
                ))
                
                val parseOpId = "parse-jump-scares-${System.currentTimeMillis()}"
                DebugLogger.startTimer(parseOpId)
                
                val scares = parseJumpScares(html)
                
                DebugLogger.timed(TAG, parseOpId, "Parse complete", mapOf(
                    "scares_found" to scares.size,
                    "avg_parse_time_per_scare" to (if (scares.isNotEmpty()) DebugLogger.endTimer(parseOpId) / scares.size else 0)
                ))
                
                if (scares.isNotEmpty()) {
                    DebugLogger.d(TAG, "Parsed scares detail:")
                    scares.forEachIndexed { index, s ->
                        DebugLogger.d(TAG, "  Scare #${index + 1}", mapOf(
                            "time_ms" to s.timeMs,
                            "time_formatted" to formatMs(s.timeMs),
                            "description" to s.description.take(80),
                            "intensity" to s.intensity
                        ))
                    }
                }
                scares
            } else {
                DebugLogger.w(TAG, "Page request failed", mapOf(
                    "status_code" to statusCode,
                    "url" to url.takeLast(50)
                ))
                emptyList()
            }
        }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Exception getting scares", e, mapOf(
                "pagePath" to pagePath,
                "exception_type" to e.javaClass.simpleName
            ))
            e.printStackTrace()
            emptyList()
        }
    }
    
    private fun formatMs(ms: Long): String {
        val s = (ms / 1000) % 60
        val m = (ms / 60000) % 60
        val h = ms / 3600000
        return String.format("%02d:%02d:%02d", h, m, s)
    }
    
    /**
     * Parse HTML content to extract jump scare timestamps
     */
    private fun parseJumpScares(html: String): List<JumpScare> {
        val scares = mutableListOf<JumpScare>()
        DebugLogger.d(TAG, "Parse Strategy Selection", mapOf(
            "html_length" to html.length,
            "searching_headers" to true,
            "searching_new_layout" to true,
            "searching_legacy" to true
        ))

        // Strategy 1: Check for Series/Episode headers
        val episodeHeaders = EPISODE_HEADER_PATTERN.findAll(html).toList()
        if (episodeHeaders.isNotEmpty()) {
            DebugLogger.i(TAG, "Strategy Selected: Series Parsing", mapOf(
                "episode_headers_found" to episodeHeaders.size,
                "reason" to "HTML contains episode headers"
            ))
            return parseSeriesJumpScares(html, episodeHeaders)
        }

        // Strategy 2: Check for New Layout (font-mono spans) if no primary old-style matches found
        val newLayoutMatches = TIMESTAMP_SPAN_PATTERN.findAll(html).toList()
        if (newLayoutMatches.isNotEmpty()) {
            DebugLogger.i(TAG, "Strategy Selected: New Layout", mapOf(
                "timestamp_matches" to newLayoutMatches.size,
                "reason" to "HTML contains font-mono timestamp spans"
            ))
            
            for (match in newLayoutMatches) {
                val timeStr = match.groupValues[1]
                val range = match.range
                
                // Look for the description in the following <p> tag
                val searchStart = range.last + 1
                val searchEnd = (searchStart + 500).coerceAtMost(html.length)
                val snippet = html.substring(searchStart, searchEnd)
                
                val pStart = snippet.indexOf("<p")
                var desc = ""
                if (pStart != -1) {
                    val pEnd = snippet.indexOf("</p>", pStart)
                    if (pEnd != -1) {
                         desc = snippet.substring(snippet.indexOf(">", pStart) + 1, pEnd)
                             .trim()
                             .replace(Regex("<[^>]+>"), "")
                    }
                }
                
                val timeMs = parseTimeToMs(timeStr)
                if (timeMs > 0) {
                    if (desc.isBlank()) desc = "Jump Scare"
                    scares.add(JumpScare(timeMs, desc))
                }
            }
            
            if (scares.isNotEmpty()) {
                DebugLogger.log(TAG, "Successfully parsed using New Layout strategy.")
                return scares.distinctBy { it.timeMs }.sortedBy { it.timeMs }
            }
        }

        // Strategy 3: Legacy Layout (TIMESTAMP_PATTERN)
        val matches = TIMESTAMP_PATTERN.findAll(html).toList()
        DebugLogger.i(TAG, "Strategy Selected: Legacy Pattern", mapOf(
            "pattern_matches" to matches.size,
            "reason" to "Using legacy timestamp pattern"
        ))
        
        for (match in matches) {
            val timeStr = match.groupValues[1]
            val desc = match.groupValues[2].trim()
                .replace(Regex("<[^>]+>"), "")
                .take(100)
            
            val timeMs = parseTimeToMs(timeStr)
            DebugLogger.log(TAG, "  Found timestamp: '$timeStr' -> ${timeMs}ms, desc: '${desc.take(30)}'")
            
            if (timeMs > 0) {
                scares.add(JumpScare(timeMs, desc))
            }
        }
        
        // Fallback: just find all timestamps if primary failed
        if (scares.isEmpty()) {
            DebugLogger.w(TAG, "All strategies failed, trying fallback", mapOf(
                "scares_so_far" to scares.size,
                "fallback_pattern" to "TIME_ONLY"
            ))
            
            val timeMatches = TIME_ONLY_PATTERN.findAll(html).toList()
            DebugLogger.i(TAG, "Fallback Pattern Results", mapOf(
                "matches_found" to timeMatches.size,
                "pattern_type" to "HH:MM:SS"
            ))
            
            for (match in timeMatches) {
                val h = match.groupValues[1].toIntOrNull() ?: 0
                val m = match.groupValues[2].toIntOrNull() ?: 0
                val s = match.groupValues[3].toIntOrNull() ?: 0
                val timeMs = ((h * 3600) + (m * 60) + s) * 1000L
                DebugLogger.log(TAG, "  Found time: $h:$m:$s -> ${timeMs}ms")
                
                if (timeMs > 0) {
                    scares.add(JumpScare(timeMs, "Jump Scare"))
                }
            }
        }
        
        val result = scares.distinctBy { it.timeMs }.sortedBy { it.timeMs }
        DebugLogger.i(TAG, "Parse Complete", mapOf(
            "before_dedup" to scares.size,
            "after_dedup" to result.size,
            "duplicates_removed" to (scares.size - result.size)
        ))
        return result
    }

    private fun parseSeriesJumpScares(html: String, episodeHeaders: List<MatchResult>): List<JumpScare> {
        val scares = mutableListOf<JumpScare>()
        val timestamps = TIMESTAMP_SPAN_PATTERN.findAll(html).toList()
        
        if (timestamps.isEmpty()) {
             DebugLogger.log(TAG, "No timestamps found for series parsing!")
             return emptyList()
        }

        for (match in timestamps) {
            val timeIndex = match.range.first
            val currentEpisodeHeader = episodeHeaders.lastOrNull { it.range.first < timeIndex }
            
            val episodePrefix = if (currentEpisodeHeader != null) {
                val headerText = currentEpisodeHeader.groupValues[1]
                val episodeNum = headerText.substringBefore(".").trim()
                "[Ep $episodeNum] "
            } else {
                "[Ep ?] "
            }
            
            val timeStr = match.groupValues[1]
            val timeMs = parseTimeToMs(timeStr)
            
            val range = match.range
            val searchStart = range.last + 1
            val searchEnd = (searchStart + 500).coerceAtMost(html.length)
            val snippet = html.substring(searchStart, searchEnd)
            val pStart = snippet.indexOf("<p")
            var desc = ""
             if (pStart != -1) {
                val pEnd = snippet.indexOf("</p>", pStart)
                if (pEnd != -1) {
                     desc = snippet.substring(snippet.indexOf(">", pStart) + 1, pEnd)
                         .trim()
                         .replace(Regex("<[^>]+>"), "")
                }
            }
            
            if (desc.isBlank()) desc = "Jump Scare"
            val fullDesc = "$episodePrefix$desc"
            
            if (timeMs > 0) {
                scares.add(JumpScare(timeMs, fullDesc))
            }
        }
        
        return scares.distinctBy { Pair(it.timeMs, it.description) }.sortedBy { it.timeMs }
    }
    
    private fun parseTimeToMs(timeStr: String): Long {
        val parts = timeStr.split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            3 -> ((parts[0] * 3600) + (parts[1] * 60) + parts[2]) * 1000L
            2 -> ((parts[0] * 60) + parts[1]) * 1000L
            else -> 0L
        }
    }
    
    /**
     * Parse scares for a SPECIFIC episode by finding its card container
     * This is the KEY FIX - scopes extraction to only the episode card
     */
    private fun parseEpisodeCard(html: String, targetEpisode: Int): List<JumpScare> {
        DebugLogger.section(TAG, "CARD-BASED EPISODE PARSING")
        DebugLogger.i(TAG, "Target episode", mapOf("episode" to targetEpisode))
        
        val scares = mutableListOf<JumpScare>()
        
        // Find h3 header containing "N. Chapter" or "N. Episode" 
        val headerPattern = Regex(
            """<h3[^>]*>\s*$targetEpisode\.\s*(Chapter|Episode)[^<]*</h3>""",
            RegexOption.IGNORE_CASE
        )
        val headerMatch = headerPattern.find(html)
        
        if (headerMatch == null) {
            DebugLogger.w(TAG, "Episode $targetEpisode header NOT found in HTML", mapOf(
                "html_length" to html.length,
                "searched_pattern" to "$targetEpisode. Chapter/Episode"
            ))
            return emptyList()
        }
        
        DebugLogger.success(TAG, "Episode $targetEpisode header found", mapOf(
            "position" to headerMatch.range.first,
            "matched_text" to headerMatch.value.take(60)
        ))
        
        // Find the episode card container (rounded-2xl div containing this header)
        // Search backwards from header to find "<div" with "rounded-2xl"
        val beforeHeader = html.substring(0, headerMatch.range.first)
        val cardStartIdx = beforeHeader.lastIndexOf("<div")
        
        if (cardStartIdx == -1) {
            DebugLogger.w(TAG, "Could not find card start for episode $targetEpisode")
            return emptyList()
        }
        
        // Find card end - count div open/close tags
        var depth = 0
        var cardEndIdx = cardStartIdx
        var i = cardStartIdx
        while (i < html.length) {
            if (html.substring(i).startsWith("<div")) {
                depth++
            } else if (html.substring(i).startsWith("</div>")) {
                depth--
                if (depth == 0) {
                    cardEndIdx = i + 6
                    break
                }
            }
            i++
        }
        
        val cardHtml = html.substring(cardStartIdx, cardEndIdx.coerceAtMost(html.length))
        DebugLogger.d(TAG, "Episode card isolated", mapOf(
            "card_length" to cardHtml.length,
            "card_start" to cardStartIdx
        ))
        
        // Extract timestamps ONLY from this card
        val timestamps = TIMESTAMP_SPAN_PATTERN.findAll(cardHtml)
        
        for (ts in timestamps) {
            val timeStr = ts.groupValues[1]
            val timeMs = parseTimeToMs(timeStr)
            
            // Get description from nearby <p> tag
            val afterTs = cardHtml.substring(ts.range.last.coerceAtMost(cardHtml.length - 1))
            val descMatch = Regex("""<p[^>]*mt-1[^>]*>([^<]+)""").find(afterTs)
            val desc = descMatch?.groupValues?.get(1)?.trim()?.replace("[notscare.me]", "")?.trim() 
                ?: "Jump Scare"
            
            scares.add(JumpScare(timeMs, "[Ep $targetEpisode] $desc"))
            
            DebugLogger.d(TAG, "Extracted scare from episode card", mapOf(
                "time" to timeStr,
                "time_ms" to timeMs,
                "desc" to desc.take(50),
                "episode" to targetEpisode
            ))
        }
        
        DebugLogger.i(TAG, "Episode $targetEpisode extraction complete", mapOf(
            "scares_found" to scares.size
        ))
        
        return scares
    }
    
    /**
     * Convenience method: Search and get scares in one call
     * Now supports season AND episode-specific extraction
     */
    fun fetchJumpScares(title: String, year: Int? = null, season: Int? = null, episode: Int? = null): List<JumpScare> {
        DebugLogger.section(TAG, "FETCH JUMP SCARES OPERATION")
        DebugLogger.i(TAG, "Operation Parameters", mapOf(
            "title" to title,
            "year" to (year ?: "null"),
            "season" to (season ?: "null"),
            "episode" to (episode ?: "null"),
            "title_length" to title.length
        ))
        
        val basePath = search(title, year)
        if (basePath == null) {
            DebugLogger.fail(TAG, "Search returned NULL - no page found!", mapOf("title" to title))
            return emptyList()
        }
        
        DebugLogger.success(TAG, "Search found base path", mapOf("path" to basePath))
        
        // If we have a season number and this is a series, try season-specific URL
        if (season != null && (basePath.contains("/series/") || basePath.contains("/show/") || basePath.contains("/tv/"))) {
            val seasonPath = "$basePath/season/$season"
            DebugLogger.step(TAG, 1, 2, "Fetching season URL", mapOf("path" to seasonPath))
            
            val seasonHtml = getPageHtml(seasonPath)
            
            if (seasonHtml != null && seasonHtml.isNotEmpty()) {
                // If episode specified, use card-based parsing for that episode ONLY
                if (episode != null) {
                    DebugLogger.step(TAG, 2, 2, "Episode-scoped parsing", mapOf("episode" to episode))
                    val episodeScares = parseEpisodeCard(seasonHtml, episode)
                    if (episodeScares.isNotEmpty()) {
                        DebugLogger.success(TAG, "Found ${episodeScares.size} scares for Ep $episode")
                        return episodeScares
                    } else {
                        DebugLogger.w(TAG, "No scares found for Ep $episode, trying full parse")
                    }
                }
                
                // Fall back to full page parse
                val allScares = parseJumpScares(seasonHtml)
                if (allScares.isNotEmpty()) {
                    DebugLogger.i(TAG, "Full parse found ${allScares.size} scares")
                    return allScares
                }
            }
        }
        
        // Fall back to base URL
        DebugLogger.d(TAG, "Falling back to base path")
        return getJumpScares(basePath)
    }
    
    /**
     * Get raw HTML from a page
     */
    private fun getPageHtml(pagePath: String): String? {
        return try {
            val url = "$BASE_URL$pagePath"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to get HTML", e, mapOf("path" to pagePath))
            null
        }
    }
}