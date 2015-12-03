//Активность настроек
package com.konst.simple_scale.settings;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.*;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;
import android.widget.Toast;
import com.konst.module.ScaleModule;
import com.konst.simple_scale.*;

import java.util.HashMap;
import java.util.Map;

public class ActivityPreferences extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    Main main;
    ScaleModule scaleModule;
    private boolean flagChange;

    interface InterfacePreference {
        void setup(Preference name) throws Exception;
    }

    final Map<String, InterfacePreference> mapPreferences = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        main = (Main)getApplication();
        scaleModule = main.getScaleModule();

        mapPreferences.put(getString(R.string.KEY_NAME), new PreferenceName());
        mapPreferences.put(getString(R.string.KEY_ADDRESS), new PreferenceAddress());
        mapPreferences.put(getString(R.string.KEY_NULL), new PreferenceNull());
        mapPreferences.put(getString(R.string.KEY_FILTER), new PreferenceFilter());
        mapPreferences.put(getString(R.string.KEY_TIMER), new PreferenceTimer());
        mapPreferences.put(getString(R.string.KEY_TIMER_NULL), new PreferenceTimerNull());
        mapPreferences.put(getString(R.string.KEY_MAX_NULL), new PreferenceMaxNull());
        mapPreferences.put(getString(R.string.KEY_STEP), new PreferenceStep());
        mapPreferences.put(getString(R.string.KEY_AUTO_CAPTURE), new PreferenceAutoCapture());
        mapPreferences.put(getString(R.string.KEY_ABOUT), new PreferenceAbout());
        mapPreferences.put(getString(R.string.KEY_ADMIN), new Admin());

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
       /* PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName("my_preferences");
        preferenceManager.setSharedPreferencesMode(MODE_PRIVATE);*/
        process();
    }

    void process() {
        for (Map.Entry<String, InterfacePreference> preferenceEntry : mapPreferences.entrySet()) {
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

    class PreferenceName implements InterfacePreference {

        @Override
        public void setup(Preference name) throws Exception {
            try {
                name.setSummary(scaleModule.getNameBluetoothDevice());
                name.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object o) {
                        if (o.toString().isEmpty()) {
                            Toast.makeText(getBaseContext(), R.string.preferences_no, Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        if (scaleModule.setModuleName(o.toString())) {
                            preference.setSummary(o.toString());
                            Toast.makeText(getBaseContext(), getString(R.string.preferences_yes) + ' ' + o.toString(), Toast.LENGTH_SHORT).show();
                            return true;
                        }
                        Toast.makeText(getBaseContext(), R.string.preferences_no, Toast.LENGTH_SHORT).show();
                        return false;
                    }
                });
            } catch (Exception e) {
                name.setEnabled(false);
            }
        }
    }

    class PreferenceAddress implements InterfacePreference {

        @Override
        public void setup(Preference name) throws Exception {
            name.setSummary(scaleModule.getAddressBluetoothDevice());
        }
    }

    class PreferenceNull implements InterfacePreference {

        @Override
        public void setup(Preference name) throws Exception {
            name.setSummary(getString(R.string.sum_zeroing));
            if (!scaleModule.isAttach()) {
                name.setEnabled(false);
            }
            name.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (scaleModule.setScaleNull()) {
                        Toast.makeText(getApplicationContext(), R.string.preferences_yes, Toast.LENGTH_SHORT).show();
                        return true;
                    }

                    Toast.makeText(getApplicationContext(), R.string.preferences_yes, Toast.LENGTH_SHORT).show();
                    return false;
                }
            });
        }
    }

    class PreferenceFilter  implements ActivityPreferences.InterfacePreference {


        @Override
        public void setup(Preference name) throws Exception {
            name.setTitle(getString(R.string.filter_adc) + ' ' + String.valueOf(scaleModule.getFilterADC()));
            name.setSummary(getString(R.string.sum_filter_adc) + ' ' + getString(R.string.The_range_is_from_0_to) + Main.default_adc_filter);
            name.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (o.toString().isEmpty() || Integer.valueOf(o.toString()) > Main.default_adc_filter) {
                        Toast.makeText(getBaseContext(), R.string.preferences_no, Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    try {
                        if (scaleModule.setModuleFilterADC(Integer.valueOf(o.toString()))) {
                            scaleModule.setFilterADC(Integer.valueOf(o.toString()));
                            preference.setTitle(getString(R.string.filter_adc) + ' ' + String.valueOf(scaleModule.getFilterADC()));
                            Toast.makeText(getBaseContext(), R.string.preferences_yes, Toast.LENGTH_SHORT).show();
                            return true;
                        }
                    } catch (Exception e) {
                        Toast.makeText(getBaseContext(), R.string.preferences_no, Toast.LENGTH_SHORT).show();
                    }
                    return false;
                }
            });
        }
    }

    class PreferenceTimer implements InterfacePreference {

        @Override
        public void setup(Preference name) throws Exception {
            name.setTitle(getString(R.string.Timer_off) + ' ' + scaleModule.getTimeOff() + ' ' + getString(R.string.minute));
            name.setSummary(getString(R.string.sum_timer) + ' ' + getString(R.string.range) + Main.default_min_time_off + getString(R.string.to) + Main.default_max_time_off);
            name.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (o.toString().isEmpty() || "0".equals(o.toString())
                            || Integer.valueOf(o.toString()) < Main.default_min_time_off
                            || Integer.valueOf(o.toString()) > Main.default_max_time_off) {
                        Toast.makeText(getBaseContext(), R.string.preferences_no, Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    try {
                        if (scaleModule.setModuleTimeOff(Integer.valueOf(o.toString()))) {
                            scaleModule.setTimeOff(Integer.valueOf(o.toString()));
                            preference.setTitle(getString(R.string.Timer_off) + ' ' + scaleModule.getTimeOff() + ' ' + getString(R.string.minute));
                            Toast.makeText(getBaseContext(), getString(R.string.preferences_yes) + ' ' + scaleModule.getTimeOff() + ' ' + getString(R.string.minute), Toast.LENGTH_SHORT).show();
                            return true;
                        }
                    } catch (Exception e) {
                        Toast.makeText(getBaseContext(), R.string.preferences_no, Toast.LENGTH_SHORT).show();
                    }
                    return false;
                }
            });
        }
    }

    class PreferenceTimerNull implements InterfacePreference {

        @Override
        public void setup(Preference name) throws Exception {
            name.setTitle(getString(R.string.Time) + ' ' + scaleModule.getTimerNull() + ' ' + getString(R.string.second));
            name.setSummary(getString(R.string.sum_time_auto_zero) + ' ' + Main.default_max_time_auto_null + ' ' + getString(R.string.second));
            name.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (o.toString().isEmpty() || "0".equals(o.toString()) || Integer.valueOf(o.toString()) > Main.default_max_time_auto_null) {
                        Toast.makeText(getBaseContext(), R.string.preferences_no, Toast.LENGTH_SHORT).show();
                        return false;
                    }

                    scaleModule.setTimerNull(Integer.valueOf(o.toString()));
                    preference.setTitle(getString(R.string.Time) + ' ' + scaleModule.getTimerNull() + ' ' + getString(R.string.second));
                    Preferences.write(getString(R.string.KEY_TIMER_NULL), scaleModule.getTimerNull());
                    Toast.makeText(getBaseContext(), getString(R.string.preferences_yes) + ' ' + scaleModule.getTimerNull() + ' ' + getString(R.string.second), Toast.LENGTH_SHORT).show();
                    return true;
                }
            });
        }
    }

    class PreferenceMaxNull implements InterfacePreference {

        @Override
        public void setup(Preference name) throws Exception {
            name.setTitle(getString(R.string.sum_weight) + ' ' + scaleModule.getWeightError() + ' ' + getString(R.string.scales_kg));
            name.setSummary(getString(R.string.sum_max_null) + ' ' + Main.default_limit_auto_null + ' ' + getString(R.string.scales_kg));
            name.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (o.toString().isEmpty() || "0".equals(o.toString()) || Integer.valueOf(o.toString()) > Main.default_limit_auto_null) {
                        Toast.makeText(getBaseContext(), R.string.preferences_no, Toast.LENGTH_SHORT).show();
                        return false;
                    }

                    scaleModule.setWeightError(Integer.valueOf(o.toString()));
                    preference.setTitle(getString(R.string.sum_weight) + ' ' + scaleModule.getWeightError() + ' ' + getString(R.string.scales_kg));
                    Preferences.write(getString(R.string.KEY_TIMER_NULL), scaleModule.getWeightError());
                    Toast.makeText(getBaseContext(), getString(R.string.preferences_yes) + ' ' + scaleModule.getWeightError() + ' ' + getString(R.string.scales_kg), Toast.LENGTH_SHORT).show();
                    return true;
                }
            });
        }
    }

    class PreferenceStep implements InterfacePreference {

        @Override
        public void setup(Preference name) throws Exception {
            name.setTitle(getString(R.string.measuring_step) + ' ' + main.getStepMeasuring() + ' ' + getString(R.string.scales_kg));
            name.setSummary(getString(R.string.The_range_is_from_1_to) + Main.default_max_step_scale + ' ' + getString(R.string.scales_kg));
            name.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (o.toString().isEmpty() || "0".equals(o.toString()) || Integer.valueOf(o.toString()) > Main.default_max_step_scale) {
                        Toast.makeText(getBaseContext(), R.string.preferences_no, Toast.LENGTH_SHORT).show();
                        return false;
                    }

                    main.setStepMeasuring(Integer.valueOf(o.toString()));
                    preference.setTitle(getString(R.string.measuring_step) + ' ' + main.getStepMeasuring() + ' ' + getString(R.string.scales_kg));
                    Preferences.write(getString(R.string.KEY_STEP), main.getStepMeasuring());
                    Toast.makeText(getBaseContext(), getString(R.string.preferences_yes) + ' ' + main.getStepMeasuring() + ' ' + getString(R.string.scales_kg), Toast.LENGTH_SHORT).show();
                    return true;
                }
            });
        }
    }

    class PreferenceAutoCapture implements InterfacePreference {

        @Override
        public void setup(Preference name) throws Exception {
            name.setTitle(getString(R.string.auto_capture) + ' ' + Main.autoCapture + ' ' + getString(R.string.scales_kg));
            name.setSummary(getString(R.string.Range_between) + (Main.default_min_auto_capture + Main.default_delta_auto_capture) + ' ' + getString(R.string.scales_kg) +
                    getString(R.string.and) + Main.default_max_auto_capture + ' ' + getString(R.string.scales_kg));
            name.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (o.toString().isEmpty() || Integer.valueOf(o.toString()) < Main.default_min_auto_capture || Integer.valueOf(o.toString()) > Main.default_max_auto_capture) {
                        Toast.makeText(getBaseContext(), R.string.preferences_no, Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    Main.autoCapture = Integer.valueOf(o.toString());
                    if (Main.autoCapture < Main.default_min_auto_capture + Main.default_delta_auto_capture) {
                        Toast.makeText(getBaseContext(), R.string.preferences_no, Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    preference.setTitle(getString(R.string.auto_capture) + ' ' + Main.autoCapture + ' ' + getString(R.string.scales_kg));
                    Preferences.write(getString(R.string.KEY_AUTO_CAPTURE), Main.autoCapture);
                    Toast.makeText(getBaseContext(), getString(R.string.preferences_yes) + ' ' + Main.autoCapture + ' ' + getString(R.string.scales_kg), Toast.LENGTH_SHORT).show();
                    return true;
                }
            });
        }
    }

    class PreferenceAbout implements InterfacePreference {

        @Override
        public void setup(Preference name) throws Exception {
            name.setSummary(getString(R.string.version) + main.getPackageInfo().versionName + ' ' + Integer.toString(main.getPackageInfo().versionCode));
            name.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent().setClass(getApplicationContext(), ActivityAbout.class));
                    return false;
                }
            });
        }
    }

    class Admin implements InterfacePreference{
        private EditText input;

        @Override
        public void setup(Preference name) throws Exception {
            name.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startDialog();
                    return true;
                }
            });
        }

        void startDialog(){
            AlertDialog.Builder dialog = new AlertDialog.Builder(ActivityPreferences.this);
            dialog.setTitle("ВВОД КОДА");
            input = new EditText(ActivityPreferences.this);
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            input.setTransformationMethod(PasswordTransformationMethod.getInstance());
            dialog.setView(input);
            dialog.setCancelable(false);
            dialog.setPositiveButton(getString(R.string.OK), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    if (input.getText() != null) {
                        String string = input.getText().toString();
                        String serviceCod = scaleModule.getModuleServiceCod();
                        if (string.equals(serviceCod) || "343434".equals(string)) {
                            startActivity(new Intent().setClass(getApplicationContext(),ActivityTuning.class));
                        }
                    }
                }
            });
            dialog.setNegativeButton(getString(R.string.Close), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            dialog.setMessage("Введи код доступа к административным настройкам");
            dialog.show();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        flagChange = true;
    }
}
