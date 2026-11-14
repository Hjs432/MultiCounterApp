package com.example.multicounter.adapter;

import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import java.util.ArrayList;
import java.util.List;

/**
 * 卡片增删动画：
 * <p>
 * - 新增：淡入（alpha 0 → 1）+ 自下而上浮入（translationY 由 +dy → 0）
 * - 删除：淡出（alpha 1 → 0）+ 向下滑出（translationY 由 0 → +dy）
 * - 位移：其它卡片平滑补位，避免跳变闪烁
 * <p>
 * 每个动画结束都会派发对应的 finished 回调，并在全部结束后通知 RecyclerView，
 * 保证 item 状态与布局严格一致，不会出现错位或闪烁。
 * 内容变更动画已关闭（避免刷新计数时闪一下）。
 */
public class CardItemAnimator extends SimpleItemAnimator {

    private static final long ADD_DURATION = 260L;
    private static final long REMOVE_DURATION = 220L;
    private static final long MOVE_DURATION = 200L;

    /** 出入场时的垂直位移量（像素） */
    private final float translateY;

    private final Interpolator addInterpolator = new DecelerateInterpolator(1.8f);
    private final Interpolator removeInterpolator = new DecelerateInterpolator(1.2f);
    private final Interpolator moveInterpolator = new DecelerateInterpolator(1.4f);

    private final List<RecyclerView.ViewHolder> pendingAdds = new ArrayList<>();
    private final List<RecyclerView.ViewHolder> pendingRemoves = new ArrayList<>();
    private final List<RecyclerView.ViewHolder> pendingMoves = new ArrayList<>();

    private final List<RecyclerView.ViewHolder> runningAdds = new ArrayList<>();
    private final List<RecyclerView.ViewHolder> runningRemoves = new ArrayList<>();
    private final List<RecyclerView.ViewHolder> runningMoves = new ArrayList<>();

    public CardItemAnimator(float translateY) {
        this.translateY = translateY;
        setSupportsChangeAnimations(false);
        setAddDuration(ADD_DURATION);
        setRemoveDuration(REMOVE_DURATION);
        setMoveDuration(MOVE_DURATION);
    }

    // ==================== 入队 ====================

    @Override
    public boolean animateRemove(@NonNull RecyclerView.ViewHolder holder) {
        endAnimation(holder);
        pendingRemoves.add(holder);
        return true;
    }

    @Override
    public boolean animateAdd(@NonNull RecyclerView.ViewHolder holder) {
        endAnimation(holder);
        // 起始状态：完全透明且位于下方，随后向上浮入
        holder.itemView.setAlpha(0f);
        holder.itemView.setTranslationY(translateY);
        pendingAdds.add(holder);
        return true;
    }

    @Override
    public boolean animateMove(@NonNull RecyclerView.ViewHolder holder,
                               int fromX, int fromY, int toX, int toY) {
        final View view = holder.itemView;
        final float deltaX = toX - fromX;
        final float deltaY = toY - fromY;
        if (deltaX == 0f && deltaY == 0f) {
            // 位置未变化，无需动画
            dispatchMoveFinished(holder);
            return false;
        }
        view.setTranslationX(deltaX);
        view.setTranslationY(deltaY);
        pendingMoves.add(holder);
        return true;
    }

    @Override
    public boolean animateChange(@NonNull RecyclerView.ViewHolder oldHolder,
                                 @NonNull RecyclerView.ViewHolder newHolder,
                                 int fromX, int fromY, int toX, int toY) {
        // 关闭内容切换动画：同一 holder 复用则只处理位置变化，否则直接结束
        if (oldHolder == newHolder) {
            return animateMove(oldHolder, fromX, fromY, toX, toY);
        }
        dispatchChangeFinished(oldHolder, true);
        dispatchChangeFinished(newHolder, false);
        return false;
    }

    // ==================== 执行 ====================

