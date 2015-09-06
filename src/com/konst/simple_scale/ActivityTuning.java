//Активность для стартовой настройки весов
package com.konst.simple_scale;

//import android.content.SharedPreferences;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import com.konst.module.InterfaceVersions;
import com.konst.module.ScaleModule;
import com.konst.simple_scale.bootloader.ActivityBootloader;

import java.math.BigDecimal;
import java.security.Signer;
import java.util.HashMap;
import java.util.Map;

//import android.preference.PreferenceManager;

public class ActivityTuning extends PreferenceActivity {
    private final Point point1 = new Point(Integer.MIN_VALUE, 0);
    private final Point point2 = new Point(Integer.MIN_VALUE, 0);
    private boolean flag_restore;
    //boolean flag_change = false;
    final Map<String, InterfacePreference> mapTuning = new HashMap<>();
    interface InterfacePreference {
        void setup(Preference name) throws Exception;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mapTuning.put(getString(R.string.KEY_POINT1), new Point1());
        mapTuning.put(getString(R.string.KEY_POINT2), new Point2());
        mapTuning.put(getString(R.string.KEY_WEIGHT_MAX), new WeightMax());
        mapTuning.put(getString(R.string.KEY_COEFFICIENT_A), new CoefficientA());
        mapTuning.put(getString(R.string.KEY_CALL_BATTERY), new CallBattery());
        mapTuning.put(getString(R.string.KEY_SERVICE_COD), new ServiceCod());
        mapTuning.put(getString(R.string.KEY_UPDATE), new Update());

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName("my_preferences");
        preferenceManager.setSharedPreferencesMode(MODE_PRIVATE);
        addPreferencesFromResource(R.xml.tuning);

        process();
    }

    void process() {
        for (Map.Entry<String, InterfacePreference> preferenceEntry : mapTuning.entrySet()) {
            Preference name = findPreference(preferenceEntry.getKey());
            if (name != null) {
                try {
                    preferenceEntry.getValue().setup(name);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class Point1 implements InterfacePreference{
        @Override
        public void setup(Preference name) throws Exception {
            name.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    try {
                        String str = ScaleModule.feelWeightSensor();
                        ScaleModule.setSensorTenzo(Integer.valueOf(str));
                        point1.x = Integer.valueOf(str);
                        point1.y = 0;
                        Toast.makeText(getApplicationContext(), R.string.preferences_yes, Toast.LENGTH_SHORT).show();
                        flag_restore = true;
                        return true;
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), R.string.preferences_no + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }
            });
        }
    }

    class Point2 implements InterfacePreference{
        @Override
        public void setup(Preference name) throws Exception {
            name.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    try {
                        String str = ScaleModule.feelWeightSensor();
                        if (str.isEmpty()) {
                            Toast.makeText(getApplicationContext(), R.string.preferences_no, Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        ScaleModule.setSensorTenzo(Integer.valueOf(str));
                        point2.x = Integer.valueOf(str);
                        point2.y = Integer.valueOf(o.toString());
                        Toast.makeText(getApplicationContext(), R.string.preferences_yes, Toast.LENGTH_SHORT).show();
                        flag_restore = true;
                        return true;
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), R.string.preferences_no + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }
            });
        }
    }

