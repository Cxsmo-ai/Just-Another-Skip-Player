package com.brouken.player.utils

/**
 * Centralized log tag definitions for hierarchical, searchable logging.
 * 
 * Tag Format: COMPONENT.SUBSYSTEM.ACTION
 * 
 * Usage: DebugLogger.d(LogTags.API_NOTSCARE_FETCH, "Starting request", mapOf("url" to url))
 */
object LogTags {
    // ═══════════════════════════════════════════════════════════════════
    // APP LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════
    const val APP_START = "APP.START"
    const val APP_STOP = "APP.STOP"
    const val APP_CRASH = "APP.CRASH"
    const val APP_CONFIG = "APP.CONFIG"
    
    // ═══════════════════════════════════════════════════════════════════
    // PLAYER - ExoPlayer state and operations
    // ═══════════════════════════════════════════════════════════════════
    const val PLAYER_INIT = "PLAYER.INIT"
    const val PLAYER_STATE = "PLAYER.STATE"
    const val PLAYER_READY = "PLAYER.READY"
    const val PLAYER_BUFFER = "PLAYER.BUFFER"
    const val PLAYER_SEEK = "PLAYER.SEEK"
    const val PLAYER_PLAY = "PLAYER.PLAY"
    const val PLAYER_PAUSE = "PLAYER.PAUSE"
    const val PLAYER_END = "PLAYER.END"
    const val PLAYER_ERROR = "PLAYER.ERROR"
    const val PLAYER_TRACK = "PLAYER.TRACK"
    
    // ═══════════════════════════════════════════════════════════════════
    // SKIP - Intro/Outro/JumpScare skip functionality
    // ═══════════════════════════════════════════════════════════════════
    const val SKIP_INIT = "SKIP.INIT"
    const val SKIP_FETCH = "SKIP.FETCH"
    const val SKIP_FOUND = "SKIP.FOUND"
    const val SKIP_TRIGGER = "SKIP.TRIGGER"
    const val SKIP_EXECUTE = "SKIP.EXECUTE"
    const val SKIP_BUTTON = "SKIP.BUTTON"
    const val SKIP_CACHE = "SKIP.CACHE"
    
    // Jump Scare specific
    const val JUMPSCARE_INIT = "JUMPSCARE.INIT"
    const val JUMPSCARE_FETCH = "JUMPSCARE.FETCH"
    const val JUMPSCARE_PARSE = "JUMPSCARE.PARSE"
    const val JUMPSCARE_FILTER = "JUMPSCARE.FILTER"
    const val JUMPSCARE_FOUND = "JUMPSCARE.FOUND"
    const val JUMPSCARE_WARN = "JUMPSCARE.WARN"
    const val JUMPSCARE_SKIP = "JUMPSCARE.SKIP"
    const val JUMPSCARE_MONITOR = "JUMPSCARE.MONITOR"
    const val JUMPSCARE_RESULT = "JUMPSCARE.RESULT"
    
    // IntroDB submission
    const val SUBMIT_START = "SUBMIT.START"
    const val SUBMIT_VALIDATE = "SUBMIT.VALIDATE"
    const val SUBMIT_SEND = "SUBMIT.SEND"
    const val SUBMIT_SUCCESS = "SUBMIT.SUCCESS"
    const val SUBMIT_FAIL = "SUBMIT.FAIL"
    
    // ═══════════════════════════════════════════════════════════════════
    // API - External API calls
    // ═══════════════════════════════════════════════════════════════════
    // IntroDB
    const val API_INTRODB_GET = "API.INTRODB.GET"
    const val API_INTRODB_POST = "API.INTRODB.POST"
    const val API_INTRODB_RESP = "API.INTRODB.RESP"
    const val API_INTRODB_ERR = "API.INTRODB.ERR"
    
    // Cinemeta
    const val API_CINEMETA_SEARCH = "API.CINEMETA.SEARCH"
    const val API_CINEMETA_RESP = "API.CINEMETA.RESP"
    const val API_CINEMETA_ERR = "API.CINEMETA.ERR"
    
