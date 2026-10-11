package com.friendswine;

/** 三种角色的资源与原始动画参数；枚举序号仅用于本机渲染，不写入存档或网络。 */
public enum GuestVariant {
    KASUMI("kasumi", 99, 10, 10, 9900, 169F / 200F, 1F, 0.23F, 0.16F),
    EMMA("emma", 21, 5, 5, 440, 159F / 185F, 159F / 185F, 0.30F, 0.10F),
    AMIYA("amiya", 27, 6, 5, 2700, 1F, 1F, 0.25F, 0.15F);

    public final String id;
    public final int frames, columns, rows, loopMs;
    public final float idleAspect, danceAspect, handX, handY; // 挂点以高度为 1 格的立板坐标为单位，再与角色一起缩放。

    GuestVariant(String id, int frames, int columns, int rows, int loopMs,
                 float idleAspect, float danceAspect, float handX, float handY) {
        this.id = id; this.frames = frames; this.columns = columns; this.rows = rows; this.loopMs = loopMs;
        this.idleAspect = idleAspect; this.danceAspect = danceAspect; this.handX = handX; this.handY = handY;
    }
}
