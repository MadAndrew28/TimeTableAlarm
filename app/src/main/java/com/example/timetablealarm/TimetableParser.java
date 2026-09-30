package com.example.timetablealarm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OCR 단어(텍스트 + 위치)로부터 시간표를 복원한다. 안드로이드 의존성이 없어 JVM에서 단위 테스트 가능.
 *
 * 동작 순서
 * 1) 요일 헤더(월 화 수 목 금 …)를 찾아 열(column) 위치를 정한다.
 * 2) 헤더 왼쪽 영역에서 행 라벨(1교시, 1, 9, 09:00, 1교시 09:00~09:50 …)을 찾는다.
 * 3) 라벨 종류에 따라 모드를 결정한다.
 *    - ROW   : 교시(또는 시간 범위) 단위 행. 각 칸 = 1교시.
 *    - LINEAR: 시각 눈금(9,10,11… 또는 09:00,10:00…). y좌표 → 시각을 선형 보간 (에브리타임 형태).
 *    - BAND  : 라벨 없음. 내용의 세로 위치로 행을 추정해 1교시, 2교시…로 가정.
 * 4) 각 칸의 글자를 모아 과목명/메모를 만들고, 칸 안에 "09:00~10:15" 같은 시간이 있으면 그것을 우선한다.
 * 5) 같은 요일에 같은 과목이 연달아 있으면 하나로 합친다(연강 → 알람 1번).
 * 헤더를 못 찾으면 줄 단위 텍스트("월 1교시 국어", "화요일 09:00 경영학")로 파싱한다.
 */
public class TimetableParser {

    public static class Config {
        /** 교시별 시작 시각(분). periodStarts[0] = 1교시. */
        public int[] periodStarts = {9 * 60, 10 * 60, 11 * 60, 12 * 60, 13 * 60, 14 * 60, 15 * 60, 16 * 60, 17 * 60};
        /** 수업 길이(분). 끝 시각이 사진에 없을 때 사용. */
        public int classLength = 50;
    }

    public static class Result {
        public final List<ClassEntry> entries = new ArrayList<>();
        public String mode = "NONE";
        public String message = "";
        /** OCR 원문 (화면에서 "인식된 글자 보기"용) */
        public String rawText = "";
    }