    // Jikan (MAL)
    const val API_JIKAN_SEARCH = "API.JIKAN.SEARCH"
    const val API_JIKAN_RESP = "API.JIKAN.RESP"
    const val API_JIKAN_MATCH = "API.JIKAN.MATCH"
    const val API_JIKAN_ERR = "API.JIKAN.ERR"
    
    // NotScare.me
    const val API_NOTSCARE_URL = "API.NOTSCARE.URL"
    const val API_NOTSCARE_FETCH = "API.NOTSCARE.FETCH"
    const val API_NOTSCARE_PARSE = "API.NOTSCARE.PARSE"
    const val API_NOTSCARE_RESP = "API.NOTSCARE.RESP"
    const val API_NOTSCARE_ERR = "API.NOTSCARE.ERR"
    
    // AniSkip
    const val API_ANISKIP_FETCH = "API.ANISKIP.FETCH"
    const val API_ANISKIP_RESP = "API.ANISKIP.RESP"
    const val API_ANISKIP_ERR = "API.ANISKIP.ERR"
    
    // ═══════════════════════════════════════════════════════════════════
    // PARSE - Name/filename parsing
    // ═══════════════════════════════════════════════════════════════════
    const val PARSE_INPUT = "PARSE.INPUT"
    const val PARSE_REGEX = "PARSE.REGEX"
    const val PARSE_SEASON = "PARSE.SEASON"
    const val PARSE_EPISODE = "PARSE.EPISODE"
    const val PARSE_YEAR = "PARSE.YEAR"
    const val PARSE_CLEAN = "PARSE.CLEAN"
    const val PARSE_RESULT = "PARSE.RESULT"
    
    // ═══════════════════════════════════════════════════════════════════
    // UI - User interface events
    // ═══════════════════════════════════════════════════════════════════
    const val UI_INIT = "UI.INIT"
    const val UI_STYLE = "UI.STYLE"
    const val UI_BUTTON = "UI.BUTTON"
    const val UI_TOAST = "UI.TOAST"
    const val UI_DIALOG = "UI.DIALOG"
    const val UI_GESTURE = "UI.GESTURE"
    const val UI_CONTROLLER = "UI.CONTROLLER"
    
    // ═══════════════════════════════════════════════════════════════════
    // REMOTE - Remote control server
    // ═══════════════════════════════════════════════════════════════════
    const val REMOTE_START = "REMOTE.START"
    const val REMOTE_STOP = "REMOTE.STOP"
    const val REMOTE_REQUEST = "REMOTE.REQUEST"
    const val REMOTE_RESPONSE = "REMOTE.RESPONSE"
    const val REMOTE_ERROR = "REMOTE.ERROR"
    
    // ═══════════════════════════════════════════════════════════════════
    // MEDIA - Media info and metadata
    // ═══════════════════════════════════════════════════════════════════
    const val MEDIA_LOAD = "MEDIA.LOAD"
    const val MEDIA_INFO = "MEDIA.INFO"
    const val MEDIA_CHAPTER = "MEDIA.CHAPTER"
    const val MEDIA_SUBTITLE = "MEDIA.SUBTITLE"
    const val MEDIA_AUDIO = "MEDIA.AUDIO"
    
    // ═══════════════════════════════════════════════════════════════════
    // CACHE - Caching operations
    // ═══════════════════════════════════════════════════════════════════
    const val CACHE_HIT = "CACHE.HIT"
    const val CACHE_MISS = "CACHE.MISS"
    const val CACHE_STORE = "CACHE.STORE"
    const val CACHE_CLEAR = "CACHE.CLEAR"
    
    // ═══════════════════════════════════════════════════════════════════
    // HTTP - Low-level HTTP operations
    // ═══════════════════════════════════════════════════════════════════
    const val HTTP_REQUEST = "HTTP.REQUEST"
    const val HTTP_RESPONSE = "HTTP.RESPONSE"
    const val HTTP_ERROR = "HTTP.ERROR"
    const val HTTP_TIMEOUT = "HTTP.TIMEOUT"
}
