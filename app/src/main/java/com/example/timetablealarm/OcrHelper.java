package com.example.timetablealarm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.exifinterface.media.ExifInterface;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 사진 URI → (회전 보정·축소) → ML Kit 한국어 OCR → TimetableParser. */
public class OcrHelper {

    public interface Callback {
        void onResult(TimetableParser.Result result, String rawText);
        void onError(String message);
    }

    private static final int MAX_SIDE = 2400;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    public static void recognize(Context ctx, Uri uri, Callback cb) {
        Context app = ctx.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        IO.execute(() -> {
            Bitmap bmp;
            try {
                bmp = loadBitmap(app, uri);
            } catch (Exception e) {
                main.post(() -> cb.onError("사진을 열 수 없습니다: " + e.getMessage()));
                return;
            }
            if (bmp == null) {
                main.post(() -> cb.onError("사진을 열 수 없습니다."));
                return;
            }
            TimetableParser.Config cfg = Storage.parserConfig(app);
            TextRecognizer recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
            attempt(recognizer, bmp, cfg, new int[]{0, 90, 270}, 0, null, cb);
        });
    }

    /** 가로로 찍힌 사진 대비: 0° → 90° → 270° 순서로 시도, 수업을 찾으면 멈춤. */
    private static void attempt(TextRecognizer recognizer, Bitmap bmp, TimetableParser.Config cfg,
                                int[] rotations, int idx, TimetableParser.Result firstResult, Callback cb) {
        recognizer.process(InputImage.fromBitmap(bmp, rotations[idx]))
                .addOnSuccessListener(text -> {
                    TimetableParser.Result r = TimetableParser.parse(toWords(text), text.getText(), cfg);
                    r.rawText = text.getText();
                    TimetableParser.Result first = firstResult != null ? firstResult : r;
                    if (!r.entries.isEmpty()) {
                        recognizer.close();
                        cb.onResult(r, r.rawText);
                    } else if (idx + 1 < rotations.length) {
                        attempt(recognizer, bmp, cfg, rotations, idx + 1, first, cb);
                    } else {
                        recognizer.close();
                        cb.onResult(first, first.rawText);
                    }
                })
                .addOnFailureListener(e -> {
                    recognizer.close();
                    cb.onError("글자 인식 실패: " + e.getMessage());
                });
    }

    static List<OcrWord> toWords(Text text) {
        List<OcrWord> out = new ArrayList<>();
        for (Text.TextBlock b : text.getTextBlocks()) {
            for (Text.Line l : b.getLines()) {
                for (Text.Element e : l.getElements()) {
                    Rect r = e.getBoundingBox();
                    if (r == null) continue;
                    out.add(new OcrWord(e.getText(), r.left, r.top, r.right, r.bottom));
                }
            }
        }
        return out;
    }

    private static Bitmap loadBitmap(Context c, Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            bmp = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bmp == null) return null;

        int rotation = 0;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in != null) {
                int o = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (o == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
                else if (o == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
                else if (o == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
            }
        } catch (Exception ignored) {}
        if (rotation != 0) {
            Matrix m = new Matrix();
            m.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            if (rotated != bmp) bmp.recycle();
            bmp = rotated;
        }
        return bmp;
    }
}
