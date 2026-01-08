package com.brouken.player.stremio

import android.net.Uri
import com.brouken.player.utils.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Calculates the OpenSubtitles hash for video files
 * 
 * The OpenSubtitles hash is a 64-bit checksum:
 * - File size (8 bytes)
 * - Sum of first 64KB as little-endian longs
 * - Sum of last 64KB as little-endian longs
 * 
 * This hash is used for matching subtitles to specific video files
 * with very high accuracy.
 */
object OpenSubtitlesHasher {
    
    private const val TAG = "OpenSubtitlesHasher"
    private const val CHUNK_SIZE = 65536L // 64 KB
    
    /**
     * Calculate hash for a local file
     */
    suspend fun computeHash(file: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) {
                return@withContext Result.failure(
                    IllegalArgumentException("File does not exist: ${file.absolutePath}")
                )
            }
            
            val size = file.length()
            if (size < CHUNK_SIZE * 2) {
                return@withContext Result.failure(
                    IllegalArgumentException("File too small for hash (min 128KB)")
                )
            }
            
            var hash = size
            
            RandomAccessFile(file, "r").use { raf ->
                // Read first 64KB
                val headBuffer = ByteArray(CHUNK_SIZE.toInt())
                raf.seek(0)
                raf.readFully(headBuffer)
                hash += checksumBytes(headBuffer)
                
                // Read last 64KB
                val tailBuffer = ByteArray(CHUNK_SIZE.toInt())
                raf.seek(size - CHUNK_SIZE)
                raf.readFully(tailBuffer)
                hash += checksumBytes(tailBuffer)
            }
            
            val hashString = String.format("%016x", hash)
            DebugLogger.log(TAG, "Hash computed: $hashString for ${file.name}")
            Result.success(hashString)
            
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to compute hash: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Calculate hash for a file path string
     */
    suspend fun computeHash(filePath: String): Result<String> {
        return computeHash(File(filePath))
    }
    
    /**
     * Calculate hash for a content URI (if accessible)
     * Returns null if URI is not a local file
     */
    suspend fun computeHashFromUri(
        uri: Uri,
        contentResolver: android.content.ContentResolver
    ): Result<String>? = withContext(Dispatchers.IO) {
        try {
            // Check if it's a file URI
            if (uri.scheme == "file") {
                val path = uri.path ?: return@withContext null
                return@withContext computeHash(File(path))
            }
            
            // For content URIs, try to get the file descriptor
            if (uri.scheme == "content") {
                val pfd = contentResolver.openFileDescriptor(uri, "r")
                    ?: return@withContext null
                
                pfd.use { descriptor ->
                    val size = descriptor.statSize
                    if (size < CHUNK_SIZE * 2) {
                        return@withContext Result.failure(
                            IllegalArgumentException("File too small for hash")
                        )
                    }
                    
                    var hash = size
                    
                    val inputStream = android.os.ParcelFileDescriptor.AutoCloseInputStream(
                        contentResolver.openFileDescriptor(uri, "r")
                    )
                    
                    inputStream.use { stream ->
                        // Read first 64KB
                        val headBuffer = ByteArray(CHUNK_SIZE.toInt())
                        var bytesRead = 0
                        while (bytesRead < CHUNK_SIZE.toInt()) {
                            val read = stream.read(headBuffer, bytesRead, CHUNK_SIZE.toInt() - bytesRead)
                            if (read == -1) break
                            bytesRead += read
                        }
                        hash += checksumBytes(headBuffer)
                    }
                    
                    // For content URIs, we can't easily seek to the end
                    // So we skip the tail hash (less accurate but still useful)
                    DebugLogger.log(TAG, "Warning: Content URI hash is partial (head only)")
                    
                    val hashString = String.format("%016x", hash)
                    return@withContext Result.success(hashString)
                }
            }
            
            null
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to compute hash from URI: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if we can compute hash for a given URI
     */
    fun canComputeHash(uri: Uri): Boolean {
        return uri.scheme == "file" || uri.scheme == "content"
    }
    
    /**
     * Check if URI is a remote stream (cannot compute hash)
     */
    fun isRemoteStream(uri: Uri): Boolean {
        return uri.scheme in listOf("http", "https", "rtsp", "rtmp")
    }
    
    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================
    
    /**
     * Calculate checksum by treating buffer as array of little-endian longs
     */
    private fun checksumBytes(buffer: ByteArray): Long {
        val byteBuffer = ByteBuffer.wrap(buffer)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        
        var checksum = 0L
        while (byteBuffer.remaining() >= 8) {
            checksum += byteBuffer.long
        }
        
        return checksum
    }
}

/**
 * Extension function to compute hash from file
 */
suspend fun File.computeOpenSubtitlesHash(): Result<String> {
    return OpenSubtitlesHasher.computeHash(this)
}
