package com.brouken.player;

import static android.content.pm.PackageManager.FEATURE_EXPANDED_PICTURE_IN_PICTURE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.UriPermission;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.LoudnessEnhancer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Rational;
import android.util.TypedValue;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.accessibility.CaptioningManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.extractor.ts.TsExtractor;
import androidx.media3.session.MediaSession;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.PlayerControlView;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;
import androidx.media3.ui.TimeBar;

import com.brouken.player.dtpv.DoubleTapPlayerView;
import com.brouken.player.dtpv.youtube.YouTubeOverlay;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetView;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import androidx.media3.common.Metadata;
import java.util.concurrent.TimeUnit;
import android.os.Handler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.brouken.player.tmdb.SkipManager;

import com.brouken.player.tmdb.CinemetaClient;
import com.brouken.player.tmdb.IntroDBClient;
import com.brouken.player.tmdb.JikanClient;
import com.brouken.player.tmdb.JumpScareManager;
import com.brouken.player.utils.RemoteServer;
import com.brouken.player.utils.DebugLogger;
import com.brouken.player.utils.ChapterScanner;
import com.brouken.player.utils.NameFixer;
import com.brouken.player.trakt.TraktScrobbleManager;
import com.brouken.player.ui.subtitle.SubtitleHub;
import com.brouken.player.stremio.SubtitleTrack;
import com.brouken.player.stremio.SubtitleSource;
import kotlin.Pair;

public class PlayerActivity extends Activity {

    private PlayerListener playerListener;
    private BroadcastReceiver mReceiver;
    private AudioManager mAudioManager;
    private MediaSession mediaSession;
    private DefaultTrackSelector trackSelector;
    public static LoudnessEnhancer loudnessEnhancer;
    private FormatSupportManager formatSupportManager;

    public CustomPlayerView playerView;
    public static ExoPlayer player;
    private YouTubeOverlay youTubeOverlay;

    private Object mPictureInPictureParamsBuilder;

    public Prefs mPrefs;
    public BrightnessControl mBrightnessControl;
    private ModernController modernController;
    public static boolean haveMedia;
    private boolean videoLoading;
    public static boolean controllerVisible;
    public static boolean controllerVisibleFully;
    public static Snackbar snackbar;
    private ExoPlaybackException errorToShow;
    public static int boostLevel = 0;
    private boolean isScaling = false;
    private boolean isScaleStarting = false;
    private float scaleFactor = 1.0f;

    private static final int REQUEST_CHOOSER_VIDEO = 1;
    private static final int REQUEST_CHOOSER_SUBTITLE = 2;
    private static final int REQUEST_CHOOSER_SCOPE_DIR = 10;
    private static final int REQUEST_CHOOSER_VIDEO_MEDIASTORE = 20;
    private static final int REQUEST_CHOOSER_SUBTITLE_MEDIASTORE = 21;
    public static final int REQUEST_SETTINGS = 100;
    private static final int REQUEST_SYSTEM_CAPTIONS = 200;
    public static final int CONTROLLER_TIMEOUT = 3500;
    private static final String ACTION_MEDIA_CONTROL = "media_control";
    private static final String EXTRA_CONTROL_TYPE = "control_type";
    private static final int REQUEST_PLAY = 1;
    private static final int REQUEST_PAUSE = 2;
    private static final int CONTROL_TYPE_PLAY = 1;
    private static final int CONTROL_TYPE_PAUSE = 2;

    private CoordinatorLayout coordinatorLayout;
    private TextView titleView;
    private ImageButton buttonOpen;
    private ImageButton buttonPiP;
    private ImageButton buttonAspectRatio;
    private ImageButton buttonRotation;
    private ImageButton exoSettings;
    private android.widget.Button buttonSkipIntro;
    private ImageButton exoPlayPause;
    private ProgressBar loadingProgressBar;
    private PlayerControlView controlView;
    private CustomDefaultTimeBar timeBar;

    private boolean restoreOrientationLock;
    private boolean restorePlayState;
    private boolean restorePlayStateAllowed;
    private boolean play;
    private float subtitlesScale;
    private boolean isScrubbing;
    private boolean scrubbingNoticeable;
    private long scrubbingStart;
    public boolean frameRendered;
    private boolean alive;
    public static boolean focusPlay = false;
    private Uri nextUri;
    private static boolean isTvBox;
    public static boolean locked = false;
    private Thread nextUriThread;
    public Thread frameRateSwitchThread;

    public static boolean restoreControllerTimeout = false;
    public static boolean shortControllerTimeout = false;

    final Rational rationalLimitWide = new Rational(239, 100);
    final Rational rationalLimitTall = new Rational(100, 239);

    static final String API_POSITION = "position";
    static final String API_DURATION = "duration";
    static final String API_RETURN_RESULT = "return_result";
    static final String API_SUBS = "subs";
    static final String API_SUBS_ENABLE = "subs.enable";
    static final String API_SUBS_NAME = "subs.name";
    static final String API_TITLE = "title";
    static final String API_END_BY = "end_by";
    boolean apiAccess;
    boolean apiAccessPartial;
    String apiTitle;
    List<MediaItem.SubtitleConfiguration> apiSubs = new ArrayList<>();
    boolean intentReturnResult;
    boolean playbackFinished;

    DisplayManager displayManager;
    DisplayManager.DisplayListener displayListener;
    SubtitleFinder subtitleFinder;

    Runnable barsHider = () -> {
        if (playerView != null && !controllerVisible) {
            Utils.toggleSystemUi(PlayerActivity.this, playerView, false);
        }
    };

    // AutoSkip Fields
    private SkipManager skipManager;

    private List<Pair<Double, Double>> currentSkipSegments;
    private boolean hasSkippedIntro = false;
    private boolean hasChapterSkip = false;
    private Handler skipHandler = new Handler();
    private Runnable skipRunnable;
    private ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private String currentImdbId = null;
    private Integer currentMalId = null;
    private String currentUiStyle = null; // Track UI style to detect changes
    
    // IntroDB Submission Fields
    private Button btnMarkStart, btnMarkEnd, btnSubmitIntro;
    private long markedStartMs = -1, markedEndMs = -1;
    private int currentSeason = 1, currentEpisode = 1;
    private RemoteServer remoteServer;
    
    // Jump Scare Skip
    private JumpScareManager jumpScareManager;
    
    // Trakt Scrobbling
    private TraktScrobbleManager traktScrobbler;
    
    // Ultimate Subtitle Hub
    private SubtitleHub subtitleHub;
    
    // Subtitle button reference (to keep it always enabled)
    private View exoSubtitleButton;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Rotate ASAP, before super/inflating to avoid glitches with activity launch animation
        mPrefs = new Prefs(this);
        Utils.setOrientation(this, mPrefs.orientation);

        // AutoSkip Init
        DebugLogger.INSTANCE.init(getApplicationContext());
        skipManager = new SkipManager();
        
        // Jump Scare Manager - DISABLED/HIDDEN
        // jumpScareManager = new JumpScareManager(this);


        
        // Remote Control Server Init
        remoteServer = new RemoteServer(new RemoteServer.PlayerController() {
            @Override
            public long getCurrentPosition() {
                return (player != null) ? player.getCurrentPosition() : 0;
            }
            
            @Override
            public long getDuration() {
                return (player != null) ? player.getDuration() : 0;
            }
            
            @Override
            public boolean isPlaying() {
                return (player != null && player.isPlaying());
            }
            
            @Override
            public String getMediaTitle() {
                return (titleView != null && titleView.getText() != null) ? titleView.getText().toString() : "";
            }
            
            @Override
            public long getStartMarker() {
                return markedStartMs;
            }
            
            @Override
            public long getEndMarker() {
                return markedEndMs;
            }
            
            @Override
            public boolean hasApiKey() {
                return mPrefs.introDbApiKey != null && !mPrefs.introDbApiKey.isEmpty();
            }
            
            @Override
            public void markStart() {
                if (player != null) {
                    markedStartMs = player.getCurrentPosition();
                    runOnUiThread(() -> {
                        Toast.makeText(PlayerActivity.this, "Start: " + (markedStartMs/1000.0) + "s", Toast.LENGTH_SHORT).show();
                        updateSubmitButtonVisibility();
                    });
                }
            }
            
            @Override
            public void markEnd() {
                if (player != null) {
                    markedEndMs = player.getCurrentPosition();
                    runOnUiThread(() -> {
                        Toast.makeText(PlayerActivity.this, "End: " + (markedEndMs/1000.0) + "s", Toast.LENGTH_SHORT).show();
                        updateSubmitButtonVisibility();
                    });
                }
            }
            
            @Override
            public void submit() {
                submitToIntroDB();
            }
            
            @Override
            public void reset() {
                markedStartMs = -1;
                markedEndMs = -1;
                runOnUiThread(() -> {
                    Toast.makeText(PlayerActivity.this, "Markers Reset", Toast.LENGTH_SHORT).show();
                    updateSubmitButtonVisibility();
                });
            }
            
            @Override
            public void seekTo(long posMs) {
                if (player != null) player.seekTo(posMs);
            }
            
            @Override
            public void togglePause() {
                if (player != null) {
                    if (player.isPlaying()) player.pause(); else player.play();
                }
            }
            
            @Override
            public void seekRelative(long offsetMs) {
                if (player != null) {
                    long newPos = player.getCurrentPosition() + offsetMs;
                    player.seekTo(Math.max(0, newPos));
                }
            }
        });
        
        // Start Remote Server if enabled
        if (mPrefs.remoteControlEnabled) {
            remoteServer.start();
            Toast.makeText(this, "Remote Control on port 8355", Toast.LENGTH_LONG).show();
            DebugLogger.INSTANCE.log("RemoteServer", "Server started on port 8355");
        } else {
            DebugLogger.INSTANCE.log("RemoteServer", "Remote Control is disabled in Settings");
        }

        skipRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (player != null && player.isPlaying() && currentSkipSegments != null && mPrefs.introSkipEnabled) {
                        long posMs = player.getCurrentPosition(); // Already in milliseconds
                        boolean inSegment = false;
                        
                        // Check if we're inside an intro segment (using millisecond precision)
                        for (Pair<Double, Double> seg : currentSkipSegments) {
                            // Use Math.round to prevent potential off-by-one truncation errors
                            long startMs = Math.round(seg.getFirst() * 1000);
                            long endMs = Math.round(seg.getSecond() * 1000);
                            
                            // Trigger if we are at or past the start (start precision)
                            if (posMs >= startMs && posMs < endMs) {
                                inSegment = true;
                                if ("auto".equals(mPrefs.introSkipMode)) {
                                    // Auto mode: skip immediately with millisecond precision
                                    if (!hasSkippedIntro) {
                                        player.seekTo(endMs); // Exact millisecond seek
                                        hasSkippedIntro = true;
                                        runOnUiThread(() -> {
                                            Toast.makeText(PlayerActivity.this, "Skipped Intro", Toast.LENGTH_SHORT).show();
                                            if (buttonSkipIntro != null) {
                                                buttonSkipIntro.setVisibility(View.GONE);
                                            }
                                        });
                                        DebugLogger.INSTANCE.log("AutoSkip", "Auto-skipped to " + endMs + "ms (precision)");
                                    }
                                } else {
                                    // Button mode: show button
                                    if (buttonSkipIntro != null && !hasSkippedIntro) {
                                        runOnUiThread(() -> buttonSkipIntro.setVisibility(View.VISIBLE));
                                    }
                                }
                                break;
                            }
                        }
                        
                        // Hide button when outside all segments
                        if (!inSegment && buttonSkipIntro != null) {
                            runOnUiThread(() -> buttonSkipIntro.setVisibility(View.GONE));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (alive) {
                    skipHandler.postDelayed(this, 10); // 10ms polling for precision
                }
            }
        };

        super.onCreate(savedInstanceState);
        
        // Check if TV before setting content view
        isTvBox = Utils.isTvBox(this);
        
        // Log UI preference value for debugging
        DebugLogger.INSTANCE.log("UI", "onCreate - skipButtonStyle pref: '" + mPrefs.skipButtonStyle + "', isTvBox: " + isTvBox);
        if (Build.VERSION.SDK_INT == 28 && Build.MANUFACTURER.equalsIgnoreCase("xiaomi") &&
                (Build.DEVICE.equalsIgnoreCase("oneday") || Build.DEVICE.equalsIgnoreCase("once"))) {
            setContentView(R.layout.activity_player_textureview);
            currentUiStyle = "textureview";
        } else if ("netflix".equals(mPrefs.skipButtonStyle)) {
            // Netflix-style player layout (TV vs Mobile)
            if (isTvBox) {
                setContentView(R.layout.activity_player_netflix_tv);
                currentUiStyle = "netflix_tv";
            } else {
                setContentView(R.layout.activity_player_netflix);
                currentUiStyle = "netflix";
            }
            DebugLogger.INSTANCE.log("UI", "Netflix UI loaded: " + currentUiStyle);
        } else {
            setContentView(R.layout.activity_player);
            currentUiStyle = "default";
        }
        
