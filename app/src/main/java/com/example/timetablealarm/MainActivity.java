package com.example.timetablealarm;

import android.Manifest;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private final List<ClassEntry> entries = new ArrayList<>();
    private ClassAdapter adapter;
    private TextView status, warning, empty, progressText;
    private View progress;
    private MaterialSwitch masterSwitch;
    private Uri photoUri;

    private ActivityResultLauncher<Uri> takePicture;
    private ActivityResultLauncher<PickVisualMediaRequest> pickImage;
    private ActivityResultLauncher<String> notifPermission;
    private ActivityResultLauncher<Intent> review;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AlarmService.ensureChannel(this);

        status = findViewById(R.id.statusText);
        warning = findViewById(R.id.warningText);
        empty = findViewById(R.id.emptyText);
        progress = findViewById(R.id.progress);
        progressText = findViewById(R.id.progressText);
        masterSwitch = findViewById(R.id.masterSwitch);

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ClassAdapter(entries, false, new ClassAdapter.Listener() {
            @Override public void onClick(int pos) {
                if (pos < 0) return;
                Dialogs.editClass(MainActivity.this, entries.get(pos), saved -> {
                    entries.set(pos, saved);
                    persist();
                });
            }
            @Override public void onDelete(int pos) {
                if (pos < 0) return;
                ClassEntry e = entries.get(pos);
                new MaterialAlertDialogBuilder(MainActivity.this)
                        .setMessage("'" + e.subject + "' (" + e.dayName() + " " + ClassEntry.fmt(e.startMin) + ") 을(를) 삭제할까요?")
                        .setPositiveButton("삭제", (d, w) -> { entries.remove(e); persist(); })
                        .setNegativeButton("취소", null).show();
            }
            @Override public void onToggle(int pos, boolean enabled) {
                if (pos < 0) return;
                entries.get(pos).enabled = enabled;
                persist();
            }
        });
        list.setAdapter(adapter);

        takePicture = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
            if (Boolean.TRUE.equals(ok) && photoUri != null) runOcr(photoUri);
        });
        pickImage = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) runOcr(uri);
        });
        notifPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), g -> refresh());
        review = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
            if (r.getResultCode() == RESULT_OK) {
                int n = AlarmScheduler.rescheduleAll(this);
                Toast.makeText(this, "시간표 저장 완료 · 알람 " + n + "개 예약", Toast.LENGTH_SHORT).show();
            }
            refresh();
        });

        Button camera = findViewById(R.id.btnCamera);
        camera.setOnClickListener(v -> openCamera());
        ((Button) findViewById(R.id.btnGallery)).setOnClickListener(v -> pickImage.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        ((Button) findViewById(R.id.btnAdd)).setOnClickListener(v ->
                Dialogs.editClass(this, null, saved -> { entries.add(saved); persist(); }));
        ((Button) findViewById(R.id.btnSettings)).setOnClickListener(v ->
                Dialogs.settings(this, () -> { AlarmScheduler.rescheduleAll(this); refresh(); }));
        masterSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (checked == Storage.alarmsEnabled(this)) return;
            Storage.setAlarmsEnabled(this, checked);
            AlarmScheduler.rescheduleAll(this);
            refresh();
        });
        warning.setOnClickListener(v -> fixPermission());

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        AlarmScheduler.rescheduleAll(this); // 권한 변경 등 반영
        refresh();
    }

    private void openCamera() {
        try {
            File dir = new File(getCacheDir(), "images");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File f = new File(dir, "timetable_" + System.currentTimeMillis() + ".jpg");
            photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            takePicture.launch(photoUri);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "카메라 앱을 찾을 수 없습니다. 갤러리에서 선택해 주세요.", Toast.LENGTH_LONG).show();
        }
    }

    private void runOcr(Uri uri) {
        progress.setVisibility(View.VISIBLE);
        progressText.setText("시간표를 읽는 중…");
        OcrHelper.recognize(this, uri, new OcrHelper.Callback() {
            @Override public void onResult(TimetableParser.Result result, String rawText) {
                progress.setVisibility(View.GONE);
                Intent i = new Intent(MainActivity.this, ReviewActivity.class)
                        .putExtra(ReviewActivity.EXTRA_ENTRIES, Storage.toJson(result.entries))
                        .putExtra(ReviewActivity.EXTRA_MESSAGE, result.message)
                        .putExtra(ReviewActivity.EXTRA_RAW, rawText);
                review.launch(i);
            }
            @Override public void onError(String message) {
                progress.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void persist() {
        Storage.saveEntries(this, entries);
        AlarmScheduler.rescheduleAll(this);
        refresh();
    }

    private void refresh() {
        entries.clear();
        entries.addAll(Storage.loadEntries(this));
        entries.sort(Comparator.comparingInt((ClassEntry e) -> e.dayOfWeek).thenComparingInt(e -> e.startMin));
        adapter.notifyDataSetChanged();
        empty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);

        boolean on = Storage.alarmsEnabled(this);
        masterSwitch.setChecked(on);
        int before = Storage.minutesBefore(this);
        String timing = before == 0 ? "수업 시작 정각" : "수업 " + before + "분 전";
        if (!on) {
            status.setText("알람 꺼짐");
        } else {
            ClassEntry next = null;
            ZonedDateTime nextT = null, now = ZonedDateTime.now();
            int count = 0;
            for (ClassEntry e : entries) {
                if (!e.enabled) continue;
                count++;
                ZonedDateTime t = AlarmTime.next(e.dayOfWeek, e.startMin, before, now);
                if (nextT == null || t.isBefore(nextT)) { nextT = t; next = e; }
            }
            String s = "알람 " + count + "개 · " + timing;
            if (next != null) {
                s += "\n다음 알람: " + nextT.getMonthValue() + "/" + nextT.getDayOfMonth() + " ("
                        + ClassEntry.DAY_NAMES[nextT.getDayOfWeek().getValue() - 1] + ") "
                        + ClassEntry.fmt(nextT.getHour() * 60 + nextT.getMinute()) + " · " + next.subject;
            }
            status.setText(s);
        }
        String w = permissionProblem();
        warning.setVisibility(w == null ? View.GONE : View.VISIBLE);
        if (w != null) warning.setText("⚠ " + w + " (눌러서 설정)");
    }

    /** 알람이 제대로 울리기 위해 필요한 권한 중 빠진 것. */
    private String permissionProblem() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (!nm.areNotificationsEnabled()) return "알림이 꺼져 있어 알람이 표시되지 않습니다";
        if (!AlarmScheduler.canExact(this)) return "'알람 및 리마인더' 권한이 없어 알람이 늦게 울릴 수 있습니다";
        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent())
            return "잠금화면 전체화면 알람 권한이 꺼져 있습니다";
        return null;
    }

    private void fixPermission() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        Intent i;
        if (!nm.areNotificationsEnabled()) {
            if (Build.VERSION.SDK_INT >= 33 && shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
            i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else if (!AlarmScheduler.canExact(this) && Build.VERSION.SDK_INT >= 31) {
            i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()));
        } else if (Build.VERSION.SDK_INT >= 34) {
            i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName()));
        } else {
            return;
        }
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        }
    }
}
