package com.brouken.player.ui.subtitle

import android.content.Context
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.brouken.player.R
import com.brouken.player.stremio.StremioSubtitleFetcher
import com.brouken.player.stremio.SubtitleSource
import com.brouken.player.stremio.SubtitleTrack
import com.brouken.player.utils.DebugLogger
import com.google.android.material.bottomsheet.BottomSheetBehavior
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.core.content.ContextCompat
import android.view.Gravity
import kotlinx.coroutines.*

/**
 * Main controller for the subtitle selection hub
 * Displays as a partial overlay so video stays visible
 */
class SubtitleHub(
    private val context: Context,
    private val parentView: ViewGroup
) {
    companion object {
        private const val TAG = "SubtitleHub"
    }
    
    // Views
    private var rootView: View? = null
    private var bottomSheet: FrameLayout? = null
    private var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>? = null
    private var recyclerView: RecyclerView? = null
    private var searchInput: EditText? = null
    private var filterGroup: RadioGroup? = null
    private var loadingContainer: LinearLayout? = null
    private var emptyContainer: LinearLayout? = null
    private var pacmanLoading: PacmanLoadingView? = null
    
    // Data
    private val adapter = SubtitleAdapter(
        onSubtitleSelected = { track -> onSubtitleSelected(track) },
        onSubtitleLongPressed = { track -> onSubtitleLongPressed(track) }
    )
    
    private val fetcher = StremioSubtitleFetcher(context)
    private var fetchJob: Job? = null
    
    // Callbacks
    var onSubtitleChosen: ((SubtitleTrack?) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null
    var onOpenSettings: (() -> Unit)? = null
    var onLoadLocalFile: (() -> Unit)? = null
    
    // State
    private var embeddedTracks: List<SubtitleTrack> = emptyList()
    private var onlineTracks: List<SubtitleTrack> = emptyList()
    private var localTracks: List<SubtitleTrack> = emptyList()
    private var selectedTrackId: String? = null
    private var isShowing = false
    
    // ========================================================================
    // PUBLIC API
    // ========================================================================
    
    /**
     * Show the subtitle hub
     */
    fun show() {
        if (isShowing) return
        
        try {
            // Inflate layout
            rootView = LayoutInflater.from(context)
                .inflate(R.layout.layout_subtitle_hub, parentView, false)
            
            parentView.addView(rootView)
            
            // Find views
            bindViews()
            
            // Setup behavior
            setupBottomSheet()
            setupSearch()
            setupChips()
            setupButtons()
            
            // Setup list
            recyclerView?.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = this@SubtitleHub.adapter
            }
            
            // Populate with current data
            updateList()
            
            // Detect if running on Android TV
            val isTv = context.packageManager.hasSystemFeature("android.software.leanback") ||
                       context.packageManager.hasSystemFeature("android.hardware.type.television")
            
            // Expand sheet - fullscreen on TV, half on mobile
            bottomSheetBehavior?.apply {
                if (isTv) {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    skipCollapsed = true
                } else {
                    state = BottomSheetBehavior.STATE_HALF_EXPANDED
                }
            }
            isShowing = true
            
            DebugLogger.log(TAG, "Subtitle hub shown (TV mode: $isTv)")
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Error showing subtitle hub: ${e.message}", e)
            android.widget.Toast.makeText(context, "Subtitle Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            // Clean up on error
            parentView.removeView(rootView)
            rootView = null
            isShowing = false
        }
    }
    
    /**
     * Hide the subtitle hub
     */
    fun hide() {
        if (!isShowing) return
        
        fetchJob?.cancel()
        
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        
        rootView?.postDelayed({
            parentView.removeView(rootView)
            rootView = null
            isShowing = false
            onDismiss?.invoke()
        }, 200)
        
        DebugLogger.log(TAG, "Subtitle hub hidden")
    }
    
    /**
     * Check if hub is currently visible
     */
    fun isVisible(): Boolean = isShowing
    
    /**
     * Set embedded tracks from the video
     */
    fun setEmbeddedTracks(tracks: List<SubtitleTrack>) {
        embeddedTracks = tracks
        if (isShowing) updateList()
    }
    
    /**
     * Set local sidecar tracks
     */
    fun setLocalTracks(tracks: List<SubtitleTrack>) {
        localTracks = tracks
        if (isShowing) updateList()
    }
    
    /**
     * Set the currently selected track
     */
    fun setSelectedTrack(trackId: String?) {
        selectedTrackId = trackId
        adapter.setSelectedSubtitle(trackId)
    }
    
    /**
     * Fetch online subtitles for video
     */
    fun fetchOnlineSubtitles(
        videoUri: Uri,
        title: String?,
        imdbId: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ) {
        fetchJob?.cancel()
        
        showLoading("Searching for subtitles...")
        
        fetchJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                fetcher.onProgressUpdate = { message ->
                    pacmanLoading?.setStatusText(message)
                }
                
                fetcher.onError = { error ->
                    showError(error)
                }
                
                val results = fetcher.fetchSubtitles(
                    videoUri = videoUri,
                    title = title,
                    imdbId = imdbId,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber
                )
                
                onlineTracks = results
                
                if (results.isEmpty() && embeddedTracks.isEmpty() && localTracks.isEmpty()) {
                    showEmpty("No subtitles found")
                } else {
                    showList()
                }
                
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    DebugLogger.e(TAG, "Fetch error: ${e.message}", e)
                    showError("Failed to fetch subtitles")
                }
            }
        }
    }
    
    // ========================================================================
    // PRIVATE METHODS
    // ========================================================================
    
    private fun bindViews() {
        rootView?.let { root ->
            bottomSheet = root.findViewById(R.id.bottom_sheet)
            recyclerView = root.findViewById(R.id.subtitle_list)
            searchInput = root.findViewById(R.id.search_input)
            filterGroup = root.findViewById(R.id.language_filter_group)
            loadingContainer = root.findViewById(R.id.loading_container)
            emptyContainer = root.findViewById(R.id.empty_container)
            pacmanLoading = root.findViewById(R.id.pacman_loading)
        }
    }
    
    private fun setupBottomSheet() {
        bottomSheet?.let { sheet ->
            bottomSheetBehavior = BottomSheetBehavior.from(sheet).apply {
                state = BottomSheetBehavior.STATE_HIDDEN
                peekHeight = (context.resources.displayMetrics.heightPixels * 0.3).toInt()
                isHideable = true
                
                addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                            hide()
                        }
                    }
                    
                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                        // Could adjust background dim here
                    }
                })
            }
        }
    }
    
    private fun setupSearch() {
        searchInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                adapter.filter.filter(s?.toString() ?: "")
            }
        })
    }
    
    private fun setupChips() {
        // Find existing "All" button
        val allBtn = filterGroup?.findViewById<RadioButton>(R.id.filter_all)
        allBtn?.tag = "all"
        
        filterGroup?.setOnCheckedChangeListener { group, checkedId ->
            if (checkedId == View.NO_ID) return@setOnCheckedChangeListener
            
            val button = group.findViewById<RadioButton>(checkedId)
            val tag = button.tag as? String
            
            if (tag == "all") {
                adapter.filterByLanguage(null)
            } else {
                adapter.filterByLanguage(tag)
            }
        }
    }
    
    private fun updateLanguageChips() {
        // Clear all except "All"
        val allBtn = filterGroup?.findViewById<View>(R.id.filter_all)
        filterGroup?.removeAllViews()
        if (allBtn != null) {
            filterGroup?.addView(allBtn)
        }
        
        // Get unique languages
        val languages = adapter.getAvailableLanguages()
        
        for ((code, name) in languages) {
            addLanguagePill(name, code)
        }
    }
    
    private fun addLanguagePill(language: String, code: String) {
        val density = context.resources.displayMetrics.density
        val padding = (16 * density).toInt()
        val height = (32 * density).toInt()
        val margin = (8 * density).toInt()

        val radioButton = RadioButton(context).apply {
            text = "$language ${getLanguageFlag(code)}"
            tag = code
            id = View.generateViewId()
            
            // Style
            setTextColor(ContextCompat.getColorStateList(context, R.color.selector_chip_text))
            setBackgroundResource(R.drawable.bg_filter_chip)
            buttonDrawable = null
            gravity = Gravity.CENTER
            setPadding(padding, 0, padding, 0)
            
            layoutParams = RadioGroup.LayoutParams(
                RadioGroup.LayoutParams.WRAP_CONTENT,
                height
            ).apply {
                marginEnd = margin
            }
        }
        filterGroup?.addView(radioButton)
    }
    
    private fun setupButtons() {
        rootView?.findViewById<View>(R.id.btn_close)?.setOnClickListener {
            hide()
        }
        
        rootView?.findViewById<View>(R.id.btn_settings)?.setOnClickListener {
            onOpenSettings?.invoke()
        }
        
        rootView?.findViewById<View>(R.id.btn_load_file)?.setOnClickListener {
            onLoadLocalFile?.invoke()
        }
        
        rootView?.findViewById<View>(R.id.btn_refresh)?.setOnClickListener {
            // Trigger refresh
        }
        
        rootView?.findViewById<View>(R.id.btn_retry)?.setOnClickListener {
            // Retry fetch
        }
    }
    
    private fun updateList() {
        adapter.setSubtitles(embeddedTracks, onlineTracks, localTracks)
        adapter.setSelectedSubtitle(selectedTrackId)
        updateLanguageChips()
    }
    
    private fun showLoading(message: String) {
        loadingContainer?.visibility = View.VISIBLE
        recyclerView?.visibility = View.GONE
        emptyContainer?.visibility = View.GONE
        
        pacmanLoading?.apply {
            reset()
            setStatusText(message)
            startAnimation()
        }
    }
    
    private fun showList() {
        loadingContainer?.visibility = View.GONE
        recyclerView?.visibility = View.VISIBLE
        emptyContainer?.visibility = View.GONE
        
        updateList()
    }
    
    private fun showEmpty(message: String) {
        loadingContainer?.visibility = View.GONE
        recyclerView?.visibility = View.GONE
        emptyContainer?.visibility = View.VISIBLE
        
        rootView?.findViewById<TextView>(R.id.empty_text)?.text = message
    }
    
    private fun showError(message: String) {
        pacmanLoading?.showError(message)
        
        // Show list if we have embedded tracks
        if (embeddedTracks.isNotEmpty() || localTracks.isNotEmpty()) {
            rootView?.postDelayed({ showList() }, 2000)
        }
    }
    
    private fun onSubtitleSelected(track: SubtitleTrack) {
        DebugLogger.log(TAG, "Subtitle selected: ${track.language} from ${track.source}")
        
        if (track.id == "off") {
            selectedTrackId = null
            adapter.setSelectedSubtitle(null)
            onSubtitleChosen?.invoke(null)
        } else {
            selectedTrackId = track.id
            adapter.setSelectedSubtitle(track.id)
            onSubtitleChosen?.invoke(track)
        }
        
        // Auto-hide after selection
        hide()
    }
    
    private fun onSubtitleLongPressed(track: SubtitleTrack): Boolean {
        // Could show details or copy URL
        return false
    }
    
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
            else -> "🌐"
        }
    }
}
