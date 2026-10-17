package com.friendswine;

/** 服务端统一决定图片、音效和自动发言的接收者；全服包含所有维度。 */
public enum DeathFlashScope {
    NEARBY, GLOBAL;

    public boolean includes(boolean sameDimension, double distanceSquared) {
        // 三个区块按球形半径 48 格计算，边界包含在内，不采用区块方形范围。
        return this == GLOBAL || sameDimension && distanceSquared <= 48 * 48;
    }

    /** 仅用于磁盘旧配置容错；网络修改必须先校验，不能把非法值当成合法修改。 */
    public static DeathFlashScope fromConfig(String value) {
        try { return valueOf(value); }
        catch (IllegalArgumentException | NullPointerException invalid) { return NEARBY; }
    }
}
