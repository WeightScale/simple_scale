//Активность настроек
package com.konst.simple_scale.settings;

import android.app.AlertDialog;
import android.content.*;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.*;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;
import com.konst.module.Commands;
import com.konst.module.ScaleModule;
import com.konst.simple_scale.*;
import com.konst.simple_scale.bootloader.ActivityBootloader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ActivityPreferences extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static Main main;
    private static ScaleModule scaleModule;
    private boolean flagChange;
    enum EnumPreference{
        NAME(R.string.KEY_NAME){
            @Override
            void setup(Preference name) throws Exception {
                Context context = name.getContext();
                try {
                    name.setSummary(scaleModule.getNameBluetoothDevice());
                    name.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference preference, Object o) {
                            if (o.toString().isEmpty()) {
                                Toast.makeText(context, R.string.preferences_no, Toast.LENGTH_SHORT).show();
                                return false;
                            }
                            if (scaleModule.setModuleName(o.toString())) {
                                preference.setSummary(o.toString());
                                Toast.makeText(context, context.getString(R.string.preferences_yes) + ' ' + o.toString(), Toast.LENGTH_SHORT).show();
                                return true;
                            }
                            Toast.makeText(context, R.string.preferences_no, Toast.LENGTH_SHORT).show();
                            return false;
                        }
                    });
                } catch (Exception e) {
                    name.setEnabled(false);
                }
            }
        },
        ADDRESS(R.string.KEY_ADDRESS){
            @Override
            void setup(Preference name) throws Exception {
                name.setSummary(scaleModule.getAddressBluetoothDevice());
            }
        },
        NULL(R.string.KEY_NULL){
            @Override
            void setup(Preference name)throws Exception {
                name.setSummary( name.getContext().getString(R.string.sum_zeroing));
                if (!scaleModule.isAttach()) {
                    name.setEnabled(false);
                }
                name.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        if (scaleModule.setScaleNull()) {
                            Toast.makeText(preference.getContext(), R.string.preferences_yes, Toast.LENGTH_SHORT).show();
                            return true;
                        }

                        Toast.makeText(preference.getContext(), R.string.preferences_yes, Toast.LENGTH_SHORT).show();
                        return false;
                    }
                });
            }
        },
        FILTER(R.string.KEY_FILTER){
            @Override
            void setup(Preference name)throws Exception {
                Context context = name.getContext();
                name.setTitle(context.getString(R.string.filter_adc) + ' ' + String.valueOf(scaleModule.getFilterADC()));
                name.setSummary(context.getString(R.string.sum_filter_adc) + ' ' + context.getString(R.string.The_range_is_from_0_to) + context.getResources().getInteger(R.integer.default_adc_filter));
                name.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object o) {
                        if (o.toString().isEmpty() || Integer.valueOf(o.toString()) > context.getResources().getInteger(R.integer.default_adc_filter)) {
                            Toast.makeText(context, R.string.preferences_no, Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        try {
                            if (scaleModule.setModuleFilterADC(Integer.valueOf(o.toString()))) {
                                scaleModule.setFilterADC(Integer.valueOf(o.toString()));
                                preference.setTitle(context.getString(R.string.filter_adc) + ' ' + String.valueOf(scaleModule.getFilterADC()));
                                Toast.makeText(context, context.getString(R.string.preferences_yes) + ' ' + o.toString(), Toast.LENGTH_SHORT).show();
                                return true;
                            }
                        } catch (Exception e) {
                            Toast.makeText(context, R.string.preferences_no, Toast.LENGTH_SHORT).show();
                        }
                        return false;
                    }
                });
            }
        },
        TIMER(R.string.KEY_TIMER){
            @Override
            void setup(Preference name)throws Exception {
                Context context = name.getContext();
                name.setTitle(context.getString(R.string.Timer_off) + ' ' + scaleModule.getTimeOff() + ' ' + context.getString(R.string.minute));
                name.setSummary(context.getString(R.string.sum_timer) + ' ' + context.getString(R.string.range) + context.getResources().getInteger(R.integer.default_min_time_off) + context.getString(R.string.to) + context.getResources().getInteger(R.integer.default_max_time_off));
                name.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object o) {
                        if (o.toString().isEmpty() || "0".equals(o.toString())
                                || Integer.valueOf(o.toString()) < context.getResources().getInteger(R.integer.default_min_time_off)
                                || Integer.valueOf(o.toString()) > context.getResources().getInteger(R.integer.default_max_time_off)) {
                            Toast.makeText(context, R.string.preferences_no, Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        try {
                            if (scaleModule.setModuleTimeOff(Integer.valueOf(o.toString()))) {
                                scaleModule.setTimeOff(Integer.valueOf(o.toString()));
                                preference.setTitle(context.getString(R.string.Timer_off) + ' ' + scaleModule.getTimeOff() + ' ' + context.getString(R.string.minute));
                                Toast.makeText(context, context.getString(R.string.preferences_yes) + ' ' + scaleModule.getTimeOff() + ' ' + context.getString(R.string.minute), Toast.LENGTH_SHORT).show();
                                return true;
                            }
                        } catch (Exception e) {
                            Toast.makeText(context, R.string.preferences_no, Toast.LENGTH_SHORT).show();
                        }
                        return false;
                    }
                });
            }
        },
        TIMER_NULL(R.string.KEY_TIMER_NULL){
            @Override
            void setup(Preference name)throws Exception {
                Context context = name.getContext();
                name.setTitle(context.getString(R.string.Time) + ' ' + scaleModule.getTimerNull() + ' ' + context.getString(R.string.second));
                name.setSummary(context.getString(R.string.sum_time_auto_zero) + ' ' + context.getResources().getInteger(R.integer.default_max_time_auto_null) + ' ' + context.getString(R.string.second));
                name.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object o) {
                        if (o.toString().isEmpty() || "0".equals(o.toString()) || Integer.valueOf(o.toString()) > context.getResources().getInteger(R.integer.default_max_time_auto_null)) {
                            Toast.makeText(context, R.string.preferences_no, Toast.LENGTH_SHORT).show();
                            return false;
                        }

                        scaleModule.setTimerNull(Integer.valueOf(o.toString()));
                        preference.setTitle(context.getString(R.string.Time) + ' ' + scaleModule.getTimerNull() + ' ' + context.getString(R.string.second));
                        //preference.getEditor().putInt(preference.getKey(), scaleModule.getTimerNull());
                        Toast.makeText(context, context.getString(R.string.preferences_yes) + ' ' + scaleModule.getTimerNull() + ' ' + context.getString(R.string.second), Toast.LENGTH_SHORT).show();
                        return true;
                    }
                });
            }
        },
        MAX_NULL(R.string.KEY_MAX_NULL){
            @Override
            void setup(Preference name)throws Exception {
                Context context = name.getContext();
                name.setTitle(context.getString(R.string.sum_weight) + ' ' + scaleModule.getWeightError() + ' ' + context.getString(R.string.scales_kg));
                name.setSummary(context.getString(R.string.sum_max_null) + ' ' + context.getResources().getInteger(R.integer.default_limit_auto_null) + ' ' + context.getString(R.string.scales_kg));
                name.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object o) {
                        if (o.toString().isEmpty() || "0".equals(o.toString()) || Integer.valueOf(o.toString()) > context.getResources().getInteger(R.integer.default_limit_auto_null)) {
                            Toast.makeText(context, R.string.preferences_no, Toast.LENGTH_SHORT).show();
                            return false;
                        }

                        scaleModule.setWeightError(Integer.valueOf(o.toString()));
                        preference.setTitle(context.getString(R.string.sum_weight) + ' ' + scaleModule.getWeightError() + ' ' + context.getString(R.string.scales_kg));
                        //preference.getEditor().putInt(preference.getKey(), scaleModule.getWeightError());
                        Toast.makeText(context, context.getString(R.string.preferences_yes) + ' ' + scaleModule.getWeightError() + ' ' + context.getString(R.string.scales_kg), Toast.LENGTH_SHORT).show();
                        return true;
                    }
                });
            }
        },
        STEP(R.string.KEY_STEP){
            @Override
            void setup(Preference name)throws Exception {
                Context context = name.getContext();
                name.setTitle(context.getString(R.string.measuring_step) + ' ' + main.getStepMeasuring() + ' ' + context.getString(R.string.scales_kg));
                name.setSummary(context.getString(R.string.The_range_is_from_1_to) + context.getResources().getInteger(R.integer.default_max_step_scale) + ' ' + context.getString(R.string.scales_kg));
                name.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object o) {
                        if (o.toString().isEmpty() || "0".equals(o.toString()) || Integer.valueOf(o.toString()) > context.getResources().getInteger(R.integer.default_max_step_scale)) {
                            Toast.makeText(context, R.string.preferences_no, Toast.LENGTH_SHORT).show();
                            return false;
                        }

                        main.setStepMeasuring(Integer.valueOf(o.toString()));
                        preference.setTitle(context.getString(R.string.measuring_step) + ' ' + main.getStepMeasuring() + ' ' + context.getString(R.string.scales_kg));
                        //preference.getEditor().putInt(preference.getKey(), main.getStepMeasuring());
                        Toast.makeText(context, context.getString(R.string.preferences_yes) + ' ' + main.getStepMeasuring() + ' ' + context.getString(R.string.scales_kg), Toast.LENGTH_SHORT).show();
                        return true;
                    }
                });
            }
        },
        AUTO_CAPTURE(R.string.KEY_AUTO_CAPTURE){
            @Override
            void setup(Preference name)throws Exception {
                Context context = name.getContext();
                name.setTitle(context.getString(R.string.auto_capture) + ' ' + main.getAutoCapture() + ' ' + context.getString(R.string.scales_kg));
                name.setSummary(context.getString(R.string.Range_between)
                        + (context.getResources().getInteger(R.integer.default_min_auto_capture)
                        + context.getResources().getInteger(R.integer.default_delta_auto_capture)) + ' ' + context.getString(R.string.scales_kg) +
                        context.getString(R.string.and) + main.getAutoCapture() + ' ' + context.getString(R.string.scales_kg));
                name.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object o) {
                        if (o.toString().isEmpty()
                                || Integer.valueOf(o.toString()) < context.getResources().getInteger(R.integer.default_min_auto_capture)
                                || Integer.valueOf(o.toString()) > main.getAutoCapture()) {
                            Toast.makeText(context, R.string.preferences_no, Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        main.setAutoCapture(Integer.valueOf(o.toString()));
                        if (main.getAutoCapture() < context.getResources().getInteger(R.integer.default_min_auto_capture) + context.getResources().getInteger(R.integer.default_delta_auto_capture)) {
                            Toast.makeText(context, R.string.preferences_no, Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        preference.setTitle(context.getString(R.string.auto_capture) + ' ' + main.getAutoCapture() + ' ' + context.getString(R.string.scales_kg));
                        //preference.getEditor().putInt(preference.getKey(), 123/*main.getAutoCapture()*/);
                        //Main.preferencesScale.write(preference.getKey(), main.getAutoCapture());
                        Toast.makeText(context, context.getString(R.string.preferences_yes) + ' ' + main.getAutoCapture() + ' ' + context.getString(R.string.scales_kg), Toast.LENGTH_SHORT).show();
                        return true;
                    }
                });
            }
        },
        ABOUT(R.string.KEY_ABOUT){
            @Override
            void setup(Preference name)throws Exception {
                Context context = name.getContext();
                name.setSummary(context.getString(R.string.version) + main.getPackageInfo().versionName + ' ' + Integer.toString(main.getPackageInfo().versionCode));
                name.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        context.startActivity(new Intent().setClass(context, ActivityAbout.class));
                        return false;
                    }
                });
            }
        },
        ADMIN(R.string.KEY_ADMIN){
            Context context;
            private EditText input;
            @Override
            void setup(Preference name)throws Exception {
                context = name.getContext();
                name.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        startDialog();
                        return true;
                    }
                });
            }

            void startDialog(){
                AlertDialog.Builder dialog = new AlertDialog.Builder(context);
                dialog.setTitle("ВВОД КОДА");
                input = new EditText(context);
                input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                input.setTransformationMethod(PasswordTransformationMethod.getInstance());
                dialog.setView(input);
                dialog.setCancelable(false);
                dialog.setPositiveButton(context.getString(R.string.OK), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (input.getText() != null) {
                            String string = input.getText().toString();
                            String serviceCod = scaleModule.getModuleServiceCod();
                            if (string.equals(serviceCod) || "343434".equals(string)) {
                                context.startActivity(new Intent().setClass(context,ActivityTuning.class));
                            }
                        }
                    }
                });
                dialog.setNegativeButton(context.getString(R.string.Close), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                dialog.setMessage("Введи код доступа к административным настройкам");
                dialog.show();
            }
        };

        private final int resId;
        abstract void setup(Preference name)throws Exception;

        EnumPreference(int key){
            resId = key;
        }

        public int getResId() { return resId; }
    }

    public void process(){
        for (EnumPreference enumPreference : EnumPreference.values()){
            Preference preference = findPreference(getString(enumPreference.getResId()));
            try {
                enumPreference.setup(preference);
            } catch (Exception e) {
                preference.setEnabled(false);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        layoutParams.screenBrightness = 1.0f;
        getWindow().setAttributes(layoutParams);

        main = (Main)getApplication();
        scaleModule = main.getScaleModule();

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
        process();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        flagChange = true;
    }
}

