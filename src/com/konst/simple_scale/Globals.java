package com.konst.simple_scale;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import com.konst.module.BootModule;
import com.konst.module.Module;
import com.konst.module.ScaleModule;

/**
 * @author Kostya
 */
public class Globals {
    private static Globals instance = new Globals();
    private ScaleModule scaleModule;
    private BootModule bootModule;
    /** Настройки для весов. */
    private Preferences preferencesScale;
    /** Настройки для обновления весов. */
    PackageInfo packageInfo;
    /** Версия пограммы весового модуля. */
    public final int microSoftware = 4;
    /** Шаг измерения (округление). */
    public int stepMeasuring;
    /** Шаг захвата (округление). */
    public int autoCapture;
    /** Время задержки для авто захвата после которого начинается захват в секундах. */
    public int timeDelayDetectCapture;
    /** Минимальное значение авто захвата веса килограммы. */
    public final int default_min_auto_capture = 20;
    /** Флаг есть соединение. */
    private boolean isScaleConnect;

    public PackageInfo getPackageInfo() {
        return packageInfo;
    }

    public ScaleModule getScaleModule() {
        return scaleModule;
    }

    public void setScaleModule(ScaleModule scaleModule) {
        this.scaleModule = scaleModule;
    }

    public void setStepMeasuring(int stepMeasuring) {
        this.stepMeasuring = stepMeasuring;
    }

    public int getStepMeasuring() {
        return stepMeasuring;
    }

    public void setBootModule(BootModule bootModule) {
        this.bootModule = bootModule;
    }

    public BootModule getBootModule() {
        return bootModule;
    }

    public int getAutoCapture() {
        return autoCapture;
    }

    public void setAutoCapture(int autoCapture) {
        this.autoCapture = autoCapture;
    }

    public Preferences getPreferencesScale() {
        return preferencesScale;
    }

    public boolean isScaleConnect() {
        return isScaleConnect;
    }

    public void setScaleConnect(boolean scaleConnect) {
        isScaleConnect = scaleConnect;
    }

    public void initialize(Context context) {
        /*PreferenceManager.setDefaultValues(this, R.xml.preferences, false);*/
        try {
            PackageManager packageManager = context.getPackageManager();
            packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {  }

        preferencesScale = new Preferences(context.getApplicationContext());//загрузить настройки

        stepMeasuring = preferencesScale.read(context.getString(R.string.KEY_STEP), context.getResources().getInteger(R.integer.default_step_scale));
        autoCapture = preferencesScale.read(context.getString(R.string.KEY_AUTO_CAPTURE), context.getResources().getInteger(R.integer.default_max_auto_capture));
        //scaleModule.setTimerNull(Preferences.read(getString(R.string.KEY_TIMER_NULL), default_max_time_auto_null));
        //scaleModule.setWeightError(Preferences.read(getString(R.string.KEY_MAX_NULL), default_limit_auto_null));
        timeDelayDetectCapture = context.getResources().getInteger(R.integer.time_delay_detect_capture);
    }

    public static Globals getInstance() { return instance; }

    public static void setInstance(Globals instance) { Globals.instance = instance; }
}
