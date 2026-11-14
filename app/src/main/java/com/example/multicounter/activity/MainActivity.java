package com.example.multicounter.activity;

import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.MenuItem;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.multicounter.R;
import com.example.multicounter.adapter.CardItemAnimator;
import com.example.multicounter.adapter.CounterAdapter;
import com.example.multicounter.model.Counter;
import com.example.multicounter.utils.CounterStore;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 唯一主页面：
 * <p>
 * - 顶部栏：标题「多计数器」+ 右上角加号按钮（点击弹出下拉菜单）
 * - 主体：2 列网格的计数器卡片
 * <p>
 * 下拉菜单三项：添加计数器 / 修改名称 / 删除计数器。
 */
public class MainActivity extends AppCompatActivity implements CounterAdapter.OnCounterActionListener {

    /** 网格列数：一行最多 2 张卡片 */
    private static final int GRID_SPAN_COUNT = 2;

    /** 单次批量新增的上限，防止极端输入造成卡顿 */
    private static final int MAX_ADD_COUNT = 100;

    /** 倒计时状态：未开始 */
    private static final int TIMER_IDLE = 0;
    /** 倒计时状态：进行中（圆球绿色） */
    private static final int TIMER_RUNNING = 1;
    /** 倒计时状态：已暂停（圆球红色） */
    private static final int TIMER_PAUSED = 2;

    private RecyclerView recyclerView;
    private CounterAdapter adapter;
    private CounterStore counterStore;

    private List<Counter> counters = new ArrayList<>();
    /** 自增 id 生成器，保证本次运行内新建对象标识唯一 */
    private long idGenerator;

    /** 当前交互模式 */
    private int mode = CounterAdapter.MODE_NORMAL;
    /** 复用同一个提示条，避免连续操作时提示排队堆积 */
    private Snackbar hintSnackbar;

    /** 倒计时圆球与左右按钮 */
    private TextView timerBall;
    private MaterialButton btnTimerStart;
    private MaterialButton btnTimerPause;
    private CountDownTimer countDownTimer;
    private int timerState = TIMER_IDLE;
    /** 用户设置的倒计时总时长，默认 1 分钟 */
    private long timerDurationMs = 60_000L;
    /** 当前剩余时间（运行 / 暂停时随 tick 更新） */
    private long timerRemainingMs = 60_000L;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        counterStore = new CounterStore(this);
        loadCounters();

