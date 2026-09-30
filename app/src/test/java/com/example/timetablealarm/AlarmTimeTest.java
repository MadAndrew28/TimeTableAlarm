package com.example.timetablealarm;

import static org.junit.Assert.assertEquals;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.Test;

public class AlarmTimeTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    // 2026-09-30 은 수요일
    private static ZonedDateTime at(int d, int h, int m) {
        return ZonedDateTime.of(2026, 9, d, h, m, 0, 0, SEOUL);
    }

    @Test
    public void laterToday() {
        // 수 08:00 기준, 수 09:00 수업 10분 전 → 오늘 08:50
        assertEquals(at(30, 8, 50), AlarmTime.next(3, 540, 10, at(30, 8, 0)));
    }

    @Test
    public void alreadyPassedTodayGoesNextWeek() {
        // 수 08:55 기준, 알람 08:50 은 지났으므로 다음 주 수요일
        assertEquals(ZonedDateTime.of(2026, 10, 7, 8, 50, 0, 0, SEOUL), AlarmTime.next(3, 540, 10, at(30, 8, 55)));
    }

    @Test
    public void earlierWeekdayGoesNextWeek() {
        // 수요일 기준 월 09:00 수업 → 10/5(월) 08:50
        assertEquals(ZonedDateTime.of(2026, 10, 5, 8, 50, 0, 0, SEOUL), AlarmTime.next(1, 540, 10, at(30, 12, 0)));
    }

    @Test
    public void laterWeekdayThisWeek() {
        // 수요일 기준 금 13:00 수업 정각 알람 → 10/2(금) 13:00
        assertEquals(ZonedDateTime.of(2026, 10, 2, 13, 0, 0, 0, SEOUL), AlarmTime.next(5, 780, 0, at(30, 12, 0)));
    }

    @Test
    public void crossesMidnightBackwards() {
        // 월 00:10 수업, 30분 전 → 일요일 23:40. 기준: 일 10/4 12:00
        assertEquals(ZonedDateTime.of(2026, 10, 4, 23, 40, 0, 0, SEOUL),
                AlarmTime.next(1, 10, 30, ZonedDateTime.of(2026, 10, 4, 12, 0, 0, 0, SEOUL)));
    }
}
