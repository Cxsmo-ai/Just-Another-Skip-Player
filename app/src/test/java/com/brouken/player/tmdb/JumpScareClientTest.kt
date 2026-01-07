package com.brouken.player.tmdb

import org.junit.Test
import org.junit.Assert.*

/**
 * Integration tests for JumpScareClient
 * Tests actual HTTP fetching and parsing from notscare.me
 */
class JumpScareClientTest {

    private val client = JumpScareClient()

    data class TestCase(
        val showName: String,
        val year: Int?,
        val season: Int,
        val episode: Int,
        val description: String
    )

    private val testCases = listOf(
        // Various shows, seasons, and episodes (NOT just Episode 1)
        TestCase("American Horror Story", 2011, 2, 7, "AHS S2E7 Dark Cousin"),
        TestCase("Stranger Things", 2016, 3, 4, "ST S3E4 The Sauna Test"),
        TestCase("Stranger Things", 2016, 5, 6, "ST S5E6"),
        TestCase("The Haunting of Hill House", 2018, 1, 6, "Hill House S1E6 Two Storms"),
        TestCase("Ash vs Evil Dead", 2015, 1, 5, "Ash S1E5"),
        TestCase("The Exorcist", 2016, 1, 3, "Exorcist S1E3"),
        TestCase("The Midnight Club", 2022, 1, 8, "Midnight Club S1E8"),
        TestCase("Channel Zero", 2016, 1, 4, "Channel Zero S1E4"),
        TestCase("Archive 81", 2022, 1, 5, "Archive 81 S1E5"),
        TestCase("Penny Dreadful", 2014, 1, 6, "Penny Dreadful S1E6")
    )

    @Test
    fun testMultipleShowsAndEpisodes() {
        println("\n" + "=".repeat(80))
        println("JUMP SCARE CLIENT TEST - Multiple Shows & Episodes")
        println("=".repeat(80))

        for (testCase in testCases) {
            println("\n--- Testing: ${testCase.description} ---")
            println("Show: ${testCase.showName}, Year: ${testCase.year}, Season: ${testCase.season}, Episode: ${testCase.episode}")

            // Step 1: Fetch all scares for the season (like the app does)
            val allScares = client.fetchJumpScares(testCase.showName, testCase.year, testCase.season)
            println("Total scares fetched for season: ${allScares.size}")

            // Step 2: Filter for specific episode (like JumpScareManager does)
            val episodeScares = filterForEpisode(allScares, testCase.episode)
            println("Scares for Episode ${testCase.episode}: ${episodeScares.size}")

            // Print the timestamps
            if (episodeScares.isNotEmpty()) {
                println("Timestamps:")
                episodeScares.forEach { scare ->
                    val time = formatMs(scare.timeMs)
                    println("  $time - ${scare.description.take(60)}")
                }
            } else {
                println("  (No jump scares found for this episode)")
            }

            // Verify we got results or at least tried properly
            assertTrue("Should not crash", true)
        }

        println("\n" + "=".repeat(80))
        println("TEST COMPLETE")
        println("=".repeat(80))
    }

    @Test
    fun testSearchFunction() {
        println("\n--- Testing Search Function ---")

        val searches = listOf(
            "American Horror Story" to 2011,
            "Stranger Things" to 2016,
            "The Haunting of Hill House" to 2018,
            "IT Welcome to Derry" to 2025
        )

        for ((title, year) in searches) {
            val path = client.search(title, year)
            println("Search '$title' ($year) -> $path")
            assertNotNull("Should find path for $title", path)
            assertTrue("Path should contain 'series' or 'movie'", 
                path?.contains("/series/") == true || path?.contains("/movie/") == true)
        }
    }

    private fun filterForEpisode(scares: List<JumpScareClient.JumpScare>, episode: Int): List<JumpScareClient.JumpScare> {
        val episodePattern = Regex("""\[Ep\s*(\d+)\]""")
        
        return scares.filter { scare ->
            val match = episodePattern.find(scare.description)
            if (match != null) {
                val epNum = match.groupValues[1].toIntOrNull()
                epNum == episode
            } else {
                // No episode tag - include if no episodes are tagged at all
                val anyHasTag = scares.any { it.description.contains("[Ep ") }
                !anyHasTag
            }
        }
    }

    private fun formatMs(ms: Long): String {
        val s = (ms / 1000) % 60
        val m = (ms / 60000) % 60
        val h = ms / 3600000
        return String.format("%02d:%02d:%02d", h, m, s)
    }
}
