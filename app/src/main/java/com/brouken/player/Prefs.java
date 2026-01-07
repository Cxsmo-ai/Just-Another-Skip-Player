package com.brouken.player;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.ui.AspectRatioFrameLayout;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.Set;

class Prefs {
    // Previously used
    // private static final String PREF_KEY_AUDIO_TRACK = "audioTrack";
    // private static final String PREF_KEY_AUDIO_TRACK_FFMPEG = "audioTrackFfmpeg";
    // private static final String PREF_KEY_SUBTITLE_TRACK = "subtitleTrack";

    private static final String PREF_KEY_MEDIA_URI = "mediaUri";
    private static final String PREF_KEY_MEDIA_TYPE = "mediaType";
    private static final String PREF_KEY_BRIGHTNESS = "brightness";
    private static final String PREF_KEY_FIRST_RUN = "firstRun";
    private static final String PREF_KEY_SUBTITLE_URI = "subtitleUri";

    private static final String PREF_KEY_AUDIO_TRACK_ID = "audioTrackId";
    private static final String PREF_KEY_SUBTITLE_TRACK_ID = "subtitleTrackId";
    private static final String PREF_KEY_RESIZE_MODE = "resizeMode";
    private static final String PREF_KEY_ORIENTATION = "orientation";
    private static final String PREF_KEY_SCALE = "scale";
    private static final String PREF_KEY_SCOPE_URI = "scopeUri";
    private static final String PREF_KEY_ASK_SCOPE = "askScope";
    private static final String PREF_KEY_AUTO_PIP = "autoPiP";
    private static final String PREF_KEY_TUNNELING = "tunneling";
    private static final String PREF_KEY_SKIP_SILENCE = "skipSilence";
    private static final String PREF_KEY_FRAMERATE_MATCHING = "frameRateMatching";
    private static final String PREF_KEY_REPEAT_TOGGLE = "repeatToggle";
    private static final String PREF_KEY_SPEED = "speed";
    private static final String PREF_KEY_FILE_ACCESS = "fileAccess";
    private static final String PREF_KEY_DECODER_PRIORITY = "decoderPriority";
    private static final String PREF_KEY_MAP_DV7 = "mapDV7ToHevc";
    private static final String PREF_KEY_LANGUAGE_AUDIO = "languageAudio";
    private static final String PREF_KEY_SUBTITLE_STYLE_EMBEDDED = "subtitleStyleEmbedded";
    private static final String PREF_KEY_SUBTITLE_STYLE_BOLD = "subtitleStyleBold";
    
    // Intro Skip Preferences
    private static final String PREF_KEY_INTRO_SKIP_ENABLED = "introSkipEnabled";
    private static final String PREF_KEY_INTRO_SKIP_MODE = "introSkipMode";
    private static final String PREF_KEY_SKIP_BUTTON_STYLE = "skipButtonStyle";

    private static final String PREF_KEY_MAL_CLIENT_ID = "malClientId";
    private static final String PREF_KEY_CINEMETA_URL = "cinemataUrl";
    private static final String PREF_KEY_INTRODB_API_KEY = "introDbApiKey";
    private static final String PREF_KEY_REMOTE_CONTROL_ENABLED = "remoteControlEnabled";
    private static final String PREF_KEY_JUMP_SCARE_SKIP_ENABLED = "jumpScareSkipEnabled";
    private static final String PREF_KEY_INTROHATER_API_KEY = "introHaterApiKey";

    // Format Support Preferences
    private static final String PREF_KEY_FORCE_SDR_TONEMAPPING = "forceSdrTonemapping";
    private static final String PREF_KEY_IMMERSIVE_AUDIO_FALLBACK = "immersiveAudioFallback";
    private static final String PREF_KEY_AURO_CHANNEL_MAPPING = "auroChannelMapping";
    private static final String PREF_KEY_DISABLE_DOLBY_VISION = "disableDolbyVision";

