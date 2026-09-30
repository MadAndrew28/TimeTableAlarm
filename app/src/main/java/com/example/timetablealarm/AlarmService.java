package com.example.timetablealarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

/** 알람 소리(반복)·진동을 재생하는 포그라운드 서비스. 1분 후 자동 종료. */
public class AlarmService extends Service {
    public static final String EXTRA_SUBJECT = "subject";
    public static final String EXTRA_INFO = "info";
    public static final String ACTION_STOP = "com.example.timetablealarm.STOP";
    public static final String CHANNEL_ID = "class_alarm";
    private static final int NOTIF_ID = 1001;
    private static final long AUTO_STOP_MS = 60_000;

    private MediaPlayer player;
    private Vibrator vibrator;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoStop = () -> stopSelf();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String subject = intent != null ? intent.getStringExtra(EXTRA_SUBJECT) : null;
        String info = intent != null ? intent.getStringExtra(EXTRA_INFO) : null;
        if (subject == null) subject = "수업 알람";
        if (info == null) info = "";

        ensureChannel(this);

        Intent full = new Intent(this, AlarmActivity.class)
                .putExtra(EXTRA_SUBJECT, subject)
                .putExtra(EXTRA_INFO, info)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent fullPi = PendingIntent.getActivity(this, 1, full,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stopPi = PendingIntent.getService(this, 2,
                new Intent(this, AlarmService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle("⏰ " + subject)
                .setContentText(info)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullPi, true)
                .setContentIntent(fullPi)
                .setOngoing(true)
                .setAutoCancel(false)
                .addAction(0, "알람 끄기", stopPi)
                .build();

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0;
        ServiceCompat.startForeground(this, NOTIF_ID, n, type);

        startRinging();
        handler.removeCallbacks(autoStop);
        handler.postDelayed(autoStop, AUTO_STOP_MS);
        return START_NOT_STICKY;
    }

    public static void ensureChannel(android.content.Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "수업 알람", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("수업 시작 전 울리는 알람");
        ch.setSound(null, null);          // 소리는 서비스에서 직접 반복 재생
        ch.enableVibration(false);        // 진동도 서비스에서 처리
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.setBypassDnd(true);
        nm.createNotificationChannel(ch);
    }

    private void startRinging() {
        stopRinging();
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception e) {
            player = null;
        }
        if (Storage.vibrate(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = getSystemService(VibratorManager.class);
                vibrator = vm != null ? vm.getDefaultVibrator() : null;
            } else {
                vibrator = getSystemService(Vibrator.class);
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 800, 600};
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0),
                        new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
            }
        }
    }

    private void stopRinging() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            player.release();
            player = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(autoStop);
        stopRinging();
        sendBroadcast(new Intent(AlarmActivity.ACTION_FINISH).setPackage(getPackageName()));
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
