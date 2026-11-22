package com.cannon.onyxlauncher;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.drawerlayout.widget.DrawerLayout;

import com.cannon.onyxlauncher.customcontrols.ControlData;
import com.cannon.onyxlauncher.customcontrols.ControlDrawerData;
import com.cannon.onyxlauncher.customcontrols.ControlJoystickData;
import com.cannon.onyxlauncher.customcontrols.ControlLayout;
import com.cannon.onyxlauncher.customcontrols.EditorExitable;
import com.cannon.onyxlauncher.prefs.LauncherPreferences;

import java.io.IOException;


public class CustomControlsActivity extends BaseActivity implements EditorExitable {
	public static final String EXTRA_BASE_LAYOUT_PATH = "extra_base_layout_path";
	public static final String EXTRA_IS_BUILTIN_BASE = "extra_is_builtin_base";

	private DrawerLayout mDrawerLayout;
	private ListView mDrawerNavigationView;
	private ControlLayout mControlLayout;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_custom_controls);

		mControlLayout = findViewById(R.id.customctrl_controllayout);
		mDrawerLayout = findViewById(R.id.customctrl_drawerlayout);
		mDrawerNavigationView = findViewById(R.id.customctrl_navigation_view);
		View mPullDrawerButton = findViewById(R.id.drawer_button);

		mPullDrawerButton.setOnClickListener(v -> mDrawerLayout.openDrawer(mDrawerNavigationView));
		mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

		mDrawerNavigationView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,getResources().getStringArray(R.array.menu_customcontrol_customactivity)));
		mDrawerNavigationView.setOnItemClickListener((parent, view, position, id) -> {
			switch(position) {
				case 0: mControlLayout.addControlButton(new ControlData("New")); break;
				case 1: mControlLayout.addDrawer(new ControlDrawerData()); break;
				case 2: mControlLayout.addJoystickButton(new ControlJoystickData()); break;
				case 3: mControlLayout.openLoadDialog(); break;
				case 4: mControlLayout.openSaveDialog(this); break;
				case 5: mControlLayout.openSetDefaultDialog(); break;
				case 6: // Saving the currently shown control
					try {
						Uri contentUri = DocumentsContract.buildDocumentUri(getString(R.string.storageProviderAuthorities), mControlLayout.saveToDirectory(mControlLayout.mLayoutFileName));

						Intent shareIntent = new Intent();
						shareIntent.setAction(Intent.ACTION_SEND);
						shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
						shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
						shareIntent.setType("application/json");
						startActivity(shareIntent);

						Intent sendIntent = Intent.createChooser(shareIntent, mControlLayout.mLayoutFileName);
						startActivity(sendIntent);
					}catch (Exception e) {
						Tools.showError(this, e);
					}
					break;
			}
			mDrawerLayout.closeDrawers();
		});
		mControlLayout.setModifiable(true);
		String baseLayoutPath = getIntent() != null ? getIntent().getStringExtra(EXTRA_BASE_LAYOUT_PATH) : null;
		final boolean isBuiltInBase = getIntent() != null && getIntent().getBooleanExtra(EXTRA_IS_BUILTIN_BASE, false);
		final String pathToLoad = (baseLayoutPath != null && !baseLayoutPath.isEmpty())
				? baseLayoutPath
				: LauncherPreferences.PREF_DEFAULTCTRL_PATH;
		try {
			mControlLayout.loadLayout(pathToLoad);
			if (isBuiltInBase || ControlLayout.isBuiltInLayoutName(mControlLayout.mLayoutFileName)) {
				mControlLayout.mLayoutFileName = "";
			}
		}catch (IOException e) {
			Tools.showError(this, e);
		}
		LauncherPreferences.computeNotchSize(this);
		mControlLayout.post(() -> {
			Tools.getDisplayMetrics(this);
			if (mControlLayout.getWidth() > 0 && mControlLayout.getHeight() > 0) {
				org.lwjgl.glfw.CallbackBridge.physicalWidth = mControlLayout.getWidth();
				org.lwjgl.glfw.CallbackBridge.physicalHeight = mControlLayout.getHeight();
			}
			mControlLayout.refreshControlButtonPositions();
		});
	}

	@Override
	public void onBackPressed() {
		mControlLayout.askToExit(this);
	}

	@Override
	public void exitEditor() {
		super.onBackPressed();
	}
}
