package com.example.timetablealarm;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 시간표와 설정을 SharedPreferences(JSON)에 저장. */
public class Storage {
    private static final String PREF = "timetable";
    private static final String K_ENTRIES = "entries";
    private static final String K_NEXT_ID = "next_id";
    private static final String K_BEFORE = "minutes_before";
    private static final String K_ENABLED = "alarms_enabled";
    private static final String K_VIBRATE = "vibrate";
    private static final String K_PERIODS = "period_starts";
    private static final String K_LENGTH = "class_length";
    private static final String K_SCHEDULED = "scheduled_ids";

    public static final String DEFAULT_PERIODS = "09:00,10:00,11:00,12:00,13:00,14:00,15:00,16:00,17:00";

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------ 수업 목록
    public static List<ClassEntry> loadEntries(Context c) {
        return fromJson(sp(c).getString(K_ENTRIES, "[]"));
    }

    /** 저장 시 id가 없는(0) 항목에 새 id를 발급한다. */
    public static void saveEntries(Context c, List<ClassEntry> list) {
        SharedPreferences p = sp(c);
        long next = p.getLong(K_NEXT_ID, 1);
        for (ClassEntry e : list) if (e.id <= 0) e.id = next++;
        p.edit().putString(K_ENTRIES, toJson(list)).putLong(K_NEXT_ID, next).apply();
    }

    public static ClassEntry findEntry(Context c, long id) {
        for (ClassEntry e : loadEntries(c)) if (e.id == id) return e;
        return null;
    }

    public static String toJson(List<ClassEntry> list) {
        JSONArray arr = new JSONArray();
        try {
            for (ClassEntry e : list) {
                JSONObject o = new JSONObject();
                o.put("id", e.id);
                o.put("subject", e.subject);
                o.put("memo", e.memo == null ? "" : e.memo);
                o.put("day", e.dayOfWeek);
                o.put("start", e.startMin);
                o.put("end", e.endMin);
                o.put("enabled", e.enabled);
                arr.put(o);
            }
        } catch (JSONException ignored) {}
        return arr.toString();
    }

    public static List<ClassEntry> fromJson(String s) {
        List<ClassEntry> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(s);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                ClassEntry e = new ClassEntry(o.optString("subject"), o.optString("memo"),
                        o.optInt("day", 1), o.optInt("start"), o.optInt("end"));
                e.id = o.optLong("id");
                e.enabled = o.optBoolean("enabled", true);
                out.add(e);
            }
        } catch (JSONException ignored) {}
        return out;
    }

    // ------------------------------------------------------------ 설정
    public static int minutesBefore(Context c) { return sp(c).getInt(K_BEFORE, 10); }
    public static void setMinutesBefore(Context c, int v) { sp(c).edit().putInt(K_BEFORE, v).apply(); }

    public static boolean alarmsEnabled(Context c) { return sp(c).getBoolean(K_ENABLED, true); }
    public static void setAlarmsEnabled(Context c, boolean v) { sp(c).edit().putBoolean(K_ENABLED, v).apply(); }

    public static boolean vibrate(Context c) { return sp(c).getBoolean(K_VIBRATE, true); }
    public static void setVibrate(Context c, boolean v) { sp(c).edit().putBoolean(K_VIBRATE, v).apply(); }

    public static int classLength(Context c) { return sp(c).getInt(K_LENGTH, 50); }
    public static void setClassLength(Context c, int v) { sp(c).edit().putInt(K_LENGTH, v).apply(); }

    public static String periodStartsText(Context c) { return sp(c).getString(K_PERIODS, DEFAULT_PERIODS); }
    public static void setPeriodStartsText(Context c, String v) { sp(c).edit().putString(K_PERIODS, v).apply(); }

    /** "09:00,09:55" → [540, 595]. 잘못된 항목은 건너뜀. */
    public static int[] parsePeriodStarts(String text) {
        List<Integer> l = new ArrayList<>();
        for (String part : text.split("[,\\s]+")) {
            String[] hm = part.trim().split("[:：.]");
            if (hm.length != 2) continue;
            try {
                int h = Integer.parseInt(hm[0].trim()), m = Integer.parseInt(hm[1].trim());
                if (h >= 0 && h < 24 && m >= 0 && m < 60) l.add(h * 60 + m);
            } catch (NumberFormatException ignored) {}
        }
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }

    public static String formatPeriodStarts(int[] a) {
        StringBuilder sb = new StringBuilder();
        for (int v : a) {
            if (sb.length() > 0) sb.append(',');
            sb.append(String.format(Locale.US, "%02d:%02d", v / 60, v % 60));
        }
        return sb.toString();
    }

    public static TimetableParser.Config parserConfig(Context c) {
        TimetableParser.Config cfg = new TimetableParser.Config();
        int[] ps = parsePeriodStarts(periodStartsText(c));
        if (ps.length > 0) cfg.periodStarts = ps;
        cfg.classLength = classLength(c);
        return cfg;
    }

    // ------------------------------------------------------------ 예약된 알람 id (취소용)
    public static List<Long> scheduledIds(Context c) {
        List<Long> out = new ArrayList<>();
        for (String s : sp(c).getString(K_SCHEDULED, "").split(",")) {
            try { if (!s.isEmpty()) out.add(Long.parseLong(s)); } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    public static void setScheduledIds(Context c, List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) { if (sb.length() > 0) sb.append(','); sb.append(id); }
        sp(c).edit().putString(K_SCHEDULED, sb.toString()).apply();
    }
}
