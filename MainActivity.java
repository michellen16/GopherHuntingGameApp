package edu.uic.cs478.s2026.project4;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String GAME_PERMISSION =
            "com.gopherhunt.game.permission.GAME_PLAYER_2024_UNIQUE";
    private static final int REQ_CODE = 1001;
    private static final String PREFS_NAME = "GopherHuntPrefs";
    private static final String KEY_PERMISSION_GRANTED = "game_permission_granted";

    private Button btnStart;
    private TextView tvStatus;
    private boolean hasPermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart = findViewById(R.id.btn_start_game);
        tvStatus = findViewById(R.id.tv_permission_status);

        btnStart.setOnClickListener(v -> {
            if (hasPermission) {
                launchGame();
            } else {
                attemptPermissionGrant();
            }
        });

        checkPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermission();
    }

    /**
     * Check permission through multiple methods:
     * 1. System checkSelfPermission (works if OS auto-granted)
     * 2. PackageManager check
     * 3. Our own SharedPreferences flag (fallback for custom permissions)
     */
    private void checkPermission() {
        boolean systemGranted = ContextCompat.checkSelfPermission(this, GAME_PERMISSION)
                == PackageManager.PERMISSION_GRANTED;

        boolean packageGranted = getPackageManager().checkPermission(
                GAME_PERMISSION, getPackageName()) == PackageManager.PERMISSION_GRANTED;

        boolean userGranted = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_PERMISSION_GRANTED, false);

        hasPermission = systemGranted || packageGranted || userGranted;

        if (hasPermission) {
            tvStatus.setText("Permission: Granted");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.success_color));
            btnStart.setEnabled(true);
            btnStart.setText("Start Game");
        } else {
            tvStatus.setText("Permission: Required");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.near_color));
            btnStart.setEnabled(true);
            btnStart.setText("Grant Permission and Start");
        }
    }

    /**
     * Try system permission request first.
     * If that fails (custom permissions often do), show our own dialog.
     */
    private void attemptPermissionGrant() {
        // First try the standard Android permission request
        ActivityCompat.requestPermissions(this,
                new String[]{GAME_PERMISSION}, REQ_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // System granted it
                onPermissionGranted();
            } else {
                // System denied it — this is expected for custom dangerous permissions
                // Show our own permission dialog as fallback
                showCustomPermissionDialog();
            }
        }
    }

    /**
     * Custom dialog that acts as our permission gate.
     * Since Android won't show a system dialog for app-defined
     * dangerous permissions, we present our own.
     */
    private void showCustomPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Game Player Permission Required")
                .setMessage(
                        "The Gopher Hunt game requires the GAME_PLAYER permission "
                                + "to start playing.\n\n"
                                + "This is a custom permission defined by this app to "
                                + "control access to the game.\n\n"
                                + "Do you want to grant this permission?")
                .setPositiveButton("Grant", (dialog, which) -> {
                    onPermissionGranted();
                })
                .setNegativeButton("Deny", (dialog, which) -> {
                    tvStatus.setText("Permission: Denied");
                    tvStatus.setTextColor(
                            ContextCompat.getColor(this, R.color.stop_color));
                })
                .setCancelable(false)
                .show();
    }

    private void onPermissionGranted() {
        // Save grant state
        SharedPreferences.Editor editor =
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putBoolean(KEY_PERMISSION_GRANTED, true);
        editor.apply();

        hasPermission = true;
        tvStatus.setText("Permission: Granted");
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.success_color));
        btnStart.setEnabled(true);
        btnStart.setText("Start Game");

        launchGame();
    }

    private void launchGame() {
        startActivity(new Intent(this, GameActivity.class));
    }
}
