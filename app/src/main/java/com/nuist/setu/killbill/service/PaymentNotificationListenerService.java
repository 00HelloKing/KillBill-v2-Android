package com.nuist.setu.killbill.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.nuist.setu.killbill.R;
import com.nuist.setu.killbill.ui.AddEditBillActivity;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Listens for payment notifications (e.g., Alipay / WeChat).
 *
 * Instead of directly starting an Activity in background (restricted on modern Android),
 * we post our own "Tap to record" notification with a PendingIntent.
 */
public class PaymentNotificationListenerService extends NotificationListenerService {

    private static final String CHANNEL_ID = "killbill_auto_capture";

    // Popular package names
    private static final String PKG_ALIPAY = "com.eg.android.AlipayGphone";
    private static final String PKG_WECHAT = "com.tencent.mm";

    private static String lastKey = null;
    private static long lastTimeMs = 0L;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannelIfNeeded();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        String pkg = sbn.getPackageName();
        if (!PKG_ALIPAY.equals(pkg) && !PKG_WECHAT.equals(pkg)) {
            return;
        }

        Notification n = sbn.getNotification();
        if (n == null) return;

        Bundle extras = n.extras;
        if (extras == null) return;

        String title = safeToString(extras.getCharSequence(Notification.EXTRA_TITLE));
        String text = safeToString(extras.getCharSequence(Notification.EXTRA_TEXT));
        String bigText = safeToString(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));

        String content = (title + " " + text + " " + bigText).trim();
        if (TextUtils.isEmpty(content)) return;

        ParseResult result = parsePayment(content);
        if (result == null) return;

        String payApp = PKG_ALIPAY.equals(pkg) ? "支付宝" : "微信";
        String note = result.note;
        if (TextUtils.isEmpty(note)) {
            note = payApp + "自动识别";
        }

        // de-duplicate within a short window
        String key = pkg + "|" + result.amount + "|" + note;
        long now = System.currentTimeMillis();
        if (key.equals(lastKey) && (now - lastTimeMs) < 8000) {
            return;
        }
        lastKey = key;
        lastTimeMs = now;

        postAutoCaptureNotification(result.amount, note, payApp);
    }

    private void postAutoCaptureNotification(double amount, String note, String payApp) {
        Intent intent = new Intent(this, AddEditBillActivity.class);
        intent.putExtra(AddEditBillActivity.EXTRA_PREFILL_AMOUNT, amount);
        intent.putExtra(AddEditBillActivity.EXTRA_PREFILL_NOTE, note);
        intent.putExtra(AddEditBillActivity.EXTRA_SOURCE, "AUTO");
        intent.putExtra(AddEditBillActivity.EXTRA_PAYMENT_APP, payApp);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        int requestCode = (int) (System.currentTimeMillis() & 0xfffffff);
        PendingIntent pi = PendingIntent.getActivity(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = getString(R.string.detected_payment_title);
        String contentText = String.format(Locale.CHINA, "%s %s，%s",
                payApp,
                String.format(Locale.CHINA, "￥%.2f", amount),
                getString(R.string.tap_to_record));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText + "\n" + note))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH);


        NotificationManagerCompat nm = NotificationManagerCompat.from(this);
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    // no permission
                    return;
                }
            }
            nm.notify(requestCode, builder.build());
        } catch (SecurityException e) {
            e.printStackTrace();
        }

    }

    private void createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_auto_capture),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(getString(R.string.channel_auto_capture_desc));
        nm.createNotificationChannel(channel);
    }

    private static String safeToString(CharSequence cs) {
        return cs == null ? "" : cs.toString();
    }

    /**
     * A simple heuristic parser that tries to detect a "payment amount" from notification text.
     *
     * It intentionally avoids matching arbitrary numbers:
     * - Prefer explicit "￥/¥"
     * - Or a number followed by "元"
     * - Require payment-related keywords
     */
    private static ParseResult parsePayment(String content) {
        String s = content.replace("\u00A0", " ").trim();

        // Must contain at least one payment keyword
        // This avoids matching order IDs etc.
        boolean hasKeyword = containsAny(s, "支付", "付款", "消费", "扣款", "支出", "已付款", "支付成功");
        if (!hasKeyword) {
            return null;
        }

        Double amount = extractAmount(s);
        if (amount == null) return null;

        if (amount <= 0 || amount > 100000) return null;

        // note: we keep a shortened context (optional)
        String note = s;
        if (note.length() > 60) {
            note = note.substring(0, 60) + "...";
        }

        return new ParseResult(amount, note);
    }

    private static boolean containsAny(String s, String... keys) {
        for (String k : keys) {
            if (s.contains(k)) return true;
        }
        return false;
    }

    private static Double extractAmount(String s) {
        // 1) Prefer ￥/¥
        Pattern p1 = Pattern.compile("(?:￥|¥)\\s*([0-9]+(?:\\.[0-9]{1,2})?)");
        Matcher m1 = p1.matcher(s);
        if (m1.find()) {
            return safeParseDouble(m1.group(1));
        }

        // 2) number + 元
        Pattern p2 = Pattern.compile("([0-9]+(?:\\.[0-9]{1,2})?)\\s*元");
        Matcher m2 = p2.matcher(s);
        if (m2.find()) {
            return safeParseDouble(m2.group(1));
        }

        return null;
    }

    private static Double safeParseDouble(String num) {
        try {
            return Double.parseDouble(num);
        } catch (Exception e) {
            return null;
        }
    }

    private static final class ParseResult {
        final double amount;
        final String note;

        ParseResult(double amount, String note) {
            this.amount = amount;
            this.note = note;
        }
    }
}
