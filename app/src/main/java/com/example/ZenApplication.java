package com.example;

import android.app.Application;

import com.example.util.ThemeHelper;

public class ZenApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ThemeHelper.applySavedThemeMode(this);
    }
}
