package com.example.timetablealarm;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;

/** 수업 목록 어댑터. 메인 화면은 켜기/끄기 스위치, 검토 화면은 삭제 버튼을 보여준다. */
public class ClassAdapter extends RecyclerView.Adapter<ClassAdapter.VH> {

    public interface Listener {
        void onClick(int position);
        void onDelete(int position);
        void onToggle(int position, boolean enabled);
    }

    private final List<ClassEntry> items;
    private final boolean reviewMode;
    private final Listener listener;

    public ClassAdapter(List<ClassEntry> items, boolean reviewMode, Listener listener) {
        this.items = items;
        this.reviewMode = reviewMode;
        this.listener = listener;
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView day, subject, detail;
        final MaterialSwitch toggle;
        final ImageButton delete;

        VH(View v) {
            super(v);
            day = v.findViewById(R.id.itemDay);
            subject = v.findViewById(R.id.itemSubject);
            detail = v.findViewById(R.id.itemDetail);
            toggle = v.findViewById(R.id.itemToggle);
            delete = v.findViewById(R.id.itemDelete);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_class, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ClassEntry e = items.get(position);
        h.day.setText(e.dayName());
        h.subject.setText(e.subject);
        String memo = e.memo == null || e.memo.isEmpty() ? "" : "  ·  " + e.memo;
        h.detail.setText(e.timeRange() + memo);
        h.itemView.setAlpha(reviewMode || e.enabled ? 1f : 0.45f);
        h.itemView.setOnClickListener(v -> listener.onClick(h.getBindingAdapterPosition()));

        if (reviewMode) {
            h.toggle.setVisibility(View.GONE);
            h.delete.setVisibility(View.VISIBLE);
            h.delete.setOnClickListener(v -> listener.onDelete(h.getBindingAdapterPosition()));
        } else {
            h.delete.setVisibility(View.GONE);
            h.toggle.setVisibility(View.VISIBLE);
            h.toggle.setOnCheckedChangeListener(null);
            h.toggle.setChecked(e.enabled);
            h.toggle.setOnCheckedChangeListener((b, checked) -> listener.onToggle(h.getBindingAdapterPosition(), checked));
            h.itemView.setOnLongClickListener(v -> {
                listener.onDelete(h.getBindingAdapterPosition());
                return true;
            });
        }
    }

    @Override
    public int getItemCount() { return items.size(); }
}
