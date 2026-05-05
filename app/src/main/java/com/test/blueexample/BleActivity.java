package com.test.blueexample;

import android.Manifest;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class BleActivity extends AppCompatActivity {

    private static final int REQ_BT_PERMS = 1001;

    private final GClient client = new GClient();
    private BluetoothCentralManager central;

    private TextView tvConnectedDevice;
    private TextView tvEpcCount;
    private TextView tvEpcList;

    private final Set<String> uniqueEpcs = new LinkedHashSet<>();
    private String connectedDeviceName = "Not connected";

    private final BluetoothCentralManagerCallback centralManagerCallback =
            new BluetoothCentralManagerCallback() {
                @Override
                public void onDiscoveredPeripheral(BluetoothPeripheral peripheral, ScanResult scanResult) {
                    try {
                        String deviceName = peripheral.getName();
                        String deviceAddress = peripheral.getAddress();

                        Log.e("RFID_BLE", "1. Found BLE device: " + deviceName + " / " + deviceAddress);

                        if (deviceName != null
                                && !deviceName.isEmpty()
                                && (deviceName.startsWith("MR")
                                || deviceName.startsWith("NA")
                                || deviceName.contains("RFID")
                                || deviceName.contains("UHF"))) {

                            Log.e("RFID_BLE", "2. Matched RFID BLE device");
                            runOnUiThread(() -> tvConnectedDevice.setText("Connecting: " + deviceName));

                            central.stopScan();

                            BleDevice bleDevice = new BleDevice(central, peripheral);
                            bleDevice.setServiceCallback(new BleServiceCallback() {
                                @Override
                                public void onServicesDiscovered(BluetoothPeripheral peripheral) {
                                    Log.e("RFID_BLE", "3. Services discovered");

                                    List<BluetoothGattService> services = peripheral.getServices();
                                    for (BluetoothGattService service : services) {
                                        String uuid = service.getUuid().toString();
                                        Log.e("RFID_BLE", "4. Service UUID: " + uuid);

                                        if ("0000fff0-0000-1000-8000-00805f9b34fb".equalsIgnoreCase(uuid)) {
                                            bleDevice.findCharacteristic(service);
                                            Log.e("RFID_BLE", "5. RFID service matched");
                                        }
                                    }

                                    bleDevice.setNotify(true);
                                    Log.e("RFID_BLE", "6. Notify enabled");
                                }
                            });

                            client.openBleDevice(bleDevice);
                            Log.e("RFID_BLE", "7. openBleDevice called");
                        }
                    } catch (Exception e) {
                        Log.e("RFID_BLE", "BLE scan error: " + e.getMessage(), e);
                    }
                }

                @Override
                public void onConnectedPeripheral(BluetoothPeripheral peripheral) {
                    connectedDeviceName = peripheral.getName() == null ? "Unknown Device" : peripheral.getName();
                    Log.e("RFID_BLE", "8. Connection successful: " + connectedDeviceName);

                    runOnUiThread(() -> tvConnectedDevice.setText("Connected Device: " + connectedDeviceName));
                }

                @Override
                public void onDisconnectedPeripheral(BluetoothPeripheral peripheral, HciStatus status) {
                    client.close();
                    Log.e("RFID_BLE", "9. Disconnected: " + peripheral.getName() + " / " + status);

                    connectedDeviceName = "Not connected";
                    runOnUiThread(() -> tvConnectedDevice.setText("Connected Device: Not connected"));
                }

                @Override
                public void onConnectionFailed(BluetoothPeripheral peripheral, HciStatus status) {
                    Log.e("RFID_BLE", "10. Connect failed: " + peripheral.getName() + " / " + status);

                    runOnUiThread(() -> tvConnectedDevice.setText("Connection failed"));
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.e("RFID_BLE", "APP STARTED");

        Button scan = findViewById(R.id.scan);
        Button stop = findViewById(R.id.stop);
        Button readCard = findViewById(R.id.readCard);
        Button stopCard = findViewById(R.id.stopCard);

        tvConnectedDevice = findViewById(R.id.tvConnectedDevice);
        tvEpcCount = findViewById(R.id.tvEpcCount);
        tvEpcList = findViewById(R.id.tvEpcList);

        subscriberHandler();
        updateEpcViews();

        central = new BluetoothCentralManager(
                this,
                centralManagerCallback,
                new Handler(Looper.getMainLooper())
        );

        requestBluetoothPermissions();

        if (scan != null) {
            scan.setOnClickListener(view -> {
                try {
                    Log.e("RFID_BLE", "11. Before scanForPeripherals()");
                    tvConnectedDevice.setText("Scanning BLE devices...");
                    central.scanForPeripherals();
                    Log.e("RFID_BLE", "12. After scanForPeripherals()");
                } catch (Throwable e) {
                    Log.e("RFID_BLE", "Scan failed: " + e.getMessage(), e);
                    tvConnectedDevice.setText("Scan failed");
                }
            });
        }

        if (stop != null) {
            stop.setOnClickListener(view -> {
                try {
                    central.stopScan();
                    Log.e("RFID_BLE", "13. Scan stopped");
                    tvConnectedDevice.setText("Scan stopped");
                } catch (Throwable e) {
                    Log.e("RFID_BLE", "Stop failed: " + e.getMessage(), e);
                }
            });
        }

        if (readCard != null) {
            readCard.setOnClickListener(v -> {
                try {
                    uniqueEpcs.clear();
                    updateEpcViews();

                    MsgBaseInventoryEpc msg = new MsgBaseInventoryEpc();
                    msg.setAntennaEnable(EnumG.AntennaNo_1);
                    msg.setInventoryMode(EnumG.InventoryMode_Inventory);
                    client.sendSynMsg(msg);

                    if (msg.getRtCode() == 0) {
                        Log.e("RFID_BLE", "14. Inventory success");
                    } else {
                        Log.e("RFID_BLE", "15. Inventory failed: " + msg.getRtMsg());
                    }
                } catch (Throwable e) {
                    Log.e("RFID_BLE", "Read failed: " + e.getMessage(), e);
                }
            });
        }

        if (stopCard != null) {
            stopCard.setOnClickListener(v -> {
                try {
                    MsgBaseStop msg = new MsgBaseStop();
                    client.sendSynMsg(msg);
                    Log.e("RFID_BLE", "16. Stop inventory: " + msg.getRtMsg());
                } catch (Throwable e) {
                    Log.e("RFID_BLE", "Stop inventory failed: " + e.getMessage(), e);
                }
            });
        }
    }

    private void updateEpcViews() {
        runOnUiThread(() -> {
            Log.e("RFID_BLE", "EPC Count: " + uniqueEpcs.size());

            tvEpcCount.setText("EPC Count: " + uniqueEpcs.size());

            if (uniqueEpcs.isEmpty()) {
                tvEpcList.setText("No EPC scanned yet");
            } else {
                StringBuilder builder = new StringBuilder();
                int index = 1;
                for (String epc : uniqueEpcs) {
                    builder.append(index).append(". ").append(epc).append("\n");
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
                    String rssi = String.valueOf(logBaseEpcInfo.getRssi());

                    Log.e("RFID_TAG", "EPC: " + epc + " RSSI: " + rssi);

                    if (epc != null && !epc.trim().isEmpty()) {
                        uniqueEpcs.add(epc.trim());
                        updateEpcViews();
                    }
                } else {
                    Log.e("RFID_BLE", "18. EPC read result code: " + logBaseEpcInfo.getResult());
                }
            }
        };

        client.onTagEpcOver = new HandlerTagEpcOver() {
            @Override
            public void log(String s, LogBaseEpcOver logBaseEpcOver) {
                Log.e("RFID_BLE", "19. Inventory over: " + logBaseEpcOver.getRtMsg());
            }
        };
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        client.close();
        if (central != null) {
            central.close();
        }
    }
}