    class WeightMax implements InterfacePreference{
        @Override
        public void setup(Preference name) throws Exception {
            name.setTitle(getString(R.string.Max_weight) + ScaleModule.getWeightMax() + getString(R.string.scales_kg));
            name.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (o.toString().isEmpty() || Integer.valueOf(o.toString()) < Main.default_max_weight) {
                        Toast.makeText(getApplicationContext(), R.string.preferences_no, Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    ScaleModule.setWeightMax(Integer.valueOf(o.toString()));
                    ScaleModule.setWeightMargin((int) (ScaleModule.getWeightMax() * 1.2));
                    preference.setTitle(getString(R.string.Max_weight) + ScaleModule.getWeightMax() + getString(R.string.scales_kg));
                    Toast.makeText(getApplicationContext(), R.string.preferences_yes, Toast.LENGTH_SHORT).show();
                    flag_restore = true;
                    return true;
                }
            });
        }
    }

    class CoefficientA implements InterfacePreference{
        @Override
        public void setup(Preference name) throws Exception {
            name.setTitle(getString(R.string.ConstantA) + new BigDecimal(String.valueOf(ScaleModule.getCoefficientA())).toPlainString());
            name.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    try {
                        ScaleModule.setCoefficientA(Float.valueOf(o.toString()));
                        preference.setTitle(getString(R.string.ConstantA) + new BigDecimal(String.valueOf(ScaleModule.getCoefficientA())).toPlainString());
                        Toast.makeText(getApplicationContext(), R.string.preferences_yes, Toast.LENGTH_SHORT).show();
                        flag_restore = true;
                        return true;
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), R.string.preferences_no + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }
            });
        }
    }

    class CallBattery implements InterfacePreference{

        @Override
        public void setup(Preference name) throws Exception {
            name.setTitle(getString(R.string.Battery) + ScaleModule.getBattery() + '%');
            name.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (o.toString().isEmpty() || "0".equals(o.toString()) || Integer.valueOf(o.toString()) > Main.default_max_battery) {
                        Toast.makeText(getApplicationContext(), R.string.preferences_no, Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    if (ScaleModule.setModuleBatteryCharge(0)) {
                        ScaleModule.setBattery(Integer.valueOf(o.toString()));
                        preference.setTitle(getString(R.string.Battery) + ScaleModule.getBattery() + '%');
                        Toast.makeText(getApplicationContext(), R.string.preferences_yes, Toast.LENGTH_SHORT).show();
                        return true;
                    }

                    Toast.makeText(getApplicationContext(), R.string.preferences_no, Toast.LENGTH_SHORT).show();
                    return false;
                }
            });
        }
    }

    class ServiceCod implements InterfacePreference{
        @Override
        public void setup(Preference name) throws Exception {
            name.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (newValue.toString().length() > 32 || newValue.toString().length() < 4) {
                        Toast.makeText(getApplicationContext(), "Длина кода больше 32 или меньше 4 знаков", Toast.LENGTH_LONG).show();
                        return false;
                    }

                    try {
                        ScaleModule.setModuleServiceCod(newValue.toString());
                        Toast.makeText(getApplicationContext(), R.string.preferences_yes, Toast.LENGTH_SHORT).show();
                        return true;
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), R.string.preferences_no, Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }
            });

        }
    }

    class Update implements InterfacePreference{
        @Override
        public void setup(Preference name) throws Exception {
            if (ScaleModule.getVersion() != null) {
                if (ScaleModule.getNumVersion() < Main.microSoftware) {
                    name.setSummary(getString(R.string.Is_new_version));
                    //name.setEnabled(true);
                } else {
                    name.setSummary(getString(R.string.Scale_update));
                    //name.setEnabled(false);
                }

                name.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    //@TargetApi(Build.VERSION_CODES.HONEYCOMB)
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        //Scales.vScale.backupPreference();
                        String hardware = ScaleModule.getModuleHardware();
                        if (hardware.isEmpty()) {
                            hardware = "MBC04.36.2";
                        }
                        Intent intent = new Intent(ActivityTuning.this, ActivityBootloader.class);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        else
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        //intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra(getString(R.string.KEY_ADDRESS), ScaleModule.getAddressBluetoothDevice());
                        intent.putExtra(InterfaceVersions.CMD_HARDWARE, hardware);
                        intent.putExtra(InterfaceVersions.CMD_VERSION, ScaleModule.getNumVersion());
                        startActivity(intent);
                        return false;
                    }
                });
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (flag_restore) {
            if (point1.x != Integer.MIN_VALUE && point2.x != Integer.MIN_VALUE) {
                ScaleModule.setCoefficientA((float) (point1.y - point2.y) / (point1.x - point2.x));
                ScaleModule.setCoefficientB(point1.y - ScaleModule.getCoefficientA() * point1.x);
            }
            ScaleModule.setLimitTenzo((int) (ScaleModule.getWeightMax() / ScaleModule.getCoefficientA()));
            if (ScaleModule.getLimitTenzo() > 0xffffff) {
                ScaleModule.setLimitTenzo(0xffffff);
                ScaleModule.setWeightMax((int) (0xffffff * ScaleModule.getCoefficientA()));
            }
            if (ScaleModule.writeData()) {
                Toast.makeText(getApplicationContext(), R.string.preferences_yes, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), R.string.preferences_no, Toast.LENGTH_SHORT).show();
            }
        }

    }
}
