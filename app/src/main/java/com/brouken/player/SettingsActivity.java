package com.brouken.player;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.widget.Toast;

import com.brouken.player.trakt.TraktAuthManager;
import com.brouken.player.trakt.DeviceCodeResponse;
import com.brouken.player.trakt.TokenResponse;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;

public class SettingsActivity extends AppCompatActivity {

    static RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
            getWindow().setNavigationBarColor(Color.TRANSPARENT);

            if (Build.VERSION.SDK_INT >= 35) {
                int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;

                if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
                    getWindow().getDecorView().setSystemUiVisibility(0);
                } else if (nightModeFlags == Configuration.UI_MODE_NIGHT_NO) {
                    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                }
            }
        }

        super.onCreate(savedInstanceState);

        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        if (Build.VERSION.SDK_INT >= 29) {
            LinearLayout layout = findViewById(R.id.settings_layout);
            layout.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                view.setPadding(windowInsets.getSystemWindowInsetLeft(),
                        windowInsets.getSystemWindowInsetTop(),
                        windowInsets.getSystemWindowInsetRight(),
                        0);
                if (recyclerView != null) {
                    recyclerView.setPadding(0,0,0, windowInsets.getSystemWindowInsetBottom());
                }
                windowInsets.consumeSystemWindowInsets();
                return windowInsets;
            });
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            Preference preferenceAutoPiP = findPreference("autoPiP");
            if (preferenceAutoPiP != null) {
                preferenceAutoPiP.setEnabled(Utils.isPiPSupported(this.getContext()));
            }
            Preference preferenceFrameRateMatching = findPreference("frameRateMatching");
            if (preferenceFrameRateMatching != null) {
                preferenceFrameRateMatching.setEnabled(Build.VERSION.SDK_INT >= 23);
            }
            ListPreference listPreferenceFileAccess = findPreference("fileAccess");
            if (listPreferenceFileAccess != null) {
                List<String> entries = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.file_access_entries)));
                List<String> values = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.file_access_values)));
                if (Build.VERSION.SDK_INT < 30) {
                    int index = values.indexOf("mediastore");
                    entries.remove(index);
                    values.remove(index);
                }
                if (!Utils.hasSAFChooser(getContext().getPackageManager())) {
                    int index = values.indexOf("saf");
                    entries.remove(index);
                    values.remove(index);
                }
                listPreferenceFileAccess.setEntries(entries.toArray(new String[0]));
                listPreferenceFileAccess.setEntryValues(values.toArray(new String[0]));
            }

            ListPreference listPreferenceLanguageAudio = findPreference("languageAudio");
            if (listPreferenceLanguageAudio != null) {
                LinkedHashMap<String, String> entries = new LinkedHashMap<>();
                entries.put(Prefs.TRACK_DEFAULT, getString(R.string.pref_language_track_default));
                entries.put(Prefs.TRACK_DEVICE, getString(R.string.pref_language_track_device));
                entries.putAll(getLanguages());
                listPreferenceLanguageAudio.setEntries(entries.values().toArray(new String[0]));
                listPreferenceLanguageAudio.setEntryValues(entries.keySet().toArray(new String[0]));
            }

            // AnimeSkip Login Handler
            Preference animeSkipLoginPref = findPreference("animeSkipLogin");
            if (animeSkipLoginPref != null) {
                updateAnimeSkipLoginSummary(animeSkipLoginPref);
                animeSkipLoginPref.setOnPreferenceClickListener(preference -> {
                    showAnimeSkipLoginDialog();
                    return true;
                });
            }

            // Trakt Handlers
            setupTraktPreferences();

            // DISABLED: Subtitle Addons (reverted to original)
            // Preference subtitleAddonsPref = findPreference("subtitleAddons");
            // if (subtitleAddonsPref != null) {
            //     subtitleAddonsPref.setOnPreferenceClickListener(preference -> {
            //         android.content.Intent intent = new android.content.Intent(
            //             requireContext(), 
            //             com.brouken.player.ui.subtitle.SubtitleAddonsActivity.class
            //         );
            //         startActivity(intent);
            //         return true;
            //     });
            // }

            // About Section Handlers
            setupAboutPreferences();
            
            // Update Handler
            setupUpdatePreferences();
        }

        private void setupAboutPreferences() {
            // Credits - opens Discord (only on mobile, not TV)
            Preference creditsPref = findPreference("credits");
            if (creditsPref != null) {
                boolean isTV = requireContext().getPackageManager().hasSystemFeature("android.software.leanback");
                if (isTV) {
                    creditsPref.setSummary("Created by Cxsmo_AI");
                    creditsPref.setSelectable(false);
                } else {
                    creditsPref.setOnPreferenceClickListener(preference -> {
                        try {
                            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                            intent.setData(android.net.Uri.parse("https://discord.gg/njSKPUQtFa"));
                            startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(requireContext(), "Could not open Discord link", Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    });
                }
            }

            // Version
            Preference versionPref = findPreference("appVersion");
            if (versionPref != null) {
                try {
                    String versionName = requireContext().getPackageManager()
                        .getPackageInfo(requireContext().getPackageName(), 0).versionName;
                    versionPref.setSummary(versionName);
                } catch (Exception e) {
                    versionPref.setSummary("Unknown");
                }
            }

            // GitHub - opens repo (only on mobile, not TV)
            Preference githubPref = findPreference("github");
            if (githubPref != null) {
                boolean isTV = requireContext().getPackageManager().hasSystemFeature("android.software.leanback");
                if (isTV) {
                    githubPref.setSelectable(false);
                } else {
                    githubPref.setOnPreferenceClickListener(preference -> {
                        try {
                            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                            intent.setData(android.net.Uri.parse("https://github.com/Cxsmo-ai/Just-Another-Skip-Player"));
                            startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(requireContext(), "Could not open GitHub link", Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    });
                }
            }
        }

        private void setupUpdatePreferences() {
            Preference checkUpdatesPref = findPreference("checkForUpdates");
            if (checkUpdatesPref != null) {
                checkUpdatesPref.setOnPreferenceClickListener(preference -> {
                    Toast.makeText(requireContext(), "Checking for updates...", Toast.LENGTH_SHORT).show();
                    
                    com.brouken.player.update.UpdateManager updateManager = 
                        com.brouken.player.update.UpdateManager.Companion.getInstance(requireContext());
                    
                    updateManager.forceCheck(updateInfo -> {
                        if (updateInfo != null && updateInfo.getHasApk()) {
                            showUpdateDialog(updateInfo);
                        } else {
                            Toast.makeText(requireContext(), "You're up to date!", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return true;
                });
            }
        }

        private void showUpdateDialog(com.brouken.player.update.UpdateInfo updateInfo) {
            // Launch premium animated update activity
            com.brouken.player.update.UpdateActivity.Companion.launch(requireContext(), updateInfo);
        }

        private void updateAnimeSkipLoginSummary(Preference pref) {
            Prefs prefs = new Prefs(requireContext());
            if (!prefs.animeSkipUsername.isEmpty()) {
                pref.setSummary("Logged in as: " + prefs.animeSkipUsername);
            } else {
                pref.setSummary("Login for community timestamps");
            }
        }

        private void showAnimeSkipLoginDialog() {
            Prefs prefs = new Prefs(requireContext());
            
            // If already logged in, show logout option
            if (!prefs.animeSkipUsername.isEmpty()) {
                new android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Anime Skip")
                    .setMessage("Logged in as: " + prefs.animeSkipUsername)
                    .setPositiveButton("Logout", (dialog, which) -> {
                        prefs.clearAnimeSkipLogin();
                        Preference pref = findPreference("animeSkipLogin");
                        if (pref != null) updateAnimeSkipLoginSummary(pref);
                        android.widget.Toast.makeText(requireContext(), "Logged out", android.widget.Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                return;
            }

            // Create login dialog
            android.view.LayoutInflater inflater = android.view.LayoutInflater.from(requireContext());
            android.view.View dialogView = inflater.inflate(android.R.layout.simple_list_item_2, null);
            
            // Create a simple linear layout with two EditTexts
            android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
            layout.setOrientation(android.widget.LinearLayout.VERTICAL);
            layout.setPadding(50, 40, 50, 10);

            android.widget.EditText usernameInput = new android.widget.EditText(requireContext());
            usernameInput.setHint("Username or Email");
            usernameInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            layout.addView(usernameInput);

            android.widget.EditText passwordInput = new android.widget.EditText(requireContext());
            passwordInput.setHint("Password");
            passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            layout.addView(passwordInput);

            new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Login to Anime Skip")
                .setView(layout)
                .setPositiveButton("Login", (dialog, which) -> {
                    String username = usernameInput.getText().toString().trim();
                    String password = passwordInput.getText().toString();
                    
                    if (username.isEmpty() || password.isEmpty()) {
                        android.widget.Toast.makeText(requireContext(), "Please enter username and password", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    // Perform login in background
                    new Thread(() -> {
                        try {
                            com.brouken.player.tmdb.AnimeSkipClient client = new com.brouken.player.tmdb.AnimeSkipClient();
                            com.brouken.player.tmdb.AnimeSkipClient.LoginResult result = client.login(username, password);
                            
                            if (result != null && result.getAuthToken() != null) {
                                // Save credentials
                                prefs.saveAnimeSkipLogin(result.getAuthToken(), result.getRefreshToken(), result.getUsername());
                                
                                requireActivity().runOnUiThread(() -> {
                                    Preference pref = findPreference("animeSkipLogin");
                                    if (pref != null) updateAnimeSkipLoginSummary(pref);
                                    android.widget.Toast.makeText(requireContext(), "Logged in as " + result.getUsername(), android.widget.Toast.LENGTH_SHORT).show();
                                });
                            } else {
                                requireActivity().runOnUiThread(() -> {
                                    android.widget.Toast.makeText(requireContext(), "Login failed - check credentials", android.widget.Toast.LENGTH_SHORT).show();
                                });
                            }
                        } catch (Exception e) {
                            requireActivity().runOnUiThread(() -> {
                                android.widget.Toast.makeText(requireContext(), "Login error: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                            });
                        }
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
        }

        // ==================== TRAKT INTEGRATION ====================

        private TraktAuthManager traktAuthManager;
        private AlertDialog traktDialog;

        private void setupTraktPreferences() {
            Prefs prefs = new Prefs(requireContext());

            Preference traktConnect = findPreference("traktConnect");
            Preference traktDisconnect = findPreference("traktDisconnect");
            Preference traktStatus = findPreference("traktStatus");

            // Update visibility based on connection status
            updateTraktStatus(prefs, traktConnect, traktDisconnect, traktStatus);

            if (traktConnect != null) {
                traktConnect.setOnPreferenceClickListener(preference -> {
                    showTraktConnectDialog();
                    return true;
                });
            }

            if (traktDisconnect != null) {
                traktDisconnect.setOnPreferenceClickListener(preference -> {
                    prefs.clearTraktTokens();
                    Toast.makeText(requireContext(), "Disconnected from Trakt", Toast.LENGTH_SHORT).show();
                    updateTraktStatus(prefs, traktConnect, traktDisconnect, traktStatus);
                    return true;
                });
            }
        }

        private void updateTraktStatus(Prefs prefs, Preference connect, Preference disconnect, Preference status) {
            boolean isConnected = prefs.isTraktConnected();
            
            if (connect != null) connect.setVisible(!isConnected);
            if (disconnect != null) disconnect.setVisible(isConnected);
            if (status != null) {
                status.setSummary(isConnected ? "✓ Connected" : "Not connected");
            }
        }

        private void showTraktConnectDialog() {
            Prefs prefs = new Prefs(requireContext());
            String clientId = prefs.traktClientId;
            String clientSecret = prefs.traktClientSecret;

            // Check if credentials are configured
            if (clientId == null || clientId.isEmpty() || clientSecret == null || clientSecret.isEmpty()) {
                Toast.makeText(requireContext(), 
                    "Please enter Client ID and Secret first", Toast.LENGTH_LONG).show();
                return;
            }

            // Show loading dialog
            traktDialog = new AlertDialog.Builder(requireContext())
                .setTitle("Connecting to Trakt...")
                .setMessage("Generating code...")
                .setCancelable(false)
                .setNegativeButton("Cancel", (d, w) -> {
                    if (traktAuthManager != null) {
                        traktAuthManager.cancelPolling();
                    }
                })
                .create();
            traktDialog.show();

            // Create auth manager
            traktAuthManager = new TraktAuthManager(
                requireContext(),
                clientId,
                clientSecret,
                tokens -> {
                    // On success - save tokens
                    long expiry = tokens.getCreatedAt() + tokens.getExpiresIn();
                    prefs.saveTraktTokens(tokens.getAccessToken(), tokens.getRefreshToken(), expiry);
                    
                    // Refresh UI
                    Preference connect = findPreference("traktConnect");
                    Preference disconnect = findPreference("traktDisconnect");
                    Preference status = findPreference("traktStatus");
                    updateTraktStatus(prefs, connect, disconnect, status);
                },
                error -> {
                    // On failure - already handled by toast in AuthManager
                }
            );

            // Start device flow
            traktAuthManager.startDeviceFlow(new TraktAuthManager.AuthCallback() {
                @Override
                public void onDeviceCodeReceived(String userCode, String verificationUrl) {
                    requireActivity().runOnUiThread(() -> {
                        if (traktDialog != null && traktDialog.isShowing()) {
                            traktDialog.setMessage(
                                "1. Go to: " + verificationUrl + "\n\n" +
                                "2. Enter code: " + userCode + "\n\n" +
                                "Waiting for authorization..."
                            );
                        }
                    });
                }

                @Override
                public void onSuccess() {
                    requireActivity().runOnUiThread(() -> {
                        if (traktDialog != null) traktDialog.dismiss();
                    });
                }

                @Override
                public void onExpired() {
                    requireActivity().runOnUiThread(() -> {
                        if (traktDialog != null) traktDialog.dismiss();
                    });
                }

                @Override
                public void onDenied() {
                    requireActivity().runOnUiThread(() -> {
                        if (traktDialog != null) traktDialog.dismiss();
                    });
                }

                @Override
                public void onError(String message) {
                    requireActivity().runOnUiThread(() -> {
                        if (traktDialog != null) traktDialog.dismiss();
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    });
                }
            });
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            if (Build.VERSION.SDK_INT >= 29) {
                recyclerView = getListView();
            }
        }

        LinkedHashMap<String, String> getLanguages() {
            LinkedHashMap<String, String> languages = new LinkedHashMap<>();
            for (Locale locale : Locale.getAvailableLocales()) {
                try {
                    // MissingResourceException: Couldn't find 3-letter language code for zz
                    String key = locale.getISO3Language();
                    String language = locale.getDisplayLanguage();
                    int length = language.offsetByCodePoints(0, 1);
                    if (!language.isEmpty()) {
                        language = language.substring(0, length).toUpperCase(locale) + language.substring(length);
                    }
                    String value = language + " [" + key + "]";
                    languages.put(key, value);
                } catch (MissingResourceException e) {
                    e.printStackTrace();
                }
            }
            Collator collator = Collator.getInstance();
            collator.setStrength(Collator.PRIMARY);
            Utils.orderByValue(languages, collator::compare);
            return languages;
        }
    }
}
