package com.brouken.player.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Handles downloading and installing APK updates
 */
class UpdateDownloader(private val context: Context) {
    
    companion object {
        private const val TAG = "UpdateDownloader"
    }
    
    private var downloadId: Long = -1
    private var downloadReceiver: BroadcastReceiver? = null
    private var progressCallback: ProgressCallback? = null
    private var completeCallback: CompleteCallback? = null
    
    fun interface ProgressCallback {
        fun onProgress(percent: Int, downloaded: Long, total: Long)
    }
    
    fun interface CompleteCallback {
        fun onComplete(success: Boolean)
    }
    
    /**
     * Start downloading the APK
     */
    fun downloadApk(
        updateInfo: UpdateInfo,
        onProgress: ProgressCallback,
        onComplete: CompleteCallback
    ) {
        val apkUrl = updateInfo.apkUrl
        if (apkUrl.isNullOrBlank()) {
            Log.e(TAG, "No APK URL available")
            onComplete.onComplete(false)
            return
        }
        
        progressCallback = onProgress
        completeCallback = onComplete
        
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            
            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("Downloading Update")
                .setDescription(updateInfo.apkName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, updateInfo.apkName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            
            downloadId = downloadManager.enqueue(request)
            Log.d(TAG, "Download started: $downloadId")
            
            // Register receiver for download complete
            registerReceiver()
            
            // Start progress monitoring
            monitorProgress()
            
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            onComplete(false)
        }
    }
    
    private fun registerReceiver() {
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    handleDownloadComplete()
                }
            }
        }
        
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(downloadReceiver, filter)
        }
    }
    
    private fun monitorProgress() {
        Thread {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            var downloading = true
            
            while (downloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                
                if (cursor.moveToFirst()) {
                    val bytesDownloaded = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    )
                    val bytesTotal = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    )
                    val status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    )
                    
                    if (bytesTotal > 0) {
                        val percent = ((bytesDownloaded * 100) / bytesTotal).toInt()
                        progressCallback?.onProgress(percent, bytesDownloaded, bytesTotal)
                    }
                    
                    if (status == DownloadManager.STATUS_SUCCESSFUL || 
                        status == DownloadManager.STATUS_FAILED) {
                        downloading = false
                    }
                }
                cursor.close()
                
                Thread.sleep(500)
            }
        }.start()
    }
    
    private fun handleDownloadComplete() {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        
        if (cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                val localUri = cursor.getString(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
                )
                Log.d(TAG, "Download complete: $localUri")
                installApk(Uri.parse(localUri))
                completeCallback?.onComplete(true)
            } else {
                Log.e(TAG, "Download failed with status: $status")
                completeCallback?.onComplete(false)
            }
        }
        cursor.close()
        
        unregisterReceiver()
    }
    
    private fun installApk(fileUri: Uri) {
        try {
            val file = File(fileUri.path ?: return)
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            
            context.startActivity(intent)
            Log.d(TAG, "Install intent launched")
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
        }
    }
    
    fun cancelDownload() {
        if (downloadId != -1L) {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.remove(downloadId)
            downloadId = -1
        }
        unregisterReceiver()
    }
    
    private fun unregisterReceiver() {
        try {
            downloadReceiver?.let { context.unregisterReceiver(it) }
            downloadReceiver = null
        } catch (e: Exception) {
            // Ignore
        }
    }
}
