package com.brouken.player.ui.subtitle

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.brouken.player.R
import com.brouken.player.stremio.CatalogAddon
import com.brouken.player.stremio.StremioAddonsCatalogClient
import com.brouken.player.stremio.SubtitleAddon
import com.brouken.player.stremio.SubtitleAddonManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Activity for managing subtitle addon URLs with live search
 */
class SubtitleAddonsActivity : AppCompatActivity() {
    
    private lateinit var addonManager: SubtitleAddonManager
    private lateinit var catalogClient: StremioAddonsCatalogClient
    private lateinit var adapter: AddonListAdapter
    private lateinit var searchAdapter: SearchResultsAdapter
    
    private lateinit var addonList: RecyclerView
    private lateinit var emptyAddons: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var searchResults: RecyclerView
    private lateinit var searchLoading: ProgressBar
    private lateinit var searchEmpty: TextView
    private lateinit var inputUrl: EditText
    private lateinit var btnAddUrl: Button
    
    private var searchJob: Job? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subtitle_addons)
        
        addonManager = SubtitleAddonManager(this)
        catalogClient = StremioAddonsCatalogClient()
        
        // Bind views
        addonList = findViewById(R.id.addon_list)
        emptyAddons = findViewById(R.id.empty_addons)
        searchInput = findViewById(R.id.search_input)
        searchResults = findViewById(R.id.search_results)
        searchLoading = findViewById(R.id.search_loading)
        searchEmpty = findViewById(R.id.search_empty)
        inputUrl = findViewById(R.id.input_url)
        btnAddUrl = findViewById(R.id.btn_add_url)
        
        // Setup toolbar
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }
        
        findViewById<Button>(R.id.btn_add).setOnClickListener {
            inputUrl.requestFocus()
        }
        
        // Setup configured addons adapter
        adapter = AddonListAdapter(
            onToggle = { addon, enabled ->
                addonManager.setEnabled(addon.id, enabled)
            },
            onDelete = { addon ->
                deleteAddon(addon)
            },
            onEdit = { addon ->
                editAddon(addon)
            }
        )
        
        addonList.layoutManager = LinearLayoutManager(this)
        addonList.adapter = adapter
        
        // Setup search results adapter
        searchAdapter = SearchResultsAdapter { catalogAddon ->
            addCatalogAddon(catalogAddon)
        }
        searchResults.layoutManager = LinearLayoutManager(this)
        searchResults.adapter = searchAdapter
        
        // Setup drag to reorder
        setupDragAndDrop()
        
        // Setup search with debounce
        setupSearch()
        
        // Setup add button
        btnAddUrl.setOnClickListener {
            val url = inputUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                addAddon(url)
            }
        }
        
        // Load configured addons
        refreshAddons()
        
        // Initial load of all subtitle addons
        performSearch("")
    }
    
    private fun setupSearch() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                // Debounce search
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300) // 300ms debounce
                    performSearch(query)
                }
            }
        })
    }
    
    private fun performSearch(query: String) {
        searchLoading.visibility = View.VISIBLE
        searchEmpty.visibility = View.GONE
        
        lifecycleScope.launch {
            val result = catalogClient.searchSubtitleAddons(query)
            
            searchLoading.visibility = View.GONE
            
            result.fold(
                onSuccess = { addons ->
                    // Filter out already-added addons
                    val existingUrls = addonManager.getAddons().map { it.manifestUrl }.toSet()
                    val filtered = addons.filter { it.manifestUrl !in existingUrls }
                    
                    if (filtered.isEmpty()) {
                        searchEmpty.text = if (query.isBlank()) 
                            "No new subtitle addons available" 
                        else 
                            "No results for \"$query\""
                        searchEmpty.visibility = View.VISIBLE
                        searchResults.visibility = View.GONE
                    } else {
                        searchEmpty.visibility = View.GONE
                        searchResults.visibility = View.VISIBLE
                        searchAdapter.submitList(filtered)
                    }
                },
                onFailure = { error ->
                    searchEmpty.text = "Failed to load: ${error.message}"
                    searchEmpty.visibility = View.VISIBLE
                    searchResults.visibility = View.GONE
                }
            )
        }
    }
    
    private fun setupDragAndDrop() {
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                return makeMovementFlags(
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                    0
                )
            }
            
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                
                adapter.moveItem(fromPos, toPos)
                
                // Save new order
                val orderedIds = adapter.getItems().map { it.id }
                addonManager.reorderAddons(orderedIds)
                
                return true
            }
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            
            override fun isLongPressDragEnabled() = true
        })
        
        touchHelper.attachToRecyclerView(addonList)
    }
    
    private fun refreshAddons() {
        val addons = addonManager.getAddons()
        adapter.submitList(addons)
        
        emptyAddons.visibility = if (addons.isEmpty()) View.VISIBLE else View.GONE
        addonList.visibility = if (addons.isEmpty()) View.GONE else View.VISIBLE
    }
    
    private fun addCatalogAddon(catalogAddon: CatalogAddon) {
        lifecycleScope.launch {
            val result = addonManager.addAddon(
                catalogAddon.manifestUrl,
                catalogAddon.name,
                catalogAddon.logoUrl
            )
            
            result.fold(
                onSuccess = { addon ->
                    Toast.makeText(
                        this@SubtitleAddonsActivity,
                        "Added: ${addon.displayName}",
                        Toast.LENGTH_SHORT
                    ).show()
                    refreshAddons()
                    // Refresh search to remove the added item
                    performSearch(searchInput.text.toString())
                },
                onFailure = { error ->
                    Toast.makeText(
                        this@SubtitleAddonsActivity,
                        "Failed: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }
    
    private fun addAddon(url: String) {
        btnAddUrl.isEnabled = false
        btnAddUrl.text = "Adding..."
        
        lifecycleScope.launch {
            val result = addonManager.addAddon(url)
            
            result.fold(
                onSuccess = { addon ->
                    Toast.makeText(
                        this@SubtitleAddonsActivity,
                        "Added: ${addon.displayName}",
                        Toast.LENGTH_SHORT
                    ).show()
                    inputUrl.text.clear()
                    refreshAddons()
                    performSearch(searchInput.text.toString())
                },
                onFailure = { error ->
                    Toast.makeText(
                        this@SubtitleAddonsActivity,
                        "Failed: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
            
            btnAddUrl.isEnabled = true
            btnAddUrl.text = "Add"
        }
    }
    
    private fun deleteAddon(addon: SubtitleAddon) {
        addonManager.removeAddon(addon.id)
        Toast.makeText(this, "Removed: ${addon.displayName}", Toast.LENGTH_SHORT).show()
        refreshAddons()
        performSearch(searchInput.text.toString())
    }
    
    private fun editAddon(addon: SubtitleAddon) {
        // Could show dialog to rename
    }
}

/**
 * Adapter for search results from the catalog
 */
class SearchResultsAdapter(
    private val onAdd: (CatalogAddon) -> Unit
) : RecyclerView.Adapter<SearchResultsAdapter.ViewHolder>() {
    
    private var items = listOf<CatalogAddon>()
    
    fun submitList(list: List<CatalogAddon>) {
        items = list
        notifyDataSetChanged()
    }
    
    override fun getItemCount() = items.size
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_addon_search, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }
    
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val logo: ImageView = view.findViewById(R.id.addon_logo)
        private val name: TextView = view.findViewById(R.id.addon_name)
        private val description: TextView = view.findViewById(R.id.addon_description)
        private val btnAdd: ImageButton = view.findViewById(R.id.btn_add)
        
        fun bind(addon: CatalogAddon) {
            name.text = addon.name
            description.text = addon.description ?: addon.manifestUrl
            
            // Load logo with Coil
            if (!addon.logoUrl.isNullOrEmpty()) {
                logo.load(addon.logoUrl) {
                    crossfade(true)
                    placeholder(R.drawable.rounded_corner_bg)
                    error(R.drawable.rounded_corner_bg)
                    transformations(RoundedCornersTransformation(16f))
                }
            } else {
                logo.setImageResource(R.drawable.rounded_corner_bg)
            }
            
            btnAdd.setOnClickListener {
                onAdd(addon)
            }
            
            itemView.setOnClickListener {
                onAdd(addon)
            }
        }
    }
}

/**
 * Adapter for configured addon list
 */
class AddonListAdapter(
    private val onToggle: (SubtitleAddon, Boolean) -> Unit,
    private val onDelete: (SubtitleAddon) -> Unit,
    private val onEdit: (SubtitleAddon) -> Unit
) : RecyclerView.Adapter<AddonListAdapter.ViewHolder>() {
    
    private var items = mutableListOf<SubtitleAddon>()
    
    fun submitList(list: List<SubtitleAddon>) {
        items = list.toMutableList()
        notifyDataSetChanged()
    }
    
    fun getItems(): List<SubtitleAddon> = items
    
    fun moveItem(from: Int, to: Int) {
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }
    
    override fun getItemCount() = items.size
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_addon, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }
    
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val logo: ImageView = view.findViewById(R.id.addon_logo)
        private val priority: TextView = view.findViewById(R.id.addon_priority)
        private val name: TextView = view.findViewById(R.id.addon_name)
        private val url: TextView = view.findViewById(R.id.addon_url)
        private val status: TextView = view.findViewById(R.id.addon_status)
        private val statusIcon: ImageView = view.findViewById(R.id.status_icon)
        private val switch: androidx.appcompat.widget.SwitchCompat = view.findViewById(R.id.addon_switch)
        private val btnMenu: ImageButton = view.findViewById(R.id.btn_menu)
        
        fun bind(addon: SubtitleAddon, position: Int) {
            // Load logo with Coil
            if (!addon.logoUrl.isNullOrEmpty()) {
                logo.load(addon.logoUrl) {
                    crossfade(true)
                    placeholder(R.drawable.rounded_corner_bg)
                    error(R.drawable.rounded_corner_bg)
                    transformations(RoundedCornersTransformation(16f))
                }
            } else {
                logo.setImageResource(R.drawable.rounded_corner_bg)
            }
            
            priority.text = "${position + 1}."
            name.text = addon.displayName
            url.text = addon.baseUrl.removePrefix("https://").removePrefix("http://")
            
            // Status
            if (addon.lastError != null) {
                statusIcon.setColorFilter(0xFFFF5722.toInt()) // Orange
                status.text = "Error: ${addon.lastError}"
                status.visibility = View.VISIBLE
            } else if (addon.lastUsed != null) {
                statusIcon.setColorFilter(0xFF4CAF50.toInt()) // Green
                status.text = "Last used: ${formatTime(addon.lastUsed)}"
                status.visibility = View.VISIBLE
            } else {
                statusIcon.setColorFilter(0xFF4CAF50.toInt())
                status.visibility = View.GONE
            }
            
            // Switch
            switch.isChecked = addon.isEnabled
            switch.setOnCheckedChangeListener { _, isChecked ->
                onToggle(addon, isChecked)
            }
            
            // Menu
            btnMenu.setOnClickListener { view ->
                showMenu(view, addon)
            }
        }
        
        private fun showMenu(anchor: View, addon: SubtitleAddon) {
            val popup = android.widget.PopupMenu(anchor.context, anchor)
            popup.menu.add("Rename")
            popup.menu.add("Test Connection")
            popup.menu.add("Delete")
            
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Rename" -> onEdit(addon)
                    "Delete" -> onDelete(addon)
                    "Test Connection" -> {
                        // Could test connection
                    }
                }
                true
            }
            
            popup.show()
        }
        
        private fun formatTime(timestamp: Long): String {
            val diff = System.currentTimeMillis() - timestamp
            return when {
                diff < 60_000 -> "Just now"
                diff < 3600_000 -> "${diff / 60_000}m ago"
                diff < 86400_000 -> "${diff / 3600_000}h ago"
                else -> "${diff / 86400_000}d ago"
            }
        }
    }
}
