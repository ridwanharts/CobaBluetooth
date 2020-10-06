package com.example.cobabluetooth;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.ArrayMap;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.pixplicity.easyprefs.library.Prefs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    BluetoothAdapter bluetoothAdapter;
    BluetoothSocket bluetoothSocket;
    BluetoothDevice bluetoothDevice;

    OutputStream outputStream;
    InputStream inputStream;
    Thread thread;

    byte[] readBuffer;
    int readBufferPosition;
    volatile boolean stopWorker;

    TextView tvPrinterName, tvBlueooth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvBlueooth = findViewById(R.id.tvBluetooth);
        tvPrinterName = findViewById(R.id.tvPrinterName);
        Button btnConnect = findViewById(R.id.btnConnect);
        Button btnDisconnect = findViewById(R.id.btnDisconnect);
        Button btnPrint = findViewById(R.id.btnPrint);

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try{
                    findBluetoothDevice();
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        });

        btnPrint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    printData();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

    }

    public void findBluetoothDevice(){
        try{

            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null){
                tvBlueooth.setText("Bluetooth not found");
            }

            Set<BluetoothDevice> pairedDevice = bluetoothAdapter.getBondedDevices();
            ArrayMap<String, BluetoothDevice> bluetoothDevices = new ArrayMap<>();

            for(BluetoothDevice bd : pairedDevice){
                bluetoothDevices.put(bd.getName(), bd);
            }

            if (!bluetoothAdapter.isEnabled()){
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivity(intent);
            }else{
                new MaterialDialog.Builder(this)
                        .title("Pilih bluetooth printer")
                        .items(bluetoothDevices.keySet())
                        .itemsCallbackSingleChoice(-1, new MaterialDialog.ListCallbackSingleChoice() {
                            @Override
                            public boolean onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                                if (which != -1) {
                                    bluetoothDevice = bluetoothDevices.valueAt(which);
                                    tvBlueooth.setText("Bluetooth Attached : " + bluetoothDevice.getName());
                                    tvPrinterName.setText("Address : " + bluetoothDevice.getAddress());
                                    try {
                                        openBluetoothPrinter();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                } else {
                                /*bluetooth.onStop();
                                bluetooth.onStart();
                                scanDevices();*/
                                }
                                return true;
                            }
                        })
                        .positiveText("OK")
                        .negativeText("Batal")
                        .onNegative(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(MaterialDialog dialog, DialogAction which) {

                            }
                        })
                        .show();
            }

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void openBluetoothPrinter() throws IOException {
        try{

            //Standard uuid from string //
            UUID uuidSting = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
            bluetoothSocket=bluetoothDevice.createRfcommSocketToServiceRecord(uuidSting);
            bluetoothSocket.connect();
            outputStream=bluetoothSocket.getOutputStream();
            inputStream=bluetoothSocket.getInputStream();

            beginListenData();

        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

    public void beginListenData(){
        try {
            final Handler handler = new Handler();
            final byte delimiter = 10;
            stopWorker = false;
            readBufferPosition = 0;
            readBuffer = new byte[1024];

            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while(!Thread.currentThread().isInterrupted() && !stopWorker){
                        try {
                            int byteAvailable = inputStream.available();
                            if (byteAvailable > 0){
                                byte[] packetByte = new byte[byteAvailable];
                                inputStream.read(packetByte);

                                for (int i=0;i<byteAvailable;i++){
                                    byte b = packetByte[i];
                                    if (b == delimiter){
                                        byte[] encodedByte = new byte[readBufferPosition];
                                        System.arraycopy(
                                                readBuffer, 0,
                                                encodedByte, 0,
                                                encodedByte.length
                                        );

                                        final String data = new String(encodedByte, "US-ASCII");
                                        //tvPrinterName.setText(data);
                                        readBufferPosition = 0;
                                        handler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                //tvPrinterName.setText(data);
                                            }
                                        });
                                    }else{
                                        readBuffer[readBufferPosition++] = b;
                                    }
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            stopWorker = true;
                        }
                    }

                }
            });
            //start thread
            thread.start();

        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void printData() throws  IOException{
        try{
            StringBuilder sb = new StringBuilder();
            String stringBluetooth = tvBlueooth.getText().toString();
            String stringPrinterName = tvPrinterName.getText().toString();
            int lengthPrinter = 42;
            for(int i=0;i<lengthPrinter;i++){
                sb.append("-");
            }
            sb.append("\n");
            sb.append(stringBluetooth);
            sb.append("\n");
            sb.append(stringPrinterName);
            sb.append("\n");
            for(int i=0;i<lengthPrinter;i++){
                sb.append("-");
            }
            sb.append("\n\n");
            outputStream.write(sb.toString().getBytes());
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

    public void disconnectBT() throws IOException{
        try {
            stopWorker=true;
            outputStream.close();
            inputStream.close();
            bluetoothSocket.close();
            tvPrinterName.setText("Printer Disconnected.");
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }
}