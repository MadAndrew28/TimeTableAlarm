package com.example.timetablealarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 재부팅·앱 업데이트·시간/시간대 변경 시 알람은 사라지거나 어긋나므로 전부 다시 예약. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AlarmScheduler.rescheduleAll(context);
    }
}