    // Anime Skip Preferences
    private static final String PREF_KEY_ANIMESKIP_AUTH_TOKEN = "animeSkipAuthToken";
    private static final String PREF_KEY_ANIMESKIP_REFRESH_TOKEN = "animeSkipRefreshToken";
    private static final String PREF_KEY_ANIMESKIP_USERNAME = "animeSkipUsername";
    private static final String PREF_KEY_ANIMESKIP_SKIP_BRANDING = "animeSkipBranding";
    private static final String PREF_KEY_ANIMESKIP_SKIP_RECAPS = "animeSkipRecaps";
    private static final String PREF_KEY_ANIMESKIP_SKIP_TITLE_CARD = "animeSkipTitleCard";
    private static final String PREF_KEY_ANIMESKIP_SKIP_INTROS = "animeSkipIntros";
    private static final String PREF_KEY_ANIMESKIP_SKIP_NEW_INTROS = "animeSkipNewIntros";
    private static final String PREF_KEY_ANIMESKIP_SKIP_MIXED_INTROS = "animeSkipMixedIntros";
    private static final String PREF_KEY_ANIMESKIP_SKIP_CANON = "animeSkipCanon";
    private static final String PREF_KEY_ANIMESKIP_SKIP_FILLER = "animeSkipFiller";
    private static final String PREF_KEY_ANIMESKIP_SKIP_TRANSITIONS = "animeSkipTransitions";
    private static final String PREF_KEY_ANIMESKIP_SKIP_CREDITS = "animeSkipCredits";
    private static final String PREF_KEY_ANIMESKIP_SKIP_NEW_CREDITS = "animeSkipNewCredits";
    private static final String PREF_KEY_ANIMESKIP_SKIP_MIXED_CREDITS = "animeSkipMixedCredits";
    private static final String PREF_KEY_ANIMESKIP_SKIP_PREVIEW = "animeSkipPreview";
    private static final String PREF_KEY_ANIMESKIP_PIRATE_MODE = "animeSkipPirateMode";
    private static final String PREF_KEY_ANIMESKIP_TIME_SHIFT = "animeSkipTimeShift";

    // Trakt Preferences
    private static final String PREF_KEY_TRAKT_ACCESS_TOKEN = "traktAccessToken";
    private static final String PREF_KEY_TRAKT_REFRESH_TOKEN = "traktRefreshToken";
    private static final String PREF_KEY_TRAKT_TOKEN_EXPIRY = "traktTokenExpiry";
    private static final String PREF_KEY_TRAKT_CLIENT_ID = "traktClientId";
    private static final String PREF_KEY_TRAKT_CLIENT_SECRET = "traktClientSecret";
    private static final String PREF_KEY_TRAKT_ENABLED = "traktEnabled";
    private static final String PREF_KEY_TRAKT_TOASTS_ENABLED = "traktToastsEnabled";
    private static final String PREF_KEY_PREFER_FILE_NAME_TITLE = "preferFileNameTitle";

    public static final String TRACK_DEFAULT = "default";
    public static final String TRACK_DEVICE = "device";

    final Context mContext;
    final SharedPreferences mSharedPreferences;

    public Uri mediaUri;
    public Uri subtitleUri;
    public Uri scopeUri;
    public String mediaType;
    public int resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    public Utils.Orientation orientation = Utils.Orientation.UNSPECIFIED;
    public float scale = 1.f;
    public float speed = 1.f;

    public String subtitleTrackId;
    public String audioTrackId;

    public int brightness = -1;
    public boolean firstRun = true;
    public boolean askScope = true;
    public boolean autoPiP = false;

    public boolean tunneling = false;
    public boolean skipSilence = false;
    public boolean frameRateMatching = false;
    public boolean repeatToggle = false;
    public String fileAccess = "auto";
    public int decoderPriority = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER; // Prefer FFmpeg for DV/HDR fallback
    public boolean mapDV7ToHevc = true; // Auto-convert DV P7 to HEVC for non-DV displays
    public String languageAudio = TRACK_DEVICE;
    public boolean subtitleStyleEmbedded = true;
    public boolean subtitleStyleBold = false;

    // Intro Skip Settings
    public boolean introSkipEnabled = false;
    public String introSkipMode = "button"; // "auto" or "button"
    public String skipButtonStyle = "default"; // "default" or "netflix"

    public String malClientId = "";
    public String cinemataUrl = "https://v3-cinemeta.strem.io";
    public String introDbApiKey = ""; // IntroDB submission API key (idb_...)
    public boolean remoteControlEnabled = false;
    public boolean jumpScareSkipEnabled = false; // Jump scare auto-skip
    // IntroHater API key - USE YOUR DEBRID SERVICE API KEY!
    // TorBox: torbox.app → Account Settings → API
    // Real-Debrid: real-debrid.com/apitoken
    // AllDebrid: alldebrid.com → Account → API Keys
    // Premiumize: premiumize.me → Account → API Key
    public String introHaterApiKey = "";

