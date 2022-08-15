/*
 * Copyright (c) 2012 Google Inc.  All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.org.ngo.squeezer;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.view.GestureDetectorCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.ActionBar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.org.ngo.squeezer.dialog.AboutDialog;
import uk.org.ngo.squeezer.dialog.EnableWifiDialog;
import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.framework.ViewParamItemView;
import uk.org.ngo.squeezer.itemlist.AlarmsActivity;
import uk.org.ngo.squeezer.itemlist.CurrentPlaylistActivity;
import uk.org.ngo.squeezer.itemlist.HomeActivity;
import uk.org.ngo.squeezer.itemlist.IServiceItemListCallback;
import uk.org.ngo.squeezer.itemlist.PlayerListActivity;
import uk.org.ngo.squeezer.itemlist.JiveItemListActivity;
import uk.org.ngo.squeezer.itemlist.JiveItemViewLogic;
import uk.org.ngo.squeezer.itemlist.PlayerViewLogic;
import uk.org.ngo.squeezer.model.CurrentPlaylistItem;
import uk.org.ngo.squeezer.model.Player;
import uk.org.ngo.squeezer.model.PlayerState;
import uk.org.ngo.squeezer.model.PlayerState.RepeatStatus;
import uk.org.ngo.squeezer.model.PlayerState.ShuffleStatus;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.service.ConnectionState;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.service.SqueezeService;
import uk.org.ngo.squeezer.service.event.ConnectionChanged;
import uk.org.ngo.squeezer.service.event.HandshakeComplete;
import uk.org.ngo.squeezer.service.event.HomeMenuEvent;
import uk.org.ngo.squeezer.service.event.MusicChanged;
import uk.org.ngo.squeezer.service.event.PlayStatusChanged;
import uk.org.ngo.squeezer.service.event.PlayersChanged;
import uk.org.ngo.squeezer.service.event.PowerStatusChanged;
import uk.org.ngo.squeezer.service.event.RegisterSqueezeNetwork;
import uk.org.ngo.squeezer.service.event.RepeatStatusChanged;
import uk.org.ngo.squeezer.service.event.ShuffleStatusChanged;
import uk.org.ngo.squeezer.service.event.SongTimeChanged;
import uk.org.ngo.squeezer.util.ImageFetcher;
import uk.org.ngo.squeezer.widget.OnSwipeListener;

public class NowPlayingFragment extends Fragment {

    private static final String TAG = "NowPlayingFragment";

    private BaseActivity mActivity;
    private JiveItemViewLogic pluginViewDelegate;

    @Nullable
    private ISqueezeService mService = null;

    private TextView albumText;

    private TextView artistAlbumText;

    private TextView artistText;

    private TextView trackText;

    private JiveItem albumItem;
    private JiveItem artistItem;

    @Nullable
    private View btnContextMenu;

    private TextView currentTime;

    private TextView totalTime;

    private MenuItem menuItemDisconnect;

    private JiveItem globalSearch;
    private MenuItem menuItemSearch;

    private MenuItem menuItemPlaylist;

    private MenuItem menuItemPlayers;

    private MenuItem menuItemTogglePower;
    private MenuItem menuItemSleep;
    private MenuItem menuItemSleepAtEndOfSong;
    private MenuItem menuItemCancelSleep;

    private MenuItem menuItemAlarm;

    private MaterialButton playPauseButton;

    @Nullable
    private Button nextButton;

    @Nullable
    private Button prevButton;

    private MaterialButton shuffleButton;

    private MaterialButton repeatButton;

    private ImageView albumArt;

    /** In full-screen mode, shows the current progress through the track. */
    private Slider slider;

    /** In mini-mode, shows the current progress through the track. */
    private ProgressBar mProgressBar;

    // Updating the seekbar
    private boolean updateSeekBar = true;

    private Button volumeButton;

    private Button playlistButton;

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager connMgr = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (networkInfo.isConnected()) {
                Log.v(TAG, "Received WIFI connected broadcast");
                if (!isConnected()) {
                    // Requires a serviceStub. Else we'll do this on the service
                    // connection callback.
                    if (mService != null && !isManualDisconnect(context)) {
                        Log.v(TAG, "Initiated connect on WIFI connected");
                        startVisibleConnection();
                    }
                }
            }
        }
    };

    /** Dialog displayed while connecting to the server. */
    private ProgressDialog connectingDialog = null;

    /**
     * Shows the "connecting" dialog if it's not already showing.
     */
    @UiThread
    private void showConnectingDialog() {
        if (connectingDialog == null || !connectingDialog.isShowing()) {
            Preferences preferences = new Preferences(mActivity);
            Preferences.ServerAddress serverAddress = preferences.getServerAddress();

            connectingDialog = ProgressDialog.show(mActivity,
                    getText(R.string.connecting_text),
                    getString(R.string.connecting_to_text, serverAddress.serverName()),
                    true, false);
        }
    }

    /**
     * Dismisses the "connecting" dialog if it's showing.
     */
    @UiThread
    private void dismissConnectingDialog() {
        if (connectingDialog != null && connectingDialog.isShowing()) {
            connectingDialog.dismiss();
        }
        connectingDialog = null;
    }


    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.v(TAG, "ServiceConnection.onServiceConnected()");
            NowPlayingFragment.this.onServiceConnected((ISqueezeService) binder);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };

    private boolean mFullHeightLayout;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mActivity = (BaseActivity) context;
        pluginViewDelegate = new JiveItemViewLogic(mActivity);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mActivity.bindService(new Intent(mActivity, SqueezeService.class), serviceConnection, Context.BIND_AUTO_CREATE);
        Log.d(TAG, "did bindService; serviceStub = " + mService);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v;

        mFullHeightLayout = (container.getLayoutParams().height == ViewGroup.LayoutParams.MATCH_PARENT);
        if (mFullHeightLayout) {
            v = inflater.inflate(R.layout.now_playing_fragment_full, container, false);

            artistText = v.findViewById(R.id.artistname);
            albumText = v.findViewById(R.id.albumname);
            shuffleButton = v.findViewById(R.id.shuffle);
            repeatButton = v.findViewById(R.id.repeat);
            currentTime = v.findViewById(R.id.currenttime);
            totalTime = v.findViewById(R.id.totaltime);
            slider = v.findViewById(R.id.seekbar);
            playlistButton = v.findViewById(R.id.playlist);

            final ViewParamItemView<JiveItem> viewHolder = new ViewParamItemView<>(mActivity, v);
            viewHolder.contextMenuButton.setOnClickListener(view -> {
                CurrentPlaylistItem currentSong = getCurrentSong();
                // This extra check is if user pressed the button before visibility is set to GONE
                if (currentSong != null) {
                    pluginViewDelegate.showContextMenu(viewHolder, currentSong);
                }
            });
            btnContextMenu = viewHolder.contextMenuButtonHolder;
        } else {
            v = inflater.inflate(R.layout.now_playing_fragment_mini, container, false);

            mProgressBar = v.findViewById(R.id.progressbar);
            artistAlbumText = v.findViewById(R.id.artistalbumname);
        }

        albumArt = v.findViewById(R.id.album);
        trackText = v.findViewById(R.id.trackname);
        playPauseButton = v.findViewById(R.id.pause);

        nextButton = v.findViewById(R.id.next);
        prevButton = v.findViewById(R.id.prev);
        volumeButton = v.findViewById(R.id.volume);

        // Marquee effect on TextViews only works if they're focused.
        trackText.requestFocus();

        playPauseButton.setOnClickListener(view -> mService.togglePausePlay());

        volumeButton.setOnClickListener(view -> mActivity.showVolumePanel());
        nextButton.setOnClickListener(view -> mService.nextTrack());
        prevButton.setOnClickListener(view -> mService.previousTrack());

        if (mFullHeightLayout) {
            artistText.setOnClickListener(v1 -> {
                if (artistItem != null) {
                    JiveItemListActivity.show(mActivity, artistItem, artistItem.goAction);
                }
            });

            albumText.setOnClickListener(v12 -> {
                if (albumItem != null) {
                    JiveItemListActivity.show(mActivity, albumItem, albumItem.goAction);
                }
            });

            trackText.setOnClickListener(v13 -> {
                CurrentPlaylistItem song = getCurrentSong();
                if (song != null) {
                    globalSearch.input.initialText = song.getName();
                    JiveItemListActivity.show(mActivity, globalSearch, globalSearch.goAction);
                }
            });

            shuffleButton.setOnClickListener(view -> mService.toggleShuffle());

            repeatButton.setOnClickListener(view -> mService.toggleRepeat());

            playlistButton.setOnClickListener(view -> CurrentPlaylistActivity.show(mActivity));

            // Update the time indicator to reflect the dragged thumb position.
            slider.addOnChangeListener((s, value, fromUser) -> {
                if (fromUser) {
                    currentTime.setText(Util.formatElapsedTime((int)value));
                }
            });

            slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
                CurrentPlaylistItem seekingSong;

                // Disable updates when user drags the thumb.
                @Override
                public void onStartTrackingTouch(@NonNull Slider s) {
                    seekingSong = getCurrentSong();
                    updateSeekBar = false;
                }

                // Re-enable updates. If the current song is the same as when
                // we started seeking then jump to the new point in the track,
                // otherwise ignore the seek.
                @Override
                public void onStopTrackingTouch(@NonNull Slider s) {
                    CurrentPlaylistItem thisSong = getCurrentSong();

                    updateSeekBar = true;

                    if (seekingSong == thisSong) {
                        setSecondsElapsed((int)s.getValue());
                    }
                }
            });
        } else {
            int screenWidthDp = getResources().getConfiguration().screenWidthDp;
            if (screenWidthDp < 456) {
                nextButton.setVisibility(View.GONE);
                prevButton.setVisibility(View.GONE);
            }

            if (screenWidthDp < 408 || (456 < screenWidthDp && screenWidthDp < 504)) {
                volumeButton.setVisibility(View.GONE);
            }

            final GestureDetectorCompat detector = new GestureDetectorCompat(mActivity, new OnSwipeListener() {
                // Clicking on the layout goes to NowPlayingActivity.
                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    NowPlayingActivity.show(mActivity);
                    return true;
                }

                // Swipe up on the layout goes to NowPlayingActivity.
                @Override
                public boolean onSwipeUp() {
                    NowPlayingActivity.show(mActivity);
                    return true;
                }
            });
            v.setOnTouchListener((view, event) -> detector.onTouchEvent(event));
        }

        return v;
    }

    @UiThread
    private void updatePlayPauseIcon(@PlayerState.PlayState String playStatus) {
        playPauseButton.setIconResource((PlayerState.PLAY_STATE_PLAY.equals(playStatus)) ? R.drawable.ic_action_pause : R.drawable.ic_action_play);
    }

    @UiThread
    private void updateShuffleStatus(ShuffleStatus shuffleStatus) {
        if (mFullHeightLayout && shuffleStatus != null) {
            shuffleButton.setIconResource(shuffleStatus.getIcon());
            shuffleButton.setIconTint(getTint(shuffleStatus == ShuffleStatus.SHUFFLE_OFF));
        }
    }

    @UiThread
    private void updateRepeatStatus(RepeatStatus repeatStatus) {
        if (mFullHeightLayout && repeatStatus != null) {
            repeatButton.setIconResource(repeatStatus.getIcon());
            repeatButton.setIconTint(getTint(repeatStatus == RepeatStatus.REPEAT_OFF));
        }
    }

    private ColorStateList getTint(boolean off) {
        return AppCompatResources.getColorStateList(mActivity, mActivity.getAttributeValue(off ? R.attr.colorControlNormal : R.attr.colorPrimary));
    }

    @UiThread
    private void updatePlayerMenuItems() {
        // The fragment may no longer be attached to the parent activity.  If so, do nothing.
        if (!isAdded()) {
            return;
        }

        Player player = getActivePlayer();
        PlayerState playerState = player != null ? player.getPlayerState() : null;
        String playerName = player != null ? player.getName() : "";

        if (menuItemTogglePower != null) {
            if (playerState != null && player.isCanpoweroff()) {
                menuItemTogglePower.setTitle(getString(playerState.isPoweredOn() ? R.string.menu_item_poweroff : R.string.menu_item_poweron, playerName));
                menuItemTogglePower.setVisible(true);
            } else {
                menuItemTogglePower.setVisible(false);
            }
        }

        if (menuItemCancelSleep != null) {
            menuItemCancelSleep.setVisible(playerState != null && playerState.getSleepDuration() != 0);
        }

        if (menuItemSleepAtEndOfSong != null) {
            menuItemSleepAtEndOfSong.setVisible(playerState != null && playerState.isPlaying());
        }
    }

    /**
     * Manages the list of connected players in the action bar.
     *
     * @param players A list of players to show. May be empty (use {@code
     * Collections.&lt;Player>emptyList()}) but not null.
     * @param activePlayer The currently active player. May be null.
     */
    @UiThread
    private void updatePlayerDropDown(@NonNull Collection<Player> players,
            @Nullable Player activePlayer) {
        if (!isAdded()) {
            return;
        }

        // Only include players that are connected to the server.
        List<Player> connectedPlayers = new ArrayList<>();
        for (Player player : players) {
            if (player.getConnected()) {
                connectedPlayers.add(player);
            }
        }
        Collections.sort(connectedPlayers); // sort players alphabetically by player name

        ActionBar actionBar = mActivity.getSupportActionBar();

        // If there are multiple players connected then show a spinner allowing the user to
        // choose between them.
        if (connectedPlayers.size() > 1) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setCustomView(R.layout.action_bar_custom_view);
            Spinner spinner = (Spinner) actionBar.getCustomView();
            final Context actionBarContext = actionBar.getThemedContext();
            final ArrayAdapter<Player> playerAdapter = new ArrayAdapter<Player>(
                    actionBarContext, android.R.layout.simple_spinner_dropdown_item,
                    connectedPlayers) {
                @Override
                public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                    return Util.getActionBarSpinnerItemView(actionBarContext, convertView, parent,
                            getItem(position).getName());
                }

                @Override
                public @NonNull View getView(int position, View convertView, @NonNull ViewGroup parent) {
                    return Util.getActionBarSpinnerItemView(actionBarContext, convertView, parent,
                            getItem(position).getName());
                }
            };
            spinner.setAdapter(playerAdapter);
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (!playerAdapter.getItem(position).equals(mService.getActivePlayer())) {
                        mService.setActivePlayer(playerAdapter.getItem(position));
                        updateUiFromPlayerState(mService.getActivePlayerState());
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
            if (activePlayer != null) {
                spinner.setSelection(playerAdapter.getPosition(activePlayer));
            }
        } else {
            // 0 or 1 players, disable the spinner, and either show the sole player in the
            // action bar, or the app name if there are no players.
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setDisplayShowCustomEnabled(false);

            if (connectedPlayers.size() == 1) {
                actionBar.setTitle(connectedPlayers.get(0).getName());
            } else {
                actionBar.setTitle(R.string.app_name);
            }
        }
    }

    protected void onServiceConnected(@NonNull ISqueezeService service) {
        Log.v(TAG, "Service bound");
        mService = service;

        maybeRegisterCallbacks(mService);

        // Assume they want to connect (unless manually disconnected).
        if (!isConnected()) {
            if (isManualDisconnect(mActivity)) {
                ConnectActivity.show(mActivity);

            } else {
                startVisibleConnection();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume...");

        if (mService != null) {
            maybeRegisterCallbacks(mService);
        }

        if (new Preferences(mActivity).isAutoConnect()) {
            mActivity.registerReceiver(broadcastReceiver, new IntentFilter(
                    ConnectivityManager.CONNECTIVITY_ACTION));
        }
    }

    /**
     * Keep track of whether callbacks have been registered
     */
    private boolean mRegisteredCallbacks;

    /**
     * This is called when the service is first connected, and whenever the activity is resumed.
     */
    private void maybeRegisterCallbacks(@NonNull ISqueezeService service) {
        if (!mRegisteredCallbacks) {
            service.getEventBus().registerSticky(this);

            mRegisteredCallbacks = true;
        }
    }

    @UiThread
    private void updateTimeDisplayTo(int secondsIn, int secondsTotal) {
        if (mFullHeightLayout) {
            if (updateSeekBar) {
                if (slider.getValueTo() != secondsTotal) {
                    slider.setValueTo(secondsTotal > 0 ? secondsTotal : 1);
                    totalTime.setText(Util.formatElapsedTime(secondsTotal));
                }
                slider.setEnabled(secondsTotal > 0);
                slider.setValue(secondsTotal > 0 ? secondsIn : 0);
                currentTime.setText(Util.formatElapsedTime(secondsIn));
            }
        } else {
            if (mProgressBar.getMax() != secondsTotal) {
                mProgressBar.setMax(secondsTotal);
            }
            mProgressBar.setProgress(secondsIn);
        }
    }

    /**
     * Update the UI based on the player state. Call this when the active player
     * changes.
     *
     * @param playerState the player state to reflect in the UI.
     */
    @UiThread
    private void updateUiFromPlayerState(@NonNull PlayerState playerState) {
        updateSongInfo(playerState);

        updatePlayPauseIcon(playerState.getPlayStatus());
        updateShuffleStatus(playerState.getShuffleStatus());
        updateRepeatStatus(playerState.getRepeatStatus());
        updatePlayerMenuItems();
    }

    /**
     * Joins elements together with ' - ', skipping nulls.
     */
    protected static final Joiner mJoiner = Joiner.on(" - ").skipNulls();

    /**
     * Update the UI when the song changes, either because the track has changed, or the
     * active player has changed.
     *
     * @param playerState the player state for the song.
     */
    @UiThread
    private void updateSongInfo(@NonNull PlayerState playerState) {
        updateTimeDisplayTo((int)playerState.getCurrentTimeSecond(),
                playerState.getCurrentSongDuration());

        CurrentPlaylistItem song = playerState.getCurrentSong();
        if (song == null) {
            // Create empty song if this is called (via _HandshakeComplete) before status is received
            song = new CurrentPlaylistItem(new HashMap<>());
        }

        // TODO handle button remapping (buttons in status response)
        if (!song.getTrack().isEmpty()) {
            trackText.setText(song.getTrack());

            // don't remove rew and fwd for remote tracks, because a single track playlist
            // is not an indication that fwd and rwd are invalid actions
            boolean canSkip = !((playerState.getCurrentPlaylistTracksNum() == 1) && !playerState.isRemote());
            nextButton.setEnabled(canSkip);
            prevButton.setEnabled(canSkip);

            if (mFullHeightLayout) {
                btnContextMenu.setVisibility(View.VISIBLE);
                artistText.setText(song.getArtist());
                albumText.setText(song.getAlbum());
                totalTime.setText(Util.formatElapsedTime(playerState.getCurrentSongDuration()));

                mService.pluginItems(song.moreAction, new IServiceItemListCallback<JiveItem>() {
                    @Override
                    public void onItemsReceived(int count, int start, Map<String, Object> parameters, List<JiveItem> items, Class<JiveItem> dataType) {
                        albumItem = findBrowseAction(items, "album_id");
                        artistItem = findBrowseAction(items, "artist_id");
                    }

                    @Override
                    public Object getClient() {
                        return mActivity;
                    }
                });
            } else {
                artistAlbumText.setText(mJoiner.join(
                        Strings.emptyToNull(song.getArtist()),
                        Strings.emptyToNull(song.getAlbum())));
            }
        } else {
            trackText.setText("");
            if (mFullHeightLayout) {
                artistText.setText("");
                albumText.setText("");
                btnContextMenu.setVisibility(View.GONE);
            } else {
                artistAlbumText.setText("");
            }
        }

        if (!song.hasArtwork()) {
            albumArt.setImageDrawable(song.getIconDrawable(mActivity, R.drawable.icon_album_noart_fullscreen));
        } else {
            ImageFetcher.getInstance(mActivity).loadImage(song.getIcon(), albumArt);
        }
    }

    private JiveItem findBrowseAction(List<JiveItem> items, String idParam) {
        for (JiveItem item : items) {
            if (item.goAction != null && item.goAction.action != null &&
                    item.goAction.action.cmd.equals(Arrays.asList("browselibrary", "items")) &&
                    item.goAction.action.params.containsKey(idParam)) {
                return item;
            }
        }
        return null;
    }

    private boolean setSecondsElapsed(int seconds) {
        return mService != null && mService.setSecondsElapsed(seconds);
    }

    private PlayerState getPlayerState() {
        if (mService == null) {
            return null;
        }
        return mService.getPlayerState();
    }

    private Player getActivePlayer() {
        if (mService == null) {
            return null;
        }
        return mService.getActivePlayer();
    }

    private CurrentPlaylistItem getCurrentSong() {
        PlayerState playerState = getPlayerState();
        return playerState != null ? playerState.getCurrentSong() : null;
    }

    private boolean isConnected() {
        return mService != null && mService.isConnected();
    }

    private boolean isConnectInProgress() {
        return mService != null && mService.isConnectInProgress();
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause...");

        dismissConnectingDialog();

        if (new Preferences(mActivity).isAutoConnect()) {
            mActivity.unregisterReceiver(broadcastReceiver);
        }

        if (mRegisteredCallbacks) {
            mService.getEventBus().unregister(this);
            mRegisteredCallbacks = false;
        }

        pluginViewDelegate.resetContextMenu();

        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mService != null) {
            mActivity.unbindService(serviceConnection);
        }
    }

    /**
     * @see Fragment#onCreateOptionsMenu(android.view.Menu,
     * android.view.MenuInflater)
     */
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        // I confess that I don't understand why using the inflater passed as
        // an argument here doesn't work -- but if you do it crashes without
        // a stracktrace on API 7.
        MenuInflater i = mActivity.getMenuInflater();
        i.inflate(R.menu.now_playing_fragment_menu, menu);
        PlayerViewLogic.inflatePlayerActions(mActivity, i, menu);

        menuItemSearch = menu.findItem(R.id.menu_item_search);
        menuItemPlaylist = menu.findItem(R.id.menu_item_playlist);
        menuItemDisconnect = menu.findItem(R.id.menu_item_disconnect);

        menuItemTogglePower = menu.findItem(R.id.toggle_power);
        menuItemSleep = menu.findItem(R.id.sleep);
        menuItemSleepAtEndOfSong = menu.findItem(R.id.end_of_song);
        menuItemCancelSleep = menu.findItem(R.id.cancel_sleep);

        menuItemPlayers = menu.findItem(R.id.menu_item_players);
        menuItemAlarm = menu.findItem(R.id.menu_item_alarm);
    }

    /**
     * Sets the state of assorted option menu items based on whether or not there is a connection to
     * the server, and if so, whether any players are connected.
     */
    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        boolean connected = isConnected();

        // These are all set at the same time, so one check is sufficient
        if (menuItemDisconnect != null) {
            // Set visibility and enabled state of menu items that are not player-specific.
            menuItemSearch.setVisible(globalSearch != null);
            menuItemDisconnect.setVisible(connected);

            // Set visibility and enabled state of menu items that are player-specific and
            // require a connection to the server.
            boolean haveConnectedPlayers = connected && mService != null
                    && !mService.getPlayers().isEmpty();

            menuItemPlaylist.setVisible(haveConnectedPlayers);
            menuItemPlayers.setVisible(haveConnectedPlayers);
            menuItemAlarm.setVisible(haveConnectedPlayers);
            menuItemSleep.setVisible(haveConnectedPlayers);

            // Don't show the item to go to current playlist if in NowPlayingActivity or CurrentPlaylistActivity.
            if (mActivity instanceof NowPlayingActivity || mActivity instanceof CurrentPlaylistActivity) {
                menuItemPlaylist.setVisible(false);
            }

            // Don't show the item to go to players if in PlayersActivity.
            if (mActivity instanceof PlayerListActivity) {
                menuItemPlayers.setVisible(false);
            }

            // Don't show the item to go to alarms if in AlarmsActivity.
            if (mActivity instanceof AlarmsActivity) {
                menuItemAlarm.setVisible(false);
            }
        }

        updatePlayerMenuItems();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (PlayerViewLogic.doPlayerAction(mService, item, getActivePlayer())) {
            return true;
        }

        int itemId = item.getItemId();
        if (itemId == R.id.menu_item_search) {
            globalSearch.input.initialText = "";
            JiveItemListActivity.show(mActivity, globalSearch, globalSearch.goAction);
            return true;
        } else if (itemId == R.id.menu_item_playlist) {
            CurrentPlaylistActivity.show(mActivity);
            return true;
        } else if (itemId == R.id.menu_item_settings) {
            SettingsActivity.show(mActivity);
            return true;
        } else if (itemId == R.id.menu_item_disconnect) {
            new Preferences(mActivity).setManualDisconnect(true);
            mService.disconnect();
            return true;
        } else if (itemId == R.id.menu_item_players) {
            PlayerListActivity.show(mActivity);
            return true;
        } else if (itemId == R.id.menu_item_alarm) {
            AlarmsActivity.show(mActivity);
            return true;
        } else if (itemId == R.id.menu_item_about) {
            new AboutDialog().show(getParentFragmentManager(), "AboutDialog");
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Has the user manually disconnected from the server?
     *
     * @return true if they have, false otherwise.
     */
    private boolean isManualDisconnect(Context context) {
        return new Preferences(context).isManualDisconnect();
    }

    public void startVisibleConnection() {
        Log.v(TAG, "startVisibleConnection");
        if (mService == null) {
            return;
        }

        Preferences preferences = new Preferences(mActivity);

        // If we are configured to automatically connect on Wi-Fi availability
        // we will also give the user the opportunity to enable Wi-Fi
        if (preferences.isAutoConnect()) {
            WifiManager wifiManager = (WifiManager) mActivity
                    .getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (!wifiManager.isWifiEnabled()) {
                FragmentManager fragmentManager = getParentFragmentManager();
                if (fragmentManager == null) {
                    Log.i(TAG, "fragment manager is null so we can't show EnableWifiDialog");
                    return;
                }

                FragmentTransaction ft = fragmentManager.beginTransaction();
                Fragment prev = fragmentManager.findFragmentByTag(EnableWifiDialog.TAG);
                if (prev != null) {
                    ft.remove(prev);
                }
                ft.addToBackStack(null);

                // Create and show the dialog.
                DialogFragment enableWifiDialog = new EnableWifiDialog();
                enableWifiDialog.show(ft, EnableWifiDialog.TAG);
                return;
                // When a Wi-Fi connection is made this method will be called again by the
                // broadcastReceiver
            }
        }

        if (!preferences.hasServerConfig()) {
            // Set up a server connection, if it is not present
            ConnectActivity.show(mActivity);
            return;
        }

        if (isConnectInProgress()) {
            Log.v(TAG, "Connection is already in progress, connecting aborted");
            return;
        }
        mService.startConnect();
    }


    @MainThread
    public void onEventMainThread(ConnectionChanged event) {
        Log.d(TAG, "ConnectionChanged: " + event);

        // The fragment may no longer be attached to the parent activity.  If so, do nothing.
        if (!isAdded()) {
            return;
        }

        // Handle any of the reasons for disconnection, clear the dialog and show the
        // ConnectActivity.
        if (event.connectionState == ConnectionState.DISCONNECTED) {
            dismissConnectingDialog();
            ConnectActivity.show(mActivity);
            return;
        }

        if (event.connectionState == ConnectionState.CONNECTION_FAILED) {
            dismissConnectingDialog();
            switch (event.connectionError) {
                case LOGIN_FALIED:
                    ConnectActivity.showLoginFailed(mActivity);
                    break;
                case INVALID_URL:
                    ConnectActivity.showInvalidUrl(mActivity);
                    break;
                case START_CLIENT_ERROR:
                case CONNECTION_ERROR:
                    ConnectActivity.showConnectionFailed(mActivity);
                    break;
            }
            return;
        }

        if (event.connectionState == ConnectionState.RECONNECT) {
            dismissConnectingDialog();
            HomeActivity.show(mActivity);
            return;
        }

        // Any other event means that a connection is in progress or completed.
        // Show the the dialog if appropriate.
        if (event.connectionState != ConnectionState.CONNECTION_COMPLETED) {
            showConnectingDialog();
        }

        // Ensure that option menu item state is adjusted as appropriate.
        getActivity().supportInvalidateOptionsMenu();

        nextButton.setEnabled(false);
        prevButton.setEnabled(false);
        volumeButton.setEnabled(false);

        if (mFullHeightLayout) {
            shuffleButton.setEnabled(false);
            repeatButton.setEnabled(false);
            playlistButton.setEnabled(false);

            albumArt.setImageResource(R.drawable.icon_album_noart_fullscreen);
            shuffleButton.setIconResource(0);
            repeatButton.setIconResource(0);
            updatePlayerDropDown(Collections.emptyList(), null);
            artistText.setText(getText(R.string.disconnected_text));
            btnContextMenu.setVisibility(View.GONE);
            currentTime.setText("--:--");
            totalTime.setText("--:--");
            slider.setEnabled(false);
            slider.setValue(0);
        } else {
            albumArt.setImageResource(R.drawable.icon_pending_artwork);
            mProgressBar.setEnabled(false);
            mProgressBar.setProgress(0);
        }
     }

    @MainThread
    public void onEventMainThread(HandshakeComplete event) {
        // Event might arrive before this fragment has connected to the service (e.g.,
        // the activity connected before this fragment did).
        // XXX: Verify that this is possible, since the fragment can't register for events
        // until it's connected to the service.
        if (mService == null) {
            return;
        }

        Log.d(TAG, "Handshake complete");

        dismissConnectingDialog();

        nextButton.setEnabled(true);
        prevButton.setEnabled(true);
        volumeButton.setEnabled(true);
        if (mFullHeightLayout) {
            shuffleButton.setEnabled(true);
            repeatButton.setEnabled(true);
            playlistButton.setEnabled(true);
        } else {
            mProgressBar.setEnabled(true);
        }

        PlayerState playerState = getPlayerState();

        // May be no players connected.
        // TODO: These views should be cleared if there's no player connected.
        if (playerState == null)
            return;

        updateUiFromPlayerState(playerState);
    }

    @MainThread
    public void onEventMainThread(@SuppressWarnings("unused") RegisterSqueezeNetwork event) {
        // We're connected but the controller needs to register with the server
        JiveItemListActivity.register(mActivity);
    }

    @MainThread
    public void onEventMainThread(MusicChanged event) {
        if (event.player.equals(mService.getActivePlayer())) {
            updateSongInfo(event.playerState);
        }
    }

    @MainThread
    public void onEventMainThread(PlayersChanged event) {
        updatePlayerDropDown(event.players.values(), mService.getActivePlayer());
    }

    @MainThread
    public void onEventMainThread(PlayStatusChanged event) {
        if (event.player.equals(mService.getActivePlayer())) {
            updatePlayPauseIcon(event.playStatus);
        }
    }

    @MainThread
    public void onEventMainThread(PowerStatusChanged event) {
        if (event.player.equals(mService.getActivePlayer())) {
            updatePlayerMenuItems();
        }
    }

    @MainThread
    public void onEventMainThread(HomeMenuEvent event) {
        globalSearch = null;
        for (JiveItem menuItem : event.menuItems) {
            if ("globalSearch".equals(menuItem.getId()) && menuItem.goAction != null) {
                globalSearch = menuItem;
                break;
            }
        }
        if (menuItemSearch != null) {
            menuItemSearch.setVisible(globalSearch != null);
        }
    }

    @MainThread
    public void onEventMainThread(RepeatStatusChanged event) {
        if (event.player.equals(mService.getActivePlayer())) {
            updateRepeatStatus(event.repeatStatus);
        }
    }

    @MainThread
    public void onEventMainThread(ShuffleStatusChanged event) {
        if (event.player.equals(mService.getActivePlayer())) {
            updateShuffleStatus(event.shuffleStatus);
        }
    }

    @MainThread
    public void onEventMainThread(SongTimeChanged event) {
        if (event.player.equals(mService.getActivePlayer())) {
            updateTimeDisplayTo(event.currentPosition, event.duration);
        }
    }
}
