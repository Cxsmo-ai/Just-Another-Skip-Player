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
import androidx.fragment.app.Fragment
import com.brouken.player.BuildConfig
import com.brouken.player.R

/**
 * Premium animated fullscreen Fragment for update notifications on Android TV
 * Features: Floating orb animations, D-pad navigation, variant-aware branding
 */
class UpdateTvFragment : Fragment() {

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
        ): UpdateTvFragment {
            return UpdateTvFragment().apply {
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_update_tv, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments ?: return

        // Background glow orbs
        val glowOrb1 = view.findViewById<View>(R.id.glowOrb1)
        val glowOrb2 = view.findViewById<View>(R.id.glowOrb2)
        val iconGlowRing = view.findViewById<View>(R.id.iconGlowRing)
        val arrowIcon = view.findViewById<ImageView>(R.id.arrowIcon)

        // Content views
        val appIcon = view.findViewById<ImageView>(R.id.appIcon)
        val appNameText = view.findViewById<TextView>(R.id.appNameText)
        val currentVersion = view.findViewById<TextView>(R.id.currentVersion)
        val newVersion = view.findViewById<TextView>(R.id.newVersion)
        val changelogText = view.findViewById<TextView>(R.id.changelogText)
        val fileSizeText = view.findViewById<TextView>(R.id.fileSizeText)
        val laterButton = view.findViewById<Button>(R.id.laterButton)
        val updateButton = view.findViewById<Button>(R.id.updateButton)

        // Set variant-specific branding
        val isJasp = BuildConfig.FLAVOR.contains("jasp", ignoreCase = true)
        appNameText.text = if (isJasp) "Just Another Skip Player" else "Just Player"
        
        if (isJasp) {
            try {
                val jaspIcon = requireContext().packageManager.getApplicationIcon(requireContext().packageName)
                appIcon.setImageDrawable(jaspIcon)
            } catch (e: Exception) { }
        }

        // Set data
        currentVersion.text = "v${BuildConfig.VERSION_NAME}"
        newVersion.text = args.getString(ARG_VERSION, "")
        changelogText.text = args.getString(ARG_CHANGELOG, "• Bug fixes and improvements")
        
        val size = args.getLong(ARG_SIZE, 0)
        fileSizeText.text = "📦 ${formatSize(size)}"

        // Start ambient animations
        startOrbAnimation(glowOrb1, 8000, 30f, 20f)
        startOrbAnimation(glowOrb2, 10000, -40f, -30f)
        startGlowRingAnimation(iconGlowRing)
        startArrowAnimation(arrowIcon)

        // Start entrance animations
        startEntranceAnimations(view)

        // D-pad focus - default to Update button
        updateButton.requestFocus()

        // Button handlers
        laterButton.setOnClickListener {
            onLater?.run()
            parentFragmentManager.popBackStack()
        }

        updateButton.setOnClickListener {
            onUpdate?.run()
        }
    }

    private fun startOrbAnimation(orb: View, duration: Long, dx: Float, dy: Float) {
        val translateX = ObjectAnimator.ofFloat(orb, "translationX", 0f, dx, 0f, -dx, 0f).apply {
            repeatCount = ValueAnimator.INFINITE
        }
        val translateY = ObjectAnimator.ofFloat(orb, "translationY", 0f, dy, 0f, -dy, 0f).apply {
            repeatCount = ValueAnimator.INFINITE
        }

        AnimatorSet().apply {
            playTogether(translateX, translateY)
            this.duration = duration
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun startGlowRingAnimation(ring: View) {
        val scaleX = ObjectAnimator.ofFloat(ring, "scaleX", 1f, 1.15f, 1f).apply {
            repeatCount = ValueAnimator.INFINITE
        }
        val scaleY = ObjectAnimator.ofFloat(ring, "scaleY", 1f, 1.15f, 1f).apply {
            repeatCount = ValueAnimator.INFINITE
        }
        val alpha = ObjectAnimator.ofFloat(ring, "alpha", 0.4f, 0.6f, 0.4f).apply {
            repeatCount = ValueAnimator.INFINITE
        }

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 2500
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun startArrowAnimation(arrow: ImageView) {
        ObjectAnimator.ofFloat(arrow, "translationX", 0f, 15f, 0f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun startEntranceAnimations(rootView: View) {
        // Initial state
        rootView.alpha = 0f

        // Fade in
        rootView.animate()
            .alpha(1f)
            .setDuration(400)
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
