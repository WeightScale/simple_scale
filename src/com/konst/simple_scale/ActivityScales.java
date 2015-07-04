package com.konst.simple_scale;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import com.konst.module.Module;
import com.konst.module.ScaleModule;

import java.util.concurrent.TimeUnit;

public class ActivityScales extends Activity implements View.OnClickListener{
    private ProgressBar progressBarWeight;
    private WeightTextView weightTextView;
    private Drawable dProgressWeight, dWeightDanger;
    private SimpleGestureFilter detectorWeightView;
    private ImageView buttonFinish;
    private final AutoWeightThread autoWeightThread = new AutoWeightThread();
    private Vibrator vibrator; //вибратор
    private LinearLayout layoutScale;
    private BluetoothAdapter bluetooth; //блютуз адаптер
    private BroadcastReceiver broadcastReceiver; //приёмник намерений

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

    /**
     * Энумератор типа веса.
     */
    protected enum WeightType {
        /**
         * Первое взвешивание
         */
        FIRST,
        /**
         * Второе взвешивание
         */
        SECOND,
        /**
         * Вес нетто
         */
        NETTO
    }

    public WeightType weightType;

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.buttonFinish:
                onBackPressed();
                break;
            case R.id.imageMenu:

                break;
            default:
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!autoWeightThread.isStart()) {
            autoWeightThread.start();
        }
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
                            switch (bluetooth.getState()) {
                                case BluetoothAdapter.STATE_OFF:
                                    Toast.makeText(getBaseContext(), R.string.bluetooth_off, Toast.LENGTH_SHORT).show();
                                    new Internet(getApplicationContext()).turnOnWiFiConnection(false);
                                    bluetooth.enable();
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
                            /*listView.setOnItemClickListener(null);
                            linearBatteryTemp.setVisibility(View.INVISIBLE);
                            imageViewRemote.setImageDrawable(getResources().getDrawable(R.drawable.rss_off));
                            imageNewCheck.setEnabled(false);*/
                            isScaleConnect = false;
                            break;
                        case BluetoothDevice.ACTION_ACL_CONNECTED://найдено соеденено
                            vibrator.vibrate(200);
                            /*listView.setOnItemClickListener(onItemClickListener);
                            linearBatteryTemp.setVisibility(View.VISIBLE);
                            imageViewRemote.setImageDrawable(getResources().getDrawable(R.drawable.rss_on));
                            imageNewCheck.setEnabled(true);*/
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

        bluetooth = BluetoothAdapter.getDefaultAdapter();
        if (bluetooth == null) {
            Toast.makeText(getBaseContext(), R.string.bluetooth_no, Toast.LENGTH_LONG).show();
            finish();
        } else {
            Toast.makeText(getBaseContext(), R.string.bluetooth_off, Toast.LENGTH_SHORT).show();
            bluetooth.enable();
            assert bluetooth != null;
            while (!bluetooth.isEnabled()) ;//ждем включения bluetooth
            connectScaleModule(Preferences.read(ActivityPreferences.KEY_LAST_SCALES, ""));
        }
    }

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            //exit();
            return;
        }
        bluetooth.cancelDiscovery();
        doubleBackToExitPressedOnce = true;
        Toast.makeText(this, R.string.press_again_to_exit /*Please click BACK again to exit*/, Toast.LENGTH_SHORT).show();
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

    /** Соеденяемся с Весовым модулем.
     * Инициализируем созданый экземпляр модуля.
     *
     * @param device Адресс bluetooth модуля.
     */
    private void connectScaleModule(String device) {
        try {
            scaleModule.init(Main.packageInfo.versionName, device);
        } catch (Exception e) {
            scaleModule.handleConnectError(Module.ResultError.CONNECT_ERROR, e.getMessage());
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

        dProgressWeight = getResources().getDrawable(R.drawable.progress_weight);
        dWeightDanger = getResources().getDrawable(R.drawable.progress_weight_danger);

        SimpleGestureFilter.SimpleGestureListener weightViewGestureListener = new SimpleGestureFilter.SimpleGestureListener() {
            @Override
            public void onSwipe(int direction) {

                switch (direction) {
                    case SimpleGestureFilter.SWIPE_RIGHT:
                    case SimpleGestureFilter.SWIPE_LEFT:
                        if (saveWeight(moduleWeight)) {
                            weightViewIsSwipe = true;
                            buttonFinish.setEnabled(true);
                            buttonFinish.setAlpha(255);
                            flagExit = true;
                        }
                        /*if (weightType == WeightType.SECOND) {
                            weightTypeUpdate();
                        }*/
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

    private boolean saveWeight(int weight/*, WeightType type*/) {
        boolean flag = false;
        /*switch (weightType) {
            case FIRST:
                if (weight > 0) {
                    //values.put(CheckTable.KEY_WEIGHT_FIRST, weight);//todo
                    vibrator.vibrate(100); //вибрация
                    flag = true;
                }
                break;
            case SECOND:
                //values.put(CheckTable.KEY_WEIGHT_SECOND, weight);//todo
                //int total = sumNetto();
                //values.put(CheckTable.KEY_PRICE_SUM, total);//todo
                vibrator.vibrate(100); //вибрация
                flag = true;
                break;
            case NETTO:
                handlerWeight.process(false);
                exit();
                break;
        }
        if (flag) {
            //((OnCheckEventListener) mTabsAdapter.getCurrentFragment()).someEvent();
            buttonFinish.setEnabled(true);
            buttonFinish.setAlpha(255);
            flagExit = true;
        }*/
        return flag;
    }

    /*private void weightTypeUpdate() {
        switch (weightType) {
            case FIRST:
                weightType = WeightType.SECOND;
                break;
            case SECOND:
                weightType = WeightType.NETTO;
                saveWeight(0);
                break;
            default:
                weightType = WeightType.FIRST;
        }
        buttonFinish.setEnabled(true);
        buttonFinish.setAlpha(255);
        flagExit = true;
    }*/

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

    protected void exit() {
        handlerWeight.stop(true);
        autoWeightThread.cancel();
        while (autoWeightThread.isStart()) ;
        if (broadcastReceiver != null)
            unregisterReceiver(broadcastReceiver);
        scaleModule.removeCallbacksAndMessages(null);
        scaleModule.dettach();
        bluetooth.disable();
        while (bluetooth.isEnabled()) ;
        //checkTable.updateEntry(entryID, values);
        //finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //AlertDialog.Builder dialog;
        setProgressBarIndeterminateVisibility(false);
        switch (resultCode) {
            case RESULT_OK:
                scaleModule.handleResultConnect(Module.ResultConnect.STATUS_LOAD_OK);
                break;
            case RESULT_CANCELED:
                scaleModule.obtainMessage(RESULT_CANCELED, "Connect error").sendToTarget();
                break;
            default:

        }
    }

    ScaleModule scaleModule = new ScaleModule() {
        AlertDialog.Builder dialog;
        ProgressDialog dialogSearch;

        /** Сообщение о результате соединения
         * @param resultConnect Результат соединения энкмератор ResultConnect.
         */
        @Override
        public void handleResultConnect(ResultConnect resultConnect) {
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

        /** Сообщение о ошибки соединения
         * @param error Тип ошибки энумератор Error.
         * @param s Описание ошибки.
         */
        @Override
        public void handleConnectError(final ResultError error, final String s) {
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
         * @return Время обновления показаний в милисекундах.*/
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
                            weightTextView.updateProgress(getString(R.string.NO_CONNECT), Color.BLACK, getResources().getDimension(R.dimen.text_large_xx));
                            progressBarWeight.setProgress(0);
                        break;
                        default:
                    }

                }
            });
            return 20; // Обновляем через милисикунды
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

    /**
     * Обработка авто сохранения веса.
     */
    private class AutoWeightThread extends Thread {
        private boolean start;
        private boolean cancelled;

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

        @Override
        public synchronized void start() {
            setPriority(Thread.MIN_PRIORITY);
            super.start();
            start = true;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
            while (!cancelled) {

                weightViewIsSwipe = false;
                numStable = 0;

                while (!cancelled && !isCapture() && !weightViewIsSwipe) {                                              //ждём начала нагружения
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                    }
                }
                handler.sendMessage(handler.obtainMessage(ACTION_START_WEIGHTING));
                isStable = false;
                while (!cancelled && !(isStable || weightViewIsSwipe)) {                                                //ждем стабилизации веса или нажатием выбора веса
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                    }
                    if (!touchWeightView) {                                                                              //если не прикасаемся к индикатору тогда стабилизируем вес
                        isStable = processStable(getWeightToStepMeasuring(moduleWeight));
                        handler.sendMessage(handler.obtainMessage(ACTION_UPDATE_PROGRESS, numStable, 0));
                    }
                }
                numStable = COUNT_STABLE;
                if (cancelled) {
                    break;
                }
                if (isStable) {
                    handler.sendMessage(handler.obtainMessage(ACTION_STORE_WEIGHTING, moduleWeight, 0));                 //сохраняем стабильный вес
                }

                weightViewIsSwipe = false;

                while (!cancelled && getWeightToStepMeasuring(moduleWeight) >= Main.default_min_auto_capture) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                    }                                   // ждем разгрузки весов
                }
                vibrator.vibrate(100);
                handler.sendMessage(handler.obtainMessage(ACTION_UPDATE_PROGRESS, 0, 0));
                if (cancelled) {
                    if (isStable && weightType == WeightType.SECOND) {                                                  //Если тара зафоксирована и выход через кнопку назад
                        weightType = WeightType.NETTO;
                    }
                    break;
                }
                try {
                    TimeUnit.SECONDS.sleep(2);
                } catch (InterruptedException ignored) {
                }                            //задержка

                if (weightType == WeightType.SECOND) {
                    cancelled = true;
                }

                handler.sendMessage(handler.obtainMessage(ACTION_STOP_WEIGHTING));
            }
            start = false;
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
                    case ACTION_STORE_WEIGHTING:
                        saveWeight(msg.arg1);
                    break;
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
                    case ACTION_UPDATE_PROGRESS:
                        weightTextView.setSecondaryProgress(msg.arg1);
                    break;
                    default:
                }
            }
        };

        private void cancel() {
            cancelled = true;
        }

        public boolean isStart() {
            return start;
        }
    }
}
