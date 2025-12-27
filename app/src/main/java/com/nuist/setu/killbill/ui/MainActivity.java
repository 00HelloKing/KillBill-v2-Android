package com.nuist.setu.killbill.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.nuist.setu.killbill.R;
import com.nuist.setu.killbill.data.BillRepository;
import com.nuist.setu.killbill.databinding.ActivityMainBinding;
import com.nuist.setu.killbill.ui.fragment.AllBillsFragment;
import com.nuist.setu.killbill.ui.fragment.DailyFragment;
import com.nuist.setu.killbill.ui.fragment.StatsFragment;
import com.nuist.setu.killbill.util.CsvExporter;
import com.nuist.setu.killbill.util.NotificationAccessUtils;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private final DailyFragment dailyFragment = new DailyFragment();
    private final StatsFragment statsFragment = new StatsFragment();
    private final AllBillsFragment allBillsFragment = new AllBillsFragment();

    private ActivityResultLauncher<String> requestPostNotificationPermission;

    // 防止每次onResume都提醒
    private static final String PREFS = "main_prefs";
    private static final String KEY_NL_PROMPTED = "notification_listener_prompted_once";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        // 注册 Android 13+ 通知权限请求
        requestPostNotificationPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (!granted) {
                        handlePostNotificationDenied();
                    }
                }
        );

        // 启动页主动申请（Android 13+）
        maybeRequestPostNotifications();

        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_daily) {
                switchTo(dailyFragment);
                return true;
            } else if (id == R.id.nav_stats) {
                switchTo(statsFragment);
                return true;
            } else if (id == R.id.nav_all) {
                switchTo(allBillsFragment);
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            binding.bottomNav.setSelectedItemId(R.id.nav_daily);
            switchTo(dailyFragment);
        }

        // 首次进入时，检查一次通知监听权限
        maybePromptNotificationAccessOnce();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void switchTo(@NonNull androidx.fragment.app.Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    /**
     * Android 13+ POST_NOTIFICATIONS 权限
     */
    private void maybeRequestPostNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // 如果用户之前拒绝过，给解释再弹系统权限框
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
            new AlertDialog.Builder(this)
                    .setTitle("Notification permission")
                    .setMessage("Enabled notifications let us send alerts during auto-accounting/reminders.")
                    .setPositiveButton("Go to authorize", (d, w) ->
                            requestPostNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS))
                    .setNegativeButton("Not yet", null)
                    .show();
        } else {
            requestPostNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    /**
     * 处理 POST_NOTIFICATIONS 被拒绝的情况：
     * - 普通拒绝：提示可以在设置里开启
     * - 拒绝并不再询问：引导跳应用设置页
     */
    private void handlePostNotificationDenied() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;

        boolean showRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.POST_NOTIFICATIONS);

        if (!showRationale) {
            // “拒绝并不再询问”，或策略限制 -> 直接引导设置
            Snackbar.make(binding.getRoot(), "Notification permission disabled," +
                            "please enabled in system settings.", Snackbar.LENGTH_LONG)
                    .setAction("Go to set", v -> openAppSettings())
                    .show();
        } else {
            // 普通拒绝
            Snackbar.make(binding.getRoot(), "Notification permission denied, " +
                            "may affect display.", Snackbar.LENGTH_SHORT)
                    .show();
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    /**
     * 通知监听权限（Notification Listener），只提示一次，避免每次回到前台都弹。
     */
    private void maybePromptNotificationAccessOnce() {
        if (NotificationAccessUtils.isNotificationListenerEnabled(this)) {
            return;
        }
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean prompted = sp.getBoolean(KEY_NL_PROMPTED, false);
        if (prompted) return;

        sp.edit().putBoolean(KEY_NL_PROMPTED, true).apply();

        // 先用 Snackbar 温和提示，用户点了再弹对话框
        Snackbar.make(binding.getRoot(), R.string.notification_permission_title, Snackbar.LENGTH_LONG)
                .setAction(R.string.open_settings, v -> showNotificationAccessDialog())
                .show();
    }

    private void showNotificationAccessDialog() {
        if (NotificationAccessUtils.isNotificationListenerEnabled(this)) return;

        new AlertDialog.Builder(this)
                .setTitle(R.string.notification_permission_title)
                .setMessage(R.string.notification_permission_msg)
                .setPositiveButton(R.string.open_settings, (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
        if (item.getItemId() == R.id.action_export) {
            exportCsv();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void exportCsv() {
        BillRepository.getInstance(this).getAllBillsOnce(bills -> {
            if (bills == null || bills.isEmpty()) {
                Snackbar.make(binding.getRoot(), "No data for exporting", Snackbar.LENGTH_SHORT).show();
                return;
            }
            CsvExporter.exportAndShare(this, bills);
        });
    }
}