    // Format Support Preferences
    public boolean forceSdrTonemapping = false; // Force HDR/DV to SDR conversion
    public boolean immersiveAudioFallback = true; // Auto fallback for Atmos/DTS:X/Auro
    public boolean auroChannelMapping = true; // Map Auro-3D to Atmos or stereo
    public boolean disableDolbyVision = false; // Disable DV, fall back to HDR10/SDR

    // Anime Skip Fields
    public String animeSkipAuthToken = "";
    public String animeSkipRefreshToken = "";
    public String animeSkipUsername = "";
    public boolean animeSkipBranding = false;     // Off for pirated sources
    public boolean animeSkipRecaps = false;       // Off for pirated sources
    public boolean animeSkipTitleCard = true;     // Skip by default
    public boolean animeSkipIntros = true;        // Skip by default
    public boolean animeSkipNewIntros = false;    // Watch by default
    public boolean animeSkipMixedIntros = false;  // Watch by default
    public boolean animeSkipCanon = false;        // Watch by default
    public boolean animeSkipFiller = true;        // Skip by default
    public boolean animeSkipTransitions = true;   // Skip by default
    public boolean animeSkipCredits = true;       // Skip by default
    public boolean animeSkipNewCredits = false;   // Watch by default
    public boolean animeSkipMixedCredits = false; // Watch by default
    public boolean animeSkipPreview = false;      // Off for pirated sources
    public boolean animeSkipPirateMode = true;    // Auto-adjust for unofficial sources
    public int animeSkipTimeShift = 0;            // Manual timestamp shift (-30 to +30 seconds)

    // Trakt Fields
    public String traktAccessToken = "";
    public String traktRefreshToken = "";
    public long traktTokenExpiry = 0;
    public String traktClientId = "";
    public String traktClientSecret = "";
    public boolean traktEnabled = false;
    public boolean traktToastsEnabled = true; // Show Trakt toast notifications (default: on)

    // Title Display
    public boolean preferFileNameTitle = true; // Default to file name, toggle for metadata

    public int seekDuration = 10000; // Default 10s
    private static final String PREF_KEY_SEEK_DURATION = "seekDuration";

    private LinkedHashMap positions;

    public boolean persistentMode = true;
    public long nonPersitentPosition = -1L;

    public Prefs(Context context) {
        mContext = context;
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        loadSavedPreferences();
        loadPositions();
    }

    private void loadSavedPreferences() {
        if (mSharedPreferences.contains(PREF_KEY_MEDIA_URI))
            mediaUri = Uri.parse(mSharedPreferences.getString(PREF_KEY_MEDIA_URI, null));
        if (mSharedPreferences.contains(PREF_KEY_MEDIA_TYPE))
            mediaType = mSharedPreferences.getString(PREF_KEY_MEDIA_TYPE, null);
        brightness = mSharedPreferences.getInt(PREF_KEY_BRIGHTNESS, brightness);
        firstRun = mSharedPreferences.getBoolean(PREF_KEY_FIRST_RUN, firstRun);
        if (mSharedPreferences.contains(PREF_KEY_SUBTITLE_URI))
            subtitleUri = Uri.parse(mSharedPreferences.getString(PREF_KEY_SUBTITLE_URI, null));
        if (mSharedPreferences.contains(PREF_KEY_AUDIO_TRACK_ID))
            audioTrackId = mSharedPreferences.getString(PREF_KEY_AUDIO_TRACK_ID, audioTrackId);
        if (mSharedPreferences.contains(PREF_KEY_SUBTITLE_TRACK_ID))
            subtitleTrackId = mSharedPreferences.getString(PREF_KEY_SUBTITLE_TRACK_ID, subtitleTrackId);
        if (mSharedPreferences.contains(PREF_KEY_RESIZE_MODE))
            resizeMode = mSharedPreferences.getInt(PREF_KEY_RESIZE_MODE, resizeMode);
        orientation = Utils.Orientation.values()[mSharedPreferences.getInt(PREF_KEY_ORIENTATION, orientation.value)];
        scale = mSharedPreferences.getFloat(PREF_KEY_SCALE, scale);
        if (mSharedPreferences.contains(PREF_KEY_SCOPE_URI))
            scopeUri = Uri.parse(mSharedPreferences.getString(PREF_KEY_SCOPE_URI, null));
        askScope = mSharedPreferences.getBoolean(PREF_KEY_ASK_SCOPE, askScope);
        speed = mSharedPreferences.getFloat(PREF_KEY_SPEED, speed);
        loadUserPreferences();
    }

