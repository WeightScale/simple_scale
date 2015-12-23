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
public class Main extends Application {
    private static Main singleton;
    //private Module Module;
    private ScaleModule scaleModule;
    private BootModule bootModule;
    /**
     * Настройки для весов.
     */
    public static Preferences preferencesScale;
    /**
     * Настройки для обновления весов.
     */
    //public static Preferences preferencesUpdate;

    PackageInfo packageInfo;
    /**
     * Версия пограммы весового модуля.
     */
    public static final int microSoftware = 4;

    /**
     * Шаг измерения (округление).
     */
    public int stepMeasuring;

    /** Шаг захвата (округление). */
    public int autoCapture;

    /**
     * Время задержки для авто захвата после которого начинается захват в секундах.
     */
    public static int timeDelayDetectCapture;
    public static int day_closed;
    public static int day_delete;

    /**
     * Вес максимальный по умолчанию килограммы.
     */
    public static final int default_max_weight = 1000;

    /**
     * Максимальный заряд батареи проценты.
     */
    public static final int default_max_battery = 100;

    /**
     * Максимальное время бездействия весов в минутах.
     */
    public static final int default_max_time_off = 60;

    /**
     * Минимальное время бездействия весов в минутах.
     */
    public static final int default_min_time_off = 10;

    /**
     * Максимальное время срабатывания авто ноль секундах.
     */
    public static final int default_max_time_auto_null = 120;

    /**
     * Предел ошибки при котором срабатывает авто ноль килограммы.
     */
    public static final int default_limit_auto_null = 50;

    /**
     * Максимальный шаг измерения весов килограммы.
     */
    public static final int default_max_step_scale = 20;

    /**
     * Максимальный значение авто захвата веса килограммы.
     */
    public static final int default_max_auto_capture = 100;

    /**
     * Дельта значение авто захвата веса килограммы.
     */
    public static final int default_delta_auto_capture = 10;

    /**
     * Минимальное значение авто захвата веса килограммы.
     */
    public static final int default_min_auto_capture = 20;

    /**
     * Максимальное количество дней для закрытия не закрытых чеков дней.
     */
    protected static final int default_day_close_check = 10;

    /**
     * Максимальное количество дней для удвления чеков дней.
     */
    protected static final int default_day_delete_check = 10;

    /**
     * Максимальное значение фильтра ацп.
     */
    public static final int default_adc_filter = 15;

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

    public Main getInstance(){
        return singleton;
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

    /*public void setModule(com.konst.module.Module module) {
        Module = module;
    }*/

    @Override
    public void onCreate() {
        super.onCreate();
        singleton = this;
        /*PreferenceManager.setDefaultValues(this, R.xml.preferences, false);*/
        try {
            PackageManager packageManager = getPackageManager();
            packageInfo = packageManager.getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {  }

        /*try {
            scaleModule = new ScaleModule(getPackageInfo().versionName);
        } catch (Exception e) {
            System.exit(1);
        }*/

        preferencesScale = new Preferences(getApplicationContext());
        //preferencesUpdate = new Preferences(getApplicationContext(), Preferences.PREF_UPDATE);
        Preferences.load(getSharedPreferences(Preferences.PREFERENCES, Context.MODE_PRIVATE)); //загрузить настройки

        stepMeasuring = Preferences.read(getString(R.string.KEY_STEP), default_max_step_scale);
        autoCapture = Preferences.read(getString(R.string.KEY_AUTO_CAPTURE), default_max_auto_capture);
        day_delete = Preferences.read(getString(R.string.KEY_DAY_CHECK_DELETE), default_day_delete_check);
        day_closed = Preferences.read(getString(R.string.KEY_DAY_CLOSED_CHECK), default_day_close_check);
        //scaleModule.setTimerNull(Preferences.read(getString(R.string.KEY_TIMER_NULL), default_max_time_auto_null));
        //scaleModule.setWeightError(Preferences.read(getString(R.string.KEY_MAX_NULL), default_limit_auto_null));
        timeDelayDetectCapture = Preferences.read(getString(R.string.KEY_TIME_DELAY_DETECT_CAPTURE), 1);
    }


}
