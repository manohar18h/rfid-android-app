package com.test.blueexample;

import android.Manifest;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.gg.reader.api.dal.GClient;
import com.gg.reader.api.dal.HandlerTagEpcLog;
import com.gg.reader.api.dal.HandlerTagEpcOver;
import com.gg.reader.api.protocol.gx.EnumG;
import com.gg.reader.api.protocol.gx.LogBaseEpcInfo;
import com.gg.reader.api.protocol.gx.LogBaseEpcOver;
import com.gg.reader.api.protocol.gx.MsgBaseInventoryEpc;
import com.gg.reader.api.protocol.gx.MsgBaseStop;
import com.peripheral.ble.BleDevice;
import com.peripheral.ble.BleServiceCallback;
import com.peripheral.ble.BluetoothCentralManager;
import com.peripheral.ble.BluetoothCentralManagerCallback;
import com.peripheral.ble.BluetoothPeripheral;
import com.peripheral.ble.HciStatus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class BleActivity extends AppCompatActivity {

    private static final int REQ_BT_PERMS = 1001;
    private static final String API_URL = "https://api.hambirejewellery.com/auth/getALlStockBoxPublic";

    private final GClient client = new GClient();
    private BluetoothCentralManager central;

    private LinearLayout homeScreen;
    private LinearLayout scanScreen;
    private LinearLayout stockBoxResultContainer;

    private TextView tvConnectedDevice;
    private TextView tvSelectedStockBox;
    private TextView tvScanStockBoxName;
    private TextView tvDbTotalCount;
    private TextView tvScannedEpcCount;
    private TextView tvEpcList;

    private TextView tvMatchedCount;
    private TextView tvMissingCount;
    private TextView tvExtraCount;

    private EditText etSearchStockBox;

    private Button btnConnectReader;
    private Button btnDisconnectReader;
    private Button btnStartScanPage;
    private Button btnStartReadTags;
    private Button btnStopReadTags;
    private Button btnClearEpcList;
    private Button btnBackHome;

    private final Set<String> uniqueEpcs = new LinkedHashSet<>();
    private final List<StockBox> allStockBoxes = new ArrayList<>();

    private StockBox selectedStockBox = null;
    private String connectedDeviceName = "Not connected";

    private final BluetoothCentralManagerCallback centralManagerCallback =
            new BluetoothCentralManagerCallback() {
                @Override
                public void onDiscoveredPeripheral(BluetoothPeripheral peripheral, ScanResult scanResult) {
                    try {
                        String deviceName = peripheral.getName();
                        String deviceAddress = peripheral.getAddress();

                        Log.e("RFID_BLE", "Found BLE: " + deviceName + " / " + deviceAddress);

                        if (deviceName != null
                                && !deviceName.isEmpty()
                                && (deviceName.startsWith("MR")
                                || deviceName.startsWith("NA")
                                || deviceName.contains("RFID")
                                || deviceName.contains("UHF"))) {

                            runOnUiThread(() -> tvConnectedDevice.setText("Connecting: " + deviceName));

                            central.stopScan();

                            BleDevice bleDevice = new BleDevice(central, peripheral);
                            bleDevice.setServiceCallback(new BleServiceCallback() {
                                @Override
                                public void onServicesDiscovered(BluetoothPeripheral peripheral) {
                                    List<BluetoothGattService> services = peripheral.getServices();

                                    for (BluetoothGattService service : services) {
                                        String uuid = service.getUuid().toString();

                                        if ("0000fff0-0000-1000-8000-00805f9b34fb".equalsIgnoreCase(uuid)) {
                                            bleDevice.findCharacteristic(service);
                                            Log.e("RFID_BLE", "RFID service matched");
                                        }
                                    }

                                    bleDevice.setNotify(true);
                                    Log.e("RFID_BLE", "Notify enabled");
                                }
                            });

                            client.openBleDevice(bleDevice);
                            Log.e("RFID_BLE", "openBleDevice called");
                        }
                    } catch (Exception e) {
                        Log.e("RFID_BLE", "BLE error: " + e.getMessage(), e);
                    }
                }

                @Override
                public void onConnectedPeripheral(BluetoothPeripheral peripheral) {
                    connectedDeviceName = peripheral.getName() == null ? "Unknown Device" : peripheral.getName();
                    Log.e("RFID_BLE", "Connected: " + connectedDeviceName);

                    runOnUiThread(() -> tvConnectedDevice.setText("Connected Device: " + connectedDeviceName));
                }

                @Override
                public void onDisconnectedPeripheral(BluetoothPeripheral peripheral, HciStatus status) {
                    client.close();
                    connectedDeviceName = "Not connected";

                    runOnUiThread(() -> tvConnectedDevice.setText("Connected Device: Not connected"));
                }

                @Override
                public void onConnectionFailed(BluetoothPeripheral peripheral, HciStatus status) {
                    runOnUiThread(() -> tvConnectedDevice.setText("Connection failed"));
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        subscriberHandler();
        initCentral();
        requestBluetoothPermissions();

        fetchStockBoxesFromApi();
        setupClicks();
        setupSearch();
        updateEpcViews();
    }

    private void bindViews() {
        homeScreen = findViewById(R.id.homeScreen);
        scanScreen = findViewById(R.id.scanScreen);
        stockBoxResultContainer = findViewById(R.id.stockBoxResultContainer);

        tvConnectedDevice = findViewById(R.id.tvConnectedDevice);
        tvSelectedStockBox = findViewById(R.id.tvSelectedStockBox);
        tvScanStockBoxName = findViewById(R.id.tvScanStockBoxName);
        tvDbTotalCount = findViewById(R.id.tvDbTotalCount);
        tvScannedEpcCount = findViewById(R.id.tvScannedEpcCount);
        tvEpcList = findViewById(R.id.tvEpcList);

        etSearchStockBox = findViewById(R.id.etSearchStockBox);

        btnConnectReader = findViewById(R.id.btnConnectReader);
        btnDisconnectReader = findViewById(R.id.btnDisconnectReader);
        btnStartScanPage = findViewById(R.id.btnStartScanPage);
        btnStartReadTags = findViewById(R.id.btnStartReadTags);
        btnStopReadTags = findViewById(R.id.btnStopReadTags);
        btnClearEpcList = findViewById(R.id.btnClearEpcList);
        btnBackHome = findViewById(R.id.btnBackHome);

        tvMatchedCount = findViewById(R.id.tvMatchedCount);
        tvMissingCount = findViewById(R.id.tvMissingCount);
        tvExtraCount = findViewById(R.id.tvExtraCount);
    }

    private void initCentral() {
        central = new BluetoothCentralManager(
                this,
                centralManagerCallback,
                new Handler(Looper.getMainLooper())
        );
    }

    private void setupClicks() {
        btnConnectReader.setOnClickListener(view -> {
            try {
                tvConnectedDevice.setText("Scanning reader...");
                central.scanForPeripherals();
            } catch (Throwable e) {
                tvConnectedDevice.setText("Reader scan failed");
                Log.e("RFID_BLE", "Scan failed: " + e.getMessage(), e);
            }
        });

        btnDisconnectReader.setOnClickListener(view -> {
            try {
                stopInventory();

                if (central != null) {
                    central.stopScan();
                    central.close();
                }

                client.close();
                initCentral();

                connectedDeviceName = "Not connected";
                tvConnectedDevice.setText("Connected Device: Not connected");
            } catch (Throwable e) {
                Log.e("RFID_BLE", "Disconnect failed: " + e.getMessage(), e);
            }
        });

        btnStartScanPage.setOnClickListener(view -> {
            if (selectedStockBox == null) return;

            homeScreen.setVisibility(View.GONE);
            scanScreen.setVisibility(View.VISIBLE);

            uniqueEpcs.clear();

            tvScanStockBoxName.setText("Stock Box: " + selectedStockBox.stockBoxName);
            tvDbTotalCount.setText("DB Total Count: " + selectedStockBox.totalStockBoxCount);

            updateEpcViews();
        });

        btnStartReadTags.setOnClickListener(view -> {
            uniqueEpcs.clear();
            updateEpcViews();
            startInventory();
        });

        btnStopReadTags.setOnClickListener(view -> stopInventory());

        btnClearEpcList.setOnClickListener(view -> {
            uniqueEpcs.clear();
            updateEpcViews();
        });

        btnBackHome.setOnClickListener(view -> {
            stopInventory();
            scanScreen.setVisibility(View.GONE);
            homeScreen.setVisibility(View.VISIBLE);
        });
    }

    private void setupSearch() {
        etSearchStockBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderStockBoxList(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void fetchStockBoxesFromApi() {
        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(API_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                int responseCode = connection.getResponseCode();

                if (responseCode != 200) {
                    runOnUiThread(() -> {
                        TextView tv = new TextView(this);
                        tv.setText("Failed to load stock boxes");
                        stockBoxResultContainer.addView(tv);
                    });
                    return;
                }

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream())
                );

                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                reader.close();

                JSONArray array = new JSONArray(response.toString());
                allStockBoxes.clear();

                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);

                    StockBox box = new StockBox();
                    box.stockBoxId = obj.optLong("stockBoxId");
                    box.stockBoxName = obj.optString("stockBoxName", "");
                    box.totalStockBoxCount = obj.optDouble("totalStockBoxCount", 0.0);
                    box.totalStockBoxWeight = obj.optDouble("totalStockBoxWeight", 0.0);

                    JSONArray dataArray = obj.optJSONArray("stockBoxData");
                    if (dataArray != null) {
                        for (int j = 0; j < dataArray.length(); j++) {
                            JSONObject item = dataArray.getJSONObject(j);

                            String epc = item.optString("epcNumber", "");
                            if (epc != null && !epc.equals("null") && !epc.trim().isEmpty()) {
                                box.dbEpcList.add(epc.trim());
                            }
                        }
                    }

                    allStockBoxes.add(box);
                }

                runOnUiThread(() -> renderStockBoxList(""));

            } catch (Exception e) {
                Log.e("API", "API error: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    TextView tv = new TextView(this);
                    tv.setText("API Error: " + e.getMessage());
                    stockBoxResultContainer.addView(tv);
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void renderStockBoxList(String query) {
        stockBoxResultContainer.removeAllViews();

        String q = query == null ? "" : query.trim().toLowerCase();

        int shown = 0;

        for (StockBox box : allStockBoxes) {
            if (!q.isEmpty() && !box.stockBoxName.toLowerCase().contains(q)) {
                continue;
            }

            TextView tv = new TextView(this);
            tv.setText(
                    box.stockBoxName
                            + "\nCount: " + box.totalStockBoxCount
                            + " | Weight: " + box.totalStockBoxWeight
            );
            tv.setTextSize(16);
            tv.setPadding(16, 16, 16, 16);
            tv.setBackgroundColor(0xFFEFEFEF);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 10);
            tv.setLayoutParams(params);

            tv.setOnClickListener(view -> {
                selectedStockBox = box;
                etSearchStockBox.setText(box.stockBoxName);
                etSearchStockBox.setSelection(etSearchStockBox.getText().length());

                tvSelectedStockBox.setText("Selected Stock Box: " + box.stockBoxName);
                btnStartScanPage.setEnabled(true);

                stockBoxResultContainer.removeAllViews();
            });

            stockBoxResultContainer.addView(tv);
            shown++;

            if (shown >= 30) break;
        }

        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText("No stock box found");
            empty.setTextSize(16);
            empty.setPadding(12, 12, 12, 12);
            stockBoxResultContainer.addView(empty);
        }
    }

    private void startInventory() {
        try {
            MsgBaseInventoryEpc msg = new MsgBaseInventoryEpc();
            msg.setAntennaEnable(EnumG.AntennaNo_1);
            msg.setInventoryMode(EnumG.InventoryMode_Inventory);
            client.sendSynMsg(msg);

            if (msg.getRtCode() == 0) {
                Log.e("RFID_BLE", "Inventory success");
            } else {
                Log.e("RFID_BLE", "Inventory failed: " + msg.getRtMsg());
            }
        } catch (Throwable e) {
            Log.e("RFID_BLE", "Start inventory failed: " + e.getMessage(), e);
        }
    }

    private void stopInventory() {
        try {
            MsgBaseStop msg = new MsgBaseStop();
            client.sendSynMsg(msg);
            Log.e("RFID_BLE", "Stop inventory: " + msg.getRtMsg());
        } catch (Throwable e) {
            Log.e("RFID_BLE", "Stop inventory failed: " + e.getMessage(), e);
        }
    }

    private void updateEpcViews() {
        runOnUiThread(() -> {
            int scannedCount = uniqueEpcs.size();

            int matchedCount = 0;
            int missingCount = 0;
            int extraCount = 0;

            if (selectedStockBox != null) {
                Set<String> dbSet = new LinkedHashSet<>();

                for (String epc : selectedStockBox.dbEpcList) {
                    if (epc != null && !epc.trim().isEmpty()) {
                        dbSet.add(epc.trim());
                    }
                }

                for (String scannedEpc : uniqueEpcs) {
                    if (dbSet.contains(scannedEpc)) {
                        matchedCount++;
                    } else {
                        extraCount++;
                    }
                }

                for (String dbEpc : dbSet) {
                    if (!uniqueEpcs.contains(dbEpc)) {
                        missingCount++;
                    }
                }
            }

            tvScannedEpcCount.setText("Scanned EPC Count: " + scannedCount);
            tvMatchedCount.setText("✅ Matched: " + matchedCount);
            tvMissingCount.setText("❌ Missing: " + missingCount);
            tvExtraCount.setText("❌ Extra: " + extraCount);

            if (uniqueEpcs.isEmpty()) {
                tvEpcList.setText("No EPC scanned yet");
            } else {
                StringBuilder builder = new StringBuilder();
                int index = 1;

                for (String epc : uniqueEpcs) {
                    String status = "";

                    if (selectedStockBox != null && selectedStockBox.dbEpcList.contains(epc)) {
                        status = " ✅";
                    } else if (selectedStockBox != null) {
                        status = " ❌ EXTRA";
                    }

                    builder.append(index).append(". ").append(epc).append(status).append("\n");
                    index++;
                }

                tvEpcList.setText(builder.toString().trim());
            }
        });
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] permissions = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };

            boolean allGranted = true;
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                ActivityCompat.requestPermissions(this, permissions, REQ_BT_PERMS);
            }
        } else {
            String[] permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN
            };

            boolean allGranted = true;
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                ActivityCompat.requestPermissions(this, permissions, REQ_BT_PERMS);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void subscriberHandler() {
        client.onTagEpcLog = new HandlerTagEpcLog() {
            @Override
            public void log(String s, LogBaseEpcInfo logBaseEpcInfo) {
                if (logBaseEpcInfo.getResult() == 0) {
                    String epc = logBaseEpcInfo.getEpc();

                    if (epc != null && !epc.trim().isEmpty()) {
                        uniqueEpcs.add(epc.trim());
                        updateEpcViews();
                    }
                }
            }
        };

        client.onTagEpcOver = new HandlerTagEpcOver() {
            @Override
            public void log(String s, LogBaseEpcOver logBaseEpcOver) {
                Log.e("RFID_BLE", "Inventory over: " + logBaseEpcOver.getRtMsg());
            }
        };
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            stopInventory();
        } catch (Exception ignored) {
        }

        client.close();

        if (central != null) {
            central.close();
        }
    }

    private static class StockBox {
        long stockBoxId;
        String stockBoxName;
        double totalStockBoxCount;
        double totalStockBoxWeight;
        List<String> dbEpcList = new ArrayList<>();
    }
}