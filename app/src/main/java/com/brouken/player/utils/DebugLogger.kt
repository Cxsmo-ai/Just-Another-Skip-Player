package com.brouken.player.utils

import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * ╔═══════════════════════════════════════════════════════════════════════════╗
 * ║                    ULTRA DEBUG LOGGER v2.0                                ║
 * ╠═══════════════════════════════════════════════════════════════════════════╣
 * ║  • Hierarchical tags (COMPONENT.SUBSYSTEM.ACTION)                         ║
 * ║  • Log levels: TRACE, DEBUG, INFO, WARN, ERROR, FATAL                     ║
 * ║  • Structured data with key-value pairs                                   ║
 * ║  • Automatic timing for operations                                        ║
 * ║  • Session tracking with unique IDs                                       ║
 * ║  • Call stack context (function name, line number)                        ║
 * ║  • Visual formatting with icons and tree structure                        ║
 * ║  • Always on - no toggles                                                 ║
 * ╚═══════════════════════════════════════════════════════════════════════════╝
 * 
 * File location: /Android/data/com.brouken.player/files/debug_log.txt
 */
object DebugLogger {
    
    // ═══════════════════════════════════════════════════════════════════════
    // ENUMS & DATA CLASSES
    // ═══════════════════════════════════════════════════════════════════════
    
    enum class Level(val icon: String, val priority: Int) {
        TRACE("🔍", 0),
        DEBUG("🐛", 1),
        INFO("ℹ️", 2),
        WARN("⚠️", 3),
        ERROR("❌", 4),
        FATAL("💀", 5)
    }
    
    data class LogEntry(
        val id: Long,
        val timestamp: Long,
        val sessionId: String,
        val level: Level,
        val tag: String,
        val message: String,
        val data: Map<String, Any?>? = null,
        val durationMs: Long? = null,
        val caller: String? = null,
        val threadName: String = Thread.currentThread().name
    )
    
    // ═══════════════════════════════════════════════════════════════════════
    // CONFIG
    // ═══════════════════════════════════════════════════════════════════════
    
    private const val FILE_NAME = "debug_log.txt"
    private const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024 // 5MB
    private const val LOGCAT_TAG = "Player"
    
    // ═══════════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════════
    
    private val executor = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val logIdCounter = AtomicLong(0)
    
    private var logFile: File? = null
    private var sessionId: String = generateSessionId()
    private val timers = ConcurrentHashMap<String, Long>()
    
    // ═══════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════
    
    fun init(context: Context) {
        try {
            val dir = context.getExternalFilesDir(null)
            if (dir != null) {
                logFile = File(dir, FILE_NAME)
                
                // Rotate log if too large
                if (logFile?.exists() == true && logFile!!.length() > MAX_FILE_SIZE_BYTES) {
                    rotateLog()
                }
                
                // Log session start with device info
                section(LogTags.APP_START, "SESSION START")
                i(LogTags.APP_START, "App initialized", mapOf(
                    "session_id" to sessionId,
                    "log_file" to logFile?.absolutePath,
                    "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                    "android" to "API ${Build.VERSION.SDK_INT}",
                    "app_version" to getAppVersion(context)
                ))
            } else {
                Log.e(LOGCAT_TAG, "External files dir is null")
            }
        } catch (e: Exception) {
            Log.e(LOGCAT_TAG, "Failed to init DebugLogger", e)
        }
    }
    
    private fun getAppVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    private fun generateSessionId(): String {
        return UUID.randomUUID().toString().substring(0, 8)
    }
    
