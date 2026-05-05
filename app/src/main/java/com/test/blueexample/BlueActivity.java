package com.test.blueexample;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import com.gg.reader.api.dal.GClient;
import com.gg.reader.api.dal.HandlerTagEpcLog;
import com.gg.reader.api.dal.HandlerTagEpcOver;
import com.gg.reader.api.dal.HandlerTcpDisconnected;
import com.gg.reader.api.dal.communication.BluetoothClient;
import com.gg.reader.api.dal.communication.BluetoothHandler;
import com.gg.reader.api.protocol.gx.EnumG;
import com.gg.reader.api.protocol.gx.LogBaseEpcInfo;
import com.gg.reader.api.protocol.gx.LogBaseEpcOver;
import com.gg.reader.api.protocol.gx.MsgBaseInventoryEpc;
import com.gg.reader.api.protocol.gx.MsgBaseStop;
import com.gg.reader.api.utils.StringUtils;

// TODO: Need to test ble, please switch Activity in AndroidManifest.xml
// TODO: For more detailed RFID API, please refer to the Java-API directory in the development package or RFIDDemo.zip
public class BlueActivity extends AppCompatActivity {

    private static final int REQ_BT_PERMS = 1001;

    BluetoothClient bluetoothClient = new BluetoothClient();
    GClient client = new GClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.e("RFID_DEBUG", "APP STARTED");

        Button scan = findViewById(R.id.scan);

        if (scan == null) {
            Log.e("RFID_DEBUG", "SCAN BUTTON IS NULL");
        } else {
            Log.e("RFID_DEBUG", "SCAN BUTTON FOUND");


            scan.setOnClickListener(view -> {
                Log.e("RFID_DEBUG", "SCAN CLICK WORKING");

                if (!hasBluetoothPermissions()) {
                    Log.e("RFID_DEBUG", "2. Permissions missing");
                    requestBluetoothPermissions();
                    return;
                }

                try {
                    Log.e("RFID_DEBUG", "3. Before scanBluetooth()");
                    bluetoothClient.scanBluetooth();
                    Log.e("RFID_DEBUG", "4. After scanBluetooth()");
                } catch (Throwable e) {
                    Log.e("RFID_DEBUG", "5. Crash in scanBluetooth: " + e.getMessage(), e);                }
            });
        }




        Button stop = findViewById(R.id.stop);
        Button readCard = findViewById(R.id.readCard);
        Button stopCard = findViewById(R.id.stopCard);

        // Subscription tag callback
        subscriberHandler();

        // Request runtime permissions
        requestBluetoothPermissions();

        bluetoothClient.registerBluetoothScanReceiver(this);
        bluetoothClient.bluetoothHandler = new BluetoothHandler() {
            @Override
            public void dispense(BluetoothDevice bluetoothDevice) {
                if (!hasBluetoothPermissions()) {
                    Log.e("RFID_DEBUG", "6. Bluetooth permission not granted");
                    return;
                }

                try {
                    String deviceName = bluetoothDevice.getName();
                    String deviceAddress = bluetoothDevice.getAddress();

                    Log.e("RFID_DEBUG", "7. Found: " + deviceName + " / " + deviceAddress);

                    if (deviceName != null
                            && !deviceName.isEmpty()
                            && (deviceName.startsWith("MR")
                            || deviceName.startsWith("UHF")
                            || deviceName.contains("RFID"))) {

                        Log.e("RFID_DEBUG", "14. Matched target device");

                        bluetoothClient.stopScanner();

                        if (client.openBluetooth(deviceAddress, 2000, 0, bluetoothClient)) {
                            client.setSendHeartBeat(true);
                            Log.e("RFID_DEBUG", "10. openBluetooth success");
                        } else {
                            Log.e("RFID_DEBUG", "11. openBluetooth failed");
                        }
                    }
                } catch (SecurityException e) {
                    Log.e("RFID_DEBUG", "8. Bluetooth permission denied: " + e.getMessage());
                } catch (Exception e) {
                    Log.e("RFID_DEBUG", "9. dispense error: " + e.getMessage(), e);
                }
            }

            @Override
            public void startDiscover() {
                Log.e("RFID_DEBUG", "12. startDiscover");
            }

            @Override
            public void finishDiscover() {
                Log.e("RFID_DEBUG", "13. finishDiscover");            }
        };


        stop.setOnClickListener(view -> {
            try {
                bluetoothClient.stopScanner();
            } catch (SecurityException e) {
                Log.e("stopScanner", "Permission denied: " + e.getMessage());
            }
        });
        readCard.setOnClickListener(v -> {
            MsgBaseInventoryEpc msg = new MsgBaseInventoryEpc();
            msg.setAntennaEnable(EnumG.AntennaNo_1);
            msg.setInventoryMode(EnumG.InventoryMode_Inventory);
            client.sendSynMsg(msg);
            if (msg.getRtCode() == 0) {
                Log.e("MsgBaseInventoryEpc", "Inventory success");
            } else {
                Log.e("MsgBaseInventoryEpc", "Inventory failed: " + msg.getRtMsg());
            }
        });

        stopCard.setOnClickListener(v -> {
            MsgBaseStop msg = new MsgBaseStop();
            client.sendSynMsg(msg);
            Log.e("MsgBaseStop", msg.getRtMsg());
        });
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return ContextCompat.checkSelfPermission(this, "android.permission.BLUETOOTH_SCAN")
                    == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, "android.permission.BLUETOOTH_CONNECT")
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] permissions = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
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
                    Log.e("epc", logBaseEpcInfo.getEpc());
                }
            }
        };

        client.onTagEpcOver = new HandlerTagEpcOver() {
            @Override
            public void log(String s, LogBaseEpcOver logBaseEpcOver) {
                Log.e("HandlerTagEpcOver", logBaseEpcOver.getRtMsg());
            }
        };

        client.onDisconnected = new HandlerTcpDisconnected() {
            @Override
            public void log(String s) {
                client.close();
                Log.e("HandlerDisconnected", s);
            }
        };
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bluetoothClient.unRegisterBluetoothScanReceiver(this);
        client.close();
    }
}