package com.example.timetablealarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/** 잠금화면 위로 뜨는 알람 화면. */
public class AlarmActivity extends AppCompatActivity {
    public static final String ACTION_FINISH = "com.example.timetablealarm.FINISH_ALARM_UI";

    private final BroadcastReceiver finisher = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) { finish(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_alarm);

        ((TextView) findViewById(R.id.alarmSubject)).setText(getIntent().getStringExtra(AlarmService.EXTRA_SUBJECT));
        ((TextView) findViewById(R.id.alarmInfo)).setText(getIntent().getStringExtra(AlarmService.EXTRA_INFO));
        Button stop = findViewById(R.id.alarmStop);
        stop.setOnClickListener(v -> stopAlarm());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() { stopAlarm(); }
        });

        ContextCompat.registerReceiver(this, finisher, new IntentFilter(ACTION_FINISH),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void stopAlarm() {
        stopService(new Intent(this, AlarmService.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(finisher);
        super.onDestroy();
    }
}
