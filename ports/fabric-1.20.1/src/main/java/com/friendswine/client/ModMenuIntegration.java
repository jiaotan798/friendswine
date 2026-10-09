package com.friendswine.client;
import com.terraformersmc.modmenu.api.*;
public final class ModMenuIntegration implements ModMenuApi {
    public ConfigScreenFactory<?> getModConfigScreenFactory() { return SettingsScreen::new; }
}
