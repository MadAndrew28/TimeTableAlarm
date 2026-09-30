package com.example.timetablealarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/** AlarmManager로 수업별 알람을 예약/취소한다. 알람이 울리면 AlarmReceiver가 다음 주 알람을 다시 예약한다. */
public class AlarmScheduler {
    public static final String EXTRA_ID = "entry_id";
    public static final String EXTRA_TEST = "is_test";
    private static final int TEST_REQUEST_CODE = 0x7FFF0000;

    public static boolean canExact(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms();
    }

    /** 기존 알람을 모두 취소하고 현재 시간표로 다시 예약. 예약된 개수 반환. */
    public static int rescheduleAll(Context c) {
        for (Long id : Storage.scheduledIds(c)) cancel(c, id);
        List<Long> scheduled = new ArrayList<>();
        if (Storage.alarmsEnabled(c)) {
            for (ClassEntry e : Storage.loadEntries(c)) {
                if (!e.enabled) continue;
                scheduleOne(c, e);
                scheduled.add(e.id);
            }
        }
        Storage.setScheduledIds(c, scheduled);
        return scheduled.size();
    }

    public static long scheduleOne(Context c, ClassEntry e) {
        ZonedDateTime t = AlarmTime.next(e.dayOfWeek, e.startMin, Storage.minutesBefore(c), ZonedDateTime.now());
        long when = t.toInstant().toEpochMilli();
        Intent i = new Intent(c, AlarmReceiver.class).putExtra(EXTRA_ID, e.id);
        PendingIntent pi = PendingIntent.getBroadcast(c, (int) e.id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        setAlarm(c, when, pi);
        return when;
    }

    /** 설정 화면의 "테스트 알람": N초 후 울림. */
    public static void scheduleTest(Context c, int seconds) {
        long when = System.currentTimeMillis() + seconds * 1000L;
        Intent i = new Intent(c, AlarmReceiver.class).putExtra(EXTRA_TEST, true);
        PendingIntent pi = PendingIntent.getBroadcast(c, TEST_REQUEST_CODE, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        setAlarm(c, when, pi);
    }

    private static void setAlarm(Context c, long when, PendingIntent pi) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (canExact(c)) {
            // setAlarmClock: 도즈 모드에서도 정확히 울리고, 상태바에 알람 아이콘 표시
            PendingIntent show = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(when, show), pi);
        } else {
            // 정확한 알람 권한이 없을 때: 몇 분 늦을 수 있음
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }

    public static void cancel(Context c, long id) {
        Intent i = new Intent(c, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, (int) id, i,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) {
            c.getSystemService(AlarmManager.class).cancel(pi);
            pi.cancel();
        }
    }
}
