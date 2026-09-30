package com.example.timetablealarm;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/** 다음 알람 시각 계산 (순수 Java, 단위 테스트 가능). */
public class AlarmTime {

    /**
     * @param dayOfWeek     1=월 … 7=일
     * @param startMin      수업 시작(자정 기준 분)
     * @param minutesBefore 몇 분 전에 울릴지
     * @param now           현재 시각
     * @return now 이후 처음 오는 알람 시각
     */
    public static ZonedDateTime next(int dayOfWeek, int startMin, int minutesBefore, ZonedDateTime now) {
        LocalDate thisWeekDay = now.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .plusDays(dayOfWeek - 1);
        ZonedDateTime classStart = ZonedDateTime.of(thisWeekDay, LocalTime.MIDNIGHT, now.getZone())
                .plusMinutes(startMin);
        ZonedDateTime alarm = classStart.minusMinutes(minutesBefore);
        // 이번 주 알람이 이미 지났으면 다음 주로 (여러 주 차이가 나지 않도록 반복)
        while (!alarm.isAfter(now)) alarm = alarm.plusWeeks(1);
        // 지난주 것이 아직 미래일 수도 있음 (예: 월 00:05 수업, 30분 전 알람 → 일요일 23:35)
        while (alarm.minusWeeks(1).isAfter(now)) alarm = alarm.minusWeeks(1);
        return alarm;
    }
}
