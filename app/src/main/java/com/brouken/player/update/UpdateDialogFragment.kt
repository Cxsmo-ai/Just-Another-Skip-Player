package com.brouken.player.update

import android.app.Dialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.brouken.player.BuildConfig
import com.brouken.player.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Mobile update dialog - shown as bottom sheet
 */
class UpdateDialogFragment : BottomSheetDialogFragment() {
    
    private var updateInfo: UpdateInfo? = null
    private var onUpdateClick: Runnable? = null
    private var onLaterClick: Runnable? = null
    
    companion object {
        fun newInstance(
            updateInfo: UpdateInfo,
            onUpdate: Runnable,
            onLater: Runnable
        ): UpdateDialogFragment {
            return UpdateDialogFragment().apply {
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
        return inflater.inflate(R.layout.dialog_update, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val info = updateInfo ?: return
        
        // Set app name and logo based on variant
        val appName = view.findViewById<TextView>(R.id.app_name)
        val appLogo = view.findViewById<ImageView>(R.id.app_logo)
        
        val isJasp = BuildConfig.FLAVOR.contains("jasp", ignoreCase = true)
        appName.text = if (isJasp) "Just Another Skip Player" else "Just Player"
        
        // Version info
        view.findViewById<TextView>(R.id.version_new).text = "v${info.versionString}"
        view.findViewById<TextView>(R.id.version_current).text = "You have v${getCurrentVersion()}"
        
        // Changelog
        view.findViewById<TextView>(R.id.changelog).text = formatChangelog(info.changelog)
        
        // Buttons
        view.findViewById<Button>(R.id.btn_later).setOnClickListener {
            onLaterClick?.run()
            dismiss()
        }
        
        view.findViewById<Button>(R.id.btn_update).setOnClickListener {
            onUpdateClick?.run()
            dismiss()
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
        // Simple markdown to plain text conversion
        return changelog
            .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("\\*(.+?)\\*"), "$1")
            .replace(Regex("^-\\s*", RegexOption.MULTILINE), "• ")
            .trim()
    }
}