        DebugLogger.INSTANCE.log("UI", "Current UI style: " + currentUiStyle + ", Pref: " + mPrefs.skipButtonStyle);
        if (Build.VERSION.SDK_INT >= 31) {
            Window window = getWindow();
            if (window != null) {
                window.setDecorFitsSystemWindows(false);
                WindowInsetsController windowInsetsController = window.getInsetsController();
                if (windowInsetsController != null) {
                    // On Android 12 BEHAVIOR_DEFAULT allows system gestures without visible system bars
                    windowInsetsController.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_DEFAULT);
                }
            }
        }

        if (isTvBox) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }

        // Check for updates on launch (if enabled in settings)
        boolean autoCheck = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("autoCheckUpdates", true);
            
        if (autoCheck) {
            com.brouken.player.update.UpdateManager.Companion.getInstance(this).checkOnLaunch(updateInfo -> {
                if (updateInfo != null && updateInfo.getHasApk()) {
                    if (!isFinishing()) {
                        showUpdateDialog(updateInfo);
                    }
                }
            });
        }

        final Intent launchIntent = getIntent();
        final String action = launchIntent.getAction();
        final String type = launchIntent.getType();

        if ("com.brouken.player.action.SHORTCUT_VIDEOS".equals(action)) {
            openFile(Utils.getMoviesFolderUri());
        } else if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
            String text = launchIntent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                final Uri parsedUri = Uri.parse(text);
                if (parsedUri.isAbsolute()) {
                    mPrefs.updateMedia(this, parsedUri, null);
                    focusPlay = true;
                }
            }
        } else if (launchIntent.getData() != null) {
            resetApiAccess();
            final Uri uri = launchIntent.getData();
            if (SubtitleUtils.isSubtitle(uri, type)) {
                handleSubtitles(uri);
            } else {
                Bundle bundle = launchIntent.getExtras();
                if (bundle != null) {
                    apiAccess = bundle.containsKey(API_POSITION) || bundle.containsKey(API_RETURN_RESULT)
                            || bundle.containsKey(API_SUBS) || bundle.containsKey(API_SUBS_ENABLE);
                    if (apiAccess) {
                        mPrefs.setPersistent(false);
                    } else if (bundle.containsKey(API_TITLE)) {
                        apiAccessPartial = true;
                    }
                    apiTitle = bundle.getString(API_TITLE);
                    
                    if (bundle.containsKey("imdbId")) {
                        currentImdbId = bundle.getString("imdbId");
                    } else if (bundle.containsKey("imdb_id")) {
                        currentImdbId = bundle.getString("imdb_id");
                    } else if (bundle.containsKey("code")) {
                        // Some players use 'code' for IMDB ID
                        currentImdbId = bundle.getString("code");
                    }
                }

                mPrefs.updateMedia(this, uri, type);

                if (bundle != null) {
                    Uri defaultSub = null;
                    Parcelable[] subsEnable = bundle.getParcelableArray(API_SUBS_ENABLE);
                    if (subsEnable != null && subsEnable.length > 0) {
                        defaultSub = (Uri) subsEnable[0];
                    }

                    Parcelable[] subs = bundle.getParcelableArray(API_SUBS);
                    String[] subsName = bundle.getStringArray(API_SUBS_NAME);
                    if (subs != null && subs.length > 0) {
                        for (int i = 0; i < subs.length; i++) {
                            Uri sub = (Uri) subs[i];
                            String name = null;
                            if (subsName != null && subsName.length > i) {
                                name = subsName[i];
                            }
                            apiSubs.add(SubtitleUtils.buildSubtitle(this, sub, name, sub.equals(defaultSub)));
                        }
                    }
                }

                if (apiSubs.isEmpty()) {
                    searchSubtitles();
                }

                if (bundle != null) {
                    intentReturnResult = bundle.getBoolean(API_RETURN_RESULT);

                    if (bundle.containsKey(API_POSITION)) {
                        mPrefs.updatePosition((long) bundle.getInt(API_POSITION));
                    }
                }
            }
            focusPlay = true;
        }

        Uri currentUri = getIntent().getData();
        if (currentImdbId == null && currentUri != null && currentUri.isHierarchical()) {
            currentImdbId = currentUri.getQueryParameter("imdbId");
            if (currentImdbId == null) currentImdbId = currentUri.getQueryParameter("imdb_id");
        }

        coordinatorLayout = findViewById(R.id.coordinatorLayout);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        playerView = findViewById(R.id.video_view);
        exoPlayPause = findViewById(R.id.exo_play_pause);
        loadingProgressBar = findViewById(R.id.loading);

        playerView.setShowNextButton(false);
        playerView.setShowPreviousButton(false);
        playerView.setShowFastForwardButton(false);
        playerView.setShowRewindButton(false);

        playerView.setRepeatToggleModes(Player.REPEAT_MODE_ONE);

        playerView.setControllerHideOnTouch(false);
        playerView.setControllerAutoShow(true);

        ((DoubleTapPlayerView)playerView).setDoubleTapEnabled(false);

        timeBar = playerView.findViewById(R.id.exo_progress);
        timeBar.addListener(new TimeBar.OnScrubListener() {
            @Override
            public void onScrubStart(TimeBar timeBar, long position) {
                if (player == null) {
                    return;
                }
                restorePlayState = player.isPlaying();
                if (restorePlayState) {
                    player.pause();
                }
                scrubbingNoticeable = false;
                isScrubbing = true;
                frameRendered = true;
                playerView.setControllerShowTimeoutMs(-1);
                scrubbingStart = player.getCurrentPosition();
                player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
                reportScrubbing(position);
            }

            @Override
            public void onScrubMove(TimeBar timeBar, long position) {
                reportScrubbing(position);
            }

            @Override
            public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
                playerView.setCustomErrorMessage(null);
                isScrubbing = false;
                if (restorePlayState) {
                    restorePlayState = false;
                    playerView.setControllerShowTimeoutMs(PlayerActivity.CONTROLLER_TIMEOUT);
                    if (player != null) {
                        player.setPlayWhenReady(true);
                    }
                }
            }
        });

        buttonOpen = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonOpen.setImageResource(R.drawable.ic_folder_open_24dp);
        buttonOpen.setId(View.generateViewId());
        buttonOpen.setContentDescription(getString(R.string.button_open));

        buttonOpen.setOnClickListener(view -> openFile(mPrefs.mediaUri));

        buttonOpen.setOnLongClickListener(view -> {
            if (!isTvBox && mPrefs.askScope) {
                askForScope(true, false);
            } else {
                loadSubtitleFile(mPrefs.mediaUri);
            }
            return true;
        });

        if (Utils.isPiPSupported(this)) {
            // TODO: Android 12 improvements:
            // https://developer.android.com/about/versions/12/features/pip-improvements
            mPictureInPictureParamsBuilder = new PictureInPictureParams.Builder();
            boolean success = updatePictureInPictureActions(R.drawable.ic_play_arrow_24dp, R.string.exo_controls_play_description, CONTROL_TYPE_PLAY, REQUEST_PLAY);

            if (success) {
                buttonPiP = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
                buttonPiP.setContentDescription(getString(R.string.button_pip));
                buttonPiP.setImageResource(R.drawable.ic_picture_in_picture_alt_24dp);

                buttonPiP.setOnClickListener(view -> enterPiP());
            }
        }

        buttonAspectRatio = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonAspectRatio.setId(Integer.MAX_VALUE - 100);
        buttonAspectRatio.setContentDescription(getString(R.string.button_crop));
        updatebuttonAspectRatioIcon();
        buttonAspectRatio.setOnClickListener(view -> {
            playerView.setScale(1.f);
            if (playerView.getResizeMode() == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
                Utils.showText(playerView, getString(R.string.video_resize_crop));
            } else {
                // Default mode
                playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                Utils.showText(playerView, getString(R.string.video_resize_fit));
            }
            updatebuttonAspectRatioIcon();
            resetHideCallbacks();
        });
        if (isTvBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            buttonAspectRatio.setOnLongClickListener(v -> {
                scaleStart();
                updatebuttonAspectRatioIcon();
                return true;
            });
        }
        buttonRotation = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonRotation.setContentDescription(getString(R.string.button_rotate));
        updateButtonRotation();
        buttonRotation.setOnClickListener(view -> {
            mPrefs.orientation = Utils.getNextOrientation(mPrefs.orientation);
            Utils.setOrientation(PlayerActivity.this, mPrefs.orientation);
            updateButtonRotation();
            Utils.showText(playerView, getString(mPrefs.orientation.description), 2500);
            resetHideCallbacks();
        });

        final int titleViewPaddingHorizontal = Utils.dpToPx(14);
        final int titleViewPaddingVertical = getResources().getDimensionPixelOffset(R.dimen.exo_styled_bottom_bar_time_padding);
        FrameLayout centerView = playerView.findViewById(R.id.exo_controls_background);
        titleView = new TextView(this);
        titleView.setBackgroundResource(R.color.ui_controls_background);
        titleView.setTextColor(Color.WHITE);
        titleView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        titleView.setPadding(titleViewPaddingHorizontal, titleViewPaddingVertical, titleViewPaddingHorizontal, titleViewPaddingVertical);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleView.setVisibility(View.GONE);
        titleView.setMaxLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        centerView.addView(titleView);

        titleView.setOnLongClickListener(view -> {
            // Prevent FileUriExposedException
            if (mPrefs.mediaUri != null && ContentResolver.SCHEME_FILE.equals(mPrefs.mediaUri.getScheme())) {
                return false;
            }

            final Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM, mPrefs.mediaUri);
            if (mPrefs.mediaType == null)
                shareIntent.setType("video/*");
            else
                shareIntent.setType(mPrefs.mediaType);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // Start without intent chooser to allow any target to be set as default
            startActivity(shareIntent);

            return true;
        });

        if (Build.VERSION.SDK_INT >= 35) {
            getWindow().setNavigationBarContrastEnforced(false);
        }

        controlView = playerView.findViewById(R.id.exo_controller);
        if (controlView != null) {
            controlView.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            if (windowInsets != null) {
                if (Build.VERSION.SDK_INT >= 31) {
                    boolean visibleBars = windowInsets.isVisible(WindowInsets.Type.statusBars());
                    if (visibleBars && !controllerVisible) {
                        playerView.postDelayed(barsHider, 2500);
                    } else {
                        playerView.removeCallbacks(barsHider);
                    }
                }

                int insetLeft = windowInsets.getSystemWindowInsetLeft();
                int insetRight = windowInsets.getSystemWindowInsetRight();

                int paddingLeft = 0;
                int marginLeft = insetLeft;

                int paddingRight = 0;
                int marginRight = insetRight;

                if (Build.VERSION.SDK_INT >= 28 && windowInsets.getDisplayCutout() != null) {
                    if (windowInsets.getDisplayCutout().getSafeInsetLeft() == insetLeft) {
                        paddingLeft = insetLeft;
                        marginLeft = 0;
                    }
                    if (windowInsets.getDisplayCutout().getSafeInsetRight() == insetRight) {
                        paddingRight = insetRight;
                        marginRight = 0;
                    }
                }

                int bottomBarPaddingBottom = 0;
                int progressBarMarginBottom = 0;

                if (Build.VERSION.SDK_INT >= 35) {
                    final int left = windowInsets.getInsets(WindowInsets.Type.navigationBars()).left;
                    final int right = windowInsets.getInsets(WindowInsets.Type.navigationBars()).right;

                    final View exoTop = findViewById(R.id.exo_top);
                    if (exoTop != null) {
                        exoTop.getLayoutParams().height = windowInsets.getSystemWindowInsetTop();
                        Utils.setViewMargins(exoTop, left, 0, right, 0);
                    }

                    // Use View instead of FrameLayout to avoid ClassCastException (LinearLayout in Netflix layout)
                    final View exoBottomBar = findViewById(R.id.exo_bottom_bar);
                    if (exoBottomBar != null) {
                        ViewGroup.LayoutParams params = exoBottomBar.getLayoutParams();
                        params.height = getResources().getDimensionPixelSize(R.dimen.exo_styled_bottom_bar_height) + windowInsets.getSystemWindowInsetBottom();
                        exoBottomBar.setLayoutParams(params);
                    }

                    View exoLeft = findViewById(R.id.exo_left);
                    View exoRight = findViewById(R.id.exo_right);
                    if (exoLeft != null) exoLeft.getLayoutParams().width = left;
                    if (exoRight != null) exoRight.getLayoutParams().width = right;

                    bottomBarPaddingBottom = windowInsets.getSystemWindowInsetBottom();
                    progressBarMarginBottom = windowInsets.getSystemWindowInsetBottom();
                } else {
                    view.setPadding(0, windowInsets.getSystemWindowInsetTop(),0, windowInsets.getSystemWindowInsetBottom());
                }

                Utils.setViewParams(titleView, paddingLeft + titleViewPaddingHorizontal, titleViewPaddingVertical, paddingRight + titleViewPaddingHorizontal, titleViewPaddingVertical,
                        marginLeft, windowInsets.getSystemWindowInsetTop(), marginRight, 0);

                View insetsBottomBar = findViewById(R.id.exo_bottom_bar);
                if (insetsBottomBar != null) {
                    Utils.setViewParams(insetsBottomBar, paddingLeft, 0, paddingRight, bottomBarPaddingBottom,
                            marginLeft, 0, marginRight, 0);
                }

                View exoProgress = findViewById(R.id.exo_progress);
                if (exoProgress != null) {
                    Utils.setViewParams(exoProgress, windowInsets.getSystemWindowInsetLeft(), 0, windowInsets.getSystemWindowInsetRight(), 0,
                            0, 0, 0, getResources().getDimensionPixelSize(R.dimen.exo_styled_progress_margin_bottom) + progressBarMarginBottom);
                }

                View errorMessage = findViewById(R.id.exo_error_message);
                if (errorMessage != null) {
                    Utils.setViewMargins(errorMessage, 0, windowInsets.getSystemWindowInsetTop() / 2, 0, getResources().getDimensionPixelSize(R.dimen.exo_error_message_margin_bottom) + windowInsets.getSystemWindowInsetBottom() / 2);
                }

                windowInsets.consumeSystemWindowInsets();
            }
            return windowInsets;
        });
        }
        timeBar.setAdMarkerColor(Color.argb(0x00, 0xFF, 0xFF, 0xFF));
        timeBar.setPlayedAdMarkerColor(Color.argb(0x98, 0xFF, 0xFF, 0xFF));

        try {
            CustomDefaultTrackNameProvider customDefaultTrackNameProvider = new CustomDefaultTrackNameProvider(getResources());
            final Field field = PlayerControlView.class.getDeclaredField("trackNameProvider");
            field.setAccessible(true);
            field.set(controlView, customDefaultTrackNameProvider);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

        View deleteBtn = findViewById(R.id.delete);
        if (deleteBtn != null) {
            deleteBtn.setOnClickListener(view -> askDeleteMedia());
        }

        View nextBtn = findViewById(R.id.next);
        if (nextBtn != null) {
            nextBtn.setOnClickListener(view -> {
                if (!isTvBox && mPrefs.askScope) {
                    askForScope(false, true);
                } else {
                    skipToNext();
                }
            });
        }

        exoPlayPause.setOnClickListener(view -> dispatchPlayPause());

        // Prevent double tap actions in controller
        View bottomBar = findViewById(R.id.exo_bottom_bar);
        if (bottomBar != null) {
            bottomBar.setOnTouchListener((v, event) -> true);
        }
        //titleView.setOnTouchListener((v, event) -> true);

        playerListener = new PlayerListener();

        mBrightnessControl = new BrightnessControl(this);
        if (mPrefs.brightness >= 0) {
            mBrightnessControl.currentBrightnessLevel = mPrefs.brightness;
            mBrightnessControl.setScreenBrightness(mBrightnessControl.levelToBrightness(mBrightnessControl.currentBrightnessLevel));
        }
        playerView.setBrightnessControl(mBrightnessControl);

        // Control bar setup - skip if Netflix layout (uses different control structure)
        final ViewGroup exoBasicControls = playerView.findViewById(R.id.exo_basic_controls);
        View exoRepeat = null;
        
        if (exoBasicControls != null) {
            // Remove ExoPlayer's subtitle button entirely - we'll use our own
            View exoSubtitle = exoBasicControls.findViewById(R.id.exo_subtitle);
            if (exoSubtitle != null) {
                exoBasicControls.removeView(exoSubtitle);
            }

            exoSettings = exoBasicControls.findViewById(R.id.exo_settings);
            if (exoSettings != null) {
                exoBasicControls.removeView(exoSettings);
                exoSettings.setOnClickListener(view -> showAudioSelectionDialog());
                
                exoSettings.setOnLongClickListener(view -> {
                    Intent intent = new Intent(this, SettingsActivity.class);
                    startActivityForResult(intent, REQUEST_SETTINGS);
                    return true;
                });
            }
            
            exoRepeat = exoBasicControls.findViewById(R.id.exo_repeat_toggle);
            if (exoRepeat != null) {
                exoBasicControls.removeView(exoRepeat);
            }
        }

        // Create custom subtitle button (always enabled, not managed by ExoPlayer)
        ImageButton buttonSubtitle = new ImageButton(this, null, 0, R.style.ExoStyledControls_Button_Bottom);
        buttonSubtitle.setImageResource(R.drawable.ic_subtitles_24dp);
        buttonSubtitle.setContentDescription(getString(R.string.button_subtitles));
        buttonSubtitle.setOnClickListener(v -> showSubtitleSelectionDialog());
        buttonSubtitle.setOnLongClickListener(v -> {
            enableRotation();
            safelyStartActivityForResult(new Intent(Settings.ACTION_CAPTIONING_SETTINGS), REQUEST_SYSTEM_CAPTIONS);
            return true;
        });

        updateButtons(false);

        // Default control bar setup - only for non-Netflix layouts
        HorizontalScrollView horizontalScrollView = null;
        LinearLayout controls = null;
        
        if (!"netflix".equals(mPrefs.skipButtonStyle)) {
            horizontalScrollView = (HorizontalScrollView) getLayoutInflater().inflate(R.layout.controls, null);
            controls = horizontalScrollView.findViewById(R.id.controls);

            // IntroDB submission buttons (only if API key is set) - Added BEFORE Open button (Left of it)
            if (mPrefs.introDbApiKey != null && !mPrefs.introDbApiKey.isEmpty()) {
                setupSubmissionButtons(controls);
            }

            controls.addView(buttonOpen);
            controls.addView(buttonSubtitle);  // Our custom always-enabled subtitle button
            controls.addView(buttonAspectRatio);
            if (Utils.isPiPSupported(this) && buttonPiP != null) {
                controls.addView(buttonPiP);
            }
            if (mPrefs.repeatToggle && exoRepeat != null) {
                controls.addView(exoRepeat);
            }
            if (!isTvBox) {
                controls.addView(buttonRotation);
            }
        }
        
        // Add Skip Intro button
        buttonSkipIntro = new android.widget.Button(this);
        buttonSkipIntro.setText("Skip Intro");
        buttonSkipIntro.setVisibility(View.GONE);
        buttonSkipIntro.setAllCaps(false);
        
        // Apply style based on preference
        if ("netflix".equals(mPrefs.skipButtonStyle)) {
            // Netflix style: semi-transparent black with white border
            buttonSkipIntro.setBackgroundResource(R.drawable.bg_skip_button_netflix);
            buttonSkipIntro.setTextColor(Color.WHITE);
            buttonSkipIntro.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            buttonSkipIntro.setPadding(32, 16, 32, 16);
            // TV/D-pad accessibility
            buttonSkipIntro.setFocusable(true);
            buttonSkipIntro.setFocusableInTouchMode(false);
            // Position: bottom-right, ABOVE the seekbar/control bar
            // Use CoordinatorLayout.LayoutParams since parent is CoordinatorLayout
            androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams netflixParams = 
                new androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                );
            netflixParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
            // Increased bottom margin to ensure button is above seekbar
            netflixParams.setMargins(0, 0, 48, 200);
            buttonSkipIntro.setLayoutParams(netflixParams);
            // Add directly to playerView's parent (CoordinatorLayout) instead of controls
            coordinatorLayout.addView(buttonSkipIntro);
        } else {
            // Default style: Netflix-like floating button (visible and styled)
            buttonSkipIntro.setBackgroundResource(R.drawable.bg_skip_button_netflix);
            buttonSkipIntro.setTextColor(Color.WHITE);
            buttonSkipIntro.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            buttonSkipIntro.setPadding(32, 16, 32, 16);
            // Position: bottom-right, ABOVE the seekbar/control bar
            // Use CoordinatorLayout.LayoutParams since parent is CoordinatorLayout
            androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams defaultParams = 
                new androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                );
            defaultParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
            // Increased bottom margin to ensure button is above seekbar
            defaultParams.setMargins(0, 0, 24, 160);
            buttonSkipIntro.setLayoutParams(defaultParams);
            // TV/D-pad accessibility
            buttonSkipIntro.setFocusable(true);
            buttonSkipIntro.setFocusableInTouchMode(false);
            // Add to coordinator layout as floating overlay
            coordinatorLayout.addView(buttonSkipIntro);
        }
        
        // Request focus on button when it becomes visible (for TV)
        buttonSkipIntro.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {}
            @Override
            public void onViewDetachedFromWindow(View v) {}
        });
        
        // Auto-focus button when visible on TV
        buttonSkipIntro.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                buttonSkipIntro.setScaleX(1.1f);
                buttonSkipIntro.setScaleY(1.1f);
            } else {
                buttonSkipIntro.setScaleX(1.0f);
                buttonSkipIntro.setScaleY(1.0f);
            }
        });
        
        buttonSkipIntro.setOnClickListener(view -> {
            if (player != null && currentSkipSegments != null) {
                long posMs = player.getCurrentPosition();
                for (Pair<Double, Double> seg : currentSkipSegments) {
                    long startMs = (long) (seg.getFirst() * 1000);
                    long endMs = (long) (seg.getSecond() * 1000);
                    if (posMs >= startMs && posMs < endMs) {
                        player.seekTo(endMs); // Millisecond precision
                        hasSkippedIntro = true;
                        Toast.makeText(PlayerActivity.this, "Skipped Intro", Toast.LENGTH_SHORT).show();
                        buttonSkipIntro.setVisibility(View.GONE);
                        DebugLogger.INSTANCE.log("SkipBtn", "User clicked skip, jumped to " + endMs + "ms");
                        break;
                    }
                }
            }
        });
        buttonSkipIntro.setOnLongClickListener(v -> {
            showDebugLogs();
            return true;
        });
        
        // Netflix-specific button bindings
        if ("netflix".equals(mPrefs.skipButtonStyle)) {
            setupNetflixControls();
        }
        
        // IntroDB submission buttons (only if API key is set)
        // Handled in controls setup above for default layout
        
        // Add settings to controls only for default layout
        if (controls != null && exoSettings != null) {
            controls.addView(exoSettings);
        }

        if (exoBasicControls != null && horizontalScrollView != null) {
            exoBasicControls.addView(horizontalScrollView);
        }

        if (horizontalScrollView != null && Build.VERSION.SDK_INT > 23) {
            horizontalScrollView.setOnScrollChangeListener((view, i, i1, i2, i3) -> resetHideCallbacks());
        }

        playerView.setControllerVisibilityListener(new PlayerView.ControllerVisibilityListener() {
            @Override
            public void onVisibilityChanged(int visibility) {
                controllerVisible = visibility == View.VISIBLE;
                controllerVisibleFully = playerView.isControllerFullyVisible();

                if (PlayerActivity.restoreControllerTimeout) {
                    restoreControllerTimeout = false;
                    if (player == null || !player.isPlaying()) {
                        playerView.setControllerShowTimeoutMs(-1);
                    } else {
                        playerView.setControllerShowTimeoutMs(PlayerActivity.CONTROLLER_TIMEOUT);
                    }
                }

                // https://developer.android.com/training/system-ui/immersive
                Utils.toggleSystemUi(PlayerActivity.this, playerView, visibility == View.VISIBLE);
                if (visibility == View.VISIBLE) {
                    // Because when using dpad controls, focus resets to first item in bottom controls bar
                    findViewById(R.id.exo_play_pause).requestFocus();
                }

                if (controllerVisible && playerView.isControllerFullyVisible()) {
                    if (mPrefs.firstRun) {
                        TapTargetView.showFor(PlayerActivity.this,
                                TapTarget.forView(buttonOpen, getString(R.string.onboarding_open_title), getString(R.string.onboarding_open_description))
                                        .outerCircleColor(R.color.green)
                                        .targetCircleColor(R.color.white)
                                        .titleTextSize(22)
                                        .titleTextColor(R.color.white)
                                        .descriptionTextSize(14)
                                        .cancelable(true),
                                new TapTargetView.Listener() {
                                    @Override
                                    public void onTargetClick(TapTargetView view) {
                                        super.onTargetClick(view);
                                        buttonOpen.performClick();
                                    }
                                });
                        // TODO: Explain gestures?
                        //  "Use vertical and horizontal gestures to change brightness, volume and seek in video"
                        mPrefs.markFirstRun();
                    }
                    if (errorToShow != null) {
                        showError(errorToShow);
                        errorToShow = null;
                    }
                }
            }
        });

        youTubeOverlay = findViewById(R.id.youtube_overlay);
        if (youTubeOverlay != null) {
            youTubeOverlay.performListener(new YouTubeOverlay.PerformListener() {
                @Override
                public void onAnimationStart() {
                    youTubeOverlay.setAlpha(1.0f);
                    youTubeOverlay.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd() {
                    youTubeOverlay.animate()
                            .alpha(0.0f)
                            .setDuration(300)
                            .setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    youTubeOverlay.setVisibility(View.GONE);
                                    youTubeOverlay.setAlpha(1.0f);
                                }
                            });
                }
            });
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (useMediaStore()) {
                Utils.scanMediaStorage(this);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        alive = true;
        
        // Check if UI style preference changed (e.g., user came back from settings)
        if (currentUiStyle != null) {
            String newPref = mPrefs.mSharedPreferences.getString("skipButtonStyle", "default");
            String expectedStyle = "netflix".equals(newPref) ? (isTvBox ? "netflix_tv" : "netflix") : "default";
            if (!currentUiStyle.equals(expectedStyle) && !currentUiStyle.equals("textureview")) {
                DebugLogger.INSTANCE.log("UI", "UI style changed from " + currentUiStyle + " to " + expectedStyle + ", recreating...");
                Toast.makeText(this, "Reloading player with new UI style...", Toast.LENGTH_SHORT).show();
                recreate();
                return;
            }
        }
        
        if (!(isTvBox && Build.VERSION.SDK_INT >= 31)) {
            updateSubtitleStyle(this);
        }
        if (Build.VERSION.SDK_INT >= 31) {
            playerView.removeCallbacks(barsHider);
            Utils.toggleSystemUi(this, playerView, true);
        }
        initializePlayer();
        updateButtonRotation();
    }

    @Override
    public void onResume() {
        super.onResume();
        restorePlayStateAllowed = true;
        if (isTvBox && Build.VERSION.SDK_INT >= 31) {
            updateSubtitleStyle(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        savePlayer();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (remoteServer != null) {
            remoteServer.stop();
        }
        alive = false;
        if (Build.VERSION.SDK_INT >= 31) {
            playerView.removeCallbacks(barsHider);
        }
        playerView.setCustomErrorMessage(null);
        releasePlayer(false);
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        restorePlayStateAllowed = false;
        super.onBackPressed();
    }

    @Override
    public void finish() {
        if (intentReturnResult) {
            Intent intent = new Intent("com.mxtech.intent.result.VIEW");
            intent.putExtra(API_END_BY, playbackFinished ? "playback_completion" : "user");
            if (!playbackFinished) {
                if (player != null) {
                    long duration = player.getDuration();
                    if (duration != C.TIME_UNSET) {
                        intent.putExtra(API_DURATION, (int) player.getDuration());
                    }
                    if (player.isCurrentMediaItemSeekable()) {
                        if (mPrefs.persistentMode) {
                            intent.putExtra(API_POSITION, (int) mPrefs.nonPersitentPosition);
                        } else {
                            intent.putExtra(API_POSITION, (int) player.getCurrentPosition());
                        }
                    }
                }
            }
            setResult(Activity.RESULT_OK, intent);
        }

        super.finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent != null) {
            final String action = intent.getAction();
            final String type = intent.getType();
            final Uri uri = intent.getData();

            if (Intent.ACTION_VIEW.equals(action) && uri != null) {
                if (SubtitleUtils.isSubtitle(uri, type)) {
                    handleSubtitles(uri);
                } else {
                    mPrefs.updateMedia(this, uri, type);
                    searchSubtitles();
                }
                focusPlay = true;
                initializePlayer();
            } else if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
                String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (text != null) {
                    final Uri parsedUri = Uri.parse(text);
                    if (parsedUri.isAbsolute()) {
                        mPrefs.updateMedia(this, parsedUri, null);
                        focusPlay = true;
                        initializePlayer();
                    }
                }
            }
        }
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (modernController != null && modernController.dispatchKeyEvent(event)) {
             return true;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                if (player == null)
                    break;
                if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                    player.pause();
                } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                    player.play();
                } else if (player.isPlaying()) {
                    player.pause();
                } else {
                    player.play();
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                Utils.adjustVolume(this, mAudioManager, playerView, keyCode == KeyEvent.KEYCODE_VOLUME_UP, event.getRepeatCount() == 0, true);
                return true;
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_SPACE:
                if (player == null)
                    break;
                if (!controllerVisibleFully) {
                    if (player.isPlaying()) {
                        player.pause();
                    } else {
                        player.play();
                    }
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (!controllerVisibleFully || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
                    if (player == null)
                        break;
                    playerView.removeCallbacks(playerView.textClearRunnable);
                    long pos = player.getCurrentPosition();
                    if (playerView.keySeekStart == -1) {
                        playerView.keySeekStart = pos;
                    }
                    long seekTo = pos - 10_000;
                    if (seekTo < 0)
                        seekTo = 0;
                    player.setSeekParameters(SeekParameters.PREVIOUS_SYNC);
                    player.seekTo(seekTo);
                    final String message = Utils.formatMilisSign(seekTo - playerView.keySeekStart) + "\n" + Utils.formatMilis(seekTo);
                    playerView.setCustomErrorMessage(message);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_BUTTON_R2:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (!controllerVisibleFully || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                    if (player == null)
                        break;
                    playerView.removeCallbacks(playerView.textClearRunnable);
                    long pos = player.getCurrentPosition();
                    if (playerView.keySeekStart == -1) {
                        playerView.keySeekStart = pos;
                    }
                    long seekTo = pos + 10_000;
                    long seekMax = player.getDuration();
                    if (seekMax != C.TIME_UNSET && seekTo > seekMax)
                        seekTo = seekMax;
                    PlayerActivity.player.setSeekParameters(SeekParameters.NEXT_SYNC);
                    player.seekTo(seekTo);
                    final String message = Utils.formatMilisSign(seekTo - playerView.keySeekStart) + "\n" + Utils.formatMilis(seekTo);
                    playerView.setCustomErrorMessage(message);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                if (isTvBox) {
                    if (controllerVisible && player != null && player.isPlaying()) {
                        playerView.hideController();
                        return true;
                    } else {
                        onBackPressed();
                    }
                }
                break;
            case KeyEvent.KEYCODE_UNKNOWN:
                return super.onKeyDown(keyCode, event);
            default:
                if (!controllerVisibleFully) {
                    // Only show legacy controller if NOT using Modern UI
                    if (!"netflix".equals(mPrefs.skipButtonStyle)) {
                        playerView.showController();
                    } else if (modernController != null) {
                        modernController.show();
                    }
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                playerView.postDelayed(playerView.textClearRunnable, CustomPlayerView.MESSAGE_TIMEOUT_KEY);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_BUTTON_R2:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (!isScrubbing) {
                    playerView.postDelayed(playerView.textClearRunnable, 1000);
                }
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isScaling) {
            final int keyCode = event.getKeyCode();
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        scale(true);
                        break;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        scale(false);
                        break;
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        break;
                    default:
                        if (isScaleStarting) {
                            isScaleStarting = false;
                        } else {
                            scaleEnd();
                        }
                }
            }
            return true;
        }

        if (isTvBox && !controllerVisibleFully) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                onKeyDown(event.getKeyCode(), event);
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                onKeyUp(event.getKeyCode(), event);
            }
            return true;
        } else {
            return super.dispatchKeyEvent(event);
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (0 != (event.getSource() & InputDevice.SOURCE_CLASS_POINTER)) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_SCROLL:
                    final float value = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                    Utils.adjustVolume(this, mAudioManager, playerView, value > 0.0f, Math.abs(value) > 1.0f, true);
                    return true;
            }
        } else if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK &&
                event.getAction() == MotionEvent.ACTION_MOVE) {
            // TODO: This somehow works, but it would use better filtering
            float value = event.getAxisValue(MotionEvent.AXIS_RZ);
            for (int i = 0; i < event.getHistorySize(); i++) {
                float historical = event.getHistoricalAxisValue(MotionEvent.AXIS_RZ, i);
                if (Math.abs(historical) > value) {
                    value = historical;
                }
            }
            if (Math.abs(value) == 1.0f) {
                Utils.adjustVolume(this, mAudioManager, playerView, value < 0, true, true);
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);

        if (isInPictureInPictureMode) {
            // On Android TV it is required to hide controller in this PIP change callback
            playerView.hideController();
            setSubtitleTextSizePiP();
            playerView.setScale(1.f);
            mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null || !ACTION_MEDIA_CONTROL.equals(intent.getAction()) || player == null) {
                        return;
                    }

                    switch (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                        case CONTROL_TYPE_PLAY:
                            player.play();
                            break;
                        case CONTROL_TYPE_PAUSE:
                            player.pause();
                            break;
                    }
                }
            };
            ContextCompat.registerReceiver(this, mReceiver, new IntentFilter(ACTION_MEDIA_CONTROL), ContextCompat.RECEIVER_EXPORTED);
        } else {
            setSubtitleTextSize();
            if (mPrefs.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                playerView.setScale(mPrefs.scale);
            }
            if (mReceiver != null) {
                unregisterReceiver(mReceiver);
                mReceiver = null;
            }
            if (!"netflix".equals(mPrefs.skipButtonStyle)) {
                playerView.setControllerAutoShow(true);
            }
            if (player != null) {
                if (player.isPlaying())
                    Utils.toggleSystemUi(this, playerView, false);
                else if (!"netflix".equals(mPrefs.skipButtonStyle))
                    playerView.showController();
            }
        }
    }

    void resetApiAccess() {
        apiAccess = false;
        apiAccessPartial = false;
        apiTitle = null;
        apiSubs.clear();
        mPrefs.setPersistent(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            if (restoreOrientationLock) {
                Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
                restoreOrientationLock = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (resultCode == RESULT_OK && alive) {
            releasePlayer();
        }

        if (requestCode == REQUEST_CHOOSER_VIDEO || requestCode == REQUEST_CHOOSER_VIDEO_MEDIASTORE) {
            if (resultCode == RESULT_OK) {
                resetApiAccess();
                restorePlayState = false;

                final Uri uri = data.getData();

                if (requestCode == REQUEST_CHOOSER_VIDEO) {
                    boolean uriAlreadyTaken = false;

                    // https://commonsware.com/blog/2020/06/13/count-your-saf-uri-permission-grants.html
                    final ContentResolver contentResolver = getContentResolver();
                    for (UriPermission persistedUri : contentResolver.getPersistedUriPermissions()) {
                        if (persistedUri.getUri().equals(mPrefs.scopeUri)) {
                            continue;
                        } else if (persistedUri.getUri().equals(uri)) {
                            uriAlreadyTaken = true;
                        } else {
                            try {
                                contentResolver.releasePersistableUriPermission(persistedUri.getUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (SecurityException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    if (!uriAlreadyTaken && uri != null) {
                        try {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException e) {
                            e.printStackTrace();
                        }
                    }
                }

                mPrefs.setPersistent(true);
                mPrefs.updateMedia(this, uri, data.getType());

                if (requestCode == REQUEST_CHOOSER_VIDEO) {
                    searchSubtitles();
                }
            }
        } else if (requestCode == REQUEST_CHOOSER_SUBTITLE || requestCode == REQUEST_CHOOSER_SUBTITLE_MEDIASTORE) {
            if (resultCode == RESULT_OK) {
                Uri uri = data.getData();

                if (requestCode == REQUEST_CHOOSER_SUBTITLE) {
                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }
                }

                handleSubtitles(uri);
            }
        } else if (requestCode == REQUEST_CHOOSER_SCOPE_DIR) {
            if (resultCode == RESULT_OK) {
                final Uri uri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    mPrefs.updateScope(uri);
                    mPrefs.markScopeAsked();
                    searchSubtitles();
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
            }
        } else if (requestCode == REQUEST_SETTINGS) {
            mPrefs.loadUserPreferences();
            updateSubtitleStyle(this);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }

        // Init here because onStart won't follow when app was only paused when file chooser was shown
        // (for example pop-up file chooser on tablets)
        if (resultCode == RESULT_OK && alive) {
            initializePlayer();
        }
    }

    private void handleSubtitles(Uri uri) {
        // Convert subtitles to UTF-8 if necessary
        SubtitleUtils.clearCache(this);
        uri = Utils.convertToUTF(this, uri);
        mPrefs.updateSubtitle(uri);
    }

    public void initializePlayer() {
        boolean isNetworkUri = Utils.isSupportedNetworkUri(mPrefs.mediaUri);
        haveMedia = mPrefs.mediaUri != null;

        if (player != null) {
            player.removeListener(playerListener);
            player.clearMediaItems();
            player.release();
            player = null;
        }

        trackSelector = new DefaultTrackSelector(this);
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true));
        if (mPrefs.tunneling) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setTunnelingEnabled(true)
            );
        }
        switch (mPrefs.languageAudio) {
            case Prefs.TRACK_DEFAULT:
                break;
            case Prefs.TRACK_DEVICE:
                trackSelector.setParameters(trackSelector.buildUponParameters()
                        .setPreferredAudioLanguages(Utils.getDeviceLanguages())
                );
                break;
            default:
                trackSelector.setParameters(trackSelector.buildUponParameters()
                        .setPreferredAudioLanguages(mPrefs.languageAudio)
                );
        }
        final CaptioningManager captioningManager = (CaptioningManager) getSystemService(Context.CAPTIONING_SERVICE);
        if (!captioningManager.isEnabled()) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            );
        }
        Locale locale = captioningManager.getLocale();
        if (locale != null) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setPreferredTextLanguage(locale.getISO3Language())
            );
        }
        
        // Initialize Format Support Manager for HDR/Audio fallback
        if (formatSupportManager == null) {
            formatSupportManager = new FormatSupportManager(this);
        }
        
        // Apply format support preferences to track selector
        formatSupportManager.applyTrackSelectorParams(
            trackSelector,
            mPrefs.forceSdrTonemapping,
            mPrefs.immersiveAudioFallback,
            mPrefs.auroChannelMapping
        );
        
        // Disable Dolby Vision codec selection if preference is enabled
        if (mPrefs.disableDolbyVision) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                .setDisabledTextTrackSelectionFlags(0)
                // Exclude DV codec MIME types - force fallback to HDR10/HEVC base layer
                .addOverride(new TrackSelectionOverride(
                    new TrackGroup(new androidx.media3.common.Format.Builder()
                        .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                        .build()),
                    /* trackIndices= */ java.util.Collections.emptyList()
                ))
            );
            DebugLogger.INSTANCE.log("DV", "Dolby Vision disabled - forcing HDR10/SDR fallback");
        }
        
        // https://github.com/google/ExoPlayer/issues/8571
        DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory()
                .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
                .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE);
        @SuppressLint("WrongConstant") RenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                .setExtensionRendererMode(mPrefs.decoderPriority)
                .setMapDV7ToHevc(mPrefs.mapDV7ToHevc);

        // Configure LoadControl to prevent OOM crashes on large 4K files during seeking
        // Using conservative buffer limits to reduce memory pressure
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15000,  // minBufferMs - reduced from default 50s
                    30000,  // maxBufferMs - reduced from default 50s 
                    1500,   // bufferForPlaybackMs - quick start
                    3000    // bufferForPlaybackAfterRebufferMs
                )
                .setBackBuffer(0, false) // Disable back buffer to save memory during seeks
                .setPrioritizeTimeOverSizeThresholds(true) // Prioritize time over size
                .build();

        ExoPlayer.Builder playerBuilder = new ExoPlayer.Builder(this, renderersFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl) // Apply buffer limits
                .setMediaSourceFactory(new DefaultMediaSourceFactory(this, extractorsFactory));

        if (haveMedia && isNetworkUri) {
            if (mPrefs.mediaUri.getScheme().toLowerCase().startsWith("http")) {
                HashMap<String, String> headers = new HashMap<>();
                String userInfo = mPrefs.mediaUri.getUserInfo();
                if (userInfo != null && userInfo.length() > 0 && userInfo.contains(":")) {
                    headers.put("Authorization", "Basic " + Base64.encodeToString(userInfo.getBytes(), Base64.NO_WRAP));
                    DefaultHttpDataSource.Factory defaultHttpDataSourceFactory = new DefaultHttpDataSource.Factory();
                    defaultHttpDataSourceFactory.setDefaultRequestProperties(headers);
                    playerBuilder.setMediaSourceFactory(new DefaultMediaSourceFactory(defaultHttpDataSourceFactory, extractorsFactory));
                }
            }
        }

        player = playerBuilder.build();

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build();
        player.setAudioAttributes(audioAttributes, true);

        if (mPrefs.skipSilence) {
            player.setSkipSilenceEnabled(true);
        }

        youTubeOverlay.player(player);
        playerView.setPlayer(player);

        if (mediaSession != null) {
            mediaSession.release();
        }

        if (player.canAdvertiseSession()) {
            try {
                mediaSession = new MediaSession.Builder(this, player).build();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }

        playerView.setControllerShowTimeoutMs(-1);

        locked = false;

        if (haveMedia) {
            if (isNetworkUri) {
                timeBar.setBufferedColor(DefaultTimeBar.DEFAULT_BUFFERED_COLOR);
            } else {
                // https://github.com/google/ExoPlayer/issues/5765
                timeBar.setBufferedColor(0x33FFFFFF);
            }

            playerView.setResizeMode(mPrefs.resizeMode);

            if (mPrefs.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                playerView.setScale(mPrefs.scale);
            } else {
                playerView.setScale(1.f);
            }
            updatebuttonAspectRatioIcon();

            MediaItem.Builder mediaItemBuilder = new MediaItem.Builder()
                    .setUri(mPrefs.mediaUri)
                    .setMimeType(mPrefs.mediaType);
            String title;
            if (apiTitle != null) {
                title = apiTitle;
            } else {
                title = Utils.getFileName(PlayerActivity.this, mPrefs.mediaUri);
            }
            if (title != null) {
                final MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                        .setTitle(title)
                        .setDisplayTitle(title)
                        .build();
                mediaItemBuilder.setMediaMetadata(mediaMetadata);
            }
            if (apiAccess && apiSubs.size() > 0) {
                mediaItemBuilder.setSubtitleConfigurations(apiSubs);
            } else if (mPrefs.subtitleUri != null && Utils.fileExists(this, mPrefs.subtitleUri)) {
                MediaItem.SubtitleConfiguration subtitle = SubtitleUtils.buildSubtitle(this, mPrefs.subtitleUri, null, true);
                mediaItemBuilder.setSubtitleConfigurations(Collections.singletonList(subtitle));
            }
            player.setMediaItem(mediaItemBuilder.build(), mPrefs.getPosition());

            try {
                if (loudnessEnhancer != null) {
                    loudnessEnhancer.release();
                }
                loudnessEnhancer = new LoudnessEnhancer(player.getAudioSessionId());
            } catch (Exception e) {
                e.printStackTrace();
            }

            notifyAudioSessionUpdate(true);

            videoLoading = true;

            updateLoading(true);

            if (mPrefs.getPosition() == 0L || apiAccess || apiAccessPartial) {
                play = true;
            }

            // Check user preference for title display
            if (mPrefs.preferFileNameTitle) {
                // Use FilenameResolver to get the REAL filename from:
                // 1. Syncler video_list.filename intent extra
                // 2. AIOStreams apiTitle (parse .mkv/.mp4 pattern)
                // 3. Content-Disposition header (async fallback)
                // 4. Raw apiTitle as final fallback
                Intent launchIntent = getIntent();
                com.brouken.player.utils.FilenameResolver.INSTANCE.resolveFilename(
                    launchIntent,
                    apiTitle,
                    mPrefs.mediaUri,
                    resolvedName -> runOnUiThread(() -> {
                        if (resolvedName != null && !resolvedName.isEmpty()) {
                            titleView.setText(resolvedName);
                        } else if (apiTitle != null) {
                            titleView.setText(apiTitle);
                        } else {
                            titleView.setText(Utils.getFileName(this, mPrefs.mediaUri));
                        }
                    })
                );
            } else if (apiTitle != null) {
                // Use API-provided title (e.g., "S1:E1 - Pilot" from Stremio)
                titleView.setText(apiTitle);
            } else {
                // Fallback to filename
                titleView.setText(Utils.getFileName(this, mPrefs.mediaUri));
            }
            titleView.setVisibility(View.VISIBLE);

            updateButtons(true);

            ((DoubleTapPlayerView)playerView).setDoubleTapEnabled(true);

            if (!apiAccess) {
                if (nextUriThread != null) {
                    nextUriThread.interrupt();
                }
                nextUri = null;
                nextUriThread = new Thread(() -> {
                    Uri uri = findNext();
                    if (!Thread.currentThread().isInterrupted()) {
                        nextUri = uri;
                    }
                });
                nextUriThread.start();
            }

            player.setHandleAudioBecomingNoisy(!isTvBox);
//            mediaSession.setActive(true);
        } else {
            if (!"netflix".equals(mPrefs.skipButtonStyle)) {
                playerView.showController();
            }
        }

        player.addListener(playerListener);
        player.prepare();

        if (restorePlayState) {
            restorePlayState = false;
            if (!"netflix".equals(mPrefs.skipButtonStyle)) {
                playerView.showController();
                playerView.setControllerShowTimeoutMs(PlayerActivity.CONTROLLER_TIMEOUT);
            }
            player.setPlayWhenReady(true);
        }

        // Initialize Modern UI Overlay if selected
        if ("netflix".equals(mPrefs.skipButtonStyle)) {
             playerView.setUseController(false); // Disable default controller
             
             // --- CRITICAL: Disable DoubleTap overlay to prevent duplicate UI ---
             if (playerView instanceof com.brouken.player.dtpv.DoubleTapPlayerView) {
                 com.brouken.player.dtpv.DoubleTapPlayerView dtpv = (com.brouken.player.dtpv.DoubleTapPlayerView) playerView;
                 dtpv.setDoubleTapEnabled(false);
                 dtpv.controller(null);
             }
             // --- End DoubleTap Disable ---
             
             if (modernController != null) modernController.release();
             modernController = new ModernController(this, player, playerView);
             modernController.attach((ViewGroup) findViewById(R.id.coordinatorLayout));
        } else {
             if (modernController != null) {
                 modernController.release();
                 modernController = null;
             }
             playerView.setUseController(true); // Enable default controller for others
        }
    }

    private void savePlayer() {
        if (player != null) {
            mPrefs.updateBrightness(mBrightnessControl.currentBrightnessLevel);
            mPrefs.updateOrientation();

            if (haveMedia) {
                // Prevent overwriting temporarily inaccessible media position
                if (player.isCurrentMediaItemSeekable()) {
                    mPrefs.updatePosition(player.getCurrentPosition());
                }
                mPrefs.updateMeta(getSelectedTrack(C.TRACK_TYPE_AUDIO),
                        getSelectedTrack(C.TRACK_TYPE_TEXT),
                        playerView.getResizeMode(),
                        playerView.getVideoSurfaceView().getScaleX(),
                        player.getPlaybackParameters().speed);
            }
        }
    }

    public void releasePlayer() {
        if (modernController != null) {
            modernController.release();
            modernController = null;
        }
        releasePlayer(true);
    }

    public void releasePlayer(boolean save) {
        if (save) {
            savePlayer();
        }

        // Trakt: Stop scrobbling and release
        traktScrobbleStop();
        releaseTraktScrobbler();

        if (player != null) {
            notifyAudioSessionUpdate(false);

//            mediaSession.setActive(false);
            if (mediaSession != null) {
                mediaSession.release();
            }

            if (player.isPlaying() && restorePlayStateAllowed) {
                restorePlayState = true;
            }
            player.removeListener(playerListener);
            player.clearMediaItems();
            player.release();
            player = null;
            skipHandler.removeCallbacks(skipRunnable);
        }
        titleView.setVisibility(View.GONE);
        updateButtons(false);
    }

    private class PlayerListener implements Player.Listener {
        @Override
        public void onAudioSessionIdChanged(int audioSessionId) {
            try {
                if (loudnessEnhancer != null) {
                    loudnessEnhancer.release();
                }
                loudnessEnhancer = new LoudnessEnhancer(audioSessionId);
            } catch (Exception e) {
                e.printStackTrace();
            }
            notifyAudioSessionUpdate(true);
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            DebugLogger.INSTANCE.log("PlayerState", "onIsPlayingChanged: isPlaying=" + isPlaying);
            playerView.setKeepScreenOn(isPlaying);

            // AutoSkip handler control
            if (isPlaying) {
                DebugLogger.INSTANCE.log("PlayerState", "Player started - starting intro skip monitoring");
                skipHandler.removeCallbacks(skipRunnable);
                skipHandler.post(skipRunnable);
                checkUpcomingIntro();
                
                // Trakt: Start/resume scrobbling
                DebugLogger.INSTANCE.log("Trakt", "╔══════════════════════════════════════════════════════════════════╗");
                DebugLogger.INSTANCE.log("Trakt", "║  onIsPlayingChanged: isPlaying=TRUE - STARTING TRAKT             ║");
                DebugLogger.INSTANCE.log("Trakt", "╚══════════════════════════════════════════════════════════════════╝");
                if (traktScrobbler == null) {
                    DebugLogger.INSTANCE.log("Trakt", "  traktScrobbler is NULL, calling initTraktScrobbler()...");
                    initTraktScrobbler();
                } else {
                    DebugLogger.INSTANCE.log("Trakt", "  traktScrobbler already exists");
                }
                DebugLogger.INSTANCE.log("Trakt", "  Calling traktScrobbleStart()...");
                traktScrobbleStart();
            } else {
                DebugLogger.INSTANCE.log("PlayerState", "Player paused - stopping intro skip monitoring");
                skipHandler.removeCallbacks(skipRunnable);
                
                // Trakt: Pause scrobbling
                DebugLogger.INSTANCE.log("Trakt", "╔══════════════════════════════════════════════════════════════════╗");
                DebugLogger.INSTANCE.log("Trakt", "║  onIsPlayingChanged: isPlaying=FALSE - PAUSING TRAKT             ║");
                DebugLogger.INSTANCE.log("Trakt", "╚══════════════════════════════════════════════════════════════════╝");
                DebugLogger.INSTANCE.log("Trakt", "  Calling traktScrobblePause()...");
                traktScrobblePause();
            }

            if (Utils.isPiPSupported(PlayerActivity.this)) {
                if (isPlaying) {
                    updatePictureInPictureActions(R.drawable.ic_pause_24dp, R.string.exo_controls_pause_description, CONTROL_TYPE_PAUSE, REQUEST_PAUSE);
                } else {
                    updatePictureInPictureActions(R.drawable.ic_play_arrow_24dp, R.string.exo_controls_play_description, CONTROL_TYPE_PLAY, REQUEST_PLAY);
                }
            }

            if (!isScrubbing) {
                if (isPlaying) {
                    if (shortControllerTimeout) {
                        playerView.setControllerShowTimeoutMs(CONTROLLER_TIMEOUT / 3);
                        shortControllerTimeout = false;
                        restoreControllerTimeout = true;
                    } else {
                        playerView.setControllerShowTimeoutMs(CONTROLLER_TIMEOUT);
                    }
                } else {
                    playerView.setControllerShowTimeoutMs(-1);
                }
            }

            if (!isPlaying) {
                PlayerActivity.locked = false;
            }
        }

        @SuppressLint("SourceLockedOrientationActivity")
        @Override
        public void onPlaybackStateChanged(int state) {
            boolean isNearEnd = false;
            final long duration = player.getDuration();
            if (duration != C.TIME_UNSET) {
                final long position = player.getCurrentPosition();
                if (position + 4000 >= duration) {
                    isNearEnd = true;
                }
            }
            setEndControlsVisible(haveMedia && (state == Player.STATE_ENDED || isNearEnd));

            if (state == Player.STATE_READY) {
                frameRendered = true;
                
                DebugLogger.INSTANCE.log("PlayerState", "╔════════════════════════════════════════════════════════════╗");
                DebugLogger.INSTANCE.log("PlayerState", "║  PLAYER STATE: READY                                       ║");
                DebugLogger.INSTANCE.log("PlayerState", "╚════════════════════════════════════════════════════════════╝");

                if (videoLoading) {
                    videoLoading = false;
                    
                    DebugLogger.INSTANCE.log("PlayerState", "Video loading complete - initializing features");

                    if (mPrefs.orientation == Utils.Orientation.UNSPECIFIED) {
                        mPrefs.orientation = Utils.getNextOrientation(mPrefs.orientation);
                        Utils.setOrientation(PlayerActivity.this, mPrefs.orientation);
                    }

                    // Fetch skip data for intro skipping (INDEPENDENT from jumpscare)
                    DebugLogger.INSTANCE.log("PlayerState", "Checking intro skip preference: " + mPrefs.introSkipEnabled);
                    if (mPrefs.introSkipEnabled) {
                        DebugLogger.INSTANCE.log("PlayerState", "Fetching intro skip data...");
                        fetchSkipData();
                    } else {
                        DebugLogger.INSTANCE.log("PlayerState", "Intro skip is disabled, skipping fetch");
                    }
                    
                    // Jump Scare feature is disabled/hidden until further notice
                    // DebugLogger.INSTANCE.log("PlayerState", "=== Jump Scare Manager DISABLED ===");


                    final Format format = player.getVideoFormat();

                    if (format != null) {
                        if (!isTvBox && mPrefs.orientation == Utils.Orientation.VIDEO) {
                            if (Utils.isPortrait(format)) {
                                PlayerActivity.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
                            } else {
                                PlayerActivity.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                            }
                            updateButtonRotation();
                        }

                        updateSubtitleViewMargin(format);
                    }

                    if (duration != C.TIME_UNSET && duration > TimeUnit.MINUTES.toMillis(20)) {
                        timeBar.setKeyTimeIncrement(TimeUnit.MINUTES.toMillis(1));
                    } else {
                        timeBar.setKeyCountIncrement(20);
                    }

                    boolean switched = false;
                    if (mPrefs.frameRateMatching) {
                        if (play) {
                            if (displayManager == null) {
                                displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
                            }
                            if (displayListener == null) {
                                displayListener = new DisplayManager.DisplayListener() {
                                    @Override
                                    public void onDisplayAdded(int displayId) {

                                    }

                                    @Override
                                    public void onDisplayRemoved(int displayId) {

                                    }

                                    @Override
                                    public void onDisplayChanged(int displayId) {
                                        if (play) {
                                            play = false;
                                            displayManager.unregisterDisplayListener(this);
                                            if (player != null) {
                                                player.play();
                                            }
                                            if (playerView != null) {
                                                playerView.hideController();
                                            }
                                        }
                                    }
                                };
                            }
                            displayManager.registerDisplayListener(displayListener, null);
                        }
                        switched = Utils.switchFrameRate(PlayerActivity.this, mPrefs.mediaUri, play);
                    }
                    if (!switched) {
                        if (displayManager != null) {
                            displayManager.unregisterDisplayListener(displayListener);
                        }
                        if (play) {
                            play = false;
                            player.play();
                            playerView.hideController();
                        }
                    }

                    updateLoading(false);

                    if (mPrefs.speed <= 0.99f || mPrefs.speed >= 1.01f) {
                        player.setPlaybackSpeed(mPrefs.speed);
                    }
                    if (!apiAccess) {
                        setSelectedTracks(mPrefs.subtitleTrackId, mPrefs.audioTrackId);
                    }
                    
                    // Force subtitle button enabled after ExoPlayer updates tracks
                    forceSubtitleButtonEnabled();
                }
            } else if (state == Player.STATE_ENDED) {
                playbackFinished = true;
                if (apiAccess) {
                    finish();
                }
            }
        }

        @Override
        public void onMetadata(Metadata metadata) {
            if (metadata != null) {
                java.util.List<Pair<Double, Double>> chapters = ChapterScanner.scanForIntro(metadata);
                if (!chapters.isEmpty()) {
                    currentSkipSegments = chapters;
                    hasChapterSkip = true;
                    DebugLogger.INSTANCE.log("SkipData", "Chapter markers found intro! Overriding any API data.");
                    runOnUiThread(() -> {
                        Utils.showToast(PlayerActivity.this, 
                            "Intro found in Chapters (" + chapters.size() + ")");
                    });
                }
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            updateLoading(false);
            if (error instanceof ExoPlaybackException) {
                final ExoPlaybackException exoPlaybackException = (ExoPlaybackException) error;
                if (exoPlaybackException.type == ExoPlaybackException.TYPE_SOURCE) {
                    releasePlayer(false);
                    return;
                }
                if (controllerVisible && controllerVisibleFully) {
                    showError(exoPlaybackException);
                } else {
                    errorToShow = exoPlaybackException;
                }
            }
        }
    }

    private void enableRotation() {
        try {
            if (Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION) == 0) {
                Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1);
                restoreOrientationLock = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    boolean useMediaStore() {
        final int targetSdkVersion = getApplicationContext().getApplicationInfo().targetSdkVersion;
        return (isTvBox && Build.VERSION.SDK_INT >= 30 && targetSdkVersion >= 30 && mPrefs.fileAccess.equals("auto")) || mPrefs.fileAccess.equals("mediastore");
    }

    public void openFile(Uri pickerInitialUri) {
        if (useMediaStore()) {
            Intent intent = new Intent(this, MediaStoreChooserActivity.class);
            startActivityForResult(intent, REQUEST_CHOOSER_VIDEO_MEDIASTORE);
        } else if ((isTvBox && mPrefs.fileAccess.equals("auto")) || mPrefs.fileAccess.equals("legacy")) {
            Utils.alternativeChooser(this, pickerInitialUri, true);
        } else {
            enableRotation();

            if (pickerInitialUri == null || Utils.isSupportedNetworkUri(pickerInitialUri)) {
                pickerInitialUri = Utils.getMoviesFolderUri();
            }

            final Intent intent = createBaseFileIntent(Intent.ACTION_OPEN_DOCUMENT, pickerInitialUri);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, Utils.supportedMimeTypesVideo);

            if (Build.VERSION.SDK_INT < 30) {
                final ComponentName systemComponentName = Utils.getSystemComponent(this, intent);
                if (systemComponentName != null) {
                    intent.setComponent(systemComponentName);
                }
            }

            safelyStartActivityForResult(intent, REQUEST_CHOOSER_VIDEO);
        }
    }

    public void loadSubtitleFile(Uri pickerInitialUri) {
        Utils.showToast(PlayerActivity.this, getString(R.string.open_subtitles));
        final int targetSdkVersion = getApplicationContext().getApplicationInfo().targetSdkVersion;
        if ((isTvBox && Build.VERSION.SDK_INT >= 30 && targetSdkVersion >= 30 && mPrefs.fileAccess.equals("auto")) || mPrefs.fileAccess.equals("mediastore")) {
            Intent intent = new Intent(this, MediaStoreChooserActivity.class);
            intent.putExtra(MediaStoreChooserActivity.SUBTITLES, true);
            startActivityForResult(intent, REQUEST_CHOOSER_SUBTITLE_MEDIASTORE);
        } else if ((isTvBox && mPrefs.fileAccess.equals("auto")) || mPrefs.fileAccess.equals("legacy")) {
            Utils.alternativeChooser(this, pickerInitialUri, false);
        } else {
            enableRotation();

            final Intent intent = createBaseFileIntent(Intent.ACTION_OPEN_DOCUMENT, pickerInitialUri);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");

            final String[] supportedMimeTypes = {
                    MimeTypes.APPLICATION_SUBRIP,
                    MimeTypes.TEXT_SSA,
                    MimeTypes.TEXT_VTT,
                    MimeTypes.APPLICATION_TTML,
                    "text/*",
                    "application/octet-stream"
            };
            intent.putExtra(Intent.EXTRA_MIME_TYPES, supportedMimeTypes);

            if (Build.VERSION.SDK_INT < 30) {
                final ComponentName systemComponentName = Utils.getSystemComponent(this, intent);
                if (systemComponentName != null) {
                    intent.setComponent(systemComponentName);
                }
            }

            safelyStartActivityForResult(intent, REQUEST_CHOOSER_SUBTITLE);
        }
    }

    private void requestDirectoryAccess() {
        enableRotation();
        final Intent intent = createBaseFileIntent(Intent.ACTION_OPEN_DOCUMENT_TREE, Utils.getMoviesFolderUri());
        safelyStartActivityForResult(intent, REQUEST_CHOOSER_SCOPE_DIR);
    }

    private Intent createBaseFileIntent(final String action, final Uri initialUri) {
        final Intent intent = new Intent(action);

        // http://stackoverflow.com/a/31334967/1615876
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);

        if (Build.VERSION.SDK_INT >= 26 && initialUri != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
        }

        return intent;
    }

    void safelyStartActivityForResult(final Intent intent, final int code) {
        if (intent.resolveActivity(getPackageManager()) == null)
            showSnack(getText(R.string.error_files_missing).toString(), intent.toString());
        else
            startActivityForResult(intent, code);
    }

    private TrackGroup getTrackGroupFromFormatId(int trackType, String id) {
        if ((id == null && trackType == C.TRACK_TYPE_AUDIO ) || player == null) {
            return null;
        }
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() == trackType) {
                final TrackGroup trackGroup = group.getMediaTrackGroup();
                final Format format = trackGroup.getFormat(0);
                if (Objects.equals(id, format.id)) {
                    return trackGroup;
                }
            }
        }
        return null;
    }

    public void setSelectedTracks(final String subtitleId, final String audioId) {
        if ("#none".equals(subtitleId)) {
            if (trackSelector == null) {
                return;
            }
            trackSelector.setParameters(trackSelector.buildUponParameters().setDisabledTextTrackSelectionFlags(C.SELECTION_FLAG_DEFAULT | C.SELECTION_FLAG_FORCED));
        }

        TrackGroup subtitleGroup = getTrackGroupFromFormatId(C.TRACK_TYPE_TEXT, subtitleId);
        TrackGroup audioGroup = getTrackGroupFromFormatId(C.TRACK_TYPE_AUDIO, audioId);

        TrackSelectionParameters.Builder overridesBuilder = new TrackSelectionParameters.Builder(this);
        TrackSelectionOverride trackSelectionOverride = null;
        final List<Integer> tracks = new ArrayList<>(); tracks.add(0);
        if (subtitleGroup != null) {
            trackSelectionOverride = new TrackSelectionOverride(subtitleGroup, tracks);
            overridesBuilder.addOverride(trackSelectionOverride);
        }
        if (audioGroup != null) {
            trackSelectionOverride = new TrackSelectionOverride(audioGroup, tracks);
            overridesBuilder.addOverride(trackSelectionOverride);
        }

        if (player != null) {
            TrackSelectionParameters.Builder trackSelectionParametersBuilder = player.getTrackSelectionParameters().buildUpon();
            if (trackSelectionOverride != null) {
                trackSelectionParametersBuilder.setOverrideForType(trackSelectionOverride);
            }
            player.setTrackSelectionParameters(trackSelectionParametersBuilder.build());
        }
    }

    private boolean hasOverrideType(final int trackType) {
        TrackSelectionParameters trackSelectionParameters = player.getTrackSelectionParameters();
        for (TrackSelectionOverride override : trackSelectionParameters.overrides.values()) {
            if (override.getType() == trackType)
                return true;
        }
        return false;
    }

    public String getSelectedTrack(final int trackType) {
        if (player == null) {
            return null;
        }
        Tracks tracks = player.getCurrentTracks();

        // Disabled (e.g. selected subtitle "None" - different than default)
        if (!tracks.isTypeSelected(trackType)) {
            return "#none";
        }

        // Audio track set to "Auto"
        if (trackType == C.TRACK_TYPE_AUDIO) {
            if (!hasOverrideType(C.TRACK_TYPE_AUDIO)) {
                return null;
            }
        }

        for (Tracks.Group group : tracks.getGroups()) {
            if (group.isSelected() && group.getType() == trackType) {
                Format format = group.getMediaTrackGroup().getFormat(0);
                return format.id;
            }
        }

        return null;
    }

    void setSubtitleTextSize() {
        setSubtitleTextSize(getResources().getConfiguration().orientation);
    }

    void setSubtitleTextSize(final int orientation) {
        // Tweak text size as fraction size doesn't work well in portrait
        final SubtitleView subtitleView = playerView.getSubtitleView();
        if (subtitleView != null) {
            final float size;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                size = SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitlesScale;
            } else {
                DisplayMetrics metrics = getResources().getDisplayMetrics();
                float ratio = ((float)metrics.heightPixels / (float)metrics.widthPixels);
                if (ratio < 1)
                    ratio = 1 / ratio;
                size = SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitlesScale / ratio;
            }

            subtitleView.setFractionalTextSize(size);
        }
    }

    void updateSubtitleViewMargin() {
        if (player == null) {
            return;
        }

        updateSubtitleViewMargin(player.getVideoFormat());
    }

    // Set margins to fix PGS aspect as subtitle view is outside of content frame
    void updateSubtitleViewMargin(Format format) {
        if (format == null) {
            return;
        }

        final Rational aspectVideo = Utils.getRational(format);
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        final Rational aspectDisplay = new Rational(metrics.widthPixels, metrics.heightPixels);

        int marginHorizontal = 0;
        int marginVertical = 0;

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (aspectDisplay.floatValue() > aspectVideo.floatValue()) {
                // Left & right bars
                int videoWidth = metrics.heightPixels / aspectVideo.getDenominator() * aspectVideo.getNumerator();
                marginHorizontal = (metrics.widthPixels - videoWidth) / 2;
            }
        }

        Utils.setViewParams(playerView.getSubtitleView(), 0, 0, 0, 0,
                marginHorizontal, marginVertical, marginHorizontal, marginVertical);
    }

    void setSubtitleTextSizePiP() {
        final SubtitleView subtitleView = playerView.getSubtitleView();
        if (subtitleView != null)
            subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 2);
    }

    @TargetApi(26)
    boolean updatePictureInPictureActions(final int iconId, final int resTitle, final int controlType, final int requestCode) {
        try {
            final ArrayList<RemoteAction> actions = new ArrayList<>();
            final PendingIntent intent = PendingIntent.getBroadcast(PlayerActivity.this, requestCode,
                    new Intent(ACTION_MEDIA_CONTROL).putExtra(EXTRA_CONTROL_TYPE, controlType), PendingIntent.FLAG_IMMUTABLE);
            final Icon icon = Icon.createWithResource(PlayerActivity.this, iconId);
            final String title = getString(resTitle);
            actions.add(new RemoteAction(icon, title, title, intent));
            ((PictureInPictureParams.Builder) mPictureInPictureParamsBuilder).setActions(actions);
            setPictureInPictureParams(((PictureInPictureParams.Builder) mPictureInPictureParamsBuilder).build());
            return true;
        } catch (IllegalStateException e) {
            // On Samsung devices with Talkback active:
            // Caused by: java.lang.IllegalStateException: setPictureInPictureParams: Device doesn't support picture-in-picture mode.
            e.printStackTrace();
        }
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private boolean isInPip() {
        if (!Utils.isPiPSupported(this))
            return false;
        return isInPictureInPictureMode();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (!isInPip()) {
            setSubtitleTextSize(newConfig.orientation);
        }
        updateSubtitleViewMargin();

        updateButtonRotation();
    }

    void showError(ExoPlaybackException error) {
        final String errorGeneral = error.getLocalizedMessage();
        String errorDetailed;

        switch (error.type) {
            case ExoPlaybackException.TYPE_SOURCE:
                errorDetailed = error.getSourceException().getLocalizedMessage();
                break;
            case ExoPlaybackException.TYPE_RENDERER:
                errorDetailed = error.getRendererException().getLocalizedMessage();
                break;
            case ExoPlaybackException.TYPE_UNEXPECTED:
                errorDetailed = error.getUnexpectedException().getLocalizedMessage();
                break;
            case ExoPlaybackException.TYPE_REMOTE:
            default:
                errorDetailed = errorGeneral;
                break;
        }

        showSnack(errorGeneral, errorDetailed);
    }

    void showSnack(final String textPrimary, final String textSecondary) {
        snackbar = Snackbar.make(coordinatorLayout, textPrimary, Snackbar.LENGTH_LONG);
        if (textSecondary != null) {
            snackbar.setAction(R.string.error_details, v -> {
                final AlertDialog.Builder builder = new AlertDialog.Builder(PlayerActivity.this);
                builder.setMessage(textSecondary);
                builder.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> dialogInterface.dismiss());
                final AlertDialog dialog = builder.create();
                dialog.show();
            });
        }
        snackbar.setAnchorView(R.id.exo_bottom_bar);
        snackbar.show();
    }

    void reportScrubbing(long position) {
        final long diff = position - scrubbingStart;
        if (Math.abs(diff) > 1000) {
            scrubbingNoticeable = true;
        }
        if (scrubbingNoticeable) {
            playerView.clearIcon();
            playerView.setCustomErrorMessage(Utils.formatMilisSign(diff));
        }
        if (frameRendered) {
            frameRendered = false;
            if (player != null) {
                player.seekTo(position);
            }
        }
    }

    void updateSubtitleStyle(final Context context) {
        final CaptioningManager captioningManager = (CaptioningManager) getSystemService(Context.CAPTIONING_SERVICE);
        final SubtitleView subtitleView = playerView.getSubtitleView();
        final boolean isTablet = Utils.isTablet(context);
        subtitlesScale = SubtitleUtils.normalizeFontScale(captioningManager.getFontScale(), isTvBox || isTablet);
        if (subtitleView != null) {
            final CaptioningManager.CaptionStyle userStyle = captioningManager.getUserStyle();
            final CaptionStyleCompat userStyleCompat = CaptionStyleCompat.createFromCaptionStyle(userStyle);
            final CaptionStyleCompat captionStyle = new CaptionStyleCompat(
                    userStyle.hasForegroundColor() ? userStyleCompat.foregroundColor : Color.WHITE,
                    userStyle.hasBackgroundColor() ? userStyleCompat.backgroundColor : Color.TRANSPARENT,
                    userStyle.hasWindowColor() ? userStyleCompat.windowColor : Color.TRANSPARENT,
                    userStyle.hasEdgeType() ? userStyleCompat.edgeType : CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    userStyle.hasEdgeColor() ? userStyleCompat.edgeColor : Color.BLACK,
                    Typeface.create(userStyleCompat.typeface != null ? userStyleCompat.typeface : Typeface.DEFAULT,
                            mPrefs.subtitleStyleBold ? Typeface.BOLD : Typeface.NORMAL));
            subtitleView.setStyle(captionStyle);
            // Force embedded styles for SSA/ASS support
            subtitleView.setApplyEmbeddedStyles(true);
            subtitleView.setBottomPaddingFraction(SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION * 2f / 3f);
        }
        setSubtitleTextSize();
    }

    void searchSubtitles() {
        if (mPrefs.mediaUri == null)
            return;

        if (Utils.isSupportedNetworkUri(mPrefs.mediaUri) && Utils.isProgressiveContainerUri(mPrefs.mediaUri)) {
            SubtitleUtils.clearCache(this);
            if (SubtitleFinder.isUriCompatible(mPrefs.mediaUri)) {
                subtitleFinder = new SubtitleFinder(PlayerActivity.this, mPrefs.mediaUri);
                subtitleFinder.start();
            }
            return;
        }

        if (mPrefs.scopeUri != null || isTvBox) {
            DocumentFile video = null;
            File videoRaw = null;
            final String scheme = mPrefs.mediaUri.getScheme();

            if (mPrefs.scopeUri != null) {
                if ("com.android.externalstorage.documents".equals(mPrefs.mediaUri.getHost()) ||
                        "org.courville.nova.provider".equals(mPrefs.mediaUri.getHost())) {
                    // Fast search based on path in uri
                    video = SubtitleUtils.findUriInScope(this, mPrefs.scopeUri, mPrefs.mediaUri);
                } else {
                    // Slow search based on matching metadata, no path in uri
                    // Provider "com.android.providers.media.documents" when using "Videos" tab in file picker
                    DocumentFile fileScope = DocumentFile.fromTreeUri(this, mPrefs.scopeUri);
                    DocumentFile fileMedia = DocumentFile.fromSingleUri(this, mPrefs.mediaUri);
                    video = SubtitleUtils.findDocInScope(fileScope, fileMedia);
                }
            } else if (ContentResolver.SCHEME_FILE.equals(scheme)) {
                videoRaw = new File(mPrefs.mediaUri.getSchemeSpecificPart());
                video = DocumentFile.fromFile(videoRaw);
            }

            if (video != null) {
                DocumentFile subtitle = null;
                if (mPrefs.scopeUri != null) {
                    subtitle = SubtitleUtils.findSubtitle(video);
                } else if (ContentResolver.SCHEME_FILE.equals(scheme)) {
                    File parentRaw = videoRaw.getParentFile();
                    DocumentFile dir = DocumentFile.fromFile(parentRaw);
                    subtitle = SubtitleUtils.findSubtitle(video, dir);
                }

                if (subtitle != null) {
                    handleSubtitles(subtitle.getUri());
                }
            }
        }
    }

    Uri findNext() {
        // TODO: Unify with searchSubtitles()
        if (mPrefs.scopeUri != null || isTvBox) {
            DocumentFile video = null;
            File videoRaw = null;

            if (!isTvBox && mPrefs.scopeUri != null) {
                if ("com.android.externalstorage.documents".equals(mPrefs.mediaUri.getHost())) {
                    // Fast search based on path in uri
                    video = SubtitleUtils.findUriInScope(this, mPrefs.scopeUri, mPrefs.mediaUri);
                } else {
                    // Slow search based on matching metadata, no path in uri
                    // Provider "com.android.providers.media.documents" when using "Videos" tab in file picker
                    DocumentFile fileScope = DocumentFile.fromTreeUri(this, mPrefs.scopeUri);
                    DocumentFile fileMedia = DocumentFile.fromSingleUri(this, mPrefs.mediaUri);
                    video = SubtitleUtils.findDocInScope(fileScope, fileMedia);
                }
            } else if (isTvBox) {
                videoRaw = new File(mPrefs.mediaUri.getSchemeSpecificPart());
                video = DocumentFile.fromFile(videoRaw);
            }

            if (video != null) {
                DocumentFile next;
                if (!isTvBox) {
                    next = SubtitleUtils.findNext(video);
                } else {
                    File parentRaw = videoRaw.getParentFile();
                    DocumentFile dir = DocumentFile.fromFile(parentRaw);
                    next = SubtitleUtils.findNext(video, dir);
                }
                if (next != null) {
                    return next.getUri();
                }
            }
        }
        return null;
    }

    void askForScope(boolean loadSubtitlesOnCancel, boolean skipToNextOnCancel) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(PlayerActivity.this);
        builder.setMessage(String.format(getString(R.string.request_scope), getString(R.string.app_name)));
        builder.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> requestDirectoryAccess()
        );
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> {
            mPrefs.markScopeAsked();
            if (loadSubtitlesOnCancel) {
                loadSubtitleFile(mPrefs.mediaUri);
            }
            if (skipToNextOnCancel) {
                nextUri = findNext();
                if (nextUri != null) {
                    skipToNext();
                }
            }
        });
        final AlertDialog dialog = builder.create();
        dialog.show();
    }

    void resetHideCallbacks() {
        if (haveMedia && player != null && player.isPlaying()) {
            // Keep controller UI visible - alternative to resetHideCallbacks()
            playerView.setControllerShowTimeoutMs(PlayerActivity.CONTROLLER_TIMEOUT);
        }
    }

    private void updateLoading(final boolean enableLoading) {
        if (enableLoading) {
            exoPlayPause.setVisibility(View.GONE);
            loadingProgressBar.setVisibility(View.VISIBLE);
        } else {
            loadingProgressBar.setVisibility(View.GONE);
            exoPlayPause.setVisibility(View.VISIBLE);
            if (focusPlay) {
                focusPlay = false;
                exoPlayPause.requestFocus();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onUserLeaveHint() {
        if (mPrefs!= null && mPrefs.autoPiP && player != null && player.isPlaying() && Utils.isPiPSupported(this))
            enterPiP();
        else
            super.onUserLeaveHint();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void enterPiP() {
        final AppOpsManager appOpsManager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        if (AppOpsManager.MODE_ALLOWED != appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, android.os.Process.myUid(), getPackageName())) {
            final Intent intent = new Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS", Uri.fromParts("package", getPackageName(), null));
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            }
            return;
        }

        if (player == null) {
            return;
        }

        playerView.setControllerAutoShow(false);
        playerView.hideController();

        final Format format = player.getVideoFormat();

        if (format != null) {
            // https://github.com/google/ExoPlayer/issues/8611
            // TODO: Test/disable on Android 11+
            final View videoSurfaceView = playerView.getVideoSurfaceView();
            if (videoSurfaceView instanceof SurfaceView) {
                ((SurfaceView)videoSurfaceView).getHolder().setFixedSize(format.width, format.height);
            }

            Rational rational = Utils.getRational(format);
            if (Build.VERSION.SDK_INT >= 33 &&
                    getPackageManager().hasSystemFeature(FEATURE_EXPANDED_PICTURE_IN_PICTURE) &&
                    (rational.floatValue() > rationalLimitWide.floatValue() || rational.floatValue() < rationalLimitTall.floatValue())) {
                ((PictureInPictureParams.Builder)mPictureInPictureParamsBuilder).setExpandedAspectRatio(rational);
            }
            if (rational.floatValue() > rationalLimitWide.floatValue())
                rational = rationalLimitWide;
            else if (rational.floatValue() < rationalLimitTall.floatValue())
                rational = rationalLimitTall;

            ((PictureInPictureParams.Builder)mPictureInPictureParamsBuilder).setAspectRatio(rational);
        }
        enterPictureInPictureMode(((PictureInPictureParams.Builder)mPictureInPictureParamsBuilder).build());
    }

    void setEndControlsVisible(boolean visible) {
        final int deleteVisible = (visible && haveMedia && Utils.isDeletable(this, mPrefs.mediaUri)) ? View.VISIBLE : View.INVISIBLE;
        final int nextVisible = (visible && haveMedia && (nextUri != null || (mPrefs.askScope && !isTvBox))) ? View.VISIBLE : View.INVISIBLE;
        View deleteView = findViewById(R.id.delete);
        View nextView = findViewById(R.id.next);
        if (deleteView != null) deleteView.setVisibility(deleteVisible);
        if (nextView != null) nextView.setVisibility(nextVisible);
    }

    void askDeleteMedia() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(PlayerActivity.this);
        builder.setMessage(getString(R.string.delete_query));
        builder.setPositiveButton(R.string.delete_confirmation, (dialogInterface, i) -> {
            releasePlayer();
            deleteMedia();
            if (nextUri == null) {
                haveMedia = false;
                setEndControlsVisible(false);
                playerView.setControllerShowTimeoutMs(-1);
            } else {
                skipToNext();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> {});
        final AlertDialog dialog = builder.create();
        dialog.show();
    }

    void deleteMedia() {
        try {
            if (ContentResolver.SCHEME_CONTENT.equals(mPrefs.mediaUri.getScheme())) {
                DocumentsContract.deleteDocument(getContentResolver(), mPrefs.mediaUri);
            } else if (ContentResolver.SCHEME_FILE.equals(mPrefs.mediaUri.getScheme())) {
                final File file = new File(mPrefs.mediaUri.getSchemeSpecificPart());
                if (file.canWrite()) {
                    file.delete();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void dispatchPlayPause() {
        if (player == null)
            return;

        @Player.State int state = player.getPlaybackState();
        if (state == Player.STATE_IDLE || state == Player.STATE_ENDED || !player.getPlayWhenReady()) {
            shortControllerTimeout = true;
            androidx.media3.common.util.Util.handlePlayButtonAction(player);
        } else {
            androidx.media3.common.util.Util.handlePauseButtonAction(player);
        }
    }

    public void skipToNext() {
        if (nextUri != null) {
            releasePlayer();
            mPrefs.updateMedia(this, nextUri, null);
            searchSubtitles();
            initializePlayer();
        }
    }

    void notifyAudioSessionUpdate(final boolean active) {
        final Intent intent = new Intent(active ? AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
                : AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.getAudioSessionId());
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        if (active) {
            intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MOVIE);
        }
        try {
            sendBroadcast(intent);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    void updateButtons(final boolean enable) {
        if (buttonPiP != null) {
            Utils.setButtonEnabled(this, buttonPiP, enable);
        }
        Utils.setButtonEnabled(this, buttonAspectRatio, enable);
        if (isTvBox) {
            Utils.setButtonEnabled(this, exoSettings, true);
        } else {
            Utils.setButtonEnabled(this, exoSettings, enable);
        }
        // Always keep subtitle button enabled for accessing addon subtitles
        forceSubtitleButtonEnabled();
    }
    
    /**
     * Force the subtitle button to always be enabled/clickable.
     * ExoPlayer's PlayerControlView normally disables it when no text tracks exist,
     * but we want users to always be able to access addon subtitles.
     */
    private void forceSubtitleButtonEnabled() {
        if (exoSubtitleButton != null) {
            exoSubtitleButton.setEnabled(true);
            exoSubtitleButton.setAlpha(1.0f);
        }
    }

    private void scaleStart() {
        isScaling = true;
        if (playerView.getResizeMode() != AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        }
        scaleFactor = playerView.getVideoSurfaceView().getScaleX();
        playerView.removeCallbacks(playerView.textClearRunnable);
        playerView.clearIcon();
        playerView.setCustomErrorMessage((int)(scaleFactor * 100) + "%");
        playerView.hideController();
        isScaleStarting = true;
    }

    private void scale(boolean up) {
        if (up) {
            scaleFactor += 0.01;
        } else {
            scaleFactor -= 0.01;
        }
        scaleFactor = Utils.normalizeScaleFactor(scaleFactor, playerView.getScaleFit());
        playerView.setScale(scaleFactor);
        playerView.setCustomErrorMessage((int)(scaleFactor * 100) + "%");
    }

    private void scaleEnd() {
        isScaling = false;
        playerView.postDelayed(playerView.textClearRunnable, 200);
        if (player != null && !player.isPlaying()) {
            if (!"netflix".equals(mPrefs.skipButtonStyle)) {
                playerView.showController();
            } else if (modernController != null) {
                modernController.show();
            }
        }
        if (Math.abs(playerView.getScaleFit() - scaleFactor) < 0.01 / 2) {
            playerView.setScale(1.f);
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        }
        updatebuttonAspectRatioIcon();
    }

    private void updatebuttonAspectRatioIcon() {
        if (playerView.getResizeMode() == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
            buttonAspectRatio.setImageResource(R.drawable.ic_fit_screen_24dp);
        } else {
            buttonAspectRatio.setImageResource(R.drawable.ic_aspect_ratio_24dp);
        }
    }

    private void updateButtonRotation() {
        boolean portrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        boolean auto = false;
        try {
            auto = Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION) == 1;
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }

        if (mPrefs.orientation == Utils.Orientation.VIDEO) {
            if (auto) {
                buttonRotation.setImageResource(R.drawable.ic_screen_lock_rotation_24dp);
            } else if (portrait) {
                buttonRotation.setImageResource(R.drawable.ic_screen_lock_portrait_24dp);
            } else {
                buttonRotation.setImageResource(R.drawable.ic_screen_lock_landscape_24dp);
            }
        } else {
            if (auto) {
                buttonRotation.setImageResource(R.drawable.ic_screen_rotation_24dp);
            } else if (portrait) {
                buttonRotation.setImageResource(R.drawable.ic_screen_portrait_24dp);
            } else {
                buttonRotation.setImageResource(R.drawable.ic_screen_landscape_24dp);
            }
        }
    }

    private void checkUpcomingIntro() {
        if (player == null || currentSkipSegments == null || buttonSkipIntro == null) {
            return;
        }

        double pos = player.getCurrentPosition() / 1000.0;
        Pair<Double, Double> upcomingSegment = skipManager.getUpcomingSegment(pos, currentSkipSegments, 0); // Auto-shift handled by SkipManager
        
        if (upcomingSegment != null && !hasSkippedIntro) {
            buttonSkipIntro.setVisibility(View.VISIBLE);
        } else {
            buttonSkipIntro.setVisibility(View.GONE);
        }
    }

    public void showAudioSelectionDialog() {
        if (player == null) {
            Toast.makeText(this, "Player not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        Tracks tracks = player.getCurrentTracks();
        List<String> audioTrackNames = new ArrayList<>();
        List<TrackGroup> audioTrackGroups = new ArrayList<>();
        List<Integer> audioTrackIndices = new ArrayList<>();

        for (Tracks.Group trackGroup : tracks.getGroups()) {
            if (trackGroup.getType() == C.TRACK_TYPE_AUDIO) {
                for (int i = 0; i < trackGroup.length; i++) {
                    Format format = trackGroup.getTrackFormat(i);
                    String label = format.label;
                    String language = format.language;
                    String name = "";
                    
                    if (label != null && !label.isEmpty()) {
                        name = label;
                    } else if (language != null && !language.isEmpty()) {
                        Locale locale = new Locale(language);
                        name = locale.getDisplayLanguage();
                    } else {
                        name = "Audio Track " + (audioTrackNames.size() + 1);
                    }
                    
                    if (format.channelCount > 0) {
                        name += " (" + format.channelCount + "ch)";
                    }
                    
                    if (trackGroup.isTrackSelected(i)) {
                        name += " ✓";
                    }
                    
                    audioTrackNames.add(name);
                    audioTrackGroups.add(trackGroup.getMediaTrackGroup());
                    audioTrackIndices.add(i);
                }
            }
        }

        if (audioTrackNames.isEmpty()) {
            Toast.makeText(this, "No audio tracks available", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] items = audioTrackNames.toArray(new String[0]);
        
        new AlertDialog.Builder(this)
                .setTitle("Select Audio Track")
                .setItems(items, (dialog, which) -> {
                    TrackGroup selectedGroup = audioTrackGroups.get(which);
                    int selectedIndex = audioTrackIndices.get(which);
                    
                    TrackSelectionParameters params = player.getTrackSelectionParameters()
                            .buildUpon()
                            .setOverrideForType(
                                    new TrackSelectionOverride(selectedGroup, Collections.singletonList(selectedIndex))
                            )
                            .build();
                    
                    player.setTrackSelectionParameters(params);
                    Utils.showToast(this, "Selected: " + audioTrackNames.get(which).replace(" ✓", ""));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public void showSubtitleSelectionDialog() {
        // REVERTED: Using ExoPlayer's default subtitle track selection
        // (SubtitleHub and online subtitle addons disabled)
        
        if (player == null) {
            Toast.makeText(this, "Player not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        Tracks tracks = player.getCurrentTracks();
        List<String> subtitleTrackNames = new ArrayList<>();
        List<TrackGroup> subtitleTrackGroups = new ArrayList<>();
        List<Integer> subtitleTrackIndices = new ArrayList<>();

        // Add "Off" option first
        subtitleTrackNames.add("🚫 OFF - No Subtitles");
        subtitleTrackGroups.add(null);
        subtitleTrackIndices.add(-1);

        for (Tracks.Group trackGroup : tracks.getGroups()) {
            if (trackGroup.getType() == C.TRACK_TYPE_TEXT) {
                for (int i = 0; i < trackGroup.length; i++) {
                    Format format = trackGroup.getTrackFormat(i);
                    String label = format.label;
                    String language = format.language;
                    String name = "";
                    
                    // Build display name
                    if (language != null && !language.isEmpty()) {
                        Locale locale = new Locale(language);
                        name = locale.getDisplayLanguage();
                    } else if (label != null && !label.isEmpty()) {
                        name = label;
                    } else {
                        name = "Subtitle Track " + (subtitleTrackNames.size());
                    }
                    
                    // Add format info
                    if (label != null && !label.isEmpty() && !name.equals(label)) {
                        name += " [" + label + "]";
                    }
                    
                    // Mark if selected
                    if (trackGroup.isTrackSelected(i)) {
                        name += " ✓";
                    }
                    
                    subtitleTrackNames.add(name);
                    subtitleTrackGroups.add(trackGroup.getMediaTrackGroup());
                    subtitleTrackIndices.add(i);
                }
            }
        }

        if (subtitleTrackNames.size() <= 1) {
            Toast.makeText(this, "No subtitle tracks available", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] items = subtitleTrackNames.toArray(new String[0]);
        
        new AlertDialog.Builder(this)
                .setTitle("Select Subtitle Track")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        // "Off" selected - disable subtitles
                        TrackSelectionParameters params = player.getTrackSelectionParameters()
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                .build();
                        player.setTrackSelectionParameters(params);
                        Utils.showToast(this, "Subtitles Disabled");
                    } else {
                        // Select specific track
                        TrackGroup selectedGroup = subtitleTrackGroups.get(which);
                        int selectedIndex = subtitleTrackIndices.get(which);
                        
                        TrackSelectionParameters params = player.getTrackSelectionParameters()
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(
                                        new TrackSelectionOverride(selectedGroup, Collections.singletonList(selectedIndex))
                                )
                                .build();
                        
                        player.setTrackSelectionParameters(params);
                        Utils.showToast(this, "Selected: " + subtitleTrackNames.get(which).replace(" ✓", ""));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    /**
     * Get embedded subtitle tracks from current media
     */
    private List<SubtitleTrack> getEmbeddedSubtitleTracks() {
        List<SubtitleTrack> tracks = new ArrayList<>();
        if (player == null) return tracks;
        
        Tracks currentTracks = player.getCurrentTracks();
        int index = 0;
        
        for (Tracks.Group trackGroup : currentTracks.getGroups()) {
            if (trackGroup.getType() == C.TRACK_TYPE_TEXT) {
                for (int i = 0; i < trackGroup.length; i++) {
                    Format format = trackGroup.getTrackFormat(i);
                    
                    String language = "Unknown";
                    String languageCode = "";
                    if (format.language != null && !format.language.isEmpty()) {
                        languageCode = format.language;
                        Locale locale = new Locale(format.language);
                        language = locale.getDisplayLanguage();
                    }
                    
                    String label = format.label;
                    boolean isSelected = trackGroup.isTrackSelected(i);
                    boolean isForced = (format.selectionFlags & C.SELECTION_FLAG_FORCED) != 0;
                    boolean isSDH = (format.roleFlags & C.ROLE_FLAG_DESCRIBES_VIDEO) != 0 ||
                                   (format.roleFlags & C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0 ||
                                   (label != null && label.toLowerCase().contains("sdh"));
                    
                    String trackId = "embedded_" + index;
                    
                    SubtitleTrack track = SubtitleTrack.createEmbedded(
                        trackId,
                        language,
                        languageCode,
                        label,
                        format.sampleMimeType,
                        index,
                        isSDH,
                        isForced
                    );
                    
                    tracks.add(track);
                    index++;
                }
            }
        }
        
        return tracks;
    }
    
    /**
     * Get current subtitle track ID
     */
    private String getCurrentSubtitleTrackId() {
        if (player == null) return null;
        
        Tracks currentTracks = player.getCurrentTracks();
        int index = 0;
        
        for (Tracks.Group trackGroup : currentTracks.getGroups()) {
            if (trackGroup.getType() == C.TRACK_TYPE_TEXT) {
                for (int i = 0; i < trackGroup.length; i++) {
                    if (trackGroup.isTrackSelected(i)) {
                        return "embedded_" + index;
                    }
                    index++;
                }
            }
        }
        
        return null; // No subtitle selected
    }
    
    /**
     * Apply a remote subtitle URL to the player
     */
    private void applyRemoteSubtitle(SubtitleTrack track) {
        if (player == null || track.getUrl() == null) return;
        
        // Build subtitle configuration
        String mimeType = track.getMimeType();
        if (mimeType == null || mimeType.isEmpty()) {
            // Guess from URL
            String url = track.getUrl().toLowerCase();
            if (url.endsWith(".srt")) {
                mimeType = MimeTypes.APPLICATION_SUBRIP;
            } else if (url.endsWith(".vtt")) {
                mimeType = MimeTypes.TEXT_VTT;
            } else if (url.endsWith(".ass") || url.endsWith(".ssa")) {
                mimeType = MimeTypes.TEXT_SSA;
            } else {
                mimeType = MimeTypes.APPLICATION_SUBRIP; // Default
            }
        }
        
        MediaItem.SubtitleConfiguration subConfig = new MediaItem.SubtitleConfiguration.Builder(
                Uri.parse(track.getUrl()))
                .setMimeType(mimeType)
                .setLanguage(track.getLanguageCode())
                .setLabel(track.getLanguage() + (track.getAddonName() != null ? " (" + track.getAddonName() + ")" : ""))
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build();
        
        // Get current media item and add subtitle
        MediaItem currentItem = player.getCurrentMediaItem();
        if (currentItem != null && currentItem.localConfiguration != null) {
            List<MediaItem.SubtitleConfiguration> subs = new ArrayList<>();
            subs.add(subConfig);
            
            MediaItem newItem = currentItem.buildUpon()
                    .setSubtitleConfigurations(subs)
                    .build();
            
            long position = player.getCurrentPosition();
            boolean wasPlaying = player.isPlaying();
            
            player.setMediaItem(newItem, position);
            player.prepare();
            if (wasPlaying) player.play();
            
            // Enable subtitle track
            TrackSelectionParameters params = player.getTrackSelectionParameters()
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .build();
            player.setTrackSelectionParameters(params);
            
            Utils.showToast(this, "Loaded: " + track.getLanguage());
        }
    }
    
    /**
     * Select an embedded subtitle track
     */
    private void selectEmbeddedSubtitle(SubtitleTrack track) {
        if (player == null) return;
        
        Tracks currentTracks = player.getCurrentTracks();
        Integer embeddedIdx = track.getEmbeddedTrackIndex();
        int targetIndex = (embeddedIdx != null) ? embeddedIdx : 0;
        int index = 0;
        
        for (Tracks.Group trackGroup : currentTracks.getGroups()) {
            if (trackGroup.getType() == C.TRACK_TYPE_TEXT) {
                for (int i = 0; i < trackGroup.length; i++) {
                    if (index == targetIndex) {
                        TrackGroup mediaTrackGroup = trackGroup.getMediaTrackGroup();
                        
                        TrackSelectionParameters params = player.getTrackSelectionParameters()
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(
                                        new TrackSelectionOverride(mediaTrackGroup, Collections.singletonList(i))
                                )
                                .build();
                        player.setTrackSelectionParameters(params);
                        Utils.showToast(this, "Selected: " + track.getLanguage());
                        return;
                    }
                    index++;
                }
            }
        }
    }

    /**
     * Initialize Jump Scare Manager independently from intro skip
     * This ensures jumpscare skip works even when intro skip is disabled
     */
    private void initJumpScareManager() {
        // Jump Scare feature is DISABLED/HIDDEN
        return;
        /*

        // Get media title from various sources
        String title = null;
        if (apiTitle != null && !apiTitle.isEmpty()) {
            title = apiTitle;
        } else if (mPrefs.mediaUri != null) {
            title = mPrefs.mediaUri.getLastPathSegment();
        }

        if (title == null || title.isEmpty()) {
            DebugLogger.INSTANCE.log("JumpScareInit", "ERROR: No title available - cannot initialize jumpscare");
            return;
        }

        final String mediaTitle = title;
        DebugLogger.INSTANCE.log("JumpScareInit", "Media title: '" + mediaTitle + "'");

        bgExecutor.execute(() -> {
            try {
                DebugLogger.INSTANCE.log("JumpScareInit", "Background thread started - parsing title...");
                
                // Use Ultimate NameCleaner to parse and sanitize filename
                com.brouken.player.utils.NameCleaner.CleanResult cleanResult = 
                    com.brouken.player.utils.NameCleaner.INSTANCE.clean(mediaTitle);
                
                String showName = cleanResult.getShowName();
                Integer year = cleanResult.getYear();
                
                DebugLogger.INSTANCE.log("JumpScareInit", "Cleaned show name: '" + showName + "'");
                DebugLogger.INSTANCE.log("JumpScareInit", "Year: " + year);
                DebugLogger.INSTANCE.log("JumpScareInit", "IMDB ID: " + currentImdbId);
                
                // Use currentImdbId if already resolved by fetchSkipData
                String imdbIdForJump = currentImdbId;
                
                DebugLogger.INSTANCE.log("JumpScareInit", "╔════════════════════════════════════════════════════════════╗");
                DebugLogger.INSTANCE.log("JumpScareInit", "║  JUMP SCARE MANAGER INITIALIZATION                            ║");
                DebugLogger.INSTANCE.log("JumpScareInit", "╚════════════════════════════════════════════════════════════╝");
                DebugLogger.INSTANCE.log("JumpScareInit", "  Show Name: '" + showName + "'");
                DebugLogger.INSTANCE.log("JumpScareInit", "  Year: " + year);
                DebugLogger.INSTANCE.log("JumpScareInit", "  IMDB ID: " + imdbIdForJump);
                DebugLogger.INSTANCE.log("JumpScareInit", "  Preference Enabled: " + mPrefs.jumpScareSkipEnabled);
                DebugLogger.INSTANCE.log("JumpScareInit", "  Player Ready: " + (player != null));
                DebugLogger.INSTANCE.log("JumpScareInit", "");
                
                runOnUiThread(() -> {
                    DebugLogger.INSTANCE.log("JumpScareInit", "Calling jumpScareManager.initialize() on UI thread...");
                    jumpScareManager.initialize(showName, year, imdbIdForJump, mPrefs.jumpScareSkipEnabled);
                    
                    if (player != null) {
                        DebugLogger.INSTANCE.log("JumpScareInit", "Player is available, starting jumpscare monitoring NOW");
                        jumpScareManager.startMonitoring(player);
                    } else {
                        DebugLogger.INSTANCE.log("JumpScareInit", "!!! WARNING: Player is NULL, cannot start monitoring yet !!!");
                        DebugLogger.INSTANCE.log("JumpScareInit", "Monitoring will start when player is ready (in onIsPlayingChanged)");
                    }
                });
            } catch (Exception e) {
                // DebugLogger.INSTANCE.log("JumpScareInit", "EXCEPTION: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                e.printStackTrace();
            }
        });
        */
    }

    private void fetchSkipData() {
        // Reset skip state for new media
        hasSkippedIntro = false;
        hasChapterSkip = false;
        currentSkipSegments = null;
        if (buttonSkipIntro != null) {
            buttonSkipIntro.setVisibility(View.GONE);
        }

        // Get media title from various sources
        String title = null;
        if (apiTitle != null && !apiTitle.isEmpty()) {
            title = apiTitle;
        } else if (mPrefs.mediaUri != null) {
            title = mPrefs.mediaUri.getLastPathSegment();
        }

        if (title == null || title.isEmpty()) {
            DebugLogger.INSTANCE.log("SkipData", "No title available for skip data lookup");
            return;
        }

        final String mediaTitle = title;
        DebugLogger.INSTANCE.log("SkipData", "Fetching skip data for: " + mediaTitle);

        bgExecutor.execute(() -> {
            try {
                // Use Ultimate NameCleaner to parse and sanitize the filename
                com.brouken.player.utils.NameCleaner.CleanResult cleanResult = 
                    com.brouken.player.utils.NameCleaner.INSTANCE.clean(mediaTitle);
                
                int season = cleanResult.getSeason();
                int episode = cleanResult.getEpisode();
                String showName = cleanResult.getShowName();
                boolean parsed = true; // NameCleaner always returns defaults if not found

                DebugLogger.INSTANCE.log("SkipData", "Cleaned show name: '" + showName + "' S" + season + "E" + episode);

                // ID Resolution: Use Cinemeta (Stremio ID provider) for IMDB IDs
                String resolvedImdbId = currentImdbId;
                Integer resolvedMalId = currentMalId;
                
                // Priority 1: Cinemeta (Unified Stremio Metadata)
                if (resolvedImdbId == null && mPrefs.cinemataUrl != null && !mPrefs.cinemataUrl.isEmpty()) {
                    DebugLogger.INSTANCE.log("SkipData", "Using Cinemeta to resolve IMDB ID for: " + showName + " (Year: " + cleanResult.getYear() + ")");
                    CinemetaClient cinemataClient = new CinemetaClient();
                    // Pass verified year for accurate scoring matching
                    String cinemataImdb = cinemataClient.searchImdbId(mPrefs.cinemataUrl, "series", showName, cleanResult.getYear());
                    
                    if (cinemataImdb != null && !cinemataImdb.isEmpty()) {
                        resolvedImdbId = cinemataImdb;
                        DebugLogger.INSTANCE.log("SkipData", "Cinemeta resolved IMDB: " + resolvedImdbId);
                        
                        // Update Trakt scrobbler with resolved IMDB ID
                        final String finalImdbId = resolvedImdbId;
                        final String finalTitle = showName;
                        runOnUiThread(() -> updateTraktImdbId(finalImdbId, finalTitle));
                    } else {
                        DebugLogger.INSTANCE.log("SkipData", "Cinemeta returned no results for: " + showName);
                    }
                } else if (resolvedImdbId == null) {
                    DebugLogger.INSTANCE.log("SkipData", "No Cinemeta URL configured");
                }
                
                // Priority 3: Jikan API for MAL ID (for AniSkip - anime only, no API key needed)
                if (resolvedMalId == null) {
                    DebugLogger.INSTANCE.log("SkipData", "Using Jikan API to resolve MAL ID for: " + showName);
                    JikanClient jikanClient = new JikanClient();
                    Integer malId = jikanClient.searchMalId(showName, cleanResult.getYear());
                    if (malId != null) {
                        resolvedMalId = malId;
                        DebugLogger.INSTANCE.log("SkipData", "Jikan resolved MAL ID: " + resolvedMalId);
                    } else {
                        DebugLogger.INSTANCE.log("SkipData", "Jikan found no anime for: " + showName);
                    }
                }

                DebugLogger.INSTANCE.log("SkipData", "Final IDs - MAL: " + resolvedMalId + ", IMDB: " + resolvedImdbId);
                
                // DEBUG: Show IMDB ID status to user
                final String debugImdb = resolvedImdbId;
                final String debugShow = showName;
                runOnUiThread(() -> {
                    if (debugImdb != null && !debugImdb.isEmpty()) {
                        Toast.makeText(PlayerActivity.this, 
                            "🔍 IMDB: " + debugImdb + " (S" + season + "E" + episode + ")", 
                            Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(PlayerActivity.this, 
                            "⚠️ No IMDB ID found for: " + debugShow, 
                            Toast.LENGTH_LONG).show();
                    }
                });
                
                // Store resolved IDs for submission use
                currentImdbId = resolvedImdbId;
                currentMalId = resolvedMalId;
                currentSeason = season;
                currentEpisode = episode;
                
                // Jump Scare Manager - DISABLED/HIDDEN (removed all init code and logs)
                

                // Try to get skip times
                final int finalSeason = season;
                final int finalEpisode = episode;
                final String finalImdbId = resolvedImdbId;
                final String finalShowName = showName;
                final String episodeName = "Episode " + episode; // Simple episode name for AnimeSkip
                
                // Callback for when data is auto-submitted to IntroDB
                Runnable onAutoSubmit = () -> {
                    runOnUiThread(() -> {
                        Toast.makeText(PlayerActivity.this, 
                            "Skip data → IntroDB submitted", 
                            Toast.LENGTH_SHORT).show();
                    });
                };
                
                // 5-tier fallback: AnimeSkip → SkipDB → IntroHater → AniSkip → IntroDB
                List<Pair<Double, Double>> segments = skipManager.getSkipTimes(
                    resolvedMalId, 
                    resolvedImdbId, 
                    season, 
                    episode,
                    finalShowName,           // for AnimeSkip
                    episodeName,             // for AnimeSkip
                    mPrefs.introHaterApiKey, // for IntroHater
                    mPrefs.introDbApiKey,    // for IntroDB submit
                    onAutoSubmit
                );

                if (segments != null && !segments.isEmpty()) {
                    if (hasChapterSkip) {
                        DebugLogger.INSTANCE.log("SkipData", "Ignoring API results because Chapter data was found (High Priority)");
                        return;
                    }
                    currentSkipSegments = segments;
                    final int segCount = segments.size();
                    runOnUiThread(() -> {
                        Toast.makeText(PlayerActivity.this, 
                            "Intro data found (" + segCount + " segment" + (segCount > 1 ? "s" : "") + ")", 
                            Toast.LENGTH_SHORT).show();
                    });
                    DebugLogger.INSTANCE.log("SkipData", "Found " + segCount + " skip segments: " + segments);
                } else {
                    DebugLogger.INSTANCE.log("SkipData", "No skip data from API for S" + finalSeason + "E" + finalEpisode);
                }

            } catch (Exception e) {
                e.printStackTrace();
                DebugLogger.INSTANCE.log("SkipData", "Error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
        });
    }

    private void showDebugLogs() {
        bgExecutor.execute(() -> {
            try {
                java.io.File logDir = getExternalFilesDir(null);
                java.io.File logFile = new java.io.File(logDir, "debug_log.txt");
                if (logFile.exists()) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(logFile));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    java.util.List<String> lines = new ArrayList<>();
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                    reader.close();
                    
                    // Show last 30 lines
                    int start = Math.max(0, lines.size() - 30);
                    for (int i = start; i < lines.size(); i++) {
                        sb.append(lines.get(i)).append("\n");
                    }
                    
                    final String logs = sb.toString();
                    runOnUiThread(() -> {
                        new AlertDialog.Builder(PlayerActivity.this)
                            .setTitle("Debug Logs (Last 30 lines)")
                            .setMessage(logs.isEmpty() ? "No logs yet" : logs)
                            .setPositiveButton("OK", null)
                            .setNeutralButton("Clear", (d, w) -> {
                                DebugLogger.INSTANCE.clear();
                                Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show();
                            })
                            .show();
                    });
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "No log file found", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error reading logs: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * Setup Netflix-style player controls and gestures
     */
    private void setupNetflixControls() {
        // Back button
        View btnBack = findViewById(R.id.netflix_btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Rewind button - uses configured seek duration
        View btnRewind = findViewById(R.id.netflix_btn_rewind);
        if (btnRewind != null) {
            btnRewind.setOnClickListener(v -> {
                if (player != null) {
                    long seekMs = mPrefs.seekDuration;
                    player.seekTo(Math.max(0, player.getCurrentPosition() - seekMs));
                }
            });
        }

        // Forward button - uses configured seek duration
        View btnForward = findViewById(R.id.netflix_btn_forward);
        if (btnForward != null) {
            btnForward.setOnClickListener(v -> {
                if (player != null) {
                    long seekMs = mPrefs.seekDuration;
                    player.seekTo(Math.min(player.getDuration(), player.getCurrentPosition() + seekMs));
                }
            });
        }

        // Fullscreen button
        View btnFullscreen = findViewById(R.id.netflix_btn_fullscreen);
        if (btnFullscreen != null) {
            btnFullscreen.setOnClickListener(v -> {
                Utils.setOrientation(this, Utils.Orientation.VIDEO);
            });
        }

        // Speed button
        View btnSpeed = findViewById(R.id.netflix_btn_speed);
        if (btnSpeed != null) {
            btnSpeed.setOnClickListener(v -> showSpeedDialog());
        }

        // Set title
        TextView tvTitle = findViewById(R.id.netflix_title);
        if (tvTitle != null && mPrefs.mediaUri != null) {
            String title = Utils.getFileName(this, mPrefs.mediaUri);
            if (title != null) {
                tvTitle.setText(title);
            }
        }
    }

    public void showSpeedDialog() {
        String[] speeds = {"0.5x", "0.75x", "1.0x (Normal)", "1.25x", "1.5x", "2.0x"};
        float[] speedValues = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        
        new AlertDialog.Builder(this)
            .setTitle("Playback Speed")
            .setItems(speeds, (dialog, which) -> {
                if (player != null) {
                    player.setPlaybackSpeed(speedValues[which]);
                    mPrefs.speed = speedValues[which];
                    Toast.makeText(this, "Speed: " + speeds[which], Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }
    public long getSeekDuration() {
        return mPrefs.seekDuration;
    }

    public void skipIntro() {
        if (currentSkipSegments != null && player != null) {
            double pos = player.getCurrentPosition() / 1000.0;
            for (Pair<Double, Double> seg : currentSkipSegments) {
                if (pos >= seg.getFirst() && pos < seg.getSecond()) {
                     player.seekTo((long) (seg.getSecond() * 1000));
                     Toast.makeText(this, "Skipped Intro", Toast.LENGTH_SHORT).show();
                     break;
                }
            }
        }
    }
    
    /**
     * Setup IntroDB submission buttons (Mark Start, Mark End, Submit)
     * Added to the Control Bar (Left of File Button)
     */
    private void setupSubmissionButtons(ViewGroup container) {
        // Larger buttons for TV (approx 40dp)
        int btnSize = Utils.dpToPx(40); 
        float textSize = 14;
        
        // Layout Params for circular buttons
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(btnSize, btnSize);
        btnParams.gravity = android.view.Gravity.CENTER_VERTICAL;
        btnParams.setMargins(0, 0, Utils.dpToPx(4), 0); // 4dp spacing
        
        // Mark Start button (S)
        btnMarkStart = new Button(this);
        btnMarkStart.setText("S");
        btnMarkStart.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        btnMarkStart.setBackgroundResource(R.drawable.bg_skip_button_selector);
        btnMarkStart.setTextColor(Color.WHITE);
        btnMarkStart.setPadding(0, 0, 0, 0);
        btnMarkStart.setFocusable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btnMarkStart.setStateListAnimator(null);
        }
        btnMarkStart.setLayoutParams(btnParams);
        
        // Mark End button (E)
        btnMarkEnd = new Button(this);
        btnMarkEnd.setText("E");
        btnMarkEnd.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        btnMarkEnd.setBackgroundResource(R.drawable.bg_skip_button_selector);
        btnMarkEnd.setTextColor(Color.WHITE);
        btnMarkEnd.setPadding(0, 0, 0, 0);
        btnMarkEnd.setFocusable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btnMarkEnd.setStateListAnimator(null);
        }
        btnMarkEnd.setLayoutParams(btnParams);
        
        // Submit button (checkmark)
        btnSubmitIntro = new Button(this);
        btnSubmitIntro.setText("✓");
        btnSubmitIntro.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        btnSubmitIntro.setBackgroundResource(R.drawable.bg_skip_button_selector);
        btnSubmitIntro.setTextColor(Color.WHITE);
        btnSubmitIntro.setPadding(0, 0, 0, 0);
        btnSubmitIntro.setFocusable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btnSubmitIntro.setStateListAnimator(null);
        }
        btnSubmitIntro.setEnabled(false);
        btnSubmitIntro.setAlpha(0.5f);
        btnSubmitIntro.setLayoutParams(btnParams);
        
        // Add buttons to container (leftmost position)
        container.addView(btnMarkStart);
        container.addView(btnMarkEnd);
        container.addView(btnSubmitIntro);
        
        // Click handlers
        btnMarkStart.setOnClickListener(v -> {
            if (player != null) {
                markedStartMs = player.getCurrentPosition();
                Toast.makeText(this, "Start: " + (markedStartMs/1000.0) + "s", Toast.LENGTH_SHORT).show();
                updateSubmitButtonVisibility();
            }
        });
        
        btnMarkEnd.setOnClickListener(v -> {
            if (player != null) {
                markedEndMs = player.getCurrentPosition();
                Toast.makeText(this, "End: " + (markedEndMs/1000.0) + "s", Toast.LENGTH_SHORT).show();
                updateSubmitButtonVisibility();
            }
        });
        
        btnSubmitIntro.setOnClickListener(v -> {
            submitToIntroDB();
        });
    }
    
    private void updateSubmitButtonVisibility() {
        if (btnSubmitIntro != null) {
            boolean canSubmit = markedStartMs >= 0 && markedEndMs > markedStartMs;
            btnSubmitIntro.setEnabled(canSubmit);
            btnSubmitIntro.setAlpha(canSubmit ? 1.0f : 0.5f);
        }
    }
    
    private void submitToIntroDB() {
        if (currentImdbId == null || currentImdbId.isEmpty()) {
            Toast.makeText(this, "No IMDB ID available", Toast.LENGTH_SHORT).show();
            return;
        }
        if (markedStartMs < 0 || markedEndMs <= markedStartMs) {
            Toast.makeText(this, "Invalid start/end points", Toast.LENGTH_SHORT).show();
            return;
        }
        
        double startSec = markedStartMs / 1000.0;
        double endSec = markedEndMs / 1000.0;
        
        Toast.makeText(this, "Submitting...", Toast.LENGTH_SHORT).show();
        
        bgExecutor.execute(() -> {
            IntroDBClient client = new IntroDBClient();
            IntroDBClient.SubmissionResult result = client.submit(
                mPrefs.introDbApiKey,
                currentImdbId,
                currentSeason,
                currentEpisode,
                startSec,
                endSec
            );
            
            runOnUiThread(() -> {
                Toast.makeText(this, result.getMessage(), Toast.LENGTH_LONG).show();
                if (result.getSuccess()) {
                    // Reset markers
                    markedStartMs = -1;
                    markedEndMs = -1;
                    updateSubmitButtonVisibility();
                }
            });
        });
    }
    
    // ==================== TRAKT SCROBBLING ====================
    
    /**
     * Initialize Trakt scrobbling if enabled
     * Call this after media metadata is resolved (e.g., after IMDB ID is known)
     */
    private void initTraktScrobbler() {
        DebugLogger.INSTANCE.log("Trakt", "initTraktScrobbler called");
        
        if (mPrefs == null) {
            DebugLogger.INSTANCE.log("Trakt", "mPrefs is null - skipping");
            return;
        }
        
        if (!mPrefs.isTraktConnected()) {
            DebugLogger.INSTANCE.log("Trakt", "Trakt not connected - skipping");
            return;
        }
        
        DebugLogger.INSTANCE.log("Trakt", "Trakt IS connected, initializing scrobbler");
        DebugLogger.INSTANCE.log("Trakt", "  Access Token: " + (mPrefs.traktAccessToken != null && !mPrefs.traktAccessToken.isEmpty() ? "SET" : "EMPTY"));
        DebugLogger.INSTANCE.log("Trakt", "  Client ID: " + (mPrefs.traktClientId != null && !mPrefs.traktClientId.isEmpty() ? "SET" : "EMPTY"));
        DebugLogger.INSTANCE.log("Trakt", "  currentImdbId: " + currentImdbId);
        DebugLogger.INSTANCE.log("Trakt", "  currentSeason: " + currentSeason);
        DebugLogger.INSTANCE.log("Trakt", "  currentEpisode: " + currentEpisode);
        
        // Get current media info - try multiple sources for title
        String rawTitle = "Unknown";
        if (apiTitle != null && !apiTitle.isEmpty()) {
            rawTitle = apiTitle;
        } else if (player != null && player.getCurrentMediaItem() != null) {
            if (player.getMediaMetadata() != null && player.getMediaMetadata().title != null) {
                rawTitle = player.getMediaMetadata().title.toString();
            } else if (player.getCurrentMediaItem().mediaMetadata.title != null) {
                rawTitle = player.getCurrentMediaItem().mediaMetadata.title.toString();
            }
        }
        
        // Clean the title using NameCleaner to get proper show name
        com.brouken.player.utils.NameCleaner.CleanResult cleanResult = 
            com.brouken.player.utils.NameCleaner.INSTANCE.clean(rawTitle);
        String title = cleanResult.getShowName();
        int season = currentSeason > 0 ? currentSeason : cleanResult.getSeason();
        int episode = currentEpisode > 0 ? currentEpisode : cleanResult.getEpisode();
        
        DebugLogger.INSTANCE.log("Trakt", "  Raw title: " + rawTitle);
        DebugLogger.INSTANCE.log("Trakt", "  Clean title: " + title);
        DebugLogger.INSTANCE.log("Trakt", "  Season: " + season + ", Episode: " + episode);
        
        // Create scrobbler
        traktScrobbler = new TraktScrobbleManager(
            this,
            mPrefs.traktAccessToken,
            mPrefs.traktClientId,
            mPrefs.traktToastsEnabled  // Pass toast preference
        );
        
        // Set media info with CLEAN title
        traktScrobbler.setMedia(
            currentImdbId,
            null, // TMDB ID not always available
            title,  // Use cleaned title, not raw filename
            season,
            episode
        );
        
        DebugLogger.INSTANCE.log("Trakt", "Scrobbler initialized for: " + title + " S" + season + "E" + episode);
        Toast.makeText(this, "Trakt: " + title, Toast.LENGTH_SHORT).show();
        
        // If IMDB ID is null, fetch it asynchronously via Cinemeta
        // This is needed when intro skip is disabled
        if (currentImdbId == null && traktScrobbler != null) {
            DebugLogger.INSTANCE.log("Trakt", "IMDB ID is null - fetching via Cinemeta for Trakt...");
            fetchTraktImdbIdAsync(title, season, episode);
        }
    }
    
    /**
     * Fetch IMDB ID for Trakt asynchronously via Cinemeta
     * This runs independently of intro skip to ensure Trakt always works
     */
    private void fetchTraktImdbIdAsync(final String showName, final int season, final int episode) {
        if (mPrefs.cinemataUrl == null || mPrefs.cinemataUrl.isEmpty()) {
            DebugLogger.INSTANCE.log("Trakt", "No Cinemeta URL configured - cannot resolve IMDB ID");
            return;
        }
        
        bgExecutor.execute(() -> {
            try {
                DebugLogger.INSTANCE.log("Trakt", "╔══════════════════════════════════════════════════════════════════╗");
                DebugLogger.INSTANCE.log("Trakt", "║  FETCHING IMDB ID FOR TRAKT (via Cinemeta)                       ║");
                DebugLogger.INSTANCE.log("Trakt", "╚══════════════════════════════════════════════════════════════════╝");
                DebugLogger.INSTANCE.log("Trakt", "  Show: " + showName);
                
                CinemetaClient cinemataClient = new CinemetaClient();
                String imdbId = cinemataClient.searchImdbId(mPrefs.cinemataUrl, "series", showName, null);
                
                if (imdbId != null && !imdbId.isEmpty()) {
                    DebugLogger.INSTANCE.log("Trakt", "  ✓ Cinemeta resolved IMDB: " + imdbId);
                    
                    // Update on UI thread
                    runOnUiThread(() -> {
                        updateTraktImdbId(imdbId, showName);
                    });
                } else {
                    DebugLogger.INSTANCE.log("Trakt", "  ✗ Cinemeta returned no results for: " + showName);
                }
            } catch (Exception e) {
                DebugLogger.INSTANCE.log("Trakt", "  ✗ Error fetching IMDB: " + e.getMessage());
            }
        });
    }
    
    /**
     * Start Trakt scrobbling (call when playback starts or resumes)
     */
    private void traktScrobbleStart() {
        DebugLogger.INSTANCE.log("Trakt", "╔══════════════════════════════════════════════════════════════════╗");
        DebugLogger.INSTANCE.log("Trakt", "║              traktScrobbleStart() CALLED                          ║");
        DebugLogger.INSTANCE.log("Trakt", "╚══════════════════════════════════════════════════════════════════╝");
        
        if (traktScrobbler == null) {
            DebugLogger.INSTANCE.log("Trakt", "  ✗ traktScrobbler is NULL!");
            return;
        }
        if (player == null) {
            DebugLogger.INSTANCE.log("Trakt", "  ✗ player is NULL!");
            return;
        }
        
        float progress = calculateTraktProgress();
        DebugLogger.INSTANCE.log("Trakt", "  Progress: " + progress + "%");
        DebugLogger.INSTANCE.log("Trakt", "  Calling traktScrobbler.onPlaybackStarted()...");
        traktScrobbler.onPlaybackStarted(progress);
    }
    
    /**
     * Pause Trakt scrobbling (call when playback is paused)
     */
    private void traktScrobblePause() {
        DebugLogger.INSTANCE.log("Trakt", "╔══════════════════════════════════════════════════════════════════╗");
        DebugLogger.INSTANCE.log("Trakt", "║              traktScrobblePause() CALLED                          ║");
        DebugLogger.INSTANCE.log("Trakt", "╚══════════════════════════════════════════════════════════════════╝");
        
        if (traktScrobbler == null) {
            DebugLogger.INSTANCE.log("Trakt", "  ✗ traktScrobbler is NULL!");
            return;
        }
        if (player == null) {
            DebugLogger.INSTANCE.log("Trakt", "  ✗ player is NULL!");
            return;
        }
        
        float progress = calculateTraktProgress();
        DebugLogger.INSTANCE.log("Trakt", "  Progress: " + progress + "%");
        DebugLogger.INSTANCE.log("Trakt", "  Calling traktScrobbler.onPlaybackPaused()...");
        traktScrobbler.onPlaybackPaused(progress);
    }
    
    /**
     * Stop Trakt scrobbling (call when playback stops or activity closes)
     */
    private void traktScrobbleStop() {
        DebugLogger.INSTANCE.log("Trakt", "╔══════════════════════════════════════════════════════════════════╗");
        DebugLogger.INSTANCE.log("Trakt", "║              traktScrobbleStop() CALLED                           ║");
        DebugLogger.INSTANCE.log("Trakt", "╚══════════════════════════════════════════════════════════════════╝");
        
        if (traktScrobbler == null) {
            DebugLogger.INSTANCE.log("Trakt", "  traktScrobbler is NULL (may be normal on first exit)");
            return;
        }
        if (player == null) {
            DebugLogger.INSTANCE.log("Trakt", "  player is NULL");
            return;
        }
        
        float progress = calculateTraktProgress();
        DebugLogger.INSTANCE.log("Trakt", "  Progress: " + progress + "%");
        DebugLogger.INSTANCE.log("Trakt", "  Calling traktScrobbler.onPlaybackStopped()...");
        traktScrobbler.onPlaybackStopped(progress);
    }
    
    /**
     * Calculate current playback progress as percentage (0-100)
     * Minimum progress is 0.1% to ensure Trakt accepts the scrobble
     */
    private float calculateTraktProgress() {
        if (player == null) return 0.1f;
        long position = player.getCurrentPosition();
        long duration = player.getDuration();
        if (duration <= 0) return 0.1f;
        float progress = (position * 100f) / duration;
        // Trakt requires minimum progress, so use 0.1% as minimum
        return Math.max(0.1f, progress);
    }
    
    /**
     * Update Trakt scrobbler with resolved IMDB ID
     * Call this after Cinemeta resolves the IMDB ID asynchronously
     */
    public void updateTraktImdbId(String imdbId, String cleanedTitle) {
        DebugLogger.INSTANCE.log("Trakt", "updateTraktImdbId called: " + imdbId + ", title: " + cleanedTitle);
        
        // Also update local field for future scrobbler initializations
        currentImdbId = imdbId;
        
        if (traktScrobbler != null) {
            traktScrobbler.updateImdbId(imdbId, cleanedTitle);
        } else {
            DebugLogger.INSTANCE.log("Trakt", "  traktScrobbler is null, will use IMDB ID on next init");
        }
    }
    
    /**
     * Release Trakt scrobbler resources
     */
    private void releaseTraktScrobbler() {
        if (traktScrobbler != null) {
            traktScrobbler.release();
            traktScrobbler = null;
        }
    }

    private void showUpdateDialog(com.brouken.player.update.UpdateInfo updateInfo) {
        // Launch premium animated update activity
        com.brouken.player.update.UpdateActivity.Companion.launch(this, updateInfo);
    }
}