        setupToolbar();
        setupRecyclerView();
        setupTimer();
    }

    @Override
    protected void onDestroy() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        super.onDestroy();
    }

    // ==================== 初始化 ====================

    /** 读取本地数据；首次启动（或数据为空）时自动创建「计数器一」 */
    private void loadCounters() {
        counters = counterStore.loadCounters();

        long maxId = 0L;
        for (Counter counter : counters) {
            maxId = Math.max(maxId, counter.getId());
        }
        idGenerator = maxId;

        if (counters.isEmpty()) {
            counters.add(new Counter(nextId(), getString(R.string.default_counter_name), 0));
            counterStore.saveCounters(counters);
        }
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_menu) {
                showActionPopup(resolveMenuAnchor(toolbar));
                return true;
            }
            return false;
        });
    }

    private void setupRecyclerView() {
        recyclerView = findViewById(R.id.recycler_counters);
        recyclerView.setLayoutManager(new GridLayoutManager(this, GRID_SPAN_COUNT));
        // 出入场动画位移量：约 28dp
        recyclerView.setItemAnimator(new CardItemAnimator(dp(28f)));
        recyclerView.setHasFixedSize(false);

        adapter = new CounterAdapter(counters, this);
        recyclerView.setAdapter(adapter);
    }

    /**
     * 加号按钮点击后弹出的下拉菜单，包含三项操作。
     * 已处于某个管理模式时，菜单项文案变为「退出 xx 模式」，再次点击即可退出。
     */
    private void showActionPopup(@NonNull View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.inflate(R.menu.menu_counter_actions);

        MenuItem renameItem = popupMenu.getMenu().findItem(R.id.action_rename);
        if (renameItem != null && mode == CounterAdapter.MODE_EDIT_NAME) {
            renameItem.setTitle(R.string.action_rename_exit);
        }
        MenuItem deleteItem = popupMenu.getMenu().findItem(R.id.action_delete);
        if (deleteItem != null && mode == CounterAdapter.MODE_DELETE) {
            deleteItem.setTitle(R.string.action_delete_exit);
        }

        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_add_counter) {
                showAddCountersDialog();
                return true;
            }
            if (itemId == R.id.action_rename) {
                toggleEditNameMode();
                return true;
            }
            if (itemId == R.id.action_delete) {
                toggleDeleteMode();
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    /** 取加号菜单项所在的 View 作为下拉菜单锚点，保证菜单紧贴按钮弹出 */
    @NonNull
    private View resolveMenuAnchor(@NonNull MaterialToolbar toolbar) {
        MenuItem menuItem = toolbar.getMenu().findItem(R.id.action_menu);
        if (menuItem != null) {
            View actionView = menuItem.getActionView();
            if (actionView != null) {
                return actionView;
            }
        }
        View itemView = toolbar.findViewById(R.id.action_menu);
        return itemView != null ? itemView : toolbar;
    }

    // ==================== 菜单功能 1：添加计数器 ====================

    private void showAddCountersDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_add_counter, null);
        final TextInputLayout inputLayout = content.findViewById(R.id.input_layout);
        final TextInputEditText inputCount = content.findViewById(R.id.input_count);
        inputCount.setText("1");
        inputCount.setSelection(inputCount.getText().length());

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_add_title)
                .setView(content)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_confirm, null)
                .create();

        // 自行接管确认按钮：校验不通过时保持弹窗打开
        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    int amount = parseCount(inputCount.getText() == null
                            ? "" : inputCount.getText().toString());
                    if (amount < 1 || amount > MAX_ADD_COUNT) {
                        inputLayout.setError(getString(R.string.error_add_count));
                        return;
                    }
                    inputLayout.setError(null);
                    addCounters(amount);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    /** 批量新增并按顺序自动命名：计数器2、计数器3…… */
    private void addCounters(int amount) {
        int startPosition = counters.size();
        int nextIndex = counterStore.getNextIndex();

        for (int i = 0; i < amount; i++) {
            String name = getString(R.string.counter_name_prefix, nextIndex);
            counters.add(new Counter(nextId(), name, 0));
            nextIndex++;
        }

        counterStore.setNextIndex(nextIndex);
        counterStore.saveCounters(counters);
        adapter.notifyItemRangeInserted(startPosition, amount);

        // 新增卡片可能位于屏幕之外，平滑滚动到末尾让用户第一时间看到
        recyclerView.post(() -> recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1));
    }

    // ==================== 菜单功能 2：修改名称 ====================

    /** 进入 / 退出修改名称模式 */
    private void toggleEditNameMode() {
        if (mode == CounterAdapter.MODE_EDIT_NAME) {
            switchMode(CounterAdapter.MODE_NORMAL);
            showHint(getString(R.string.tip_mode_exit));
            return;
        }
        switchMode(CounterAdapter.MODE_EDIT_NAME);
        showHint(getString(R.string.tip_edit_mode));
    }

    private void showRenameDialog(@NonNull final Counter counter) {
        View content = getLayoutInflater().inflate(R.layout.dialog_rename, null);
        final TextInputLayout inputLayout = content.findViewById(R.id.input_layout);
        final TextInputEditText inputName = content.findViewById(R.id.input_name);
        inputName.setText(counter.getName());
        inputName.setSelection(inputName.getText().length());

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_rename_title)
                .setView(content)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_confirm, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String newName = inputName.getText() == null
                            ? "" : inputName.getText().toString().trim();
                    if (TextUtils.isEmpty(newName)) {
                        inputLayout.setError(getString(R.string.error_name_empty));
                        return;
                    }
                    inputLayout.setError(null);
                    renameCounter(counter, newName);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void renameCounter(@NonNull Counter counter, @NonNull String newName) {
        int position = counters.indexOf(counter);
        if (position < 0) {
            return;
        }
        counter.setName(newName);
        counterStore.saveCounters(counters);
        adapter.notifyItemChanged(position);
    }

    // ==================== 菜单功能 3：删除计数器 ====================

    /** 进入 / 退出删除模式 */
    private void toggleDeleteMode() {
        if (mode == CounterAdapter.MODE_DELETE) {
            switchMode(CounterAdapter.MODE_NORMAL);
            showHint(getString(R.string.tip_mode_exit));
            return;
        }
        switchMode(CounterAdapter.MODE_DELETE);
        showHint(getString(R.string.tip_delete_mode));
    }

    private void showDeleteConfirmDialog(@NonNull final Counter counter) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(getString(R.string.dialog_delete_message, counter.getName()))
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_confirm, (d, which) -> deleteCounter(counter))
                .show();
    }

    /** 执行删除并触发卡片退场动画；始终保留最后一个计数器 */
    private void deleteCounter(@NonNull Counter counter) {
        if (counters.size() <= 1) {
            showHint(getString(R.string.tip_last_counter));
            return;
        }
        int position = counters.indexOf(counter);
        if (position < 0) {
            return;
        }
        counters.remove(position);
        counterStore.saveCounters(counters);
        adapter.notifyItemRemoved(position);
    }

    private void switchMode(int newMode) {
        mode = newMode;
        adapter.setMode(newMode);
    }

    // ==================== 卡片交互回调 ====================

    @Override
    public void onCountClick(int position) {
        Counter counter = counters.get(position);
        counter.increment();
        counterStore.saveCounters(counters);
        adapter.notifyItemChanged(position);
    }

    @Override
    public void onResetClick(int position) {
        final Counter counter = counters.get(position);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_reset_title)
                .setMessage(getString(R.string.dialog_reset_message, counter.getName()))
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_confirm, (d, which) -> {
                    counter.reset();
                    counterStore.saveCounters(counters);
                    adapter.notifyItemChanged(counters.indexOf(counter));
                })
                .show();
    }

    @Override
    public void onNameClick(int position) {
        if (mode != CounterAdapter.MODE_EDIT_NAME) {
            return;
        }
        showRenameDialog(counters.get(position));
    }

    @Override
    public void onCardClick(int position) {
        if (mode != CounterAdapter.MODE_DELETE) {
            return;
        }
        if (counters.size() <= 1) {
            showHint(getString(R.string.tip_last_counter));
            return;
        }
        showDeleteConfirmDialog(counters.get(position));
    }

    // ==================== 底部倒计时 ====================

    private void setupTimer() {
        timerBall = findViewById(R.id.timer_ball);
        btnTimerStart = findViewById(R.id.btn_timer_start);
        btnTimerPause = findViewById(R.id.btn_timer_pause);

        btnTimerStart.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            startOrResumeTimer();
        });
        btnTimerPause.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            pauseOrClearTimer();
        });
        timerBall.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showTimerSettingDialog();
        });

        // 等宽数字：倒计时跳动时数字宽度不抖动
        timerBall.setFontFeatureSettings("tnum");
        updateTimerUi();
    }

    /** 左按钮：未开始时启动，暂停时继续 */
    private void startOrResumeTimer() {
        if (timerState == TIMER_RUNNING) {
            return;
        }
        if (timerState == TIMER_IDLE) {
            if (timerDurationMs <= 0L) {
                showHint(getString(R.string.tip_timer_zero));
                showTimerSettingDialog();
                return;
            }
            timerRemainingMs = timerDurationMs;
        }
        countDownTimer = new CountDownTimer(timerRemainingMs, 100L) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerRemainingMs = millisUntilFinished;
                updateTimerUi();
            }

            @Override
            public void onFinish() {
                countDownTimer = null;
                timerState = TIMER_IDLE;
                timerRemainingMs = timerDurationMs;
                updateTimerUi();
                timerBall.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                showHint(getString(R.string.tip_timer_finished));
            }
        }.start();
        timerState = TIMER_RUNNING;
        updateTimerUi();
    }

    /** 右按钮：进行中点击为暂停；暂停时点击为清零（回到未开始） */
    private void pauseOrClearTimer() {
        if (timerState == TIMER_RUNNING) {
            if (countDownTimer != null) {
                countDownTimer.cancel();
                countDownTimer = null;
            }
            timerState = TIMER_PAUSED;
            updateTimerUi();
        } else if (timerState == TIMER_PAUSED) {
            timerState = TIMER_IDLE;
            timerRemainingMs = timerDurationMs;
            updateTimerUi();
        }
    }

    /** 点击圆球弹出设置弹窗；设置新时长后回到未开始状态 */
    private void showTimerSettingDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_timer_setting, null);
        final NumberPicker pickerMinutes = content.findViewById(R.id.picker_minutes);
        final NumberPicker pickerSeconds = content.findViewById(R.id.picker_seconds);

        pickerMinutes.setMinValue(0);
        pickerMinutes.setMaxValue(99);
        pickerSeconds.setMinValue(0);
        pickerSeconds.setMaxValue(59);
        pickerSeconds.setFormatter(value ->
                String.format(Locale.getDefault(), "%02d", value));

        long totalSeconds = timerDurationMs / 1000L;
        pickerMinutes.setValue((int) (totalSeconds / 60L));
        pickerSeconds.setValue((int) (totalSeconds % 60L));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_timer_title)
                .setView(content)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_confirm, (d, which) -> {
                    if (countDownTimer != null) {
                        countDownTimer.cancel();
                        countDownTimer = null;
                    }
                    timerDurationMs = (pickerMinutes.getValue() * 60L
                            + pickerSeconds.getValue()) * 1000L;
                    timerRemainingMs = timerDurationMs;
                    timerState = TIMER_IDLE;
                    updateTimerUi();
                })
                .show();
    }

    /** 按当前状态刷新圆球颜色、剩余时间与按钮文案 */
    private void updateTimerUi() {
        long shownMs = timerState == TIMER_IDLE ? timerDurationMs : timerRemainingMs;
        timerBall.setText(formatDuration(shownMs));

        int fillColor;
        int textColor;
        if (timerState == TIMER_RUNNING) {
            fillColor = ContextCompat.getColor(this, R.color.timer_running);
            textColor = ContextCompat.getColor(this, R.color.md_on_primary);
        } else if (timerState == TIMER_PAUSED) {
            fillColor = ContextCompat.getColor(this, R.color.md_error);
            textColor = ContextCompat.getColor(this, R.color.md_on_primary);
        } else {
            fillColor = ContextCompat.getColor(this, R.color.md_surface_container);
            textColor = ContextCompat.getColor(this, R.color.md_on_surface);
        }

        GradientDrawable ball = new GradientDrawable();
        ball.setShape(GradientDrawable.OVAL);
        ball.setColor(fillColor);
        if (timerState == TIMER_IDLE) {
            ball.setStroke((int) dp(1f), ContextCompat.getColor(this, R.color.md_hairline));
        }
        timerBall.setBackground(ball);
        timerBall.setTextColor(textColor);

        btnTimerStart.setText(timerState == TIMER_PAUSED
                ? R.string.timer_resume : R.string.timer_start);
        btnTimerPause.setText(timerState == TIMER_PAUSED
                ? R.string.timer_clear : R.string.timer_pause);
        btnTimerPause.setEnabled(timerState != TIMER_IDLE);
    }

    /** 毫秒转 mm:ss，剩余时间向上取整避免最后一秒提前显示 0 */
    private static String formatDuration(long ms) {
        long totalSeconds = (ms + 999L) / 1000L;
        return String.format(Locale.getDefault(), "%02d:%02d",
                totalSeconds / 60L, totalSeconds % 60L);
    }

    // ==================== 工具方法 ====================

    /** 生成本次运行内唯一的新 id */
    private long nextId() {
        return ++idGenerator;
    }

    private static int parseCount(@NonNull String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    /** 统一的轻量提示（复用同一个 Snackbar，避免连续操作时排队堆积） */
    private void showHint(@NonNull CharSequence text) {
        if (hintSnackbar != null) {
            hintSnackbar.dismiss();
        }
        hintSnackbar = Snackbar.make(recyclerView, text, Snackbar.LENGTH_SHORT);
        hintSnackbar.show();
    }
}
