package com.example.timetablealarm;

import java.util.Locale;

/** 시간표의 수업 1개. 순수 Java 클래스(안드로이드 의존성 없음). */
public class ClassEntry {
    public long id;
    public String subject;
    public String memo;       // 강의실·교수 등 부가 정보
    public int dayOfWeek;     // 1=월 ... 7=일 (ISO)
    public int startMin;      // 자정 기준 분 (예: 9:00 -> 540)
    public int endMin;
    public boolean enabled = true;

    public static final String[] DAY_NAMES = {"월", "화", "수", "목", "금", "토", "일"};

    public ClassEntry() {}

    public ClassEntry(String subject, String memo, int dayOfWeek, int startMin, int endMin) {
        this.subject = subject;
        this.memo = memo;
        this.dayOfWeek = dayOfWeek;
        this.startMin = startMin;
        this.endMin = endMin;
    }

    public ClassEntry copy() {
        ClassEntry c = new ClassEntry(subject, memo, dayOfWeek, startMin, endMin);
        c.id = id;
        c.enabled = enabled;
        return c;
    }

    public String dayName() {
        return (dayOfWeek >= 1 && dayOfWeek <= 7) ? DAY_NAMES[dayOfWeek - 1] : "?";
    }

    public static String fmt(int minutes) {
        int m = ((minutes % 1440) + 1440) % 1440;
        return String.format(Locale.US, "%02d:%02d", m / 60, m % 60);
    }

    public String timeRange() {
        return fmt(startMin) + " ~ " + fmt(endMin);
    }

    @Override
    public String toString() {
        return dayName() + " " + timeRange() + " " + subject + (memo == null || memo.isEmpty() ? "" : " (" + memo + ")");
    }
}
