package com.craftion.farmer.gui;

public interface FarmerMenu {

    String id();

    FarmerMenuAccess requiredAccess();

    default void render(MenuRenderContext context, MenuLayoutBuilder builder) {
    }
}
