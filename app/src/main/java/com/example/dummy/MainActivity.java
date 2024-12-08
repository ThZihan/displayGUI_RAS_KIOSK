package com.example.dummy;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    // UI Elements
    private TextView tvPH, tvTurbidity, tvDO, tvEC, tvTemp, tvORP, tvTDS;

    // Receiver
    private HomeButtonReceiver homeButtonReceiver;

    // Handler and Runnable for periodic reload
    private Handler reloadHandler;
    private Runnable reloadRunnable;

    // Reload interval in milliseconds (15 seconds)
    private static final long RELOAD_INTERVAL = 15000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Set fullscreen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUI();

        setContentView(R.layout.activity_main);

        // Initialize TextViews
        tvPH = findViewById(R.id.tvPH);
        tvTurbidity = findViewById(R.id.tvTurbidity);
        tvDO = findViewById(R.id.tvDO);
        tvEC = findViewById(R.id.tvEC);
        tvTemp = findViewById(R.id.tvTemp);
        tvORP = findViewById(R.id.tvORP);
        tvTDS = findViewById(R.id.tvTDS);

        // Start data fetch
        new FetchDataTask().execute("https://api.thingspeak.com/channels/2732596/feeds.json?results=1");

        // Register HomeButtonReceiver
        homeButtonReceiver = new HomeButtonReceiver();
        IntentFilter filter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            registerReceiver(homeButtonReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(homeButtonReceiver, filter);
        }

        // Ensure this app is set as the default launcher
        ensureDefaultLauncher();

        // Initialize Handler and Runnable for periodic reload
        reloadHandler = new Handler();
        reloadRunnable = new Runnable() {
            @Override
            public void run() {
                reloadApp(); // Reload the app
                reloadHandler.postDelayed(this, RELOAD_INTERVAL); // Schedule next reload
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        startPeriodicReload(); // Start periodic reload when activity resumes
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPeriodicReload(); // Stop periodic reload when activity pauses
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (homeButtonReceiver != null) {
            unregisterReceiver(homeButtonReceiver);
            homeButtonReceiver = null;
        }
        stopPeriodicReload(); // Ensure periodic reload is stopped when activity is destroyed
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // Disable all touch interactions
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                // Back button simulation
                onBackPressed();
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                // Home button simulation
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return true;
            case KeyEvent.KEYCODE_BACK:
                // Reload the app
                reloadApp();
                return true;
            case KeyEvent.KEYCODE_APP_SWITCH: // Recent Apps button
                // Reload the app
                reloadApp();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public void onBackPressed() {
        reloadApp();
    }

    private void reloadApp() {
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        decorView.setSystemUiVisibility(uiOptions);
    }

    private void ensureDefaultLauncher() {
        // Check if this app is the default launcher, if not, prompt user
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.addCategory(Intent.CATEGORY_DEFAULT);

        ResolveInfo resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);

        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            // No default launcher found, prompt user to set this app as default launcher
            promptSetAsDefaultLauncher();
            return;
        }

        // Create ComponentName from ResolveInfo
        ComponentName defaultLauncher = new ComponentName(
                resolveInfo.activityInfo.packageName,
                resolveInfo.activityInfo.name
        );

        if (!defaultLauncher.getPackageName().equals(getPackageName())) {
            // Current app is not the default launcher, prompt user to set it as default
            promptSetAsDefaultLauncher();
        }
    }

    private void promptSetAsDefaultLauncher() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void startPeriodicReload() {
        reloadHandler.postDelayed(reloadRunnable, RELOAD_INTERVAL);
    }

    private void stopPeriodicReload() {
        if (reloadHandler != null && reloadRunnable != null) {
            reloadHandler.removeCallbacks(reloadRunnable);
        }
    }

    private class FetchDataTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.setConnectTimeout(15000);
                urlConnection.setReadTimeout(15000);

                InputStreamReader in = new InputStreamReader(urlConnection.getInputStream());
                StringBuilder response = new StringBuilder();
                int data;
                while ((data = in.read()) != -1) {
                    response.append((char) data);
                }
                in.close();
                return response.toString();
            } catch (Exception e) {
                Log.e("FetchDataTask", "Error fetching data", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            if (result != null) {
                try {
                    JSONObject jsonResponse = new JSONObject(result);
                    JSONArray feeds = jsonResponse.getJSONArray("feeds");
                    JSONObject latestFeed = feeds.getJSONObject(0);

                    String pH = latestFeed.getString("field1");
                    String turbidity = latestFeed.getString("field2");
                    String dissolvedOxygen = latestFeed.getString("field3");
                    String electricalConductivity = latestFeed.getString("field4");
                    String temperature = latestFeed.getString("field5");
                    String orp = latestFeed.getString("field6");
                    String tds = latestFeed.getString("field7");

                    tvPH.setText("pH: " + pH);
                    tvTurbidity.setText("Turbidity: " + turbidity + " NTU");
                    tvDO.setText("Dissolved Oxygen: " + dissolvedOxygen + " mg/L");
                    tvEC.setText("Electrical Conductivity: " + electricalConductivity + " μS/cm");
                    tvTemp.setText("Temperature: " + temperature + " °C");
                    tvORP.setText("Oxidation-Reduction Potential: " + orp + " mV");
                    tvTDS.setText("Total Dissolved Solids: " + tds + " ppm");

                } catch (Exception e) {
                    Log.e("MainActivity", "Error parsing JSON response", e);
                }
            }
        }
    }

    // HomeButtonReceiver as an inner class
    public class HomeButtonReceiver extends BroadcastReceiver {
        private static final String SYSTEM_DIALOG_REASON_KEY = "reason";
        private static final String SYSTEM_DIALOG_REASON_HOME_KEY = "homekey";
        private static final String SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps";

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())) {
                String reason = intent.getStringExtra(SYSTEM_DIALOG_REASON_KEY);
                if (SYSTEM_DIALOG_REASON_HOME_KEY.equals(reason) ||
                        SYSTEM_DIALOG_REASON_RECENT_APPS.equals(reason)) {
                    // Relaunch the MainActivity when Home or Recent Apps button is pressed
                    Intent i = new Intent(context, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    context.startActivity(i);
                }
            }
        }
    }

}
