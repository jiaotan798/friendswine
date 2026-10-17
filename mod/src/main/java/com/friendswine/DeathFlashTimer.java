package com.friendswine;

import java.util.UUID;

/** 客户端单调时钟状态：重触发合并为最后一次结束后的单句发言，不建立延迟任务队列。 */
public final class DeathFlashTimer {
    public static final long DURATION_NANOS = 2_000_000_000L;
    private static final long FADE_NANOS = 500_000_000L;
    private UUID lastEvent;
    private long deadline;
    private boolean pending;
    private boolean hidden;

    public boolean start(UUID event, long now) {
        if (event.equals(lastEvent)) return false;
        lastEvent = event;
        deadline = now + DURATION_NANOS;
        pending = true;
        hidden = false;
        return true;
    }

    /** 先在绘制阶段停止覆盖，再允许下一次客户端 tick 发言，避免图片仍显示时提前发送。 */
    public boolean visible(long now) {
        if (!pending) return false;
        if (now - deadline < 0) return true;
        hidden = true;
        return false;
    }

    /** 前 1.5 秒保持不透明，最后 0.5 秒线性渐隐；只读取状态，不提前放行待发言。 */
    public float opacity(long now) {
        if (!pending) return 0;
        return Math.max(0, Math.min(1, (deadline - now) / (float) FADE_NANOS));
    }

    public boolean takeChat() {
        if (!pending || !hidden) return false;
        pending = false;
        return true;
    }

    public void clear() {
        lastEvent = null;
        pending = false;
        hidden = false;
    }

    public static void main(String[] args) {
        var timer = new DeathFlashTimer();
        var first = UUID.randomUUID();
        assert timer.start(first, 0) && !timer.start(first, 1);
        assert timer.opacity(0) == 1 && timer.opacity(1_500_000_000L) == 1;
        assert timer.opacity(1_750_000_000L) == .5F && !timer.takeChat();
        assert timer.opacity(2_000_000_000L) == 0 && !timer.takeChat();
        assert timer.visible(1_999_999_999L) && !timer.takeChat();
        assert timer.start(UUID.randomUUID(), 1_500_000_000L);
        assert timer.opacity(2_000_000_000L) == 1;
        assert timer.visible(2_000_000_000L) && !timer.takeChat();
        assert !timer.visible(3_500_000_000L) && timer.takeChat() && !timer.takeChat();
        timer.start(UUID.randomUUID(), 4_000_000_000L);
        timer.clear();
        assert !timer.visible(9_000_000_000L) && timer.opacity(9_000_000_000L) == 0 && !timer.takeChat();
        timer.start(UUID.randomUUID(), Long.MAX_VALUE - 1_000_000_000L);
        assert timer.opacity(Long.MIN_VALUE + 749_999_999L) == .5F;
        assert !timer.visible(Long.MIN_VALUE + 999_999_999L) && timer.takeChat();
        assert DeathFlashScope.NEARBY.includes(true, 48 * 48);
        assert !DeathFlashScope.NEARBY.includes(true, 48 * 48 + .01);
        assert !DeathFlashScope.NEARBY.includes(false, 0);
        assert DeathFlashScope.GLOBAL.includes(false, 1e12);
        assert DeathFlashScope.fromConfig(null) == DeathFlashScope.NEARBY;
        assert DeathFlashScope.fromConfig("invalid") == DeathFlashScope.NEARBY;
        System.out.println("PASS death flash duration, fade, refresh, duplicate, cleanup and scope boundaries");
    }
}
