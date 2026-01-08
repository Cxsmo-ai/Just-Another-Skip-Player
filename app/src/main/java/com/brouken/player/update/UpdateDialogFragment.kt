package com.brouken.player.update

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.brouken.player.BuildConfig
import com.brouken.player.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Premium animated bottom sheet dialog for update notifications on Mobile
 */
class UpdateDialogFragment : BottomSheetDialogFragment() {

    private var updateInfo: UpdateInfo? = null
    private var onUpdate: Runnable? = null
    private var onLater: Runnable? = null

    companion object {
        private const val ARG_VERSION = "version"
        private const val ARG_CHANGELOG = "changelog"
        private const val ARG_SIZE = "size"
        private const val ARG_TAG = "tag"

        fun newInstance(
            updateInfo: UpdateInfo,
            onUpdate: Runnable,
            onLater: Runnable
        ): UpdateDialogFragment {
            return UpdateDialogFragment().apply {
                this.updateInfo = updateInfo
                this.onUpdate = onUpdate
                this.onLater = onLater
                arguments = Bundle().apply {
                    putString(ARG_VERSION, updateInfo.versionString)
                    putString(ARG_CHANGELOG, updateInfo.changelog)
                    putLong(ARG_SIZE, updateInfo.apkSize)
                    putString(ARG_TAG, updateInfo.tagName)
                }
            }
        }
    }

    override fun getTheme(): Int = R.style.UpdateBottomSheetStyle

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_update, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments ?: return

        // Bind views
        val iconGlow = view.findViewById<View>(R.id.iconGlow)
        val appIcon = view.findViewById<ImageView>(R.id.appIcon)
        val updateBadge = view.findViewById<TextView>(R.id.updateBadge)
        val currentVersion = view.findViewById<TextView>(R.id.currentVersion)
        val newVersion = view.findViewById<TextView>(R.id.newVersion)
        val changelogText = view.findViewById<TextView>(R.id.changelogText)
        val fileSizeText = view.findViewById<TextView>(R.id.fileSizeText)
        val laterButton = view.findViewById<Button>(R.id.laterButton)
        val updateButton = view.findViewById<Button>(R.id.updateButton)

        // Set data
        currentVersion.text = "v${BuildConfig.VERSION_NAME}"
        newVersion.text = args.getString(ARG_VERSION, "")
        changelogText.text = args.getString(ARG_CHANGELOG, "• Bug fixes and improvements")
        
        val size = args.getLong(ARG_SIZE, 0)
        fileSizeText.text = "📦 Download size: ${formatSize(size)}"

        // Set variant-specific icon
        if (BuildConfig.FLAVOR.contains("jasp", ignoreCase = true)) {
            try {
                val jaspIcon = requireContext().packageManager.getApplicationIcon(requireContext().packageName)
                appIcon.setImageDrawable(jaspIcon)
            } catch (e: Exception) {
                // Fallback to default
            }
        }

        // Start animations
        startIconGlowAnimation(iconGlow)
        startEntranceAnimations(view)

        // Button click handlers
        laterButton.setOnClickListener {
            onLater?.run()
            dismiss()
        }

        updateButton.setOnClickListener {
            onUpdate?.run()
            dismiss()
        }
    }

    private fun startIconGlowAnimation(glowView: View) {
        // Pulsing glow effect
        val scaleX = ObjectAnimator.ofFloat(glowView, "scaleX", 1f, 1.2f, 1f).apply {
            repeatCount = ValueAnimator.INFINITE
        }
        val scaleY = ObjectAnimator.ofFloat(glowView, "scaleY", 1f, 1.2f, 1f).apply {
            repeatCount = ValueAnimator.INFINITE
        }
        val alpha = ObjectAnimator.ofFloat(glowView, "alpha", 0.3f, 0.5f, 0.3f).apply {
            repeatCount = ValueAnimator.INFINITE
        }

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 2000
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun startEntranceAnimations(rootView: View) {
        // Fade in and slide up effect
        rootView.alpha = 0f
        rootView.translationY = 50f

        rootView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
}