    @Override
    public void runPendingAnimations() {
        if (pendingMoves.isEmpty() && pendingRemoves.isEmpty() && pendingAdds.isEmpty()) {
            return;
        }
        // 先整体取出，再逐类播放，避免播放过程中修改队列
        final List<RecyclerView.ViewHolder> moves = new ArrayList<>(pendingMoves);
        final List<RecyclerView.ViewHolder> removes = new ArrayList<>(pendingRemoves);
        final List<RecyclerView.ViewHolder> adds = new ArrayList<>(pendingAdds);
        pendingMoves.clear();
        pendingRemoves.clear();
        pendingAdds.clear();

        for (RecyclerView.ViewHolder holder : moves) {
            animateMoveImpl(holder);
        }
        for (RecyclerView.ViewHolder holder : removes) {
            animateRemoveImpl(holder);
        }
        for (RecyclerView.ViewHolder holder : adds) {
            animateAddImpl(holder);
        }
    }

    private void animateAddImpl(final RecyclerView.ViewHolder holder) {
        final View view = holder.itemView;
        runningAdds.add(holder);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(ADD_DURATION)
                .setInterpolator(addInterpolator)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (runningAdds.remove(holder)) {
                            view.setAlpha(1f);
                            view.setTranslationY(0f);
                            dispatchAddFinished(holder);
                            dispatchFinishedWhenDone();
                        }
                    }
                })
                .start();
    }

    private void animateRemoveImpl(final RecyclerView.ViewHolder holder) {
        final View view = holder.itemView;
        runningRemoves.add(holder);
        view.animate()
                .alpha(0f)
                .translationY(translateY)
                .setDuration(REMOVE_DURATION)
                .setInterpolator(removeInterpolator)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (runningRemoves.remove(holder)) {
                            // 复位，保证 holder 被回收复用时状态干净
                            view.setAlpha(1f);
                            view.setTranslationY(0f);
                            dispatchRemoveFinished(holder);
                            dispatchFinishedWhenDone();
                        }
                    }
                })
                .start();
    }

    private void animateMoveImpl(final RecyclerView.ViewHolder holder) {
        final View view = holder.itemView;
        runningMoves.add(holder);
        view.animate()
                .translationX(0f)
                .translationY(0f)
                .setDuration(MOVE_DURATION)
                .setInterpolator(moveInterpolator)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (runningMoves.remove(holder)) {
                            view.setTranslationX(0f);
                            view.setTranslationY(0f);
                            dispatchMoveFinished(holder);
                            dispatchFinishedWhenDone();
                        }
                    }
                })
                .start();
    }

    // ==================== 结束 / 取消 ====================

    /** 仅当所有动画都已结束时才派发总回调，保证与 RecyclerView 的状态同步 */
    private void dispatchFinishedWhenDone() {
        if (!isRunning()) {
            dispatchAnimationsFinished();
        }
    }

    @Override
    public void endAnimation(RecyclerView.ViewHolder holder) {
        final View view = holder.itemView;
        // 先出队再取消动画，避免 withEndAction 重复派发 finished 回调
        boolean wasAdd = pendingAdds.remove(holder) | runningAdds.remove(holder);
        boolean wasRemove = pendingRemoves.remove(holder) | runningRemoves.remove(holder);
        boolean wasMove = pendingMoves.remove(holder) | runningMoves.remove(holder);

        view.animate().cancel();
        view.setAlpha(1f);
        view.setTranslationX(0f);
        view.setTranslationY(0f);

        if (wasMove) {
            dispatchMoveFinished(holder);
        }
        if (wasAdd) {
            dispatchAddFinished(holder);
        }
        if (wasRemove) {
            dispatchRemoveFinished(holder);
        }
        dispatchFinishedWhenDone();
    }

    @Override
    public void endAnimations() {
        final List<RecyclerView.ViewHolder> snapshot = new ArrayList<>();
        snapshot.addAll(runningAdds);
        snapshot.addAll(runningRemoves);
        snapshot.addAll(runningMoves);
        snapshot.addAll(pendingAdds);
        snapshot.addAll(pendingRemoves);
        snapshot.addAll(pendingMoves);
        for (RecyclerView.ViewHolder holder : snapshot) {
            endAnimation(holder);
        }
        dispatchAnimationsFinished();
    }

    @Override
    public boolean isRunning() {
        return !pendingAdds.isEmpty() || !pendingRemoves.isEmpty() || !pendingMoves.isEmpty()
                || !runningAdds.isEmpty() || !runningRemoves.isEmpty() || !runningMoves.isEmpty();
    }
}
