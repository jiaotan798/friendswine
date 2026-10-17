package com.friendswine.client;

import com.friendswine.DeathFlashTimer;
import com.friendswine.FriendsWine;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

/** 只管理当前会话的一次合并闪屏；断线／换世界时清空，不进入存档。 */
public final class ClientDeathFlash {
    private static final Identifier IMAGE = Identifier.fromNamespaceAndPath("friendswine", "textures/gui/guest_death_flash.png");
    private static final DeathFlashTimer TIMER = new DeathFlashTimer();
    private static Object level, connection;
    private static FlashSound sound;
    /** 接收服务端确认的事件；同一事件去重，新事件刷新两秒并重播声音。 */
    public static void start(UUID event, String sourceDimension) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getConnection() == null) return;
        checkSession(mc);
        // 来源维度不是接收者维度：GLOBAL 允许跨维度接收，不能在这里错误过滤。
        if (!TIMER.start(event, System.nanoTime())) return;
        cancelSound(mc);
        sound = new FlashSound();
        mc.getSoundManager().play(sound);
    }

    /** 必须在现有音乐逻辑的暂停／静音 early return 之前运行，真实两秒不依赖唱片状态。 */
    public static void tick(Minecraft mc) {
        checkSession(mc);
        if (mc.player != null && mc.getConnection() != null && TIMER.takeChat()) {
            // 正常签名聊天链路，不改聊天框内容，也不绕过服务端禁言或反刷屏规则。
            mc.getConnection().sendChat("这你冰我");
        }
    }
    /** 在最终 GUI 阶段覆盖画面；到期先停止绘制，再允许下一 tick 发言。 */
    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        checkSession(mc);
        long now = System.nanoTime();
        if (!TIMER.visible(now)) return;
        float opacity = TIMER.opacity(now);
        int color = (Math.round(opacity * 255) << 24) | 0xFFFFFF;
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        double scale = Math.min(width / 1920.0, height / 1080.0);
        int drawnWidth = Math.max(1, (int)Math.round(1920 * scale));
        int drawnHeight = Math.max(1, (int)Math.round(1080 * scale));
        int x = (width - drawnWidth) / 2, y = (height - drawnHeight) / 2;
        // 等比完整显示附件，白边与原图背景一致；最终 GUI 阶段覆盖菜单和隐藏 HUD 的画面。
        graphics.nextStratum();
        // 白边只填图片外部，与图片使用同一透明度，避免重叠混合导致中间区域渐隐变慢。
        graphics.fill(0, 0, width, y, color);
        graphics.fill(0, y + drawnHeight, width, height, color);
        graphics.fill(0, y, x, y + drawnHeight, color);
        graphics.fill(x + drawnWidth, y, width, y + drawnHeight, color);
        // 新 GUI 会按管线批处理排序；图片单独分层，保持最终覆盖阶段的绘制顺序。
        graphics.nextStratum();
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, IMAGE, x, y,
                0F, 0F, drawnWidth, drawnHeight, 1920, 1080, 1920, 1080, color);
    }
    /** 世界或连接变化时同时取消图片、声音和待发言，避免旧事件进入新会话。 */
    private static void checkSession(Minecraft mc) {
        if (level == mc.level && connection == mc.getConnection()) return;
        TIMER.clear();
        cancelSound(mc);
        level = mc.level;
        connection = mc.getConnection();
    }

    private static void cancelSound(Minecraft mc) {
        if (sound == null) return;
        sound.cancel();
        mc.getSoundManager().stop(sound);
        sound = null;
    }

    /** 取消标记也覆盖音频异步解码阶段，连续触发不能让旧声音稍后又开始播放。 */
    private static final class FlashSound extends AbstractTickableSoundInstance {
        FlashSound() {
            super(FriendsWine.GUEST_DEATH_FLASH.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
            volume = 1;
            pitch = 1;
        }
        void cancel() { stop(); }
        @Override public boolean canPlaySound() { return !isStopped(); }
        @Override public void tick() {}
    }
}
