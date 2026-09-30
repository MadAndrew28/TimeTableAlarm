package com.example.timetablealarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class TimetableParserTest {

    private static final int H = 30; // 글자 높이(px)

    private static OcrWord w(String t, int cx, int cy) {
        int half = Math.max(15, t.length() * 12);
        return new OcrWord(t, cx - half, cy - H / 2, cx + half, cy + H / 2);
    }

    private static ClassEntry find(List<ClassEntry> es, int day, String subject) {
        for (ClassEntry e : es) if (e.dayOfWeek == day && e.subject.equals(subject)) return e;
        return null;
    }

    /** 중·고등학교형: 왼쪽에 "1교시" + 시간, 칸마다 과목/선생님. */
    @Test
    public void schoolTableWithPeriodsAndTimes() {
        List<OcrWord> ws = new ArrayList<>();
        ws.add(w("2학년", 300, 20)); ws.add(w("3반", 400, 20)); ws.add(w("시간표", 500, 20));
        String[] days = {"월", "화", "수", "목", "금"};
        for (int i = 0; i < 5; i++) ws.add(w(days[i], 250 + i * 150, 100));
        String[] starts = {"08:50", "09:45", "10:40", "11:35"};
        String[] ends = {"09:35", "10:30", "11:25", "12:20"};
        for (int r = 0; r < 4; r++) {
            int cy = 200 + r * 120;
            ws.add(w((r + 1) + "교시", 90, cy - 20));
            ws.add(w(starts[r] + "~" + ends[r], 90, cy + 20));
        }
        // 점심
        ws.add(w("점심시간", 90, 680));
        String[][] grid = {
                {"국어", "수학", "영어", "과학"},
                {"수학", "수학", "체육", "음악"},
                {"영어", "국어", "사회", "미술"},
                {"과학", "영어", "국어", "수학"},
                {"체육", "사회", "수학", "국어"}};
        for (int d = 0; d < 5; d++)
            for (int r = 0; r < 4; r++) {
                int cy = 200 + r * 120;
                ws.add(w(grid[d][r], 250 + d * 150, cy - 20));
                ws.add(w("김선생", 250 + d * 150, cy + 20));
            }
        ws.add(w("점심", 400, 680));

        TimetableParser.Result r = TimetableParser.parse(ws, "", new TimetableParser.Config());
        assertEquals("ROW", r.mode);
        ClassEntry kor = find(r.entries, 1, "국어");
        assertTrue(kor != null);
        assertEquals(8 * 60 + 50, kor.startMin);
        assertEquals(9 * 60 + 35, kor.endMin);
        assertEquals("김선생", kor.memo);
        // 화요일 수학 1·2교시 연강 → 하나로 합침
        ClassEntry math = find(r.entries, 2, "수학");
        assertEquals(8 * 60 + 50, math.startMin);
        assertEquals(10 * 60 + 30, math.endMin);
        // 5요일 × 4교시 = 20칸, 연강 1건 합쳐서 19개
        assertEquals(19, r.entries.size());
        ClassEntry fri = find(r.entries, 5, "국어");
        assertEquals(11 * 60 + 35, fri.startMin);
    }

    /** 교시 번호만 있는 표 → 설정의 교시 시작 시각 사용. */
    @Test
    public void periodNumbersOnlyUsesConfig() {
        List<OcrWord> ws = new ArrayList<>();
        ws.add(w("월요일", 250, 100)); ws.add(w("화요일", 400, 100)); ws.add(w("수요일", 550, 100));
        for (int r = 0; r < 3; r++) ws.add(w(String.valueOf(r + 1), 90, 200 + r * 100));
        ws.add(w("국어", 250, 200)); ws.add(w("영어", 400, 300)); ws.add(w("과학", 550, 400));
        TimetableParser.Config cfg = new TimetableParser.Config();
        cfg.periodStarts = new int[]{540, 600, 660};
        cfg.classLength = 45;
        TimetableParser.Result r = TimetableParser.parse(ws, "", cfg);
        assertEquals("ROW", r.mode);
        assertEquals(3, r.entries.size());
        assertEquals(540, find(r.entries, 1, "국어").startMin);
        assertEquals(600, find(r.entries, 2, "영어").startMin);
        assertEquals(705, find(r.entries, 3, "과학").endMin);
    }

    /** 대학(에브리타임형): 왼쪽 눈금 9,10,11,12,1,2 + 수업 덩어리. */
    @Test
    public void universityHourGrid() {
        List<OcrWord> ws = new ArrayList<>();
        ws.add(w("10:32", 60, 10)); // 상태바 시계 (무시돼야 함)
        String[] days = {"월", "화", "수", "목", "금"};
        for (int i = 0; i < 5; i++) ws.add(w(days[i], 150 + i * 180, 80));
        String[] hours = {"9", "10", "11", "12", "1", "2", "3", "4"};
        int y0 = 110, perHour = 160; // 1시간 = 160px, 9시 눈금 top = 110
        for (int i = 0; i < hours.length; i++) {
            ws.add(new OcrWord(hours[i], 20, y0 + i * perHour + 4, 45, y0 + i * perHour + 4 + H));
        }
        // 월 10:30 자료구조 (텍스트는 칸 윗변보다 6px 아래)
        int y1030 = y0 + (int) (1.5 * perHour) + 6;
        ws.add(new OcrWord("자료구조", 100, y1030, 200, y1030 + H));
        ws.add(new OcrWord("공학관", 100, y1030 + 34, 170, y1030 + 34 + H));
        ws.add(new OcrWord("301", 175, y1030 + 34, 210, y1030 + 34 + H));
        // 월 13:00 운영체제 (바로 아래, 빈 공간 있음)
        int y13 = y0 + 4 * perHour + 6;
        ws.add(new OcrWord("운영체제", 100, y13, 200, y13 + H));
        // 수 9:00 영어회화
        int y9 = y0 + 6;
        ws.add(new OcrWord("영어회화", 460, y9, 560, y9 + H));
        TimetableParser.Config cfg = new TimetableParser.Config();
        cfg.classLength = 75;
        TimetableParser.Result r = TimetableParser.parse(ws, "", cfg);
        assertEquals("LINEAR", r.mode);
        assertEquals(3, r.entries.size());
        ClassEntry ds = find(r.entries, 1, "자료구조");
        assertEquals(10 * 60 + 30, ds.startMin);
        assertEquals(11 * 60 + 45, ds.endMin);
        assertEquals("공학관 301", ds.memo);
        assertEquals(13 * 60, find(r.entries, 1, "운영체제").startMin);
        assertEquals(9 * 60, find(r.entries, 3, "영어회화").startMin);
    }

    /** 칸 안의 시간 표기(대학 강의표)가 우선한다. */
    @Test
    public void explicitTimeInsideCell() {
        List<OcrWord> ws = new ArrayList<>();
        ws.add(w("MON", 250, 100)); ws.add(w("TUE", 400, 100));
        ws.add(w("경영학원론", 250, 200));
        ws.add(w("15:00-16:15", 250, 235));
        TimetableParser.Result r = TimetableParser.parse(ws, "", new TimetableParser.Config());
        assertEquals(1, r.entries.size());
        ClassEntry e = r.entries.get(0);
        assertEquals("경영학원론", e.subject);
        assertEquals(15 * 60, e.startMin);
        assertEquals(16 * 60 + 15, e.endMin);
    }

    /** 헤더를 못 찾으면 줄 텍스트로 파싱. */
    @Test
    public void textFallback() {
        String text = "내 시간표\n월 1교시 국어\n화요일 09:00~10:15 경영학원론\n목 오후 2시 체육\n월급날";
        TimetableParser.Result r = TimetableParser.parse(new ArrayList<>(), text, new TimetableParser.Config());
        assertEquals("TEXT", r.mode);
        assertEquals(3, r.entries.size());
        assertEquals(540, find(r.entries, 1, "국어").startMin);
        ClassEntry b = find(r.entries, 2, "경영학원론");
        assertEquals(540, b.startMin);
        assertEquals(615, b.endMin);
        assertEquals(14 * 60, find(r.entries, 4, "체육").startMin);
    }

    @Test
    public void findTimesVariants() {
        assertEquals(List.of(540, 590), TimetableParser.findTimes("09:00~09:50"));
        assertEquals(List.of(13 * 60 + 30, 14 * 60 + 45), TimetableParser.findTimes("1:30-2:45"));
        assertEquals(List.of(15 * 60 + 30), TimetableParser.findTimes("오후 3시 30분"));
        assertEquals(List.of(10 * 60 + 30), TimetableParser.findTimes("10시반"));
    }
}