    private fun rotateLog() {
        try {
            val backup = File(logFile?.parent, "debug_log_prev.txt")
            backup.delete()
            logFile?.renameTo(backup)
            logFile?.createNewFile()
        } catch (e: Exception) {
            Log.e(LOGCAT_TAG, "Failed to rotate log", e)
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // CORE LOGGING METHODS
    // ═══════════════════════════════════════════════════════════════════════
    
    /** TRACE - Finest detail, for tracing code paths */
    fun t(tag: String, message: String, data: Map<String, Any?>? = null) {
        log(Level.TRACE, tag, message, data)
    }
    
    /** DEBUG - Diagnostic information */
    fun d(tag: String, message: String, data: Map<String, Any?>? = null) {
        log(Level.DEBUG, tag, message, data)
    }
    
    /** INFO - General information */
    fun i(tag: String, message: String, data: Map<String, Any?>? = null) {
        log(Level.INFO, tag, message, data)
    }
    
    /** WARN - Warning conditions */
    fun w(tag: String, message: String, data: Map<String, Any?>? = null) {
        log(Level.WARN, tag, message, data)
    }
    
    /** ERROR - Error conditions */
    fun e(tag: String, message: String, error: Throwable? = null, data: Map<String, Any?>? = null) {
        val errorData = data?.toMutableMap() ?: mutableMapOf()
        if (error != null) {
            errorData["error_type"] = error.javaClass.simpleName
            errorData["error_msg"] = error.message
            errorData["stack_trace"] = error.stackTraceToString().take(500)
        }
        log(Level.ERROR, tag, message, errorData)
    }
    
    /** FATAL - Critical errors that may crash the app */
    fun fatal(tag: String, message: String, error: Throwable? = null) {
        e(tag, "FATAL: $message", error)
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // LEGACY COMPATIBILITY
    // ═══════════════════════════════════════════════════════════════════════
    
    /** Legacy method for backwards compatibility */
    fun log(tag: String, message: String) {
        d(tag, message)
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // VISUAL HELPERS
    // ═══════════════════════════════════════════════════════════════════════
    
    /** Create a visual section header */
    fun section(tag: String, title: String) {
        val line = "═".repeat(60)
        writeRaw("\n╔$line╗")
        writeRaw("║  $title".padEnd(62) + "║")
        writeRaw("╚$line╝")
    }
    
    /** Log success result */
    fun success(tag: String, message: String, data: Map<String, Any?>? = null) {
        i(tag, "✓ $message", data)
    }
    
    /** Log failure result */
    fun fail(tag: String, message: String, data: Map<String, Any?>? = null) {
        w(tag, "✗ $message", data)
    }
    
    /** Log a step in a process */
    fun step(tag: String, stepNum: Int, total: Int, message: String) {
        d(tag, "[$stepNum/$total] $message")
    }
    
    /** Log a step in a process with additional data */
    fun step(tag: String, stepNum: Int, total: Int, message: String, data: Map<String, Any?>?) {
        d(tag, "[$stepNum/$total] $message", data)
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // TIMING HELPERS
    // ═══════════════════════════════════════════════════════════════════════
    
    /** Start a timer for an operation */
    fun startTimer(operationId: String) {
        timers[operationId] = System.currentTimeMillis()
    }
    
    /** End timer and return duration */
    fun endTimer(operationId: String): Long {
        val start = timers.remove(operationId) ?: return 0
        return System.currentTimeMillis() - start
    }
    
    /** Log with automatic timing from a started timer */
    fun timed(tag: String, operationId: String, message: String, data: Map<String, Any?>? = null) {
        val duration = endTimer(operationId)
        val timedData = (data?.toMutableMap() ?: mutableMapOf()).also {
            it["duration_ms"] = duration
        }
        i(tag, "$message (${duration}ms)", timedData)
    }
    
    /** Execute a block and log its duration */
    inline fun <T> measure(tag: String, operation: String, block: () -> T): T {
        val opId = "$operation-${System.currentTimeMillis()}"
        startTimer(opId)
        d(tag, "→ Starting: $operation")
        return try {
            val result = block()
            timed(tag, opId, "✓ Complete: $operation")
            result
        } catch (e: Exception) {
            val duration = endTimer(opId)
            e(tag, "✗ Failed: $operation (${duration}ms)", e)
            throw e
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // HTTP LOGGING HELPERS
    // ═══════════════════════════════════════════════════════════════════════
    
    /** Log HTTP request */
    fun httpRequest(method: String, url: String, headers: Map<String, String>? = null) {
        d(LogTags.HTTP_REQUEST, "$method $url", mapOf(
            "method" to method,
            "url" to url,
            "headers" to (headers?.keys?.joinToString() ?: "none")
        ))
    }
    
    /** Log HTTP response */
    fun httpResponse(url: String, code: Int, durationMs: Long, bodyPreview: String? = null) {
        val level = when {
            code in 200..299 -> Level.INFO
            code in 400..499 -> Level.WARN
            else -> Level.ERROR
        }
        log(level, LogTags.HTTP_RESPONSE, "$code response", mapOf(
            "url" to url.takeLast(80),
            "status" to code,
            "duration_ms" to durationMs,
            "body_preview" to (bodyPreview?.take(200) ?: "")
        ))
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // API CALL HELPERS
    // ═══════════════════════════════════════════════════════════════════════
    
    /** Log API call start */
    fun apiStart(tag: String, endpoint: String, params: Map<String, Any?>? = null) {
        startTimer("api-$endpoint")
        d(tag, "→ API Call: $endpoint", params)
    }
    
    /** Log API call success */
    fun apiSuccess(tag: String, endpoint: String, resultSummary: String, data: Map<String, Any?>? = null) {
        val duration = endTimer("api-$endpoint")
        i(tag, "✓ API Success: $endpoint (${duration}ms) - $resultSummary", data)
    }
    
    /** Log API call error */
    fun apiError(tag: String, endpoint: String, code: Int?, message: String) {
        val duration = endTimer("api-$endpoint")
        w(tag, "✗ API Error: $endpoint (${duration}ms)", mapOf(
            "status" to (code ?: "N/A"),
            "error" to message
        ))
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════════════
    
    fun clear() {
        executor.execute {
            try {
                logFile?.delete()
                logFile?.createNewFile()
                sessionId = generateSessionId()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════════════════
    
    private fun log(level: Level, tag: String, message: String, data: Map<String, Any?>? = null) {
        val entry = LogEntry(
            id = logIdCounter.incrementAndGet(),
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId,
            level = level,
            tag = tag,
            message = message,
            data = data,
            caller = getCaller(),
            threadName = Thread.currentThread().name
        )
        
        writeEntry(entry)
    }
    
    private fun getCaller(): String {
        return try {
            val stack = Thread.currentThread().stackTrace
            // Skip: getStackTrace, getCaller, log, [d/i/w/e], actual caller
            val callerFrame = stack.getOrNull(5) ?: stack.getOrNull(4) ?: return "unknown"
            val className = callerFrame.className.substringAfterLast('.')
            "${className}.${callerFrame.methodName}:${callerFrame.lineNumber}"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    private fun writeEntry(entry: LogEntry) {
        val timestamp = dateFormat.format(Date(entry.timestamp))
        val levelStr = entry.level.name.padEnd(5)
        
        // Build main log line
        val mainLine = "$timestamp [${entry.sessionId}] $levelStr ${entry.tag} | ${entry.message}"
        
        // Log to Logcat with appropriate level
        when (entry.level) {
            Level.TRACE, Level.DEBUG -> Log.d(entry.tag, entry.message)
            Level.INFO -> Log.i(entry.tag, entry.message)
            Level.WARN -> Log.w(entry.tag, entry.message)
            Level.ERROR, Level.FATAL -> Log.e(entry.tag, entry.message)
        }
        
        // Write to file
        if (logFile == null) return
        
        executor.execute {
            try {
                FileWriter(logFile, true).use { writer ->
                    writer.append(mainLine).append("\n")
                    
                    // Write data as tree structure
                    entry.data?.let { data ->
                        val entries = data.entries.toList()
                        entries.forEachIndexed { index, (key, value) ->
                            val prefix = if (index == entries.lastIndex) "  └─" else "  ├─"
                            val valueStr = when (value) {
                                is String -> if (value.length > 100) "${value.take(100)}..." else value
                                null -> "null"
                                else -> value.toString()
                            }
                            writer.append("$prefix $key: $valueStr\n")
                        }
                    }
                    
                    // Add caller info for debug/trace
                    if (entry.level.priority <= Level.DEBUG.priority && entry.caller != null) {
                        writer.append("  └─ @${entry.caller}\n")
                    }
                }
            } catch (e: IOException) {
                Log.e(LOGCAT_TAG, "Failed to write log", e)
            }
        }
    }
    
    private fun writeRaw(text: String) {
        if (logFile == null) return
        executor.execute {
            try {
                FileWriter(logFile, true).use { writer ->
                    writer.append(text).append("\n")
                }
            } catch (e: IOException) {
                Log.e(LOGCAT_TAG, "Failed to write raw log", e)
            }
        }
    }
}
