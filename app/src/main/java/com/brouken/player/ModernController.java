package com.brouken.player;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Util;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.TimeBar;

// Utils is in same package, no import needed

import java.util.Formatter;
import java.util.Locale;

/**
 * ModernController manages the "Modern" (Netflix-style) UI overlay.
 * It is completely independent of the standard ExoPlayer controller logic.
 * This ensures stability and full control over the user experience (Touch & TV).
 */
public class ModernController implements Player.Listener {

    private final Activity activity;
    private final Player player;
    private final PlayerView playerView;
    private View overlayRoot;

    // Controls
    private View topBar;
    private View bottomBar;
    private View centerControls;
    
    private ImageButton btnPlay;
    private ImageButton btnRewind;
    private ImageButton btnForward;
    private ProgressBar loadingIndicator;
    private Button btnSkipIntro;
    private TextView tvPosition;
    private TextView tvDuration;
    private TextView tvTitle;
    private CustomDefaultTimeBar timeBar;
    
    // New Bottom Controls
    private TextView btnSpeed;
    private ImageButton btnLock;
    private ImageButton btnFile;
    private ImageButton btnSubtitle;
    private ImageButton btnAudio;
    private ImageButton btnSettings;
    private ImageButton btnNext;

    private boolean isVisible = true;
    private boolean isLocked = false;
    private final Handler hideHandler = new Handler(Looper.getMainLooper());
    private static final int HIDE_TIMEOUT_MS = 4000;
    
    private final StringBuilder formatBuilder = new StringBuilder();
    private final Formatter formatter = new Formatter(formatBuilder, Locale.getDefault());

    private boolean scrubbing = false;

    public ModernController(Activity activity, Player player, PlayerView playerView) {
        this.activity = activity;
        this.player = player;
        this.playerView = playerView;
    }

    public void attach(ViewGroup parent) {
        // Inflate the overlay
        LayoutInflater inflater = LayoutInflater.from(activity);
        overlayRoot = inflater.inflate(R.layout.layout_modern_interface, parent, false);
        parent.addView(overlayRoot);

        // --- CRITICAL: Hide conflicting legacy/DoubleTap UI elements ---
        // These exist inside DOubleTapPlayerView from exo_player_view.xml
        View youtubeOverlay = playerView.findViewById(R.id.youtube_overlay);
        if (youtubeOverlay != null) {
            youtubeOverlay.setVisibility(View.GONE);
        }
        View exoControllerPlaceholder = playerView.findViewById(R.id.exo_controller_placeholder);
        if (exoControllerPlaceholder != null) {
            exoControllerPlaceholder.setVisibility(View.GONE);
        }
        // Also hide any stock ExoPlayer buffering indicator if present
        View exoBuffering = playerView.findViewById(R.id.exo_buffering);
        if (exoBuffering != null) {
            exoBuffering.setVisibility(View.GONE);
        }
        // --- End Conflicting UI Hiding ---

        initViews();
        setupListeners();
        
        // Listen to player state
        player.addListener(this);
        
        // Listen to taps from CustomPlayerView (to handle toggling while preserving gestures)
        if (playerView instanceof CustomPlayerView) {
            ((CustomPlayerView) playerView).setOnTapListener(this::toggleVisibility);
        }
        
        // Initial state update
        updatePlayPauseButton();
        updateProgress();
        updateSpeedText();
        
        // Start hiding timer
        resetHideTimer();

        // If title is available, set it
        updateTitle();
    }

    private void initViews() {
        topBar = overlayRoot.findViewById(R.id.modern_top_bar);
        bottomBar = overlayRoot.findViewById(R.id.modern_bottom_bar);
        centerControls = overlayRoot.findViewById(R.id.modern_center_controls);
        
        btnPlay = overlayRoot.findViewById(R.id.modern_btn_play);
        btnRewind = overlayRoot.findViewById(R.id.modern_btn_rewind);
        btnForward = overlayRoot.findViewById(R.id.modern_btn_forward);
        loadingIndicator = overlayRoot.findViewById(R.id.modern_loading);
        btnSkipIntro = overlayRoot.findViewById(R.id.modern_btn_skip_intro);
        tvPosition = overlayRoot.findViewById(R.id.modern_position);
        tvDuration = overlayRoot.findViewById(R.id.modern_duration);
        tvTitle = overlayRoot.findViewById(R.id.modern_title);
        timeBar = overlayRoot.findViewById(R.id.modern_time_bar);
        
        btnSpeed = overlayRoot.findViewById(R.id.modern_btn_speed);
        btnLock = overlayRoot.findViewById(R.id.modern_btn_lock);
        btnFile = overlayRoot.findViewById(R.id.modern_btn_file);
        btnSubtitle = overlayRoot.findViewById(R.id.modern_btn_subtitle);
        btnAudio = overlayRoot.findViewById(R.id.modern_btn_audio);
        btnSettings = overlayRoot.findViewById(R.id.modern_btn_settings);
        btnNext = overlayRoot.findViewById(R.id.modern_btn_next);

        // Configure TimeBar
        if (timeBar != null) {
            timeBar.addListener(new TimeBar.OnScrubListener() {
                @Override
                public void onScrubStart(TimeBar timeBar, long position) {
                    scrubbing = true;
                    resetHideTimer();
                }

                @Override
                public void onScrubMove(TimeBar timeBar, long position) {
                    if (tvPosition != null) {
                        tvPosition.setText(Util.getStringForTime(formatBuilder, formatter, position));
                    }
                }

                @Override
                public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
                    scrubbing = false;
                    if (!canceled && player != null) {
                        player.seekTo(position);
                    }
                    resetHideTimer();
                }
            });
        }
        
