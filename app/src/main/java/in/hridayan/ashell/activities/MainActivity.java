package in.hridayan.ashell.activities;

import static in.hridayan.ashell.utils.Preferences.LOCAL_FRAGMENT;
import static in.hridayan.ashell.utils.Preferences.MODE_REMEMBER_LAST_MODE;
import static in.hridayan.ashell.utils.Preferences.OTG_FRAGMENT;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;
import in.hridayan.ashell.R;
import in.hridayan.ashell.UI.KeyboardUtils;
import in.hridayan.ashell.UI.MainViewModel;
import in.hridayan.ashell.adapters.SettingsAdapter;
import in.hridayan.ashell.fragments.StartFragment;
import in.hridayan.ashell.fragments.aShellFragment;
import in.hridayan.ashell.fragments.otgShellFragment;
import in.hridayan.ashell.utils.FetchLatestVersionCode;
import in.hridayan.ashell.utils.Preferences;
import in.hridayan.ashell.utils.SettingsItem;
import in.hridayan.ashell.utils.ThemeUtils;
import in.hridayan.ashell.utils.Utils;
import in.hridayan.ashell.utils.Utils.FetchLatestVersionCodeCallback;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity
    implements otgShellFragment.OnFragmentInteractionListener, FetchLatestVersionCodeCallback {
  private boolean isKeyboardVisible;
  public BottomNavigationView mNav;
  private SettingsAdapter adapter;
  private SettingsItem settingsList;
  private static int currentFragment;
  private boolean isBlackThemeEnabled, isAmoledTheme;
  private MainViewModel viewModel;
  private MaterialTextView changelog, version;
  private String buildGradleUrl =
      "https://raw.githubusercontent.com/DP-Hridayan/aShellYou/master/app/build.gradle";

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);

    // handle intent for "Use" feature
    if (intent.hasExtra("use_command")) {
      String useCommand = intent.getStringExtra("use_command");
      handleUseCommandIntent(useCommand, intent);
    }

    // handle intent for text shared to aShell You
    if (intent.hasExtra(Intent.EXTRA_TEXT)) {
      String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
      if (sharedText != null) {
        sharedText = sharedText.trim().replaceAll("^\"|\"$", "");
        handleSharedTextIntent(sharedText, intent);
      }
    }

    // handle intent when usb is disconnected
    if (intent != null && "com.example.ACTION_USB_DETACHED".equals(intent.getAction())) {
      onUsbDetached();
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    setCurrentFragment();
    viewModel.setCurrentFragment(currentFragment);
  }

  @Override
  protected void onResume() {
    super.onResume();
    // Amoled theme
    isAmoledTheme = Preferences.getAmoledTheme(this);
    boolean currentTheme = isAmoledTheme;
    if (currentTheme != isBlackThemeEnabled) {
      recreate();
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    EdgeToEdge.enable(this);
    ThemeUtils.updateTheme(this);

    List<SettingsItem> settingsList = new ArrayList<>();
    adapter = new SettingsAdapter(settingsList, this);

    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    viewModel = new ViewModelProvider(this).get(MainViewModel.class);
    isAmoledTheme = Preferences.getAmoledTheme(this);

    mNav = findViewById(R.id.bottom_nav_bar);

    // Hide the navigation bar when the keyboard is visible
    KeyboardUtils.attachVisibilityListener(
        this,
        new KeyboardUtils.KeyboardVisibilityListener() {
          @Override
          public void onKeyboardVisibilityChanged(boolean visible) {
            isKeyboardVisible = visible;
            if (isKeyboardVisible) {
              mNav.setVisibility(View.GONE);
            } else {
              new Handler(Looper.getMainLooper())
                  .postDelayed(
                      () -> {
                        mNav.setVisibility(View.VISIBLE);
                      },
                      100);
            }
          }
        });

    setupNavigation();

    // Show What's new bottom sheet on opening the app after an update
    if (Utils.isAppUpdated(this)) {
      showBottomSheetChangelog();
    }
    Preferences.setSavedVersionCode(this, Utils.currentVersion());

    isBlackThemeEnabled = isAmoledTheme;

    // Displaying badges on navigation bar
    setBadge(R.id.nav_wireless, "Soon");

    if (Preferences.getAutoUpdateCheck(this)) {
      new FetchLatestVersionCode(this, this).execute(buildGradleUrl);
    }
  }

  // Intent to get the text shared to aShell You app
  private void handleSharedTextIntent(String sharedText, Intent intent) {
    setTextOnEditText(sharedText, intent);
  }

  // Intent to get the text when we use the "Use" feature in command examples
  private void handleUseCommandIntent(String useCommand, Intent intent) {
    setTextOnEditText(useCommand, intent);
  }

  // Set the text in the Input Field
  private void setTextOnEditText(String text, Intent intent) {
    int currentFragment = Preferences.getCurrentFragment(this);

    switch (currentFragment) {
      case LOCAL_FRAGMENT:
        aShellFragment fragmentLocalAdb =
            (aShellFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragmentLocalAdb != null) {
          if (intent.hasExtra(Intent.EXTRA_TEXT)) {
            fragmentLocalAdb.handleSharedTextIntent(getIntent(), text);
          } else {
            fragmentLocalAdb.updateInputField(text);
          }
        }
        break;

      case OTG_FRAGMENT:
        otgShellFragment fragmentOtg =
            (otgShellFragment)
                getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragmentOtg != null) {
          fragmentOtg.updateInputField(text);
        }
        break;

      default:
        break;
    }
  }

  // Main navigation setup
  private void setupNavigation() {
    mNav.setVisibility(View.VISIBLE);
    mNav.setOnItemSelectedListener(
        item -> {
          switch (item.getItemId()) {
            case R.id.nav_localShell:
              showaShellFragment();
              Preferences.setCurrentFragment(this, LOCAL_FRAGMENT);
              return true;

            case R.id.nav_otgShell:
              showotgShellFragment();
              Preferences.setCurrentFragment(this, OTG_FRAGMENT);
              return true;

            default:
              return false;
          }
        });

    initialFragment();
  }

  // Takes the fragment we want to navigate to as argument and then starts that fragment
  public void replaceFragment(Fragment fragment) {
    if (!getSupportFragmentManager().isStateSaved()) {
      setCurrentFragment();
      getSupportFragmentManager()
          .beginTransaction()
          .replace(R.id.fragment_container, fragment)
          .commit();
    }
  }

  // If not on OtgShell then go to OtgShell
  private void showotgShellFragment() {
    if (!(getSupportFragmentManager().findFragmentById(R.id.fragment_container)
        instanceof otgShellFragment)) {
      /* Don't show again logic
      if (PreferenceManager.getDefaultSharedPreferences(this)
          .getBoolean("Don't show beta otg warning", true)) {
        showBetaWarning();
      } else { */
      currentFragment = OTG_FRAGMENT;
      replaceFragment(new otgShellFragment());
      /*   } */
    }
  }

  // If not on LocalShell then go to LocalShell (aShellFragment)
  private void showaShellFragment() {
    if (!(getSupportFragmentManager().findFragmentById(R.id.fragment_container)
        instanceof aShellFragment)) {
      currentFragment = LOCAL_FRAGMENT;
      replaceFragment(new aShellFragment());
    }
  }

  // Experimental feature warning for OtgShell
  private void showBetaWarning() {
    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
    builder
        .setCancelable(false)
        .setTitle(getString(R.string.warning))
        .setMessage(getString(R.string.beta_warning))
        .setPositiveButton(
            getString(R.string.accept),
            (dialogInterface, i) -> {
              replaceFragment(new otgShellFragment());
            })
        .setNegativeButton(
            getString(R.string.go_back),
            (dialogInterface, i) -> {
              mNav.setSelectedItemId(R.id.nav_localShell);
            })
        .setNeutralButton(
            getString(R.string.donot_show_again),
            (dialogInterface, i) -> {
              PreferenceManager.getDefaultSharedPreferences(this)
                  .edit()
                  .putBoolean("Don't show beta otg warning", false)
                  .apply();
              replaceFragment(new otgShellFragment());
            })
        .show();
  }

  // Function to set a Badge on the Navigation Bar
  private void setBadge(int id, String text) {
    BadgeDrawable badge = mNav.getOrCreateBadge(id);
    badge.setVisible(true);
    badge.setText(text);
    badge.setHorizontalOffset(0);
  }

  // Since there is option to set which working mode you want to display when app is launched , this
  // piece of code handles the logic for initial fragment
  private void initialFragment() {
    if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("firstLaunch", true)) {
      mNav.setVisibility(View.GONE);
      replaceFragment(new StartFragment());
    } else {
      boolean isFragmentSaved = viewModel.isFragmentSaved();
      if (isFragmentSaved) {
        int currentFragment = viewModel.currentFragment();
        switchFragments(currentFragment);
      } else {
        int currentFragment = Preferences.getCurrentFragment(this);
        int workingMode = Preferences.getWorkingMode(this);
        switchFragments(workingMode == MODE_REMEMBER_LAST_MODE ? currentFragment : workingMode + 1);
      }
    }
  }

  private void setCurrentFragment() {
    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
    if (fragment instanceof aShellFragment) {
      currentFragment = LOCAL_FRAGMENT;
    } else if (fragment instanceof otgShellFragment) {
      currentFragment = OTG_FRAGMENT;
    }
  }

  private void switchFragments(int currentFragment) {
    switch (currentFragment) {
      case LOCAL_FRAGMENT:
        mNav.setSelectedItemId(R.id.nav_localShell);
        replaceFragment(new aShellFragment());
        break;
      case OTG_FRAGMENT:
        mNav.setSelectedItemId(R.id.nav_otgShell);
        replaceFragment(new otgShellFragment());
        break;
      default:
        break;
    }
  }

  private void showBottomSheetChangelog() {
    BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
    View bottomSheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_changelog, null);
    bottomSheetDialog.setContentView(bottomSheetView);
    bottomSheetDialog.show();

    version = bottomSheetView.findViewById(R.id.version);
    changelog = bottomSheetView.findViewById(R.id.changelog);
    version.setText(Utils.getAppVersionName(this));

    String versionName = Utils.getAppVersionName(this);
    changelog.setText(Utils.loadChangelogText(versionName, this));
  }

  private void showBottomSheetUpdate() {
    BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
    View bottomSheetView =
        LayoutInflater.from(this).inflate(R.layout.bottom_sheet_update_checker, null);
    bottomSheetDialog.setContentView(bottomSheetView);
    bottomSheetDialog.show();

    MaterialButton downloadButton = bottomSheetView.findViewById(R.id.download_button);
    MaterialButton cancelButton = bottomSheetView.findViewById(R.id.cancel_button);

    downloadButton.setOnClickListener(
        v -> {
          Utils.openUrl(this, "https://github.com/DP-Hridayan/aShellYou/releases/latest");
        });
    cancelButton.setOnClickListener(
        v -> {
          bottomSheetDialog.dismiss();
        });
  }

  // Execute functions when the Usb connection is removed
  public void onUsbDetached() {
    // Reset the OtgShellFragment in this case
    onRequestReset();
  }

  // Reset the OtgFragment
  @Override
  public void onRequestReset() {
    if ((getSupportFragmentManager().findFragmentById(R.id.fragment_container)
        instanceof otgShellFragment)) {
      currentFragment = OTG_FRAGMENT;
      mNav.setSelectedItemId(R.id.nav_otgShell);
      replaceFragment(new otgShellFragment());
    }
  }

  // This funtion is run to perform actions if there is an update available or not
  @Override
  public void onResult(int result) {
    if (result == Preferences.UPDATE_AVAILABLE) {
      showBottomSheetUpdate();
    }
  }
}
