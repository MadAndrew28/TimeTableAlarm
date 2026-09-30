package com.example.timetablealarm;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** OCR 결과 확인·수정 화면. 여기서 저장해야 실제 시간표/알람에 반영된다. */
public class ReviewActivity extends AppCompatActivity {
    public static final String EXTRA_ENTRIES = "entries";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_RAW = "raw";
    private static final String STATE_ENTRIES = "state_entries";

    private final List<ClassEntry> entries = new ArrayList<>();
    private ClassAdapter adapter;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review);
        setTitle("인식 결과 확인");

        String json = savedInstanceState != null ? savedInstanceState.getString(STATE_ENTRIES)
                : getIntent().getStringExtra(EXTRA_ENTRIES);
        entries.addAll(Storage.fromJson(json == null ? "[]" : json));
        for (ClassEntry e : entries) e.id = 0; // 저장 시 새 id 발급

        ((TextView) findViewById(R.id.reviewMessage)).setText(getIntent().getStringExtra(EXTRA_MESSAGE));
        empty = findViewById(R.id.reviewEmpty);

        RecyclerView list = findViewById(R.id.reviewList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ClassAdapter(entries, true, new ClassAdapter.Listener() {
            @Override public void onClick(int pos) {
                if (pos < 0) return;
                Dialogs.editClass(ReviewActivity.this, entries.get(pos), saved -> {
                    entries.set(pos, saved);
                    sortAndRefresh();
                });
            }
            @Override public void onDelete(int pos) {
                if (pos < 0) return;
                entries.remove(pos);
                sortAndRefresh();
            }
            @Override public void onToggle(int pos, boolean enabled) {}
        });
        list.setAdapter(adapter);
        sortAndRefresh();

        String raw = getIntent().getStringExtra(EXTRA_RAW);
        ((Button) findViewById(R.id.btnRaw)).setOnClickListener(v -> Dialogs.rawText(this, raw));
        ((Button) findViewById(R.id.btnReviewAdd)).setOnClickListener(v ->
                Dialogs.editClass(this, null, saved -> { entries.add(saved); sortAndRefresh(); }));
        ((Button) findViewById(R.id.btnReplace)).setOnClickListener(v -> save(true));
        ((Button) findViewById(R.id.btnAppend)).setOnClickListener(v -> save(false));
    }

    private void save(boolean replace) {
        if (entries.isEmpty()) {
            Toast.makeText(this, "저장할 수업이 없습니다. '+ 수업 추가'로 직접 입력하세요.", Toast.LENGTH_LONG).show();
            return;
        }
        List<ClassEntry> existing = Storage.loadEntries(this);
        if (replace && !existing.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setMessage("기존 시간표 " + existing.size() + "개를 지우고 새 시간표로 바꿀까요?")
                    .setPositiveButton("바꾸기", (d, w) -> commit(new ArrayList<>()))
                    .setNegativeButton("취소", null)
                    .show();
        } else {
            commit(replace ? new ArrayList<>() : existing);
        }
    }

    private void commit(List<ClassEntry> base) {
        base.addAll(entries);
        Storage.saveEntries(this, base);
        setResult(RESULT_OK);
        finish();
    }

    private void sortAndRefresh() {
        entries.sort(Comparator.comparingInt((ClassEntry e) -> e.dayOfWeek).thenComparingInt(e -> e.startMin));
        adapter.notifyDataSetChanged();
        empty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATE_ENTRIES, Storage.toJson(entries));
    }
}
