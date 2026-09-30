package com.example.timetablealarm;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/** 알람 시각에 호출됨 → 알람 서비스 시작 + 다음 주 같은 수업 알람 재예약. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean test = intent.getBooleanExtra(AlarmScheduler.EXTRA_TEST, false);
        String subject;
        String info;
        if (test) {
            subject = "테스트 알람";
            info = "알람이 정상적으로 울립니다";
        } else {
            long id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1);
            ClassEntry e = Storage.findEntry(context, id);
            if (e == null || !e.enabled || !Storage.alarmsEnabled(context)) return;
            AlarmScheduler.scheduleOne(context, e); // 다음 주 예약
            int before = Storage.minutesBefore(context);
            subject = e.subject;
            info = e.dayName() + " " + ClassEntry.fmt(e.startMin) + " 시작"
                    + (before > 0 ? " (" + before + "분 전)" : "")
                    + (e.memo == null || e.memo.isEmpty() ? "" : " · " + e.memo);
        }
        Intent s = new Intent(context, AlarmService.class)
                .putExtra(AlarmService.EXTRA_SUBJECT, subject)
                .putExtra(AlarmService.EXTRA_INFO, info);
        try {
            ContextCompat.startForegroundService(context, s);
        } catch (Exception ex) {
            // 정확한 알람 권한이 없어 백그라운드 서비스 시작이 막힌 경우: 일반 알림으로라도 알림
            fallbackNotification(context, subject, info);
        }
    }

    private static void fallbackNotification(Context c, String subject, String info) {
        AlarmService.ensureChannel(c);
        Intent full = new Intent(c, AlarmActivity.class)
                .putExtra(AlarmService.EXTRA_SUBJECT, subject)
                .putExtra(AlarmService.EXTRA_INFO, info)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 3, full,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(c, AlarmService.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle("⏰ " + subject)
                .setContentText(info)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        try {
            c.getSystemService(NotificationManager.class).notify(1002, n);
        } catch (SecurityException ignored) {
            // 알림 권한 없음
        }
    }
}
