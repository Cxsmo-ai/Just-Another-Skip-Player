package com.brouken.player.update

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.brouken.player.R

/**
 * Transparent Activity to host the update dialog/fragment
 * Needed because PlayerActivity extends Activity, not AppCompatActivity
 */
class UpdateActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_VERSION = "extra_version"
        private const val EXTRA_CHANGELOG = "extra_changelog"
        private const val EXTRA_SIZE = "extra_size"
        private const val EXTRA_TAG = "extra_tag"
        private const val EXTRA_APK_URL = "extra_apk_url"
        private const val EXTRA_APK_NAME = "extra_apk_name"

        fun launch(context: Context, updateInfo: UpdateInfo) {
            val intent = Intent(context, UpdateActivity::class.java).apply {
                putExtra(EXTRA_VERSION, updateInfo.versionString)
                putExtra(EXTRA_CHANGELOG, updateInfo.changelog)
                putExtra(EXTRA_SIZE, updateInfo.apkSize)
                putExtra(EXTRA_TAG, updateInfo.tagName)
                putExtra(EXTRA_APK_URL, updateInfo.apkUrl)
                putExtra(EXTRA_APK_NAME, updateInfo.apkName)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update)

        val updateInfo = UpdateInfo(
            tagName = intent.getStringExtra(EXTRA_TAG) ?: "",
            name = "",
            changelog = intent.getStringExtra(EXTRA_CHANGELOG) ?: "",
            apkUrl = intent.getStringExtra(EXTRA_APK_URL),
            apkSize = intent.getLongExtra(EXTRA_SIZE, 0),
            apkName = intent.getStringExtra(EXTRA_APK_NAME) ?: "update.apk"
        )

        val isTV = packageManager.hasSystemFeature("android.software.leanback")

        if (isTV) {
            // Show TV fullscreen fragment
            val tvFragment = UpdateTvFragment.newInstance(
                updateInfo,
                Runnable { downloadAndInstall(updateInfo) },
                Runnable { 
                    UpdateManager.getInstance(this).skipVersion(updateInfo.tagName)
                    finish()
                }
            )
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragmentContainer, tvFragment)
                .commit()
        } else {
            // Show mobile bottom sheet
            val dialogFragment = UpdateDialogFragment.newInstance(
                updateInfo,
                Runnable { downloadAndInstall(updateInfo) },
                Runnable {
                    UpdateManager.getInstance(this).skipVersion(updateInfo.tagName)
                    finish()
                }
            )
            dialogFragment.show(supportFragmentManager, "update_dialog")
        }
    }

    private fun downloadAndInstall(updateInfo: UpdateInfo) {
        Toast.makeText(this, "Downloading update...", Toast.LENGTH_SHORT).show()
        
        val downloader = UpdateDownloader(this)
        downloader.downloadApk(
            updateInfo,
            { percent, downloaded, total ->
                // Progress callback
            },
            { success ->
                if (!success) {
                    runOnUiThread {
                        Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show()
                    }
                }
                finish()
            }
        )
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
