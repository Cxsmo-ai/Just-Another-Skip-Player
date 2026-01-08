package com.brouken.player.update

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.brouken.player.BuildConfig
import com.brouken.player.R

/**
 * Android TV fullscreen update fragment
 */
class UpdateTvFragment : Fragment() {
    
    private var updateInfo: UpdateInfo? = null
    private var onUpdateClick: Runnable? = null
    private var onLaterClick: Runnable? = null
    
    companion object {
        fun newInstance(
            updateInfo: UpdateInfo,
            onUpdate: Runnable,
            onLater: Runnable
        ): UpdateTvFragment {
            return UpdateTvFragment().apply {
                this.updateInfo = updateInfo
                this.onUpdateClick = onUpdate
                this.onLaterClick = onLater
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_update_tv, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val info = updateInfo ?: return
        
        // Set app name and logo based on variant
        val appName = view.findViewById<TextView>(R.id.app_name)
        val isJasp = BuildConfig.FLAVOR.contains("jasp", ignoreCase = true)
        appName.text = if (isJasp) "Just Another Skip Player" else "Just Player"
        
        // Version info
        view.findViewById<TextView>(R.id.version_new).text = "Version ${info.versionString}"
        view.findViewById<TextView>(R.id.version_current).text = "You have ${getCurrentVersion()}"
        
        // Changelog
        view.findViewById<TextView>(R.id.changelog).text = formatChangelog(info.changelog)
        
        // Buttons with focus handling for D-pad
        val btnLater = view.findViewById<Button>(R.id.btn_later)
        val btnUpdate = view.findViewById<Button>(R.id.btn_update)
        
        btnLater.setOnClickListener {
            onLaterClick?.run()
        }
        
        btnUpdate.setOnClickListener {
            onUpdateClick?.run()
        }
        
        // Set initial focus to update button
        btnUpdate.requestFocus()
        
        // Add focus change listeners for visual feedback
        btnLater.setOnFocusChangeListener { v, hasFocus ->
            v.alpha = if (hasFocus) 1.0f else 0.7f
        }
        
        btnUpdate.setOnFocusChangeListener { v, hasFocus ->
            v.alpha = if (hasFocus) 1.0f else 0.7f
        }
    }
    
    private fun getCurrentVersion(): String {
        return try {
            requireContext().packageManager.getPackageInfo(
                requireContext().packageName, 0
            ).versionName ?: "?"
        } catch (e: PackageManager.NameNotFoundException) {
            "?"
        }
    }
    
    private fun formatChangelog(changelog: String): String {
        return changelog
            .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("\\*(.+?)\\*"), "$1")
            .replace(Regex("^-\\s*", RegexOption.MULTILINE), "• ")
            .trim()
    }
}