    public void loadUserPreferences() {
        autoPiP = mSharedPreferences.getBoolean(PREF_KEY_AUTO_PIP, autoPiP);
        tunneling = mSharedPreferences.getBoolean(PREF_KEY_TUNNELING, tunneling);
        skipSilence = mSharedPreferences.getBoolean(PREF_KEY_SKIP_SILENCE, skipSilence);
        frameRateMatching = mSharedPreferences.getBoolean(PREF_KEY_FRAMERATE_MATCHING, frameRateMatching);
        repeatToggle = mSharedPreferences.getBoolean(PREF_KEY_REPEAT_TOGGLE, repeatToggle);
        fileAccess = mSharedPreferences.getString(PREF_KEY_FILE_ACCESS, fileAccess);
        decoderPriority = Integer
                .parseInt(mSharedPreferences.getString(PREF_KEY_DECODER_PRIORITY, String.valueOf(decoderPriority)));
        mapDV7ToHevc = mSharedPreferences.getBoolean(PREF_KEY_MAP_DV7, mapDV7ToHevc);
        languageAudio = mSharedPreferences.getString(PREF_KEY_LANGUAGE_AUDIO, languageAudio);
        subtitleStyleEmbedded = mSharedPreferences.getBoolean(PREF_KEY_SUBTITLE_STYLE_EMBEDDED, subtitleStyleEmbedded);
        subtitleStyleBold = mSharedPreferences.getBoolean(PREF_KEY_SUBTITLE_STYLE_BOLD, subtitleStyleBold);
        seekDuration = mSharedPreferences.getInt(PREF_KEY_SEEK_DURATION, seekDuration);
        introSkipEnabled = mSharedPreferences.getBoolean(PREF_KEY_INTRO_SKIP_ENABLED, introSkipEnabled);
        introSkipMode = mSharedPreferences.getString(PREF_KEY_INTRO_SKIP_MODE, introSkipMode);
        skipButtonStyle = mSharedPreferences.getString(PREF_KEY_SKIP_BUTTON_STYLE, skipButtonStyle);

        malClientId = mSharedPreferences.getString(PREF_KEY_MAL_CLIENT_ID, malClientId);
        cinemataUrl = mSharedPreferences.getString(PREF_KEY_CINEMETA_URL, cinemataUrl);
        introDbApiKey = mSharedPreferences.getString(PREF_KEY_INTRODB_API_KEY, introDbApiKey);
        remoteControlEnabled = mSharedPreferences.getBoolean(PREF_KEY_REMOTE_CONTROL_ENABLED, remoteControlEnabled);
        jumpScareSkipEnabled = mSharedPreferences.getBoolean(PREF_KEY_JUMP_SCARE_SKIP_ENABLED, jumpScareSkipEnabled);
        introHaterApiKey = mSharedPreferences.getString(PREF_KEY_INTROHATER_API_KEY, introHaterApiKey);

        // Format Support Preferences
        forceSdrTonemapping = mSharedPreferences.getBoolean(PREF_KEY_FORCE_SDR_TONEMAPPING, forceSdrTonemapping);
        immersiveAudioFallback = mSharedPreferences.getBoolean(PREF_KEY_IMMERSIVE_AUDIO_FALLBACK, immersiveAudioFallback);
        auroChannelMapping = mSharedPreferences.getBoolean(PREF_KEY_AURO_CHANNEL_MAPPING, auroChannelMapping);
        disableDolbyVision = mSharedPreferences.getBoolean(PREF_KEY_DISABLE_DOLBY_VISION, disableDolbyVision);

        // Anime Skip Preferences
        animeSkipAuthToken = mSharedPreferences.getString(PREF_KEY_ANIMESKIP_AUTH_TOKEN, animeSkipAuthToken);
        animeSkipRefreshToken = mSharedPreferences.getString(PREF_KEY_ANIMESKIP_REFRESH_TOKEN, animeSkipRefreshToken);
        animeSkipUsername = mSharedPreferences.getString(PREF_KEY_ANIMESKIP_USERNAME, animeSkipUsername);
        animeSkipBranding = mSharedPreferences.getBoolean(PREF_KEY_ANIMESKIP_SKIP_BRANDING, animeSkipBranding);
        animeSkipRecaps = mSharedPreferences.getBoolean(PREF_KEY_ANIMESKIP_SKIP_RECAPS, animeSkipRecaps);
        animeSkipTitleCard = mSharedPreferences.getBoolean(PREF_KEY_ANIMESKIP_SKIP_TITLE_CARD, animeSkipTitleCard);
        animeSkipIntros = mSharedPreferences.getBoolean(PREF_KEY_ANIMESKIP_SKIP_INTROS, animeSkipIntros);
        animeSkipNewIntros = mSharedPreferences.getBoolean(PREF_KEY_ANIMESKIP_SKIP_NEW_INTROS, animeSkipNewIntros);
        animeSkipMixedIntros = mSharedPreferences.getBoolean(PREF_KEY_ANIMESKIP_SKIP_MIXED_INTROS, animeSkipMixedIntros);
        animeSkipCanon = mSharedPreferences.getBoolean(PREF_KEY_ANIMESKIP_SKIP_CANON, animeSkipCanon);
        animeSkipFiller = mSharedPreferences.getBoolean(PREF_KEY_ANIMESKIP_SKIP_FILLER, animeSkipFiller);
        animeSkipTransitions = mSharedPreferences.getBoolean(PREF_KEY_ANIMESKIP_SKIP_TRANSITIONS, animeSkipTransitions);
        animeSkipCredits = mSharedPreferences.getBoolean(PREF_KEY_ANIMESKIP_SKIP_CREDITS, animeSkipCredits);
        animeSkipNewCredits = mSharedPreferences.getBoolean(PREF_KEY_ANIMESKIP_SKIP_NEW_CREDITS, animeSkipNewCredits);
        animeSkipMixedCredits = mSharedPreferences.getBoolean(PREF_KEY_ANIMESKIP_SKIP_MIXED_CREDITS, animeSkipMixedCredits);
        animeSkipPreview = mSharedPreferences.getBoolean(PREF_KEY_ANIMESKIP_SKIP_PREVIEW, animeSkipPreview);
        animeSkipPirateMode = mSharedPreferences.getBoolean(PREF_KEY_ANIMESKIP_PIRATE_MODE, animeSkipPirateMode);
        animeSkipTimeShift = mSharedPreferences.getInt(PREF_KEY_ANIMESKIP_TIME_SHIFT, animeSkipTimeShift);

        // Trakt Preferences
        traktAccessToken = mSharedPreferences.getString(PREF_KEY_TRAKT_ACCESS_TOKEN, traktAccessToken);
        traktRefreshToken = mSharedPreferences.getString(PREF_KEY_TRAKT_REFRESH_TOKEN, traktRefreshToken);
        traktTokenExpiry = mSharedPreferences.getLong(PREF_KEY_TRAKT_TOKEN_EXPIRY, traktTokenExpiry);
        traktClientId = mSharedPreferences.getString(PREF_KEY_TRAKT_CLIENT_ID, traktClientId);
        traktClientSecret = mSharedPreferences.getString(PREF_KEY_TRAKT_CLIENT_SECRET, traktClientSecret);
        traktEnabled = mSharedPreferences.getBoolean(PREF_KEY_TRAKT_ENABLED, traktEnabled);
        traktToastsEnabled = mSharedPreferences.getBoolean(PREF_KEY_TRAKT_TOASTS_ENABLED, traktToastsEnabled);

        // Title Display
        preferFileNameTitle = mSharedPreferences.getBoolean(PREF_KEY_PREFER_FILE_NAME_TITLE, preferFileNameTitle);
    }

