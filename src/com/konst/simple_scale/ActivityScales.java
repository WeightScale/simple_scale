package com.konst.simple_scale;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.*;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.*;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.TextAppearanceSpan;
import android.view.*;
import android.widget.*;
import com.konst.module.Module;
import com.konst.module.OnEventConnectResult;
import com.konst.module.ScaleModule;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class ActivityScales extends Activity implements View.OnClickListener, Runnable{
    private SpannableStringBuilder textKg;
    private SpannableStringBuilder textBattery;
    private TextView textViewBattery;
    private ListView listView;
    private ArrayList<WeightObject> arrayList;
    private ArrayAdapter<WeightObject> customListAdapter;
    private ProgressBar progressBarStable;
    private ProgressBar progressBarWeight;
    private TextView weightTextView;
    private Drawable dProgressWeight, dWeightDanger;
    private SimpleGestureFilter detectorWeightView;
    private ImageView buttonFinish;
    //private TextView textLog;
    private Vibrator vibrator; //вибратор
    private LinearLayout layoutScale;
    private BroadcastReceiver broadcastReceiver; //приёмник намерений
    //final AutoWeight autoWeight = new AutoWeight();
    ScaleModule scaleModule;

    public int numStable;
    int moduleWeight;
    //int moduleSensorValue;
    protected int tempWeight;
    Thread threadAutoWeight;
    boolean running;
    /**
     * Количество стабильных показаний веса для авто сохранения
     */
    public static final int COUNT_STABLE = 64;
    static final int REQUEST_SEARCH_SCALE = 2;

    protected boolean isStable;
    private boolean flagExit = true;
    private boolean touchWeightView;
    private boolean weightViewIsSwipe;
    private boolean doubleBackToExitPressedOnce;
    public static boolean isScaleConnect;

    enum Action{
        /** Остановка взвешивания.          */
        STOP_WEIGHTING,
        /** Пуск взвешивания.               */
        START_WEIGHTING,
        /** Сохранить результат взвешивания.*/
        STORE_WEIGHTING,
        /** Обновить данные веса.           */
        UPDATE_PROGRESS,
        /** Начало процесса.                */
        START
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.buttonFinish:
                onBackPressed();
            break;
            case R.id.imageMenu:
                openOptionsMenu();
            break;
            default:
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handlerBatteryTemperature.start();
        handlerWeight.start();
        screenUnlock();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handlerBatteryTemperature.stop(false);
        handlerWeight.stop(true);
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_ACTION_BAR);
        setContentView(R.layout.scale_green);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        layoutScale = (LinearLayout)findViewById(R.id.screenScale);
        layoutScale.setVisibility(View.INVISIBLE);

        buttonFinish = (ImageView) findViewById(R.id.buttonFinish);
        buttonFinish.setOnClickListener(this);

        textViewBattery = (TextView)findViewById(R.id.textBattery);

        listView = (ListView)findViewById(R.id.listView);

        findViewById(R.id.imageMenu).setOnClickListener(this);

        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        layoutParams.screenBrightness = 1.0f;
        getWindow().setAttributes(layoutParams);

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (networkInfo != null) {
            if (networkInfo.isAvailable()) {//Если используется
                new Internet(this).turnOnWiFiConnection(false); // для телефонов у которых один модуль wifi и bluetooth
            }
        }

        textKg = new SpannableStringBuilder(getResources().getString(R.string.scales_kg));
        textKg.setSpan(new TextAppearanceSpan(this, R.style.SpanTextKg),0,textKg.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);

        textBattery = new SpannableStringBuilder("Заряд батареи ");
        textBattery.setSpan(new TextAppearanceSpan(this, R.style.SpanTextBattery),0,textBattery.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);

        broadcastReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) { //обработчик Bluetooth
                String action = intent.getAction();
                if (action != null) {
                    switch (action) {
                        case BluetoothAdapter.ACTION_STATE_CHANGED:
                            switch (scaleModule.getAdapter().getState()) {
                                case BluetoothAdapter.STATE_OFF:
                                    Toast.makeText(getBaseContext(), R.string.bluetooth_off, Toast.LENGTH_SHORT).show();
                                    new Internet(getApplicationContext()).turnOnWiFiConnection(false);
                                    scaleModule.getAdapter().enable();
                                    break;
                                case BluetoothAdapter.STATE_TURNING_ON:
                                    Toast.makeText(getBaseContext(), R.string.bluetooth_turning_on, Toast.LENGTH_SHORT).show();
                                    break;
                                case BluetoothAdapter.STATE_ON:
                                    Toast.makeText(getBaseContext(), R.string.bluetooth_on, Toast.LENGTH_SHORT).show();
                                    break;
                                default:
                                    break;
                            }
                            break;
                        case BluetoothDevice.ACTION_ACL_DISCONNECTED://устройство отсоеденено
                            vibrator.vibrate(200);
                            isScaleConnect = false;
                            break;
                        case BluetoothDevice.ACTION_ACL_CONNECTED://найдено соеденено
                            vibrator.vibrate(200);
                            isScaleConnect = true;
                            break;
                        default:
                    }
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(broadcastReceiver, intentFilter);

        try {
            scaleModule = new ScaleModule(Main.packageInfo.versionName, onEventConnectResult);
            Toast.makeText(getBaseContext(), R.string.bluetooth_off, Toast.LENGTH_SHORT).show();
            connectScaleModule(Preferences.read(getString(R.string.KEY_LAST_SCALES), ""));
        } catch (Exception e) {
            Toast.makeText(getBaseContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            //exit();
            return;
        }
        scaleModule.getAdapter().cancelDiscovery();
        doubleBackToExitPressedOnce = true;
        Toast.makeText(this, R.string.press_again_to_exit , Toast.LENGTH_SHORT).show();
        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                doubleBackToExitPressedOnce = false;

            }
        }, 2000);
    }

    @Override
    public void onDestroy() { //при разрушении активности
        super.onDestroy();
        exit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_scales, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.preferences:
                startActivity(new Intent(this, ActivityPreferences.class));
                break;
            /*case R.id.tuning:
                startActivity(new Intent(this, ActivityTuning.class));
            break;*/
            case R.id.search:
                vibrator.vibrate(100);
                openSearch();
                break;
            case R.id.exit:
                closeOptionsMenu();
                break;
            case R.id.power_off:
                AlertDialog.Builder dialog = new AlertDialog.Builder(this);
                dialog.setTitle(getString(R.string.Scale_off));
                dialog.setCancelable(false);
                dialog.setPositiveButton(getString(R.string.OK), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (i == DialogInterface.BUTTON_POSITIVE) {
                            if (ScaleModule.isAttach())
                                ScaleModule.setModulePowerOff();
                        }
                    }
                });
                dialog.setNegativeButton(getString(R.string.Close), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish();
                    }
                });
                dialog.setMessage(getString(R.string.TEXT_MESSAGE15));
                dialog.show();
                break;
            default:

        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //AlertDialog.Builder dialog;
        setProgressBarIndeterminateVisibility(false);
        switch (resultCode) {
            case RESULT_OK:
                onEventConnectResult.handleResultConnect(Module.ResultConnect.STATUS_LOAD_OK);
                break;
            case RESULT_CANCELED:
                scaleModule.obtainMessage(RESULT_CANCELED, "Connect error").sendToTarget();
                break;
            default:

        }
    }

    /** Соеденяемся с Весовым модулем.
     * Инициализируем созданый экземпляр модуля.
     */
    private void connectScaleModule(String address) {
        try {
            scaleModule.init(address);
            scaleModule.attach();
        } catch (Exception e) {
            openSearch();
        }

    }

    private void setupWeightView() {

        progressBarWeight = (ProgressBar) findViewById(R.id.progressBarWeight);
        progressBarWeight.setMax(ScaleModule.getMarginTenzo());
        progressBarWeight.setSecondaryProgress(ScaleModule.getLimitTenzo());

        progressBarStable = (ProgressBar)findViewById(R.id.progressBarStable);
        progressBarStable.setMax(COUNT_STABLE);
        progressBarStable.setSecondaryProgress(numStable = 0);

        //weightTextView = new TextView(this);
        weightTextView = (TextView) findViewById(R.id.weightTextView);
        //weightTextView.setMax(COUNT_STABLE);
        //weightTextView.setSecondaryProgress(numStable = 0);

        //textLog = (TextView)findViewById(R.id.textLog);
        //textLog.setTextSize(getResources().getDimension(R.dimen.text_micro));

        dProgressWeight = getResources().getDrawable(R.drawable.progress_weight);
        dWeightDanger = getResources().getDrawable(R.drawable.progress_weight_danger);

        SimpleGestureFilter.SimpleGestureListener weightViewGestureListener = new SimpleGestureFilter.SimpleGestureListener() {
            @Override
            public void onSwipe(int direction) {

                switch (direction) {
                    case SimpleGestureFilter.SWIPE_RIGHT:
                    case SimpleGestureFilter.SWIPE_LEFT:
                        weightViewIsSwipe = true;
                        break;
                    default:
                }
            }

            @Override
            public void onDoubleTap() {
                progressBarStable.setSecondaryProgress(0);
                vibrator.vibrate(100);
                new ZeroThread(ActivityScales.this).start();
            }
        };

        detectorWeightView = new SimpleGestureFilter(this, weightViewGestureListener);
        detectorWeightView.setSwipeMinVelocity(50);
        weightTextView.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                detectorWeightView.setSwipeMaxDistance(v.getMeasuredWidth());
                detectorWeightView.setSwipeMinDistance(detectorWeightView.getSwipeMaxDistance() / 3);
                detectorWeightView.onTouchEvent(event);
                switch (event.getAction()) {
                    case MotionEvent.ACTION_MOVE:
                        touchWeightView = true;
                        vibrator.vibrate(5);
                        int progress = (int) (event.getX() / (detectorWeightView.getSwipeMaxDistance() / progressBarStable.getMax()));
                        progressBarStable.setSecondaryProgress(progress);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        progressBarStable.setSecondaryProgress(0);
                        touchWeightView = false;
                        break;
                    default:
                }
                return false;
            }
        });

        layoutScale.setVisibility(View.VISIBLE);
    }

    private void setupListView(){
        arrayList = new ArrayList<>();
        customListAdapter = new CustomListAdapter(this, R.layout.list_item_weight, arrayList);
        listView.setAdapter(customListAdapter);
    }

    /**
     * Открыть активность поиска весов.
     */
    private void openSearch() {
        handlerWeight.stop(true);
        //autoWeightThread.cancel();
        scaleModule.removeCallbacksAndMessages(null);
        scaleModule.dettach();
        startActivityForResult(new Intent(getBaseContext(), ActivitySearch.class), REQUEST_SEARCH_SCALE);
    }

    /**
     * Захват веса для авто сохранения веса.
     * Задержка захвата от ложных срабатываний. Устанавливается значения в настройках.
     *
     * @return true - Условия захвата истины.
     */
    public boolean isCapture() {
        boolean capture = false;
        while (getWeightToStepMeasuring(moduleWeight) > Main.autoCapture) {
            if (capture) {
                return true;
            } else {
                try {
                    TimeUnit.SECONDS.sleep(Main.timeDelayDetectCapture);
                } catch (InterruptedException ignored) {
                }
                capture = true;
            }
        }
        return false;
    }

    public boolean processStable(int weight) {
        if (tempWeight - Main.stepMeasuring <= weight && tempWeight + Main.stepMeasuring >= weight) {
            if (++numStable >= COUNT_STABLE) {
                return true;
            }
        } else {
            numStable = 0;
        }
        tempWeight = weight;
        return false;
    }

    /**
     * Преобразовать вес в шкалу шага веса.
     * Шаг измерения установливается в настройках.
     *
     * @param weight Вес для преобразования.
     * @return Преобразованый вес.
     */
    private int getWeightToStepMeasuring(int weight) {
        return weight / Main.stepMeasuring * Main.stepMeasuring;
    }

    //==================================================================================================================
   /* public void log(String string) { //для текста
        textLog.setText(string + '\n' + textLog.getText());
    }*/

    protected void exit() {
        stopThread();
        if (broadcastReceiver != null)
            unregisterReceiver(broadcastReceiver);
        scaleModule.dettach();
        scaleModule.getAdapter().disable();
        while (scaleModule.getAdapter().isEnabled()) ;
    }

    private void wakeUp(){
        PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock((PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP), "TAG");
        wakeLock.acquire();
    }

    private void screenUnlock(){
        KeyguardManager keyguardManager = (KeyguardManager) getApplicationContext().getSystemService(Context.KEYGUARD_SERVICE);
        KeyguardManager.KeyguardLock keyguardLock = keyguardManager.newKeyguardLock("TAG");
        keyguardLock.disableKeyguard();
    }

    OnEventConnectResult onEventConnectResult = new OnEventConnectResult() {
        AlertDialog.Builder dialog;
        ProgressDialog dialogSearch;

        /** Сообщение о результате соединения
         * @param resultConnect Результат соединения энкмератор ResultConnect.
         */
        @Override
        public void handleResultConnect(Module.ResultConnect resultConnect) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switch (resultConnect) {
                        case STATUS_LOAD_OK:
                            try {
                                setTitle(getString(R.string.app_name) + " \"" + ScaleModule.getNameBluetoothDevice() + "\", v." + ScaleModule.getNumVersion()); //установить заголовок
                            } catch (Exception e) {
                                setTitle(getString(R.string.app_name) + " , v." + ScaleModule.getNumVersion()); //установить заголовок
                            }
                            Preferences.write(getString(R.string.KEY_LAST_SCALES), ScaleModule.getAddressBluetoothDevice());
                            setupListView();
                            setupWeightView();
                            handlerWeight.start();
                            handlerBatteryTemperature.start();
                            startThread();
                            break;
                        case STATUS_SCALE_UNKNOWN:

                            break;
                        case STATUS_ATTACH_START:
                            dialogSearch = new ProgressDialog(ActivityScales.this);
                            dialogSearch.setCancelable(false);
                            dialogSearch.setIndeterminate(false);
                            dialogSearch.show();
                            dialogSearch.setContentView(R.layout.custom_progress_dialog);
                            TextView tv1 = (TextView) dialogSearch.findViewById(R.id.textView1);
                            tv1.setText(getString(R.string.Connecting) + '\n' + ScaleModule.getNameBluetoothDevice());
                            break;
                        case STATUS_ATTACH_FINISH:
                            if (dialogSearch.isShowing()) {
                                dialogSearch.dismiss();
                            }
                            break;
                        default:
                    }
                }
            });

        }

        /** Сообщение о ошибки соединения.
         * @param error Тип ошибки энумератор Error.
         * @param s Описание ошибки.
         */
        @Override
        public void handleConnectError(final Module.ResultError error, final String s) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switch (error) {
                        case TERMINAL_ERROR:
                            dialog = new AlertDialog.Builder(ActivityScales.this);
                            dialog.setTitle(getString(R.string.preferences_error));
                            dialog.setCancelable(false);
                            dialog.setNegativeButton(getString(R.string.Close), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.dismiss();
                                    doubleBackToExitPressedOnce = true;
                                    onBackPressed();
                                }
                            });
                            dialog.setMessage(s);
                            Toast.makeText(getBaseContext(), R.string.preferences_error, Toast.LENGTH_SHORT).show();
                            setTitle(getString(R.string.app_name) + ": " + getString(R.string.preferences_error));
                            dialog.setPositiveButton(getString(R.string.OK), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    startActivity(new Intent(ActivityScales.this, ActivityPreferences.class));
                                    dialogInterface.dismiss();
                                }
                            });
                            dialog.show();
                            break;
                        case MODULE_ERROR:
                            dialog = new AlertDialog.Builder(ActivityScales.this);
                            dialog.setTitle("Ошибка в настройках");
                            dialog.setCancelable(false);
                            dialog.setNegativeButton(getString(R.string.Close), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.dismiss();
                                    onBackPressed();
                                }
                            });
                            dialog.setMessage("Запросите настройки у администратора. Настройки должен выполнять опытный пользователь. Ошибка(" + s + ')');
                            Toast.makeText(getBaseContext(), R.string.preferences_error, Toast.LENGTH_SHORT).show();
                            setTitle(getString(R.string.app_name) + ": админ настройки неправельные");
                            dialog.setPositiveButton(getString(R.string.OK), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    startActivity(new Intent(ActivityScales.this, ActivityTuning.class));
                                    dialogInterface.dismiss();
                                }
                            });
                            dialog.show();
                            break;
                        case CONNECT_ERROR:
                            setTitle(getString(R.string.app_name) + getString(R.string.NO_CONNECT)); //установить заголовок
                            layoutScale.setVisibility(View.INVISIBLE);
                            Intent intent = new Intent(getBaseContext(), ActivitySearch.class);
                            startActivityForResult(intent, REQUEST_SEARCH_SCALE);
                            break;
                        default:
                    }
                }
            });
        }
    };

    /**
     * Обработчик показаний веса.
     * Возвращяем время обновления показаний веса в милисекундах.
     */
    final ScaleModule.HandlerWeight handlerWeight = new ScaleModule.HandlerWeight() {
        /** Сообщение показаний веса.
         * @param what Результат статуса сообщения энумератор ResultWeight.
         * @param weight Данные веса в килограмах.
         * @param sensor Данные показания сенсорного датчика.
         * @return Время обновления показаний в милисекундах.
         */

        @Override
        public int onEvent(final ScaleModule.ResultWeight what, final int weight, final int sensor) {

            moduleWeight = getWeightToStepMeasuring(weight);

            runOnUiThread(new Runnable() {
                Rect bounds;
                SpannableStringBuilder w;
                String textWeight = String.valueOf(moduleWeight);
                @Override
                public void run() {

                    switch (what) {
                        case WEIGHT_NORMAL:
                            w = new SpannableStringBuilder(textWeight);
                            w.setSpan(new AbsoluteSizeSpan(getResources().getDimensionPixelSize(R.dimen.text_big)), 0, w.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                            w.setSpan(new ForegroundColorSpan(Color.WHITE), 0, w.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                            w.append(textKg);
                            progressBarWeight.setProgress(sensor);
                            progressBarWeight.setProgressDrawable(dProgressWeight);

                        break;
                        case WEIGHT_LIMIT:
                            w = new SpannableStringBuilder(textWeight);
                            w.setSpan(new AbsoluteSizeSpan(getResources().getDimensionPixelSize(R.dimen.text_big)), 0, w.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                            w.setSpan(new ForegroundColorSpan(Color.RED), 0, w.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                            w.append(textKg);
                            progressBarWeight.setProgress(sensor);
                            progressBarWeight.setProgressDrawable(dWeightDanger);
                        break;
                        case WEIGHT_MARGIN:
                            w = new SpannableStringBuilder(getString(R.string.OVER_LOAD));
                            w.setSpan(new AbsoluteSizeSpan(getResources().getDimensionPixelSize(R.dimen.text_large_xx)), 0, w.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                            w.setSpan(new ForegroundColorSpan(Color.RED), 0, w.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                            progressBarWeight.setProgress(sensor);
                            vibrator.vibrate(100);
                        break;
                        case WEIGHT_ERROR:
                            w = new SpannableStringBuilder(getString(R.string.NO_CONNECT));
                            w.setSpan(new AbsoluteSizeSpan(getResources().getDimensionPixelSize(R.dimen.text_large_xx)), 0, w.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                            w.setSpan(new ForegroundColorSpan(Color.RED), 0, w.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                            moduleWeight = 0;
                            progressBarWeight.setProgress(0);
                        break;
                        default:
                    }
                    weightTextView.setText(w, TextView.BufferType.SPANNABLE);
                }
            });
            return 50; // Обновляем через милисикунды
        }
    };

    /** Обработчик показаний заряда батареи и температуры.
     * Возвращяет время обновления в секундах.
     */
    private final ScaleModule.HandlerBatteryTemperature handlerBatteryTemperature = new ScaleModule.HandlerBatteryTemperature() {
        /** Сообщение
         * @param battery Заряд батареи в процентах.
         * @param temperature Температура в градусах.
         * @return Время обновления показаний заряда батареи и температуры в секундах.*/
        @Override
        public int onEvent(final int battery, final int temperature) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (battery > 15) {
                        textViewBattery.setText("заряд батареи " + battery + '%' + " t- " + temperature + '°' + 'C');
                        textViewBattery.setTextColor(Color.WHITE);
                        textViewBattery.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_battery, 0, 0, 0);
                    } else if (battery > 0) {
                        textViewBattery.setText("заряд низкий!!! " + battery + '%' + " t- " + temperature + '°' + 'C');
                        textViewBattery.setTextColor(Color.RED);
                        textViewBattery.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_battery_red, 0, 0, 0);
                    }

                }
            });
            return 5; //Обновляется через секунд
        }
    };

    @Override
    public void run() {
        handler.obtainMessage(Action.START.ordinal()).sendToTarget();
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        while (running) {

            weightViewIsSwipe = false;
            numStable = 0;

            while (running && !isCapture() && !weightViewIsSwipe) {                                                     //ждём начала нагружения
                try { Thread.sleep(50); } catch (InterruptedException ignored) { }
            }
            handler.obtainMessage(Action.START_WEIGHTING.ordinal()).sendToTarget();
            isStable = false;
            while (running && !(isStable || weightViewIsSwipe)) {                                                       //ждем стабилизации веса или нажатием выбора веса
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                if (!touchWeightView) {                                                                                 //если не прикасаемся к индикатору тогда стабилизируем вес
                    isStable = processStable(moduleWeight);
                    handler.obtainMessage(Action.UPDATE_PROGRESS.ordinal(), numStable, 0).sendToTarget();
                }
            }
            numStable = COUNT_STABLE;
            if (!running) {
                break;
            }
            tempWeight = moduleWeight;
            if (isStable || weightViewIsSwipe) {
                handler.obtainMessage(Action.STORE_WEIGHTING.ordinal(), moduleWeight, 0).sendToTarget();                 //сохраняем стабильный вес
            }

            while (running && !((moduleWeight >= tempWeight + Main.default_min_auto_capture)
                    || (moduleWeight <= tempWeight- Main.default_min_auto_capture))) {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}                                       // ждем изменения веса
            }

            handler.obtainMessage(Action.STOP_WEIGHTING.ordinal()).sendToTarget();
        }
    }

    /**
     * Обработчик сообщений.
     */
    final Handler handler = new Handler() {
        /** Сообщение от обработчика авто сохранения.
         * @param msg Данные сообщения.
         */
        @Override
        public void handleMessage(Message msg) {

            switch (Action.values()[msg.what]) {
                case UPDATE_PROGRESS:
                    progressBarStable.setSecondaryProgress(msg.arg1);
                    break;
                case STORE_WEIGHTING:
                    vibrator.vibrate(100);
                    /*StringBuilder stringBuilder = new StringBuilder();
                    Date date = new Date();
                    if(msg.arg2 == 0)
                        stringBuilder.append("А--");
                    else
                        stringBuilder.append("Р--");
                    stringBuilder.append(msg.arg1 + getString(R.string.scales_kg)).append(" ");

                    stringBuilder.append("(").append(new SimpleDateFormat("dd.MM.yyyy").format(date)).append("--");
                    stringBuilder.append(new SimpleDateFormat("HH:mm:ss").format(date)).append(")").append('\n');*/
                    arrayList.add(new WeightObject(msg.arg1));
                    customListAdapter.notifyDataSetChanged();
                    //log(stringBuilder.toString());
                    //buttonFinish.setEnabled(true);
                    //buttonFinish.setAlpha(255);
                    break;
                case START:
                case STOP_WEIGHTING:
                    //weightTypeUpdate();
                    //buttonFinish.setEnabled(true);
                    //buttonFinish.setAlpha(255);
                    //((OnCheckEventListener) mTabsAdapter.getCurrentFragment()).someEvent();todo
                    progressBarStable.setSecondaryProgress(0);
                    flagExit = true;
                    break;
                case START_WEIGHTING:
                    //buttonFinish.setEnabled(false);
                    //buttonFinish.setAlpha(100);
                    flagExit = false;
                    break;
                default:
            }
        }
    };

    /**
     * Обработка обнуления весов.
     */
    private class ZeroThread extends Thread {
        private final ProgressDialog dialog;

        ZeroThread(Context context) {
            // Создаём новый поток
            super(getString(R.string.Zeroing));
            dialog = new ProgressDialog(context);
            dialog.setCancelable(false);
            dialog.setIndeterminate(false);
            dialog.show();
            dialog.setContentView(R.layout.custom_progress_dialog);
            TextView tv1 = (TextView) dialog.findViewById(R.id.textView1);
            tv1.setText(R.string.Zeroing);
            //start(); // Запускаем поток
        }

        @Override
        public void run() {
            ScaleModule.setOffsetScale();
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        }
    }

    public void startThread(){
        running = true;
        threadAutoWeight = new Thread(this);
        threadAutoWeight.start();
    }

    public void stopThread(){
        running = false;
        boolean retry = true;
        while(retry){
            try {
                threadAutoWeight.join();
                retry = false;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    class CustomListAdapter extends ArrayAdapter<WeightObject>{
        ArrayList<WeightObject> item;

        public CustomListAdapter(Context context, int textViewResourceId, ArrayList<WeightObject> objects) {
            super(context, textViewResourceId, objects);
            item = objects;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                LayoutInflater layoutInflater = (LayoutInflater) super.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = layoutInflater.inflate(R.layout.list_item_weight, parent, false);
            }

            WeightObject o = getItem(position);
            if(o != null){
                TextView tt = (TextView) view.findViewById(R.id.topText);
                TextView bt = (TextView) view.findViewById(R.id.bottomText);

                tt.setText(String.valueOf(o.getWeight())+" кг");
                bt.setText(o.getTime() + "   " + o.getDate());
            }


            return view;
        }
    }

    class WeightObject {
        String date;
        String time;
        int weight;

        WeightObject(int weight){
            this.weight = weight;
            Date d = new Date();
            date = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(d);
            time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(d);
        }

        public int getWeight() { return weight; }

        public String getDate() { return date;  }

        public String getTime() { return time;  }
    }

    public void removeAtomPayOnClickHandler(View view) {

        //ListView list = getListView();
        int position = listView.getPositionForView(view);
        arrayList.remove(position);
        customListAdapter.notifyDataSetChanged();
    }

}
