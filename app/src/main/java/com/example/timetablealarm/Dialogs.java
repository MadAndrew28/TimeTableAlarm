package com.example.timetablealarm;

import android.app.TimePickerDialog;
import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

/** 수업 편집 / 설정 / 원문 보기 대화상자. */
public class Dialogs {

    public interface OnSaved { void onSaved(ClassEntry entry); }

    /** entry == null 이면 새 수업 추가. */
    public static void editClass(Context c, ClassEntry entry, OnSaved cb) {
        ClassEntry e = entry != null ? entry.copy() : new ClassEntry("", "", 1, 9 * 60, 9 * 60 + Storage.classLength(c));
        View v = LayoutInflater.from(c).inflate(R.layout.dialog_class, null);
        EditText subject = v.findViewById(R.id.editSubject);
        EditText memo = v.findViewById(R.id.editMemo);
        Spinner day = v.findViewById(R.id.editDay);
        Button start = v.findViewById(R.id.editStart);
        Button end = v.findViewById(R.id.editEnd);

        subject.setText(e.subject);
        memo.setText(e.memo);
        ArrayAdapter<String> days = new ArrayAdapter<>(c, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"});
        day.setAdapter(days);
        day.setSelection(Math.max(0, Math.min(6, e.dayOfWeek - 1)));
        start.setText("시작 " + ClassEntry.fmt(e.startMin));
        end.setText("종료 " + ClassEntry.fmt(e.endMin));

        start.setOnClickListener(x -> new TimePickerDialog(c, (tp, h, m) -> {
            int len = e.endMin - e.startMin;
            e.startMin = h * 60 + m;
            if (e.endMin <= e.startMin) e.endMin = e.startMin + Math.max(10, len);
            start.setText("시작 " + ClassEntry.fmt(e.startMin));
            end.setText("종료 " + ClassEntry.fmt(e.endMin));
        }, e.startMin / 60, e.startMin % 60, true).show());
        end.setOnClickListener(x -> new TimePickerDialog(c, (tp, h, m) -> {
            e.endMin = h * 60 + m;
            end.setText("종료 " + ClassEntry.fmt(e.endMin));
        }, e.endMin / 60, e.endMin % 60, true).show());

        AlertDialog d = new MaterialAlertDialogBuilder(c)
                .setTitle(entry == null ? "수업 추가" : "수업 수정")
                .setView(v)
                .setPositiveButton("저장", null)
                .setNegativeButton("취소", null)
                .create();
        d.setOnShowListener(di -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String s = subject.getText().toString().trim();
            if (s.isEmpty()) {
                subject.setError("과목명을 입력하세요");
                return;
            }
            if (e.endMin <= e.startMin) {
                Toast.makeText(c, "종료 시각이 시작보다 늦어야 합니다", Toast.LENGTH_SHORT).show();
                return;
            }
            e.subject = s;
            e.memo = memo.getText().toString().trim();
            e.dayOfWeek = day.getSelectedItemPosition() + 1;
            cb.onSaved(e);
            d.dismiss();
        }));
        d.show();
    }

    /** 알람/인식 설정. 저장하면 onChanged 호출. */
    public static void settings(Context c, Runnable onChanged) {
        View v = LayoutInflater.from(c).inflate(R.layout.dialog_settings, null);
        EditText before = v.findViewById(R.id.setBefore);
        EditText length = v.findViewById(R.id.setLength);
        EditText periods = v.findViewById(R.id.setPeriods);
        MaterialSwitch vib = v.findViewById(R.id.setVibrate);
        before.setText(String.valueOf(Storage.minutesBefore(c)));
        length.setText(String.valueOf(Storage.classLength(c)));
        periods.setText(Storage.periodStartsText(c));
        vib.setChecked(Storage.vibrate(c));

        AlertDialog d = new MaterialAlertDialogBuilder(c)
                .setTitle("설정")
                .setView(v)
                .setPositiveButton("저장", null)
                .setNegativeButton("취소", null)
                .setNeutralButton("테스트 알람", (di, w) -> {
                    AlarmScheduler.scheduleTest(c, 5);
                    Toast.makeText(c, "5초 후 알람이 울립니다. 화면을 꺼서 확인해 보세요.", Toast.LENGTH_LONG).show();
                })
                .create();
        d.setOnShowListener(di -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            int b, len;
            try {
                b = Integer.parseInt(before.getText().toString().trim());
                len = Integer.parseInt(length.getText().toString().trim());
            } catch (NumberFormatException ex) {
                Toast.makeText(c, "숫자를 입력하세요", Toast.LENGTH_SHORT).show();
                return;
            }
            if (b < 0 || b > 180) { before.setError("0~180분"); return; }
            if (len < 10 || len > 300) { length.setError("10~300분"); return; }
            int[] ps = Storage.parsePeriodStarts(periods.getText().toString());
            if (ps.length == 0) { periods.setError("예: 09:00,10:00,11:00"); return; }
            Storage.setMinutesBefore(c, b);
            Storage.setClassLength(c, len);
            Storage.setPeriodStartsText(c, Storage.formatPeriodStarts(ps));
            Storage.setVibrate(c, vib.isChecked());
            onChanged.run();
            d.dismiss();
        }));
        d.show();
    }

    public static void rawText(Context c, String text) {
        EditText t = new EditText(c);
        t.setText(text == null || text.isEmpty() ? "(인식된 글자가 없습니다)" : text);
        t.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        t.setKeyListener(null);
        t.setTextIsSelectable(true);
        int pad = (int) (20 * c.getResources().getDisplayMetrics().density);
        t.setPadding(pad, pad, pad, pad);
        new MaterialAlertDialogBuilder(c)
                .setTitle("인식된 글자 (원문)")
                .setView(t)
                .setPositiveButton("닫기", null)
                .show();
    }
}
