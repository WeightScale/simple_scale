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
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import com.konst.module.Module;
import com.konst.module.OnEventConnectResult;
import com.konst.module.ScaleModule;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class ActivityScales extends Activity implements View.OnClickListener{
    private ProgressBar progressBarWeight;
    private WeightTextView weightTextView;
    private Drawable dProgressWeight, dWeightDanger;
    private SimpleGestureFilter detectorWeightView;
    private ImageView buttonFinish;
    private TextView textLog;
    private Vibrator vibrator; //вибратор
    private LinearLayout layoutScale;
    private BroadcastReceiver broadcastReceiver; //приёмник намерений
    final AutoWeight autoWeight = new AutoWeight();
    ScaleModule scaleModule;

    public int numStable;
    int moduleWeight;
    int moduleSensorValue;
    protected int tempWeight;
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
    protected void onResume() {
        super.onResume();
        if (!autoWeight.isStart()) {
            new Thread(autoWeight).start();
        }
        handlerWeight.start();
        screenUnlock();
    }

    @Override
    protected void onPause() {
        super.onPause();
        autoWeight.cancel();
        handlerWeight.stop(true);
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scale);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        layoutScale = (LinearLayout)findViewById(R.id.screenScale);
        layoutScale.setVisibility(View.INVISIBLE);

        buttonFinish = (ImageView) findViewById(R.id.buttonFinish);
        buttonFinish.setOnClickListener(this);

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
            connectScaleModule(Preferences.read(ActivityPreferences.KEY_LAST_SCALES, ""));
        } catch (Exception e) {
            Toast.makeText(getBaseContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            exit();
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
        //exit();
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

        weightTextView = new WeightTextView(this);
        weightTextView = (WeightTextView) findViewById(R.id.weightTextView);
        weightTextView.setMax(COUNT_STABLE);
        weightTextView.setSecondaryProgress(numStable = 0);

        textLog = (TextView)findViewById(R.id.textLog);
        textLog.setTextSize(getResources().getDimension(R.dimen.text_micro));

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
                weightTextView.setSecondaryProgress(0);
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
                        int progress = (int) (event.getX() / (detectorWeightView.getSwipeMaxDistance() / weightTextView.getMax()));
                        weightTextView.setSecondaryProgress(progress);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        touchWeightView = false;
                        break;
                    default:
                }
                return false;
            }
        });

        layoutScale.setVisibility(View.VISIBLE);
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
    public void log(String string) { //для текста
        textLog.setText(string + '\n' + textLog.getText());
    }

    protected void exit() {
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
                            Preferences.write(ActivityPreferences.KEY_LAST_SCALES, ScaleModule.getAddressBluetoothDevice());
                            setupWeightView();
                            handlerWeight.start();
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
            runOnUiThread(new Runnable() {
                Rect bounds;

                @Override
                public void run() {
                    switch (what) {
                        case WEIGHT_NORMAL:
                            moduleWeight = weight;
                            moduleSensorValue = sensor;
                            progressBarWeight.setProgress(sensor);
                            bounds = progressBarWeight.getProgressDrawable().getBounds();
                            weightTextView.updateProgress(getWeightToStepMeasuring(weight), Color.BLACK, getResources().getDimension(R.dimen.text_big));
                            progressBarWeight.setProgressDrawable(dProgressWeight);
                            progressBarWeight.getProgressDrawable().setBounds(bounds);
                        break;
                        case WEIGHT_LIMIT:
                            moduleWeight = weight;
                            moduleSensorValue = sensor;
                            progressBarWeight.setProgress(sensor);
                            bounds = progressBarWeight.getProgressDrawable().getBounds();
                            weightTextView.updateProgress(getWeightToStepMeasuring(weight), Color.RED, getResources().getDimension(R.dimen.text_big));
                            progressBarWeight.setProgressDrawable(dWeightDanger);
                            progressBarWeight.getProgressDrawable().setBounds(bounds);
                        break;
                        case WEIGHT_MARGIN:
                            moduleWeight = weight;
                            moduleSensorValue = sensor;
                            progressBarWeight.setProgress(sensor);
                            weightTextView.updateProgress(getString(R.string.OVER_LOAD), Color.RED, getResources().getDimension(R.dimen.text_large_xx));
                            vibrator.vibrate(100);
                        break;
                        case WEIGHT_ERROR:
                            moduleWeight = 0;
                            moduleSensorValue = 0;
                            weightTextView.updateProgress(getString(R.string.NO_CONNECT), Color.BLACK, getResources().getDimension(R.dimen.text_large_x));
                            progressBarWeight.setProgress(0);
                        break;
                        default:
                    }
                }
            });
            return 50; // Обновляем через милисикунды
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

    class AutoWeight implements Runnable {
        private boolean start;
        private boolean cancelled;
        private int tempWeight;

        /**
         * Остановка взвешивания
         */
        final int ACTION_STOP_WEIGHTING = 1;
        /**
         * Пуск взвешивания
         */
        final int ACTION_START_WEIGHTING = 2;
        /**
         * Сохранить результат взвешивания
         */
        final int ACTION_STORE_WEIGHTING = 3;
        /**
         * Обновить данные веса.
         */
        final int ACTION_UPDATE_PROGRESS = 4;
        /**
         * Начало процесса.
         */
        final int ACTION_START = 5;

        @Override
        public void run() {
            start = true;
            cancelled = false;
            handler.sendMessage(handler.obtainMessage(ACTION_START));
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            while (!cancelled) {

                weightViewIsSwipe = false;
                numStable = 0;

                while (!cancelled && !isCapture() && !weightViewIsSwipe) {                                              //ждём начала нагружения
                    try { Thread.sleep(50); } catch (InterruptedException ignored) { }
                }
                handler.sendMessage(handler.obtainMessage(ACTION_START_WEIGHTING));
                isStable = false;
                while (!cancelled && !(isStable || weightViewIsSwipe)) {                                                //ждем стабилизации веса или нажатием выбора веса
                    try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                    if (!touchWeightView) {                                                                              //если не прикасаемся к индикатору тогда стабилизируем вес
                        isStable = processStable(getWeightToStepMeasuring(moduleWeight));
                        handler.sendMessage(handler.obtainMessage(ACTION_UPDATE_PROGRESS, numStable, 0));
                    }
                }
                numStable = COUNT_STABLE;
                if (cancelled) {
                    break;
                }
                tempWeight = moduleWeight;
                if (isStable) {
                    handler.sendMessage(handler.obtainMessage(ACTION_STORE_WEIGHTING, getWeightToStepMeasuring(moduleWeight), 0));                 //сохраняем стабильный вес
                }else if (weightViewIsSwipe){
                    handler.sendMessage(handler.obtainMessage(ACTION_STORE_WEIGHTING, getWeightToStepMeasuring(moduleWeight), 1));
                    weightViewIsSwipe = false;
                }

                while (!cancelled && !((getWeightToStepMeasuring(moduleWeight) >= tempWeight + Main.default_min_auto_capture)
                        || (getWeightToStepMeasuring(moduleWeight) <= tempWeight- Main.default_min_auto_capture))) {
                    try { Thread.sleep(50); } catch (InterruptedException ignored) {}                                   // ждем изменения веса
                }

                /*while (!cancelled && getWeightToStepMeasuring(moduleWeight) >= Main.default_min_auto_capture) {
                    try { Thread.sleep(50); } catch (InterruptedException ignored) {}                                   // ждем разгрузки весов
                }
                vibrator.vibrate(100);
                handler.sendMessage(handler.obtainMessage(ACTION_UPDATE_PROGRESS, 0, 0));
                if (cancelled) {
                    if (isStable && weightType == WeightType.SECOND) {                                                  //Если тара зафоксирована и выход через кнопку назад
                        weightType = WeightType.NETTO;
                    }
                    break;
                }
                try { TimeUnit.SECONDS.sleep(2); } catch (InterruptedException ignored) {  }                            //задержка

                if (weightType == WeightType.SECOND) {
                    cancelled = true;
                }*/

                handler.sendMessage(handler.obtainMessage(ACTION_STOP_WEIGHTING));
            }
            start = false;
        }

        private void cancel() {
            cancelled = true;
        }

        boolean isStart(){
            return start;
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

                switch (msg.what) {
                    case ACTION_UPDATE_PROGRESS:
                        weightTextView.setSecondaryProgress(msg.arg1);
                    break;
                    case ACTION_STORE_WEIGHTING:
                        StringBuilder stringBuilder = new StringBuilder();
                        Date date = new Date();
                        if(msg.arg2 == 0)
                            stringBuilder.append("А--");
                        else
                            stringBuilder.append("Р--");
                        stringBuilder.append(msg.arg1 + getString(R.string.scales_kg)).append(" ");

                        stringBuilder.append("(").append(new SimpleDateFormat("dd.MM.yyyy").format(date)).append("--");
                        stringBuilder.append(new SimpleDateFormat("HH:mm:ss").format(date)).append(")").append('\n');

                        log(stringBuilder.toString());
                        buttonFinish.setEnabled(true);
                        buttonFinish.setAlpha(255);
                    break;
                    case ACTION_START:
                    case ACTION_STOP_WEIGHTING:
                        //weightTypeUpdate();
                        buttonFinish.setEnabled(true);
                        buttonFinish.setAlpha(255);
                        //((OnCheckEventListener) mTabsAdapter.getCurrentFragment()).someEvent();todo
                        flagExit = true;
                    break;
                    case ACTION_START_WEIGHTING:
                        buttonFinish.setEnabled(false);
                        buttonFinish.setAlpha(100);
                        flagExit = false;
                    break;
                    default:
                }
            }
        };
    }

}
