package com.konst.simple_scale;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.konst.module.ScaleModule;

/**
 * @author Kostya
 */
class DialogFilterADC extends DialogPreference /*implements ActivityPreferences.InterfacePreference*/ {
    private int mNumber = 0;
    private NumberPicker numberPicker;
    int minValue;
    int maxValue;

    public DialogFilterADC(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray attributesArray = context.obtainStyledAttributes(attrs, R.styleable.dialogFilterADC,R.attr.dialogFilterADCStyle, 0);
        minValue = attributesArray.getInt(R.styleable.dialogFilterADC_minFilterADC, 0);
        maxValue = attributesArray.getInt(R.styleable.dialogFilterADC_maxFilterADC, 0);
        setPersistent(true);
        setDialogLayoutResource(R.layout.activity_light);
    }

    @Override
    protected void onBindDialogView(View view) {
        numberPicker = (NumberPicker) view.findViewById(R.id.numberPicker);
        numberPicker.setMaxValue(maxValue);
        numberPicker.setMinValue(minValue);
        numberPicker.setValue(mNumber);
        super.onBindDialogView(view);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            // needed when user edits the text field and clicks OK
            numberPicker.clearFocus();
            setValue(numberPicker.getValue());
        }
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setValue(restoreValue ? getPersistedInt(mNumber) : (Integer) defaultValue);
    }

    @Override
    public void setDefaultValue(Object defaultValue) {
        super.setDefaultValue(defaultValue);
    }

    public void setValue(int value) {
        if (shouldPersist()) {
            persistInt(value);
        }

        if (value != mNumber) {
            mNumber = value;
            notifyChanged();
            callChangeListener(value);
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, 0);
    }


}