    public void updateSeekDuration(int duration) {
        this.seekDuration = duration;
        mSharedPreferences.edit().putInt(PREF_KEY_SEEK_DURATION, duration).apply();
    }

    /**
     * Save Anime Skip login credentials
     */
    public void saveAnimeSkipLogin(String authToken, String refreshToken, String username) {
        this.animeSkipAuthToken = authToken != null ? authToken : "";
        this.animeSkipRefreshToken = refreshToken != null ? refreshToken : "";
        this.animeSkipUsername = username != null ? username : "";
        
        mSharedPreferences.edit()
            .putString(PREF_KEY_ANIMESKIP_AUTH_TOKEN, this.animeSkipAuthToken)
            .putString(PREF_KEY_ANIMESKIP_REFRESH_TOKEN, this.animeSkipRefreshToken)
            .putString(PREF_KEY_ANIMESKIP_USERNAME, this.animeSkipUsername)
            .apply();
    }

    /**
     * Clear Anime Skip login (logout)
     */
    public void clearAnimeSkipLogin() {
        saveAnimeSkipLogin("", "", "");
    }

    /**
     * Save Trakt OAuth tokens
     */
    public void saveTraktTokens(String accessToken, String refreshToken, long expiry) {
        this.traktAccessToken = accessToken != null ? accessToken : "";
        this.traktRefreshToken = refreshToken != null ? refreshToken : "";
        this.traktTokenExpiry = expiry;
        this.traktEnabled = !this.traktAccessToken.isEmpty();
        
        mSharedPreferences.edit()
            .putString(PREF_KEY_TRAKT_ACCESS_TOKEN, this.traktAccessToken)
            .putString(PREF_KEY_TRAKT_REFRESH_TOKEN, this.traktRefreshToken)
            .putLong(PREF_KEY_TRAKT_TOKEN_EXPIRY, this.traktTokenExpiry)
            .putBoolean(PREF_KEY_TRAKT_ENABLED, this.traktEnabled)
            .apply();
    }

