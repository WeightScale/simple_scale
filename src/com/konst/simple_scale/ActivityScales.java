package com.konst.simple_scale;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.*;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.*;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.TextAppearanceSpan;
import android.view.*;
import android.widget.*;
import com.konst.module.*;
import com.konst.simple_scale.settings.ActivityPreferences;
import com.konst.simple_scale.settings.ActivityTuning;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class ActivityScales extends Activity implements View.OnClickListener, Runnable{
    private Main main;
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
    private Vibrator vibrator; //вибратор
    private LinearLayout layoutScale;
    private BroadcastReceiver broadcastReceiver; //приёмник намерений
    private ScaleModule scaleModule;
    private BatteryTemperatureCallback batteryTemperatureCallback = null;
    private WeightCallback weightCallback = null;

    public int numStable;
    private int moduleWeight;
    //int moduleSensorValue;
    protected int tempWeight;
    private Thread threadAutoWeight;
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
    protected void onResume() {
        super.onResume();
        startThread();
        scaleModule.startMeasuringBatteryTemperature(batteryTemperatureCallback);
        scaleModule.startMeasuringWeight(weightCallback);
        screenUnlock();
    }

    @Override
    protected void onPause() {
        super.onPause();
        scaleModule.stopMeasuringBatteryTemperature();
        scaleModule.stopMeasuringWeight();
        stopThread();
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_ACTION_BAR);
        Thread.setDefaultUncaughtExceptionHandler(new ReportHelper(this));
        setContentView(R.layout.scale_green);

        main = (Main)getApplication();

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        progressBarWeight = (ProgressBar) findViewById(R.id.progressBarWeight);
        progressBarStable = (ProgressBar)findViewById(R.id.progressBarStable);
        weightTextView = (TextView) findViewById(R.id.weightTextView);

        layoutScale = (LinearLayout)findViewById(R.id.screenScale);
        layoutScale.setVisibility(View.INVISIBLE);

        buttonFinish = (ImageView) findViewById(R.id.buttonFinish);
        buttonFinish.setOnClickListener(this);

        textViewBattery = (TextView)findViewById(R.id.textBattery);

        listView = (ListView)findViewById(R.id.listView);
        listView.setCacheColorHint(getResources().getColor(R.color.transparent));
        listView.setVerticalFadingEdgeEnabled(false);

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

        //scaleModule = main.getScaleModule();
        //scaleModule.setOnEventConnectResult(onEventConnectResult);
        //Toast.makeText(getBaseContext(), R.string.bluetooth_off, Toast.LENGTH_SHORT).show();
        //connectScaleModule(Preferences.read(getString(R.string.KEY_LAST_SCALES), ""));
        try {
            scaleModule = new ScaleModule(main.getPackageInfo().versionName, connectResultCallback);
            //scaleModule = new ScaleModule(main.getPackageInfo().versionName, handlerConnect);
            main.setScaleModule(scaleModule);
            scaleModule.setTimerNull(main.getPreferencesScale().read(getString(R.string.KEY_TIMER_NULL), getResources().getInteger(R.integer.default_max_time_auto_null)));
            scaleModule.setWeightError(main.getPreferencesScale().read(getString(R.string.KEY_MAX_NULL), getResources().getInteger(R.integer.default_limit_auto_null)));
            Toast.makeText(getBaseContext(), R.string.bluetooth_off, Toast.LENGTH_SHORT).show();
            connectScaleModule(main.getPreferencesScale().read(getString(R.string.KEY_LAST_SCALES), ""));
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
                            if (scaleModule.isAttach())
                                scaleModule.setModulePowerOff();
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
        setProgressBarIndeterminateVisibility(false);
        switch (resultCode) {
            case RESULT_OK:
                connectResultCallback.resultConnect(Module.ResultConnect.STATUS_LOAD_OK);
                break;
            case RESULT_CANCELED:
                //scaleModule.obtainMessage(RESULT_CANCELED, "Connect error").sendToTarget();
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

        //progressBarWeight = (ProgressBar) findViewById(R.id.progressBarWeight);
        progressBarWeight.setMax(scaleModule.getMarginTenzo());
        progressBarWeight.setSecondaryProgress(scaleModule.getLimitTenzo());

        //progressBarStable = (ProgressBar)findViewById(R.id.progressBarStable);
        progressBarStable.setMax(COUNT_STABLE);
        progressBarStable.setProgress(numStable = 0);


        //weightTextView = (TextView) findViewById(R.id.weightTextView);


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
                progressBarStable.setProgress(0);
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
                        progressBarStable.setProgress(progress);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        progressBarStable.setProgress(0);
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
        scaleModule.stopMeasuringWeight();
        scaleModule.stopMeasuringBatteryTemperature();
        stopThread();
        //scaleModule.removeCallbacksAndMessages(null);
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
        while (getWeightToStepMeasuring(moduleWeight) > main.getAutoCapture()) {
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
        if (tempWeight - main.getStepMeasuring() <= weight && tempWeight + main.getStepMeasuring() >= weight) {
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
        return weight / main.getStepMeasuring() * main.getStepMeasuring();
    }

    protected void exit() {
        stopThread();
        if (broadcastReceiver != null)
            unregisterReceiver(broadcastReceiver);
        scaleModule.dettach();
        scaleModule.getAdapter().disable();
        while (scaleModule.getAdapter().isEnabled());
        System.exit(0);
        //int pid = android.os.Process.myPid();
        //android.os.Process.killProcess(pid);

        //System.runFinalization();
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

    final ConnectResultCallback connectResultCallback = new ConnectResultCallback() {
        AlertDialog.Builder dialog;
        ProgressDialog dialogSearch;

        /** Сообщение о результате соединения
         * @param resultConnect Результат соединения энкмератор ResultConnect.
         */
        @Override
        public void resultConnect(Module.ResultConnect resultConnect) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switch (resultConnect) {
                        case STATUS_LOAD_OK:
                            try {
                                setTitle(getString(R.string.app_name) + " \"" + scaleModule.getNameBluetoothDevice() + "\", v." + scaleModule.getNumVersion()); //установить заголовок
                            } catch (Exception e) {
                                setTitle(getString(R.string.app_name) + " , v." + scaleModule.getNumVersion()); //установить заголовок
                            }
                            main.getPreferencesScale().write(getString(R.string.KEY_LAST_SCALES), scaleModule.getAddressBluetoothDevice());
                            setupListView();
                            setupWeightView();
                            batteryTemperatureCallback = new BatteryTemperatureCallback();
                            weightCallback = new WeightCallback();
                            scaleModule.startMeasuringWeight(weightCallback);
                            scaleModule.startMeasuringBatteryTemperature(batteryTemperatureCallback);
                            startThread();
                            break;
                        case STATUS_VERSION_UNKNOWN:

                            break;
                        case STATUS_ATTACH_START:
                            dialogSearch = new ProgressDialog(ActivityScales.this);
                            dialogSearch.setCancelable(false);
                            dialogSearch.setIndeterminate(false);
                            dialogSearch.show();
                            dialogSearch.setContentView(R.layout.custom_progress_dialog);
                            TextView tv1 = (TextView) dialogSearch.findViewById(R.id.textView1);
                            tv1.setText(getString(R.string.Connecting) + '\n' + scaleModule.getNameBluetoothDevice());
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
        public void connectError(final Module.ResultError error, final String s) {
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
                    progressBarStable.setProgress(msg.arg1);
                    break;
                case STORE_WEIGHTING:
                    vibrator.vibrate(100);
                    arrayList.add(new WeightObject(msg.arg1));
                    customListAdapter.notifyDataSetChanged();
                    break;
                case START:
                case STOP_WEIGHTING:
                    progressBarStable.setProgress(0);
                    flagExit = true;
                    break;
                case START_WEIGHTING:
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
            scaleModule.setOffsetScale();
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        }
    }

    public void startThread(){
        running = true;
        threadAutoWeight = new Thread(this);
        //threadAutoWeight.setDaemon(true);
        threadAutoWeight.start();
    }

    public void stopThread(){
        if(threadAutoWeight != null){
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

                tt.setText(o.getWeight() +" кг");
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

    public void removeWeightOnClick(View view) {

        //ListView list = getListView();
        int position = listView.getPositionForView(view);
        arrayList.remove(position);
        vibrator.vibrate(50);
        customListAdapter.notifyDataSetChanged();
    }

    /** Класс обработчик показаний заряда батареи и температуры. */
    class  BatteryTemperatureCallback implements ScaleModule.BatteryTemperatureCallback {
        /** Сообщение
         * @param battery Заряд батареи в процентах.
         * @param temperature Температура в градусах.*/
        @Override
        public void batteryTemperature(int battery, int temperature) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (battery > 15) {
                        textViewBattery.setText("заряд батареи " + battery + '%' + "   " + temperature + '°' + 'C');
                        textViewBattery.setTextColor(Color.WHITE);
                        textViewBattery.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_battery, 0, 0, 0);
                    } else if (battery >= 0) {
                        textViewBattery.setText("заряд низкий!!! " + battery + '%' + "   " + temperature + '°' + 'C');
                        textViewBattery.setTextColor(Color.RED);
                        textViewBattery.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_battery_red, 0, 0, 0);
                    }else {
                        textViewBattery.setText("нет данных!!! " + '%' + "   " + temperature + '°' + 'C');
                        textViewBattery.setTextColor(Color.BLUE);
                        textViewBattery.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_battery_red, 0, 0, 0);
                    }

                }
            });
        }
    };

    /** Класс обработки показаний веса. */
    class WeightCallback implements ScaleModule.WeightCallback{

        /** Сообщение показаний веса.
         *  @param what Результат статуса сообщения энумератор ResultWeight.
         *  @param weight Данные веса в килограмах.
         *  @param sensor Данные показания сенсорного датчика.
         */
        @Override
        public void weight(ScaleModule.ResultWeight what, int weight, int sensor) {
            moduleWeight = getWeightToStepMeasuring(weight);
            runOnUiThread(new Runnable() {
                Rect bounds;
                SpannableStringBuilder w;
                final String textWeight = String.valueOf(moduleWeight);
                @Override
                public void run() {
                    switch (what) {
                        case WEIGHT_NORMAL:
                            w = new SpannableStringBuilder(textWeight);
                            w.setSpan(new AbsoluteSizeSpan(getResources().getDimensionPixelSize(R.dimen.text_big)), 0, w.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                            w.setSpan(new ForegroundColorSpan(Color.WHITE), 0, w.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                            w.append(textKg);
                            progressBarWeight.setProgress(sensor);
                            bounds = progressBarWeight.getProgressDrawable().getBounds();
                            progressBarWeight.setProgressDrawable(dProgressWeight);
                            progressBarWeight.getProgressDrawable().setBounds(bounds);
                            break;
                        case WEIGHT_LIMIT:
                            w = new SpannableStringBuilder(textWeight);
                            w.setSpan(new AbsoluteSizeSpan(getResources().getDimensionPixelSize(R.dimen.text_big)), 0, w.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                            w.setSpan(new ForegroundColorSpan(Color.RED), 0, w.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                            w.append(textKg);
                            progressBarWeight.setProgress(sensor);
                            bounds = progressBarWeight.getProgressDrawable().getBounds();
                            progressBarWeight.setProgressDrawable(dWeightDanger);
                            progressBarWeight.getProgressDrawable().setBounds(bounds);
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
                    //weightTextView.setText("120");
                    //weightTextView.setVisibility(View.VISIBLE);
                    //weightTextView.setTextColor(Color.WHITE);
                }
            });
        }
    }

    public class ReportHelper implements Thread.UncaughtExceptionHandler {
        private AlertDialog dialog;
        private Context context;

        public ReportHelper(Context context) {
            this.context = context;
        }

        @Override
        public void uncaughtException(Thread thread, Throwable ex) {
            showToastInThread(ex.getMessage());
        }

        public void showToastInThread(final String str){
            new Thread() {
                @Override
                public void run() {
                    Looper.prepare();
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setMessage(str)
                            .setTitle("Ошибка приложения")
                            .setCancelable(false)
                            .setNegativeButton("Выход", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();
                                    exit();
                                }
                            });
                    dialog = builder.create();

                    //Toast.makeText(context, str, Toast.LENGTH_LONG).show();
                    if(!dialog.isShowing())
                        dialog.show();
                    Looper.loop();
                }
            }.start();
        }
    }

}
