package com.example.multicounter.adapter;

import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.multicounter.R;
import com.example.multicounter.model.Counter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

/**
 * 计数器网格适配器：渲染单张计数器卡片，并对外抛出交互事件。
 * <p>
 * 三种模式：
 * - {@link #MODE_NORMAL}：正常计数
 * - {@link #MODE_EDIT_NAME}：点击卡片名称可改名（名称高亮）
 * - {@link #MODE_DELETE}：点击卡片可删除（卡片描边标红）
 */
public class CounterAdapter extends RecyclerView.Adapter<CounterAdapter.CounterViewHolder> {

    public static final int MODE_NORMAL = 0;
    public static final int MODE_EDIT_NAME = 1;
    public static final int MODE_DELETE = 2;

    /** 卡片交互回调 */
    public interface OnCounterActionListener {
        /** 点击「计数」按钮 */
        void onCountClick(int position);

        /** 点击「清零」按钮 */
        void onResetClick(int position);

        /** 点击计数器名称（修改名称模式下生效） */
        void onNameClick(int position);

        /** 点击卡片本体（删除模式下生效） */
        void onCardClick(int position);
    }

    private final List<Counter> counters;
    private final OnCounterActionListener listener;
    private int mode = MODE_NORMAL;

    public CounterAdapter(List<Counter> counters, OnCounterActionListener listener) {
        this.counters = counters;
        this.listener = listener;
    }

    /** 切换交互模式并刷新卡片外观 */
    public void setMode(int mode) {
        if (this.mode == mode) {
            return;
        }
        this.mode = mode;
        notifyItemRangeChanged(0, getItemCount());
    }

    public int getMode() {
        return mode;
    }

    @NonNull
    @Override
    public CounterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_counter, parent, false);
        return new CounterViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull CounterViewHolder holder, int position) {
        holder.bind(counters.get(position), mode, listener);
    }

    @Override
    public int getItemCount() {
        return counters.size();
    }

    /** 单张计数器卡片 */
    public static class CounterViewHolder extends RecyclerView.ViewHolder {

        private final MaterialCardView cardView;
        private final TextView tvName;
        private final TextView tvCount;
        private final MaterialButton btnCount;
        private final MaterialButton btnReset;

        CounterViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_container);
            tvName = itemView.findViewById(R.id.tv_name);
            tvCount = itemView.findViewById(R.id.tv_count);
            btnCount = itemView.findViewById(R.id.btn_count);
            btnReset = itemView.findViewById(R.id.btn_reset);

            // 触摸按压缩放反馈（返回 false 不拦截事件，点击回调正常触发）
            attachPressScale(btnCount, 0.92f);
            attachPressScale(btnReset, 0.92f);
            attachPressScale(cardView, 0.97f);
        }

        void bind(final Counter counter, final int mode, final OnCounterActionListener listener) {
            tvName.setText(counter.getName());
            tvCount.setText(String.valueOf(counter.getCount()));
            applyModeStyle(mode);

            btnCount.setOnClickListener(v -> {
                // 短促触感反馈，强化「计一下数」的确认感
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onCountClick(position);
                }
            });
            btnReset.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onResetClick(position);
                }
            });
            tvName.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && mode == MODE_EDIT_NAME) {
                    listener.onNameClick(position);
                }
            });
            cardView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && mode == MODE_DELETE) {
                    listener.onCardClick(position);
                }
            });
        }

        /** 根据当前模式更新卡片视觉状态，让用户清楚“点哪里会产生什么效果” */
        private void applyModeStyle(int mode) {
            Context context = itemView.getContext();
            switch (mode) {
                case MODE_EDIT_NAME:
                    cardView.setStrokeWidth((int) dp(context, 2));
                    cardView.setStrokeColor(ContextCompat.getColor(context, R.color.md_primary));
                    tvName.setBackgroundResource(R.drawable.bg_name_highlight);
                    break;
                case MODE_DELETE:
                    cardView.setStrokeWidth((int) dp(context, 2));
                    cardView.setStrokeColor(ContextCompat.getColor(context, R.color.md_error));
                    tvName.setBackground(null);
                    break;
                case MODE_NORMAL:
                default:
                    // 常态保留 1dp 发丝描边，扁平卡片靠细线分层
                    cardView.setStrokeWidth((int) dp(context, 1));
                    cardView.setStrokeColor(ContextCompat.getColor(context, R.color.md_hairline));
                    tvName.setBackground(null);
                    break;
            }
        }

        private static void attachPressScale(final View view, final float pressedScale) {
            view.setOnTouchListener((v, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.animate()
                                .scaleX(pressedScale)
                                .scaleY(pressedScale)
                                .setDuration(90L)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(180L)
                                .setInterpolator(new OvershootInterpolator(2.6f))
                                .start();
                        break;
                    default:
                        break;
                }
                // 不消费触摸事件，保证 OnClickListener 与 RecyclerView 滚动正常
                return false;
            });
        }

        private static float dp(Context context, float value) {
            return value * context.getResources().getDisplayMetrics().density;
        }
    }
}