    /**
     * Save Trakt client credentials
     */
    public void saveTraktClientCredentials(String clientId, String clientSecret) {
        this.traktClientId = clientId != null ? clientId : "";
        this.traktClientSecret = clientSecret != null ? clientSecret : "";
        
        mSharedPreferences.edit()
            .putString(PREF_KEY_TRAKT_CLIENT_ID, this.traktClientId)
            .putString(PREF_KEY_TRAKT_CLIENT_SECRET, this.traktClientSecret)
            .apply();
    }

    /**
     * Clear Trakt tokens (disconnect)
     */
    public void clearTraktTokens() {
        saveTraktTokens("", "", 0);
    }

    /**
     * Check if connected to Trakt
     */
    public boolean isTraktConnected() {
        return traktEnabled && !traktAccessToken.isEmpty();
    }

    /**
     * Check if logged into Anime Skip
     */
    public boolean isAnimeSkipLoggedIn() {
        return animeSkipAuthToken != null && !animeSkipAuthToken.isEmpty();
    }


    public void updateMedia(final Context context, final Uri uri, final String type) {
        mediaUri = uri;
        mediaType = type;
        updateSubtitle(null);
        updateMeta(null, null, AspectRatioFrameLayout.RESIZE_MODE_FIT, 1.f, 1.f);

        if (mediaType != null && mediaType.endsWith("/*")) {
            mediaType = null;
        }

        if (mediaType == null) {
            if (ContentResolver.SCHEME_CONTENT.equals(mediaUri.getScheme())) {
                mediaType = context.getContentResolver().getType(mediaUri);
            }
        }

        if (persistentMode) {
            final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
            if (mediaUri == null)
                sharedPreferencesEditor.remove(PREF_KEY_MEDIA_URI);
            else
                sharedPreferencesEditor.putString(PREF_KEY_MEDIA_URI, mediaUri.toString());
            if (mediaType == null)
                sharedPreferencesEditor.remove(PREF_KEY_MEDIA_TYPE);
            else
                sharedPreferencesEditor.putString(PREF_KEY_MEDIA_TYPE, mediaType);
            sharedPreferencesEditor.apply();
        }
    }

    public void updateSubtitle(final Uri uri) {
        subtitleUri = uri;
        subtitleTrackId = null;
        if (persistentMode) {
            final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
            if (uri == null)
                sharedPreferencesEditor.remove(PREF_KEY_SUBTITLE_URI);
            else
                sharedPreferencesEditor.putString(PREF_KEY_SUBTITLE_URI, uri.toString());
            sharedPreferencesEditor.remove(PREF_KEY_SUBTITLE_TRACK_ID);
            sharedPreferencesEditor.apply();
        }
    }

