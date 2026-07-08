package com.cannon.onyxlauncher.fragments;

import static com.cannon.onyxlauncher.Tools.openPath;
import static com.cannon.onyxlauncher.Tools.shareLog;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import com.cannon.onyxlauncher.CustomControlsActivity;
import com.cannon.onyxlauncher.R;
import com.cannon.onyxlauncher.Tools;
import com.cannon.onyxlauncher.extra.ExtraConstants;
import com.cannon.onyxlauncher.extra.ExtraCore;
import com.cannon.onyxlauncher.prefs.LauncherPreferences;
import com.cannon.onyxlauncher.progresskeeper.ProgressKeeper;
import com.cannon.onyxlauncher.value.launcherprofiles.LauncherProfiles;
import com.cannon.onyxlauncher.value.launcherprofiles.MinecraftProfile;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mNewsButton = view.findViewById(R.id.news_button);
        Button mDiscordButton = view.findViewById(R.id.discord_button);
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mInstallJarButton = view.findViewById(R.id.install_jar_button);
        Button mShareLogsButton = view.findViewById(R.id.share_logs_button);
        Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        Button mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        setClickListener(mNewsButton, v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
        setClickListener(mDiscordButton, v -> Tools.openURL(requireActivity(), getString(R.string.discord_invite)));
        setClickListener(mCustomControlButton, v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        setClickListener(mInstallJarButton, v -> runInstallerWithConfirmation(false));
        setLongClickListener(mInstallJarButton, v->{
            runInstallerWithConfirmation(true);
            return true;
        });
        setClickListener(mEditProfileButton, v -> {
            if (mVersionSpinner != null) {
                mVersionSpinner.openProfileEditor(requireActivity());
            }
        });

        setClickListener(mPlayButton, v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));

        setClickListener(mShareLogsButton, (v) -> shareLog(requireContext()));

        setClickListener(mOpenDirectoryButton, (v)-> {
            Tools.switchDemo(Tools.isDemoProfile(v.getContext())); // avoid switching accounts being able to access
            if(Tools.isDemoProfile(v.getContext())){
                Toast.makeText(v.getContext(), R.string.toast_not_available_demo, Toast.LENGTH_LONG).show();
                return;
            }

            openPath(v.getContext(), getCurrentProfileDirectory(), false);
        });


        setLongClickListener(mNewsButton, (v)->{
            Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
            return true;
        });
    }

    private void setClickListener(@Nullable View target, View.OnClickListener listener) {
        if (target != null) target.setOnClickListener(listener);
    }

    private void setLongClickListener(@Nullable View target, View.OnLongClickListener listener) {
        if (target != null) target.setOnLongClickListener(listener);
    }

    private File getCurrentProfileDirectory() {
        String currentProfile = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        if(!Tools.isValidString(currentProfile)) return new File(Tools.DIR_GAME_NEW);
        LauncherProfiles.load();
        MinecraftProfile profileObject = LauncherProfiles.mainProfileJson.profiles.get(currentProfile);
        if(profileObject == null) return new File(Tools.DIR_GAME_NEW);
        return Tools.getGameDirPath(profileObject);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mVersionSpinner != null) {
            mVersionSpinner.reloadProfiles();
        }
    }

    private void runInstallerWithConfirmation(boolean isCustomArgs) {
        // avoid using custom installers to install a version
        if(Tools.isLocalProfile(requireContext()) || Tools.isDemoProfile(requireContext())){
            Toast.makeText(requireContext(), R.string.toast_not_available_demo, Toast.LENGTH_LONG).show();
            return;
        }

        if (ProgressKeeper.getTaskCount() == 0)
            Tools.installMod(requireActivity(), isCustomArgs);
        else
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}
