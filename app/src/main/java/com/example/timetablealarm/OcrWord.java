package com.example.timetablealarm;

/** OCR로 인식된 단어 1개와 그 위치(픽셀). ML Kit 결과를 이 형태로 변환해 파서에 넘긴다. */
public class OcrWord {
    public final String text;
    public final int left, top, right, bottom;

    public OcrWord(String text, int left, int top, int right, int bottom) {
        this.text = text == null ? "" : text.trim();
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public float cx() { return (left + right) / 2f; }
    public float cy() { return (top + bottom) / 2f; }
    public int height() { return Math.max(1, bottom - top); }
    public int width() { return Math.max(1, right - left); }

    @Override
    public String toString() {
        return "'" + text + "'@(" + left + "," + top + "," + right + "," + bottom + ")";
    }
}