        // Enforce skip button position programmatically (bottom-right)
        if (btnSkipIntro != null) {
            android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                (int) (48 * activity.getResources().getDisplayMetrics().density)
            );
            params.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
            params.rightMargin = (int) (24 * activity.getResources().getDisplayMetrics().density);
            params.bottomMargin = (int) (100 * activity.getResources().getDisplayMetrics().density);
            btnSkipIntro.setLayoutParams(params);
        }
    }

    private void setupListeners() {
        // Play/Pause
        btnPlay.setOnClickListener(v -> {
            if (isLocked) { showLockedToast(); return; }
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.play();
            }
            resetHideTimer();
        });

        // Rewind
        if (btnRewind != null) {
            btnRewind.setOnClickListener(v -> {
                 if (isLocked) { showLockedToast(); return; }
                 if (player != null) {
                     long seekMs = ((PlayerActivity)activity).getSeekDuration(); 
                     if (seekMs <= 0) seekMs = 10000; // Fallback 10s
                     player.seekTo(Math.max(0, player.getCurrentPosition() - seekMs));
                 }
                 resetHideTimer();
            });
        }

        // Forward
        if (btnForward != null) {
            btnForward.setOnClickListener(v -> {
                 if (isLocked) { showLockedToast(); return; }
                 if (player != null) {
                     long seekMs = ((PlayerActivity)activity).getSeekDuration();
                     if (seekMs <= 0) seekMs = 10000; // Fallback 10s
                     player.seekTo(Math.min(player.getDuration(), player.getCurrentPosition() + seekMs));
                 }
                 resetHideTimer();
            });
        }

        // Back
        overlayRoot.findViewById(R.id.modern_btn_back).setOnClickListener(v -> activity.finish());
        
        // Skip Intro
        btnSkipIntro.setOnClickListener(v -> {
            if (isLocked) { showLockedToast(); return; }
            ((PlayerActivity)activity).skipIntro();
        });

        // Speed
        btnSpeed.setOnClickListener(v -> {
            if (isLocked) { showLockedToast(); return; }
            ((PlayerActivity)activity).showSpeedDialog();
            resetHideTimer();
        });

        // Lock Toggle
        btnLock.setOnClickListener(v -> {
            toggleLock();
            resetHideTimer();
        });
        
        // File Selection (Replaces Episodes)
        btnFile.setOnClickListener(v -> {
            if (isLocked) { showLockedToast(); return; }
            ((PlayerActivity)activity).openFile(null);
            resetHideTimer();
        });
        
        // Subtitles (New)
        if (btnSubtitle != null) {
            btnSubtitle.setOnClickListener(v -> {
                if (isLocked) { showLockedToast(); return; }
                ((PlayerActivity)activity).showSubtitleSelectionDialog();
                resetHideTimer();
            });
        }
        
        // Audio (New)
        btnAudio.setOnClickListener(v -> {
             if (isLocked) { showLockedToast(); return; }
             ((PlayerActivity)activity).showAudioSelectionDialog();
             resetHideTimer();
        });
        
        // Settings (Settings Activity) - Click Only
        btnSettings.setOnClickListener(v -> {
             if (isLocked) { showLockedToast(); return; }
             Intent intent = new Intent(activity, SettingsActivity.class);
             activity.startActivityForResult(intent, PlayerActivity.REQUEST_SETTINGS);
        });
        // Removed LongClick listener for Settings as requested
        btnSettings.setOnLongClickListener(null); // Ensure no legacy long click if any

        // Next Episode
        btnNext.setOnClickListener(v -> {
             if (isLocked) { showLockedToast(); return; }
             ((PlayerActivity)activity).skipToNext();
             resetHideTimer();
        });
        
         // Fullscreen (Rotate) logic is now handled by orientation change usually
         // But we removed the dedicated fullscreen button from bottom bar in new design.
         // If user needs it, we can re-add. But sticking to "Netflix 1-1", usually distinct.
    }

    private void showLockedToast() {
         Utils.showToast(activity, "Screen Locked");
         // Bounce the lock icon?
         btnLock.animate().scaleX(1.2f).scaleY(1.2f).withEndAction(() -> btnLock.animate().scaleX(1f).scaleY(1f).start()).start();
    }
    
    private void updateLockState() {
        btnLock.setImageResource(isLocked ? R.drawable.ic_lock_24dp : R.drawable.ic_lock_open_24dp);
        // Dim other controls handled in fade/show logic
        float alpha = isLocked ? 0.3f : 1.0f;
        
        centerControls.setAlpha(alpha);
        if (btnRewind != null) btnRewind.setEnabled(!isLocked);
        if (btnForward != null) btnForward.setEnabled(!isLocked);
        if (btnPlay != null) btnPlay.setEnabled(true); // Always enabled, but toast if locked? 
        // Logic for play button is checked in onClick
        
        topBar.setAlpha(alpha);
        timeBar.setAlpha(alpha);
        btnSpeed.setAlpha(alpha);
        btnFile.setAlpha(alpha);
        if (btnSubtitle != null) btnSubtitle.setAlpha(alpha);
        btnAudio.setAlpha(alpha);
        btnSettings.setAlpha(alpha);
        btnNext.setAlpha(alpha);
        
        // Disable clickability handled in listeners
    }

    public void updateTitle() {
        if (tvTitle == null || player.getCurrentMediaItem() == null) return;
        
        // Get preferences
        Prefs prefs = new Prefs(activity);
        String displayTitle = null;
        
        if (prefs.preferFileNameTitle) {
            // MODE: Use FilenameResolver to get REAL filename from:
            // 1. Syncler video_list.filename intent extra
            // 2. AIOStreams apiTitle (parse .mkv/.mp4 pattern)
            // 3. Content-Disposition header (async)
            // 4. Raw apiTitle fallback
            android.net.Uri uri = player.getCurrentMediaItem().localConfiguration != null
                ? player.getCurrentMediaItem().localConfiguration.uri
                : null;
            
            // Get apiTitle from MediaItem metadata (passed by Stremio/Syncler)
            String apiTitle = null;
            if (player.getCurrentMediaItem().mediaMetadata.title != null) {
                apiTitle = player.getCurrentMediaItem().mediaMetadata.title.toString();
            }
            
            final String finalApiTitle = apiTitle;
            final android.net.Uri finalUri = uri;
            
            com.brouken.player.utils.FilenameResolver.INSTANCE.resolveFilename(
                activity.getIntent(),
                finalApiTitle,
                finalUri,
                resolvedName -> activity.runOnUiThread(() -> {
                    if (resolvedName != null && !resolvedName.isEmpty()) {
                        tvTitle.setText(resolvedName);
                    } else if (finalApiTitle != null) {
                        tvTitle.setText(finalApiTitle);
                    } else if (finalUri != null) {
                        tvTitle.setText(extractFileName(finalUri));
                    }
                })
            );
            return;
        } else {
            // MODE: Prefer metadata
            // 1. Try embedded file metadata from container (MKV/MP4 tags)
            //    This comes from player.getMediaMetadata() which is populated
            //    from the actual file's metadata during playback
            if (player.getMediaMetadata() != null && player.getMediaMetadata().title != null) {
                displayTitle = player.getMediaMetadata().title.toString();
            }
            
            // 2. Fallback to MediaItem metadata (from calling app like Stremio)
            if ((displayTitle == null || displayTitle.isEmpty()) && 
                player.getCurrentMediaItem().mediaMetadata.title != null) {
                displayTitle = player.getCurrentMediaItem().mediaMetadata.title.toString();
            }
        }
        
        // Final fallback: file name if nothing else available
        if (displayTitle == null || displayTitle.isEmpty()) {
            android.net.Uri uri = player.getCurrentMediaItem().localConfiguration != null
                ? player.getCurrentMediaItem().localConfiguration.uri
                : null;
            if (uri != null) {
                displayTitle = extractFileName(uri);
            }
        }
        
        if (displayTitle != null && !displayTitle.isEmpty()) {
            tvTitle.setText(displayTitle);
        }
    }
    
    private String extractFileName(android.net.Uri uri) {
        if (uri == null) return null;
        
        String path = uri.getLastPathSegment();
        if (path == null) {
            path = uri.toString();
        }
        
        // Remove extension
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex > 0) {
            path = path.substring(0, dotIndex);
        }
        
        // Clean up common naming patterns
        path = path.replace(".", " ")
                   .replace("_", " ")
                   .replace("-", " ");
        
        return path.trim();
    }
    
    public void setSkipButtonVisible(boolean visible) {
        if (btnSkipIntro != null) {
            btnSkipIntro.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public void setLoading(boolean loading) {
        if (loadingIndicator != null) {
            loadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
            btnPlay.setVisibility(loading ? View.GONE : View.VISIBLE);
        }
    }

    private void toggleVisibility() {
        if (isVisible) {
            hide();
        } else {
            show();
        }
    }

    public void show() {
        if (!isVisible) {
            isVisible = true;
            fade(topBar, true);
            fade(bottomBar, true);
            fade(centerControls, true);
            fade(overlayRoot.findViewById(R.id.modern_scrim_top), true);
            fade(overlayRoot.findViewById(R.id.modern_scrim_bottom), true);
            
            updateSpeedText();
            btnPlay.requestFocus();
        }
        resetHideTimer();
    }

    public void hide() {
        if (isVisible) {
            isVisible = false;
            fade(topBar, false);
            fade(bottomBar, false);
            fade(centerControls, false);
            fade(overlayRoot.findViewById(R.id.modern_scrim_top), false);
            fade(overlayRoot.findViewById(R.id.modern_scrim_bottom), false);
        }
        hideHandler.removeCallbacks(this::hide);
    }

    private void resetHideTimer() {
        hideHandler.removeCallbacks(this::hide);
        if (isVisible && player.isPlaying() && !isLocked) {
            hideHandler.postDelayed(this::hide, HIDE_TIMEOUT_MS);
        }
    }

    private void fade(View view, boolean show) {
        if (view == null) return;
        view.animate()
            .alpha(show ? (isLocked && view != btnLock ? 0.3f : 1f) : 0f)
            .setDuration(200)
            .withStartAction(() -> {
                if (show) view.setVisibility(View.VISIBLE);
            })
            .withEndAction(() -> {
                if (!show) {
                    view.setVisibility(View.GONE);
                    // Ensure Lock button stays visible if Locked
                    if (isLocked && btnLock != null) { 
                        btnLock.setVisibility(View.VISIBLE);
                        btnLock.setAlpha(0.2f); // Dimmed when hidden
                    }
                }
            })
            .start();
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        updatePlayPauseButton();
        if (isPlaying) {
            resetHideTimer();
        } else {
            show(); // Show controls when paused
        }
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        if (playbackState == Player.STATE_BUFFERING) {
            setLoading(true);
        } else {
            setLoading(false);
        }
        updateProgress();
    }
    
    @Override
    public void onPlaybackParametersChanged(androidx.media3.common.PlaybackParameters playbackParameters) {
        updateSpeedText();
    }

    @Override
    public void onPositionDiscontinuity(int reason) {
        updateProgress();
    }

    public void updateProgress() {
        if (!isVisible || scrubbing) return;
        
        long duration = player.getDuration();
        long position = player.getCurrentPosition();
        long buffered = player.getBufferedPosition();

        if (timeBar != null) {
            timeBar.setDuration(duration);
            timeBar.setPosition(position);
            timeBar.setBufferedPosition(buffered);
        }

        if (tvPosition != null) {
            tvPosition.setText(Util.getStringForTime(formatBuilder, formatter, position));
        }
        if (tvDuration != null) {
            tvDuration.setText(Util.getStringForTime(formatBuilder, formatter, duration));
        }

        // Schedule next update
        if (player.isPlaying()) {
             hideHandler.postDelayed(this::updateProgress, 1000);
        }
    }
    
    private void updateSpeedText() {
        if (btnSpeed != null) {
            float speed = player.getPlaybackParameters().speed;
            btnSpeed.setText(String.format("Speed (%.1fx)", speed));
        }
    }

    private void updatePlayPauseButton() {
        if (btnPlay != null) {
            boolean playing = player.isPlaying();
            btnPlay.setImageResource(playing ? R.drawable.exo_styled_controls_pause : R.drawable.exo_styled_controls_play); 
        }
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        // Handle D-pad center to show controls
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (!isVisible) {
                 show();
                 return true;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (isVisible) {
                    hide();
                    return true;
                }
            }
            // Reset timer on any key interaction
            resetHideTimer();
        }
        return false; 
    }
    
    public void release() {
        player.removeListener(this);
        hideHandler.removeCallbacksAndMessages(null);
        if (playerView instanceof CustomPlayerView) {
            ((CustomPlayerView) playerView).setOnTapListener(null);
    }
    }

    private void toggleLock() {
        isLocked = !isLocked;
        updateLockState();
        if (isLocked) {
             showLockedToast();
             hide();
             // Keep lock button visible
             if (btnLock != null) {
                 btnLock.setVisibility(View.VISIBLE);
                 btnLock.setAlpha(1f);
             }
        } else {
             Utils.showToast(activity, "Screen Unlocked");
             show();
        }
    }
}