    // ---------------------------------------------------------------- 정규식
    private static final Pattern DAY_KO = Pattern.compile("^[\\[(]?(월|화|수|목|금|토|일)(요일|욜)?[\\])]?([(\\[].*)?[.,:]?$");
    private static final Pattern DAY_EN = Pattern.compile("^(MON|TUE|WED|THU|FRI|SAT|SUN)[A-Z]*\\.?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PERIOD = Pattern.compile("(\\d{1,2})\\s*교\\s*시");
    private static final Pattern NUMBER_ONLY = Pattern.compile("^\\(?(\\d{1,2})\\)?[.)]?$");
    private static final Pattern HOUR_SI = Pattern.compile("^(오전|오후)?\\s*(\\d{1,2})\\s*시$");
    private static final Pattern TIME = Pattern.compile("(오전|오후|AM|PM|am|pm)?\\s*(\\d{1,2})\\s*[:：;.]\\s*(\\d{2})(?!\\d)|(오전|오후)?\\s*(\\d{1,2})\\s*시\\s*(?:(\\d{1,2})\\s*분|반)?");
    private static final Pattern HAS_LETTER = Pattern.compile("[\\p{L}]");
    private static final String[] NON_CLASS = {"점심", "중식", "lunch", "쉬는시간", "휴식", "break", "조회", "종례"};

    // ---------------------------------------------------------------- 내부 구조
    private static class Col { int day; float cx, left, right; }

    private static class Line {
        final List<OcrWord> words = new ArrayList<>();
        float top = Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
        void add(OcrWord w) { words.add(w); top = Math.min(top, w.top); bottom = Math.max(bottom, w.bottom); }
        float cy() { return (top + bottom) / 2f; }
        String text() {
            List<OcrWord> s = new ArrayList<>(words);
            s.sort(Comparator.comparingInt(a -> a.left));
            StringBuilder sb = new StringBuilder();
            for (OcrWord w : s) { if (sb.length() > 0) sb.append(' '); sb.append(w.text); }
            return sb.toString().trim();
        }
    }

    private static class Marker {
        float top, bottom;
        Integer period, number, start, end;
        boolean hasSi, skip;
        float cy() { return (top + bottom) / 2f; }
    }

    // ================================================================ 진입점
    public static Result parse(List<OcrWord> words, String fullText, Config cfg) {
        Result res = new Result();
        List<OcrWord> ws = new ArrayList<>();
        for (OcrWord w : words) if (!w.text.isEmpty()) ws.add(w);

        List<OcrWord> header = findHeader(ws);
        if (header != null) {
            parseGrid(ws, header, cfg, res);
        }
        if (res.entries.isEmpty()) {
            List<ClassEntry> fromText = parseText(fullText, cfg);
            if (!fromText.isEmpty()) {
                res.entries.addAll(fromText);
                res.mode = "TEXT";
            }
        }
        List<ClassEntry> merged = mergeAndSort(res.entries);
        res.entries.clear();
        res.entries.addAll(merged);
        if (res.entries.isEmpty()) {
            res.message = header == null
                    ? "요일(월·화·수…) 표시를 찾지 못했습니다. 시간표 전체가 반듯하게 나오도록 다시 찍거나 직접 추가해 주세요."
                    : "요일은 찾았지만 수업 칸을 읽지 못했습니다. 직접 추가해 주세요.";
        } else {
            res.message = res.entries.size() + "개 수업을 찾았습니다. 틀린 부분은 눌러서 고쳐 주세요.";
        }
        return res;
    }

    // ================================================================ 1) 요일 헤더
    static int dayOf(String raw) {
        String t = raw.trim().replace(" ", "");
        Matcher m = DAY_KO.matcher(t);
        if (m.matches()) return "월화수목금토일".indexOf(m.group(1)) + 1;
        m = DAY_EN.matcher(t);
        if (m.matches()) {
            String k = m.group(1).toUpperCase(Locale.US);
            String[] en = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
            for (int i = 0; i < 7; i++) if (en[i].equals(k)) return i + 1;
        }
        return 0;
    }

    private static List<OcrWord> findHeader(List<OcrWord> ws) {
        List<OcrWord> cands = new ArrayList<>();
        for (OcrWord w : ws) if (dayOf(w.text) > 0) cands.add(w);
        if (cands.size() < 2) return null;
        List<Line> groups = groupIntoLines(cands, 0.9f);
        List<OcrWord> best = null;
        int bestDistinct = 1;
        float bestY = Float.MAX_VALUE;
        for (Line g : groups) {
            Set<Integer> days = new HashSet<>();
            for (OcrWord w : g.words) days.add(dayOf(w.text));
            int d = days.size();
            if (d > bestDistinct || (d == bestDistinct && d >= 2 && g.top < bestY)) {
                bestDistinct = d;
                bestY = g.top;
                best = g.words;
            }
        }
        if (best == null || bestDistinct < 2) return null;
        // 요일별로 하나만 (가장 왼쪽 것)
        List<OcrWord> uniq = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        List<OcrWord> sorted = new ArrayList<>(best);
        sorted.sort(Comparator.comparingDouble(OcrWord::cx));
        for (OcrWord w : sorted) if (seen.add(dayOf(w.text))) uniq.add(w);
        return uniq;
    }

    // ================================================================ 2~4) 격자 파싱
    private static void parseGrid(List<OcrWord> ws, List<OcrWord> header, Config cfg, Result res) {
        // 열 구성
        List<Col> cols = new ArrayList<>();
        float headerBottom = 0, headerH = 0;
        for (OcrWord h : header) {
            Col c = new Col();
            c.day = dayOf(h.text);
            c.cx = h.cx();
            cols.add(c);
            headerBottom = Math.max(headerBottom, h.bottom);
            headerH += h.height();
        }
        headerH /= header.size();
        float spacing;
        if (cols.size() >= 2) {
            List<Float> diffs = new ArrayList<>();
            for (int i = 1; i < cols.size(); i++) diffs.add(cols.get(i).cx - cols.get(i - 1).cx);
            Collections.sort(diffs);
            spacing = diffs.get(diffs.size() / 2);
        } else {
            spacing = header.get(0).width() * 4f;
        }
        for (int i = 0; i < cols.size(); i++) {
            Col c = cols.get(i);
            c.left = i == 0 ? c.cx - spacing / 2f : (cols.get(i - 1).cx + c.cx) / 2f;
            c.right = i == cols.size() - 1 ? c.cx + spacing / 2f : (cols.get(i + 1).cx + c.cx) / 2f;
        }

        Set<OcrWord> headerSet = new HashSet<>(header);
        List<OcrWord> leftZone = new ArrayList<>();
        List<OcrWord> body = new ArrayList<>();
        float minY = headerBottom - headerH * 0.2f;
        for (OcrWord w : ws) {
            if (headerSet.contains(w) || w.cy() <= minY) continue;
            if (w.cx() < cols.get(0).left) leftZone.add(w);
            else body.add(w);
        }
        if (body.isEmpty()) return;

        List<Marker> markers = buildMarkers(leftZone);
        float medH = medianHeight(body);

        // 모드 결정
        boolean anyPeriod = false, anyRange = false, anyStart = false, anySi = false;
        List<Marker> nums = new ArrayList<>();
        for (Marker m : markers) {
            if (m.period != null) anyPeriod = true;
            if (m.start != null && m.end != null) anyRange = true;
            if (m.start != null) anyStart = true;
            if (m.hasSi) anySi = true;
            if (m.number != null) nums.add(m);
        }

        if (anyPeriod || anyRange) {
            res.mode = "ROW";
            gridRows(body, cols, markers, headerBottom, cfg, res);
        } else if (anyStart && countWithStart(markers) >= 2) {
            res.mode = "LINEAR";
            gridLinear(body, cols, markers, medH, cfg, res);
        } else if (nums.size() >= 2) {
            boolean hours = anySi || nums.get(0).number >= 7;
            for (Marker m : nums) if (m.number > 12) hours = true;
            if (hours) {
                // 9,10,11,12,1,2,3 → 9..15 로 펼치기
                int prev = -1, add = 0;
                for (Marker m : nums) {
                    int v = m.number + add;
                    if (prev >= 0 && v < prev) { add += 12; v = m.number + add; }
                    m.start = v * 60;
                    prev = v;
                }
                res.mode = "LINEAR";
                gridLinear(body, cols, nums, medH, cfg, res);
            } else {
                for (Marker m : nums) m.period = m.number;
                res.mode = "ROW";
                gridRows(body, cols, nums, headerBottom, cfg, res);
            }
        } else {
            res.mode = "BAND";
            gridBands(body, cols, medH, cfg, res);
        }
    }

    private static int countWithStart(List<Marker> ms) {
        int n = 0;
        for (Marker m : ms) if (m.start != null) n++;
        return n;
    }

    private static List<Marker> buildMarkers(List<OcrWord> leftZone) {
        List<Line> lines = groupIntoLines(leftZone, 0.6f);
        lines.sort(Comparator.comparingDouble(l -> l.top));
        List<Marker> out = new ArrayList<>();
        for (Line l : lines) {
            String t = l.text();
            String compact = t.replace(" ", "");
            Marker m = new Marker();
            m.top = l.top;
            m.bottom = l.bottom;
            String lower = compact.toLowerCase(Locale.ROOT);
            for (String nc : NON_CLASS) if (lower.contains(nc)) m.skip = true;
            Matcher pm = PERIOD.matcher(t);
            if (pm.find()) m.period = Integer.parseInt(pm.group(1));
            Matcher hs = HOUR_SI.matcher(compact);
            if (m.period == null && hs.matches()) {
                int h = Integer.parseInt(hs.group(2));
                if ("오후".equals(hs.group(1)) && h < 12) h += 12;
                m.number = h;
                m.hasSi = true;
            } else if (m.period == null) {
                Matcher nm = NUMBER_ONLY.matcher(compact);
                if (nm.matches()) m.number = Integer.parseInt(nm.group(1));
            }
            if (!(m.number != null && m.hasSi)) {
                List<Integer> times = findTimes(t);
                if (!times.isEmpty()) m.start = times.get(0);
                if (times.size() >= 2) m.end = times.get(1);
            }
            boolean useful = m.period != null || m.number != null || m.start != null || m.skip;
            if (!useful) continue;

            // "1교시" 다음 줄에 "09:00~09:50" 이 따로 있는 경우 → 합치기
            if (!out.isEmpty()) {
                Marker prev = out.get(out.size() - 1);
                float gap = m.top - prev.bottom;
                float h = Math.max(1, prev.bottom - prev.top);
                boolean timeOnly = m.period == null && m.number == null && m.start != null && !m.skip;
                if (timeOnly && prev.start == null && (prev.period != null || prev.number != null) && gap < h * 1.5f) {
                    prev.start = m.start;
                    prev.end = m.end;
                    prev.bottom = m.bottom;
                    continue;
                }
            }
            out.add(m);
        }
        return out;
    }

    /** ROW 모드: 라벨의 세로 중심 사이 중간값을 칸 경계로 사용. */
    private static void gridRows(List<OcrWord> body, List<Col> cols, List<Marker> markers, float headerBottom,
                                 Config cfg, Result res) {
        List<Marker> ms = new ArrayList<>(markers);
        ms.sort(Comparator.comparingDouble(Marker::cy));
        int n = ms.size();
        float[] lo = new float[n], hi = new float[n];
        float avgGap = 0;
        for (int i = 1; i < n; i++) avgGap += ms.get(i).cy() - ms.get(i - 1).cy();
        avgGap = n > 1 ? avgGap / (n - 1) : (ms.get(0).bottom - ms.get(0).top) * 4f;
        for (int i = 0; i < n; i++) {
            lo[i] = i == 0 ? Math.max(headerBottom, ms.get(0).cy() - avgGap / 2f) : (ms.get(i - 1).cy() + ms.get(i).cy()) / 2f;
            hi[i] = i == n - 1 ? ms.get(i).cy() + avgGap / 2f : (ms.get(i).cy() + ms.get(i + 1).cy()) / 2f;
        }
        // 교시 번호가 없는 행(시간만 있는 경우)은 순서대로 번호 부여
        int seq = 0;
        for (Marker m : ms) {
            if (m.skip) continue;
            seq++;
            if (m.period == null) m.period = seq;
        }
        for (Col c : cols) {
            for (int i = 0; i < n; i++) {
                Marker m = ms.get(i);
                if (m.skip) continue;
                List<OcrWord> cell = new ArrayList<>();
                for (OcrWord w : body) {
                    float x = w.cx(), y = w.cy();
                    if (x >= c.left && x < c.right && y >= lo[i] && y < hi[i]) cell.add(w);
                }
                if (cell.isEmpty()) continue;
                int start = m.start != null ? m.start : periodStart(cfg, m.period);
                int end = m.end != null ? m.end : start + cfg.classLength;
                ClassEntry e = makeEntry(cell, c.day, start, end);
                if (e != null) res.entries.add(e);
            }
        }
    }

    /** LINEAR 모드: 시각 눈금으로 y→분 선형 회귀 후, 열마다 글자 덩어리를 수업 하나로 본다. */
    private static void gridLinear(List<OcrWord> body, List<Col> cols, List<Marker> markers, float medH,
                                   Config cfg, Result res) {
        List<float[]> pts = new ArrayList<>();
        for (Marker m : markers) if (m.start != null) pts.add(new float[]{m.top, m.start});
        if (pts.size() < 2) { gridBands(body, cols, medH, cfg, res); return; }
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (float[] p : pts) { sx += p[0]; sy += p[1]; sxx += p[0] * p[0]; sxy += p[0] * p[1]; }
        int k = pts.size();
        double denom = k * sxx - sx * sx;
        if (Math.abs(denom) < 1e-6) { gridBands(body, cols, medH, cfg, res); return; }
        double slope = (k * sxy - sx * sy) / denom;
        double icpt = (sy - slope * sx) / k;
        if (slope <= 0) { gridBands(body, cols, medH, cfg, res); return; }

        for (Col c : cols) {
            List<OcrWord> colWords = new ArrayList<>();
            for (OcrWord w : body) if (w.cx() >= c.left && w.cx() < c.right) colWords.add(w);
            for (List<OcrWord> block : verticalBlocks(colWords, medH)) {
                float top = Float.MAX_VALUE;
                for (OcrWord w : block) top = Math.min(top, w.top);
                int t = (int) Math.round(slope * top + icpt);
                int start = (t / 5) * 5; // 글자는 칸 윗변보다 약간 아래 있으므로 5분 단위 내림
                ClassEntry e = makeEntry(block, c.day, start, start + cfg.classLength);
                if (e != null) res.entries.add(e);
            }
        }
    }

    /** BAND 모드: 라벨이 없을 때 모든 열의 글자 덩어리를 세로 위치로 묶어 행으로 추정. */
    private static void gridBands(List<OcrWord> body, List<Col> cols, float medH, Config cfg, Result res) {
        List<List<OcrWord>> blocks = new ArrayList<>();
        List<Integer> blockDay = new ArrayList<>();
        for (Col c : cols) {
            List<OcrWord> colWords = new ArrayList<>();
            for (OcrWord w : body) if (w.cx() >= c.left && w.cx() < c.right) colWords.add(w);
            for (List<OcrWord> b : verticalBlocks(colWords, medH)) { blocks.add(b); blockDay.add(c.day); }
        }
        if (blocks.isEmpty()) return;
        // 덩어리의 top 값을 모아 행 대표값으로 군집화
        List<Float> tops = new ArrayList<>();
        for (List<OcrWord> b : blocks) tops.add(blockTop(b));
        List<Float> sorted = new ArrayList<>(tops);
        Collections.sort(sorted);
        List<Float> rowTops = new ArrayList<>();
        for (float t : sorted) {
            if (rowTops.isEmpty() || t - rowTops.get(rowTops.size() - 1) > medH * 1.5f) rowTops.add(t);
        }
        for (int i = 0; i < blocks.size(); i++) {
            float t = tops.get(i);
            int row = 0;
            for (int r = 0; r < rowTops.size(); r++) if (t >= rowTops.get(r) - medH * 0.75f) row = r;
            int start = periodStart(cfg, row + 1);
            ClassEntry e = makeEntry(blocks.get(i), blockDay.get(i), start, start + cfg.classLength);
            if (e != null) res.entries.add(e);
        }
    }

    private static float blockTop(List<OcrWord> b) {
        float t = Float.MAX_VALUE;
        for (OcrWord w : b) t = Math.min(t, w.top);
        return t;
    }

    /** 한 열의 단어들을 세로 간격 기준으로 덩어리(=수업 칸)로 나눈다. */
    private static List<List<OcrWord>> verticalBlocks(List<OcrWord> colWords, float medH) {
        List<List<OcrWord>> out = new ArrayList<>();
        if (colWords.isEmpty()) return out;
        List<Line> lines = groupIntoLines(colWords, 0.6f);
        lines.sort(Comparator.comparingDouble(l -> l.top));
        List<OcrWord> cur = new ArrayList<>();
        float curBottom = -1;
        for (Line l : lines) {
            if (!cur.isEmpty() && l.top - curBottom > medH * 0.9f) {
                out.add(cur);
                cur = new ArrayList<>();
            }
            cur.addAll(l.words);
            curBottom = Math.max(curBottom, l.bottom);
        }
        if (!cur.isEmpty()) out.add(cur);
        return out;
    }

    // ================================================================ 칸 → 수업
    private static ClassEntry makeEntry(List<OcrWord> cell, int day, int start, int end) {
        List<Line> lines = groupIntoLines(cell, 0.6f);
        lines.sort(Comparator.comparingDouble(l -> l.top));
        List<String> texts = new ArrayList<>();
        Integer tStart = null, tEnd = null;
        for (Line l : lines) {
            String t = l.text();
            List<Integer> times = findTimes(t);
            if (!times.isEmpty() && tStart == null) {
                tStart = times.get(0);
                if (times.size() >= 2) tEnd = times.get(1);
            }
            String stripped = TIME.matcher(t).replaceAll(" ").replaceAll("[~\\-–—]", " ").replaceAll("\\s+", " ").trim();
            if (stripped.isEmpty() || !HAS_LETTER.matcher(stripped).find()) continue;
            texts.add(stripped);
        }
        if (texts.isEmpty()) return null;
        String subject = texts.get(0);
        String low = subject.toLowerCase(Locale.ROOT).replace(" ", "");
        for (String nc : NON_CLASS) if (low.startsWith(nc)) return null;
        StringBuilder memo = new StringBuilder();
        for (int i = 1; i < texts.size(); i++) { if (memo.length() > 0) memo.append(" / "); memo.append(texts.get(i)); }
        if (tStart != null) {
            start = tStart;
            end = tEnd != null ? tEnd : start + Math.max(10, end - start);
        }
        if (end <= start) end = start + 50;
        return new ClassEntry(subject, memo.toString(), day, start, end);
    }

    // ================================================================ 텍스트 폴백
    private static final Pattern TEXT_DAY = Pattern.compile("(?:^|[\\s,(\\[])(월|화|수|목|금|토|일)(?:요일)?(?=$|[\\s,)\\]\\d])");

    static List<ClassEntry> parseText(String fullText, Config cfg) {
        List<ClassEntry> out = new ArrayList<>();
        if (fullText == null) return out;
        for (String raw : fullText.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            Matcher dm = TEXT_DAY.matcher(line);
            if (!dm.find()) continue;
            int day = "월화수목금토일".indexOf(dm.group(1)) + 1;
            String rest = (line.substring(0, dm.start(1)) + " " + line.substring(dm.end())).replaceFirst("^요일", "");
            Integer start = null, end = null;
            Matcher pm = PERIOD.matcher(rest);
            if (pm.find()) {
                start = periodStart(cfg, Integer.parseInt(pm.group(1)));
                end = start + cfg.classLength;
                rest = rest.substring(0, pm.start()) + " " + rest.substring(pm.end());
            }
            List<Integer> times = findTimes(rest);
            if (!times.isEmpty()) {
                start = times.get(0);
                end = times.size() >= 2 ? times.get(1) : start + cfg.classLength;
            }
            if (start == null) continue;
            String subject = TIME.matcher(rest).replaceAll(" ").replaceAll("요일", " ")
                    .replaceAll("[~\\-–—:|,]", " ").replaceAll("\\s+", " ").trim();
            if (subject.isEmpty() || !HAS_LETTER.matcher(subject).find()) subject = "수업";
            out.add(new ClassEntry(subject, "", day, start, end));
        }
        return out;
    }

    // ================================================================ 유틸
    static List<Integer> findTimes(String t) {
        List<Integer> out = new ArrayList<>();
        Matcher m = TIME.matcher(t);
        while (m.find()) {
            String ampm;
            int h, mi;
            if (m.group(2) != null) {
                ampm = m.group(1);
                h = Integer.parseInt(m.group(2));
                mi = Integer.parseInt(m.group(3));
            } else if (m.group(5) != null) {
                ampm = m.group(4);
                h = Integer.parseInt(m.group(5));
                mi = m.group(6) != null ? Integer.parseInt(m.group(6)) : (m.group(0).trim().endsWith("반") ? 30 : 0);
            } else continue;
            if (ampm != null) {
                String a = ampm.toUpperCase(Locale.ROOT);
                if ((a.equals("오후") || a.equals("PM")) && h < 12) h += 12;
                if ((a.equals("오전") || a.equals("AM")) && h == 12) h = 0;
            }
            if (h > 23 || mi > 59) continue;
            out.add(h * 60 + mi);
        }
        // 오후 시간을 12시간제로 쓴 경우(예: 1:30 → 13:30) 보정: 앞 시각보다 작아지면 +12h
        for (int i = 1; i < out.size(); i++) {
            if (out.get(i) < out.get(i - 1) && out.get(i) + 720 < 1440) out.set(i, out.get(i) + 720);
        }
        if (!out.isEmpty() && out.get(0) < 7 * 60 && out.get(0) >= 60) {
            // "1:00"~"6:59" 은 수업 시각으로는 오후일 가능성이 높음
            int shift = 720;
            for (int i = 0; i < out.size(); i++) if (out.get(i) < 12 * 60) out.set(i, out.get(i) + shift);
        }
        return out;
    }

    static int periodStart(Config cfg, int period) {
        int[] ps = cfg.periodStarts;
        if (ps == null || ps.length == 0) return 9 * 60 + (period - 1) * 60;
        if (period >= 1 && period <= ps.length) return ps[period - 1];
        if (period < 1) return ps[0];
        int step = ps.length >= 2 ? ps[ps.length - 1] - ps[ps.length - 2] : 60;
        return ps[ps.length - 1] + (period - ps.length) * step;
    }

    private static float medianHeight(List<OcrWord> ws) {
        if (ws.isEmpty()) return 20f;
        List<Integer> hs = new ArrayList<>();
        for (OcrWord w : ws) hs.add(w.height());
        Collections.sort(hs);
        return hs.get(hs.size() / 2);
    }

    /** 세로 중심이 비슷한 단어들을 한 줄로 묶는다. */
    private static List<Line> groupIntoLines(List<OcrWord> ws, float tol) {
        List<OcrWord> s = new ArrayList<>(ws);
        s.sort(Comparator.comparingDouble(OcrWord::cy));
        List<Line> lines = new ArrayList<>();
        for (OcrWord w : s) {
            Line target = null;
            for (Line l : lines) {
                float lh = l.bottom - l.top;
                if (Math.abs(l.cy() - w.cy()) < Math.max(lh, w.height()) * tol) { target = l; break; }
            }
            if (target == null) { target = new Line(); lines.add(target); }
            target.add(w);
        }
        return lines;
    }

    /** 요일·시작시각 순 정렬, 같은 과목 연강 합치기, 중복 제거. */
    static List<ClassEntry> mergeAndSort(List<ClassEntry> in) {
        List<ClassEntry> s = new ArrayList<>(in);
        s.sort(Comparator.comparingInt((ClassEntry e) -> e.dayOfWeek).thenComparingInt(e -> e.startMin));
        List<ClassEntry> out = new ArrayList<>();
        for (ClassEntry e : s) {
            if (!out.isEmpty()) {
                ClassEntry p = out.get(out.size() - 1);
                boolean sameSubj = p.subject.replace(" ", "").equals(e.subject.replace(" ", ""));
                if (p.dayOfWeek == e.dayOfWeek && sameSubj && e.startMin - p.endMin <= 15 && e.startMin >= p.startMin) {
                    p.endMin = Math.max(p.endMin, e.endMin);
                    if ((p.memo == null || p.memo.isEmpty()) && e.memo != null) p.memo = e.memo;
                    continue;
                }
            }
            out.add(e);
        }
        return out;
    }
}