    public void updatePosition(final long position) {
        if (mediaUri == null)
            return;

        while (positions.size() > 100)
            positions.remove(positions.keySet().toArray()[0]);

        if (persistentMode) {
            positions.put(mediaUri.toString(), position);
            savePositions();
        } else {
            nonPersitentPosition = position;
        }
    }

    public void updateBrightness(final int brightness) {
        if (brightness >= -1) {
            this.brightness = brightness;
            final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
            sharedPreferencesEditor.putInt(PREF_KEY_BRIGHTNESS, brightness);
            sharedPreferencesEditor.apply();
        }
    }

    public void markFirstRun() {
        this.firstRun = false;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putBoolean(PREF_KEY_FIRST_RUN, false);
        sharedPreferencesEditor.apply();
    }

    public void markScopeAsked() {
        this.askScope = false;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putBoolean(PREF_KEY_ASK_SCOPE, false);
        sharedPreferencesEditor.apply();
    }

    private void savePositions() {
        try {
            FileOutputStream fos = mContext.openFileOutput("positions", Context.MODE_PRIVATE);
            ObjectOutputStream os = new ObjectOutputStream(fos);
            os.writeObject(positions);
            os.close();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadPositions() {
        try {
            FileInputStream fis = mContext.openFileInput("positions");
            ObjectInputStream is = new ObjectInputStream(fis);
            positions = (LinkedHashMap) is.readObject();
            is.close();
            fis.close();
        } catch (Exception e) {
            e.printStackTrace();
            positions = new LinkedHashMap(10);
        }
    }

    public long getPosition() {
        if (!persistentMode) {
            return nonPersitentPosition;
        }

        Object val = positions.get(mediaUri.toString());
        if (val != null)
            return (long) val;

        // Return position for uri from limited scope (loaded after using Next action)
        if (ContentResolver.SCHEME_CONTENT.equals(mediaUri.getScheme())) {
            final String searchPath = SubtitleUtils.getTrailPathFromUri(mediaUri);
            if (searchPath == null || searchPath.length() < 1)
                return 0L;
            final Set<String> keySet = positions.keySet();
            final Object[] keys = keySet.toArray();
            for (int i = keys.length; i > 0; i--) {
                final String key = (String) keys[i - 1];
                final Uri uri = Uri.parse(key);
                if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
                    final String keyPath = SubtitleUtils.getTrailPathFromUri(uri);
                    if (searchPath.equals(keyPath)) {
                        return (long) positions.get(key);
                    }
                }
            }
        }

        return 0L;
    }

    public void updateOrientation() {
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        sharedPreferencesEditor.putInt(PREF_KEY_ORIENTATION, orientation.value);
        sharedPreferencesEditor.apply();
    }

    public void updateMeta(final String audioTrackId, final String subtitleTrackId, final int resizeMode,
            final float scale, final float speed) {
        this.audioTrackId = audioTrackId;
        this.subtitleTrackId = subtitleTrackId;
        this.resizeMode = resizeMode;
        this.scale = scale;
        this.speed = speed;
        if (persistentMode) {
            final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
            if (audioTrackId == null)
                sharedPreferencesEditor.remove(PREF_KEY_AUDIO_TRACK_ID);
            else
                sharedPreferencesEditor.putString(PREF_KEY_AUDIO_TRACK_ID, audioTrackId);
            if (subtitleTrackId == null)
                sharedPreferencesEditor.remove(PREF_KEY_SUBTITLE_TRACK_ID);
            else
                sharedPreferencesEditor.putString(PREF_KEY_SUBTITLE_TRACK_ID, subtitleTrackId);
            sharedPreferencesEditor.putInt(PREF_KEY_RESIZE_MODE, resizeMode);
            sharedPreferencesEditor.putFloat(PREF_KEY_SCALE, scale);
            sharedPreferencesEditor.putFloat(PREF_KEY_SPEED, speed);
            sharedPreferencesEditor.apply();
        }
    }

    public void updateScope(final Uri uri) {
        scopeUri = uri;
        final SharedPreferences.Editor sharedPreferencesEditor = mSharedPreferences.edit();
        if (uri == null)
            sharedPreferencesEditor.remove(PREF_KEY_SCOPE_URI);
        else
            sharedPreferencesEditor.putString(PREF_KEY_SCOPE_URI, uri.toString());
        sharedPreferencesEditor.apply();
    }

    public void setPersistent(boolean persistentMode) {
        this.persistentMode = persistentMode;
    }
}