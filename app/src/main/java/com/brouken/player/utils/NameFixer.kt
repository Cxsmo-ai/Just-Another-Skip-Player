package com.brouken.player.utils

import java.util.regex.Pattern

object NameFixer {
    
    // Extract Season/Episode (S01E01, 1x01, etc.)
    // Returns Triple(ShowName, Season, Episode) or null
    fun extractSeasonEpisode(filename: String): Triple<String, Int, Int>? {
        // Common regex: "Show Name S01E01"
        val regexSxE = Pattern.compile("(.+?)[ .][sS](\\d{1,2})[eE](\\d{1,2})") // S01E01
        
        var m = regexSxE.matcher(filename)
        if (m.find()) {
            val name = m.group(1).replace(".", " ").trim()
            val season = m.group(2).toInt()
            val episode = m.group(3).toInt()
            return Triple(name, season, episode)
        }
        
        // Try "1x01" format
        val regexNxN = Pattern.compile("(.+?)[ .](\\d{1,2})[xX](\\d{1,2})")
        m = regexNxN.matcher(filename)
        if (m.find()) {
             val name = m.group(1).replace(".", " ").trim()
            val season = m.group(2).toInt()
            val episode = m.group(3).toInt()
            return Triple(name, season, episode)
        }
        
        return null
    }

    fun fixTitle(name: String): String {
        return name.replace(".", " ").replace("_", " ").trim()
    }
}
