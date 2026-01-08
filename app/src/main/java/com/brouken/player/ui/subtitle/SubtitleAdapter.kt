package com.brouken.player.ui.subtitle

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.brouken.player.R
import com.brouken.player.stremio.SubtitleSource
import com.brouken.player.stremio.SubtitleTrack

/**
 * RecyclerView adapter for displaying subtitle tracks
 * Supports filtering by language and search query
 */
class SubtitleAdapter(
    private val onSubtitleSelected: (SubtitleTrack) -> Unit,
    private val onSubtitleLongPressed: (SubtitleTrack) -> Boolean = { false }
) : ListAdapter<SubtitleListItem, RecyclerView.ViewHolder>(SubtitleDiffCallback()), Filterable {
    
    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_SUBTITLE = 1
        private const val VIEW_TYPE_OFF = 2
    }
    
    // Full list of items (headers + subtitles + off option)
    private var allItems: List<SubtitleListItem> = emptyList()
    
    // Currently selected subtitle ID
    private var selectedId: String? = null
    
    // Current filter state
    private var currentLanguageFilter: String? = null
    private var currentSearchQuery: String = ""
    
    // ========================================================================
    // PUBLIC API
    // ========================================================================
    
    /**
     * Set subtitles grouped by source
     */
    fun setSubtitles(
        embeddedTracks: List<SubtitleTrack>,
        onlineTracks: List<SubtitleTrack>,
        localTracks: List<SubtitleTrack> = emptyList()
    ) {
        val items = mutableListOf<SubtitleListItem>()
        
        // Embedded section
        if (embeddedTracks.isNotEmpty()) {
            items.add(SubtitleListItem.Header(
                title = "EMBEDDED",
                count = embeddedTracks.size,
                icon = "📥"
            ))
            items.addAll(embeddedTracks.map { SubtitleListItem.Track(it) })
        }
        
        // Local sidecar section
        if (localTracks.isNotEmpty()) {
            items.add(SubtitleListItem.Header(
                title = "LOCAL FILES",
                count = localTracks.size,
                icon = "📁"
            ))
            items.addAll(localTracks.map { SubtitleListItem.Track(it) })
        }
        
        // Online section
        if (onlineTracks.isNotEmpty()) {
            items.add(SubtitleListItem.Header(
                title = "ONLINE",
                count = onlineTracks.size,
                icon = "🌐"
            ))
            items.addAll(onlineTracks.map { SubtitleListItem.Track(it) })
        }
        
        // Off option at the end
        items.add(SubtitleListItem.Off)
        
        allItems = items
        applyFilters()
    }
    
    /**
     * Set the currently selected subtitle
     */
    fun setSelectedSubtitle(subtitleId: String?) {
        val oldSelected = selectedId
        selectedId = subtitleId
        
        // Notify changes for old and new selection
        currentList.forEachIndexed { index, item ->
            if (item is SubtitleListItem.Track) {
                if (item.track.id == oldSelected || item.track.id == selectedId) {
                    notifyItemChanged(index)
                }
            } else if (item is SubtitleListItem.Off && (oldSelected == null || selectedId == null)) {
                notifyItemChanged(index)
            }
        }
    }
    
    /**
     * Filter by language code
     */
    fun filterByLanguage(languageCode: String?) {
        currentLanguageFilter = languageCode
        applyFilters()
    }
    
    /**
     * Get available languages from current subtitles
     */
    fun getAvailableLanguages(): List<Pair<String, String>> {
        return allItems
            .filterIsInstance<SubtitleListItem.Track>()
            .map { it.track.languageCode to it.track.language }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }
    
    // ========================================================================
    // ADAPTER IMPLEMENTATION
    // ========================================================================
    
    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is SubtitleListItem.Header -> VIEW_TYPE_HEADER
            is SubtitleListItem.Track -> VIEW_TYPE_SUBTITLE
            is SubtitleListItem.Off -> VIEW_TYPE_OFF
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.item_subtitle_header, parent, false)
                HeaderViewHolder(view)
            }
            VIEW_TYPE_OFF -> {
                val view = inflater.inflate(R.layout.item_subtitle_off, parent, false)
                OffViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_subtitle_track, parent, false)
                TrackViewHolder(view)
            }
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SubtitleListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is SubtitleListItem.Track -> (holder as TrackViewHolder).bind(item.track, item.track.id == selectedId)
            is SubtitleListItem.Off -> (holder as OffViewHolder).bind(selectedId == null)
        }
    }
    
    // ========================================================================
    // VIEW HOLDERS
    // ========================================================================
    
    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val iconText: TextView = view.findViewById(R.id.header_icon)
        private val titleText: TextView = view.findViewById(R.id.header_title)
        private val countText: TextView = view.findViewById(R.id.header_count)
        
        fun bind(header: SubtitleListItem.Header) {
            iconText.text = header.icon
            titleText.text = header.title
            countText.text = "(${header.count})"
        }
    }
    
    inner class TrackViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val radioButton: View = view.findViewById(R.id.radio_button)
        private val languageText: TextView = view.findViewById(R.id.language_text)
        private val labelText: TextView = view.findViewById(R.id.label_text)
        private val sourceText: TextView = view.findViewById(R.id.source_text)
        private val scoreText: TextView = view.findViewById(R.id.score_text)
        private val badgesContainer: ViewGroup = view.findViewById(R.id.badges_container)
        
        fun bind(track: SubtitleTrack, isSelected: Boolean) {
            // Radio button state
            radioButton.isSelected = isSelected
            itemView.isActivated = isSelected
            
            // Language with flag
            val flag = getLanguageFlag(track.languageCode)
            languageText.text = "$flag ${track.language}"
            
            // Label (SDH, Forced, etc.)
            if (!track.label.isNullOrEmpty() || track.isSDH || track.isForced) {
                val labels = mutableListOf<String>()
                track.label?.let { labels.add(it) }
                if (track.isSDH) labels.add("SDH")
                if (track.isForced) labels.add("Forced")
                labelText.text = "[${labels.joinToString(", ")}]"
                labelText.visibility = View.VISIBLE
            } else {
                labelText.visibility = View.GONE
            }
            
            // Source info
            val sourceInfo = when (track.source) {
                SubtitleSource.EMBEDDED -> "Embedded"
                SubtitleSource.LOCAL -> "Local file"
                SubtitleSource.ONLINE -> track.addonName ?: "Online"
                SubtitleSource.MANUAL -> "Manual"
            }
            sourceText.text = sourceInfo
            
            // Match score (only for online)
            if (track.source == SubtitleSource.ONLINE && track.matchScore > 0) {
                scoreText.text = "🎯 ${track.matchScore}%"
                scoreText.visibility = View.VISIBLE
                
                // Color based on score
                val color = when {
                    track.matchScore >= 90 -> 0xFF4CAF50.toInt() // Green
                    track.matchScore >= 70 -> 0xFFFF9800.toInt() // Orange
                    else -> 0xFF9E9E9E.toInt() // Gray
                }
                scoreText.setTextColor(color)
            } else {
                scoreText.visibility = View.GONE
            }
            
            // Hash match badge
            badgesContainer.removeAllViews()
            if (track.isHashMatch) {
                addBadge("⭐", "Hash Match")
            }
            
            // Click handlers
            itemView.setOnClickListener {
                onSubtitleSelected(track)
            }
            
            itemView.setOnLongClickListener {
                onSubtitleLongPressed(track)
            }
        }
        
        private fun addBadge(icon: String, label: String) {
            // Could inflate a badge layout here
        }
    }
    
    inner class OffViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val radioButton: View = view.findViewById(R.id.radio_button)
        private val text: TextView = view.findViewById(R.id.off_text)
        
        fun bind(isSelected: Boolean) {
            radioButton.isSelected = isSelected
            itemView.isActivated = isSelected
            text.text = "🚫 OFF - No Subtitles"
            
            itemView.setOnClickListener {
                // Create a fake "off" track
                onSubtitleSelected(SubtitleTrack(
                    id = "off",
                    language = "Off",
                    languageCode = "",
                    source = SubtitleSource.MANUAL
                ))
            }
        }
    }
    
    // ========================================================================
    // FILTERING
    // ========================================================================
    
    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                currentSearchQuery = constraint?.toString()?.lowercase() ?: ""
                
                val filtered = allItems.filter { item ->
                    when (item) {
                        is SubtitleListItem.Header -> true // Always show headers
                        is SubtitleListItem.Off -> true   // Always show off option
                        is SubtitleListItem.Track -> {
                            val track = item.track
                            
                            // Language filter
                            val passesLanguage = currentLanguageFilter == null ||
                                    track.languageCode == currentLanguageFilter
                            
                            // Search query filter
                            val passesSearch = currentSearchQuery.isEmpty() ||
                                    track.language.lowercase().contains(currentSearchQuery) ||
                                    track.addonName?.lowercase()?.contains(currentSearchQuery) == true ||
                                    track.label?.lowercase()?.contains(currentSearchQuery) == true
                            
                            passesLanguage && passesSearch
                        }
                    }
                }
                
                // Remove headers with no items after them
                val cleaned = mutableListOf<SubtitleListItem>()
                var lastHeader: SubtitleListItem.Header? = null
                var headerHasItems = false
                
                for (item in filtered) {
                    when (item) {
                        is SubtitleListItem.Header -> {
                            if (lastHeader != null && headerHasItems) {
                                cleaned.add(lastHeader)
                            }
                            lastHeader = item
                            headerHasItems = false
                        }
                        is SubtitleListItem.Track -> {
                            if (lastHeader != null && !headerHasItems) {
                                cleaned.add(lastHeader)
                                headerHasItems = true
                            }
                            cleaned.add(item)
                        }
                        is SubtitleListItem.Off -> {
                            if (lastHeader != null && headerHasItems) {
                                // Don't need to re-add, already handled
                            }
                            cleaned.add(item)
                        }
                    }
                }
                
                return FilterResults().apply { values = cleaned }
            }
            
            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                submitList(results?.values as? List<SubtitleListItem> ?: emptyList())
            }
        }
    }
    
    private fun applyFilters() {
        filter.filter(currentSearchQuery)
    }
    
    // ========================================================================
    // HELPERS
    // ========================================================================
    
    private fun getLanguageFlag(code: String): String {
        return when (code) {
            "eng", "en" -> "🇬🇧"
            "spa", "es" -> "🇪🇸"
            "fra", "fr" -> "🇫🇷"
            "deu", "de" -> "🇩🇪"
            "ita", "it" -> "🇮🇹"
            "por", "pt" -> "🇵🇹"
            "rus", "ru" -> "🇷🇺"
            "jpn", "ja" -> "🇯🇵"
            "kor", "ko" -> "🇰🇷"
            "zho", "zh" -> "🇨🇳"
            "ara", "ar" -> "🇸🇦"
            "hin", "hi" -> "🇮🇳"
            "tur", "tr" -> "🇹🇷"
            "pol", "pl" -> "🇵🇱"
            "nld", "nl" -> "🇳🇱"
            "swe", "sv" -> "🇸🇪"
            "nor", "no" -> "🇳🇴"
            "dan", "da" -> "🇩🇰"
            "fin", "fi" -> "🇫🇮"
            "heb", "he" -> "🇮🇱"
            "tha", "th" -> "🇹🇭"
            "vie", "vi" -> "🇻🇳"
            "ind", "id" -> "🇮🇩"
            "hun", "hu" -> "🇭🇺"
            "ces", "cs" -> "🇨🇿"
            "ron", "ro" -> "🇷🇴"
            "ell", "el" -> "🇬🇷"
            "ukr", "uk" -> "🇺🇦"
            else -> "🌐"
        }
    }
}

// ============================================================================
// DATA CLASSES
// ============================================================================

sealed class SubtitleListItem {
    data class Header(
        val title: String,
        val count: Int,
        val icon: String
    ) : SubtitleListItem()
    
    data class Track(
        val track: SubtitleTrack
    ) : SubtitleListItem()
    
    object Off : SubtitleListItem()
}

// ============================================================================
// DIFF CALLBACK
// ============================================================================

class SubtitleDiffCallback : DiffUtil.ItemCallback<SubtitleListItem>() {
    override fun areItemsTheSame(oldItem: SubtitleListItem, newItem: SubtitleListItem): Boolean {
        return when {
            oldItem is SubtitleListItem.Header && newItem is SubtitleListItem.Header ->
                oldItem.title == newItem.title
            oldItem is SubtitleListItem.Track && newItem is SubtitleListItem.Track ->
                oldItem.track.id == newItem.track.id
            oldItem is SubtitleListItem.Off && newItem is SubtitleListItem.Off ->
                true
            else -> false
        }
    }
    
    override fun areContentsTheSame(oldItem: SubtitleListItem, newItem: SubtitleListItem): Boolean {
        return oldItem == newItem
    }
}
