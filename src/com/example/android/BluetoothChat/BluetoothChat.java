/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.BluetoothChat;


import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This is the main Activity that displays the current chat session.
 */
public class BluetoothChat extends Activity implements SensorListener{
    // Debugging
    private static final String TAG = "BluetoothChat";
    private static final boolean D = true;

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    private static final int REQUEST_OPEN_FILE = 4;

    // Layout Views
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;
   
    private Button mHexModeButton;
    private Button mCharModeButton;
    private Button mBeginButton;    
    private Button mStopButton;
    private Button mOpenButton;
    private Button mCleanButton;

    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Array adapter for the conversation thread
    private ArrayAdapter<String> mConversationArrayAdapter;
    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothService mChatService = null;
    
    //data for send ToyData;
    private int mSpeed = 10;   // 10 degree/s
    private final int mChanNum = 3;
    private final int mSendDuration = 1000; // 10 times per second
    private long lastUpdate;
    private int mStop = 1;
    
    
    private SensorManager mSensorManager;
    private Sensor mSensor;
    
    private int mShowMode = mHexMode; 
    
    private static final int mCharMode = 0;
    private static final int mHexMode = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.e(TAG, "+++ ON CREATE +++");

        // Set up the window layout
        setContentView(R.layout.main);

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        
        if (mSensorManager == null){
            Toast.makeText(this, "SensorManger is not available", Toast.LENGTH_LONG).show();
            finish();
            return;        
        }
        
        mSensor = (Sensor) mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        
        if (mSensor == null) {
            Toast.makeText(this, "Accelerometer Sensoris not available", Toast.LENGTH_LONG).show();
            finish();
            return;             
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // Otherwise, setup the chat session
        } else {
            if (mChatService == null) setupChat();
        }
        
        mSensorManager.registerListener(this, 
                                        SensorManager.SENSOR_ACCELEROMETER,
                                        SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "+ ON RESUME +");

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothService.STATE_NONE) {
              // Start the Bluetooth chat services
              mChatService.start();
            }
        }
        mSensorManager.registerListener(this, 
                                        SensorManager.SENSOR_ACCELEROMETER,
                                        SensorManager.SENSOR_DELAY_GAME);
    }

    private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        mConversationView = (ListView) findViewById(R.id.in);
        mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
        mOutEditText = (EditText) findViewById(R.id.edit_text_out);
        mOutEditText.setOnEditorActionListener(mWriteListener);

        // Initialize the send button with a listener that for click events
        mSendButton = (Button) findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                TextView view = (TextView) findViewById(R.id.edit_text_out);
                String message = view.getText().toString();
                
                String [] msgs = message.split(";");
                for (int i=0; i < msgs.length; i++) {
                   // if (isHex(msgs[i])) {
                        sendBytes(stringToHex(msgs[i]));
                   // } else {
                    //    sendMessage(msgs[i]);
                    //}
                        
                }
                
            }
        });
        
        mHexModeButton = (Button) findViewById(R.id.button_hex_mode);
        mHexModeButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mShowMode = mHexMode;
            }
        });
        
        mCharModeButton = (Button) findViewById(R.id.button_char_mode); 
        mCharModeButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mShowMode = mCharMode;
            }
        });
        
        mCleanButton = (Button) findViewById(R.id.button_clean); 
        mCleanButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mConversationArrayAdapter.clear();
            }
        });
        
        mStopButton = (Button) findViewById(R.id.button_stop);  
        mStopButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mStop = 1;
            }
        });
        
        mBeginButton = (Button) findViewById(R.id.button_begin);  
        mBeginButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mStop = 0;
            }
        });
        
        mOpenButton = (Button) findViewById(R.id.button_open);
        
        mOpenButton.setOnClickListener(new OnClickListener () {
            public void onClick(View v) {
                Intent intent = new Intent(BluetoothChat.this, MyFileManager.class);
                startActivityForResult(intent, REQUEST_OPEN_FILE);
            }
        });        
        
       // mOpenButton = (Button) fineViewById(R.id.button_open);
        

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothService(this, mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
        
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if (mChatService != null) mChatService.stop();
        if(D) Log.e(TAG, "--- ON DESTROY ---");
        
        mSensorManager.unregisterListener(this);
    }

    private void ensureDiscoverable() {
        if(D) Log.d(TAG, "ensure discoverable");
        if (mBluetoothAdapter.getScanMode() !=
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }

    // The action listener for the EditText widget, to listen for the return key
    private TextView.OnEditorActionListener mWriteListener =
        new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // If the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                sendMessage(message);
            }
            if(D) Log.i(TAG, "END onEditorAction");
            return true;
        }
    };

    private final void setStatus(int resId) {
//        final ActionBar actionBar = getActionBar();
//        actionBar.setSubtitle(resId);
    }

    private final void setStatus(CharSequence subTitle) {
//        final ActionBar actionBar = getActionBar();
//        actionBar.setSubtitle(subTitle);
    }

    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case BluetoothService.STATE_CONNECTED:
                    setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                    mConversationArrayAdapter.clear();
                    break;
                case BluetoothService.STATE_CONNECTING:
                    setStatus(R.string.title_connecting);
                    break;
                case BluetoothService.STATE_LISTEN:
                case BluetoothService.STATE_NONE:
                    setStatus(R.string.title_not_connected);
                    break;
                }
                break;
            case MESSAGE_WRITE:
                byte[] writeBuf = (byte[]) msg.obj;
                // construct a string from the buffer
                String writeMessage;
                if (mShowMode == mHexMode) {
                    writeMessage = toHexString(writeBuf);               
                }
                else {
                    writeMessage = new String(writeBuf);
                }

                mConversationArrayAdapter.add("Me:  " + writeMessage);
                break;
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                mConversationArrayAdapter.add(mConnectedDeviceName+":  " + readMessage);
                break;
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE_SECURE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                connectDevice(data, true);
            }
            break;
        case REQUEST_CONNECT_DEVICE_INSECURE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                connectDevice(data, false);
            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a chat session
                setupChat();
            } else {
                // User did not enable Bluetooth or an error occurred
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
        case REQUEST_OPEN_FILE:
             // When the request to open file
            if (resultCode == Activity.RESULT_OK) {
                Bundle bundle = null;
                if (data != null && (bundle=data.getExtras())!=null) {
                    sendFile(bundle.getString("file"));
                    //mOutEditText.setText(bundle.getString("file"));
                }
            }
        }
    }

    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device, secure);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent serverIntent = null;
        switch (item.getItemId()) {
        case R.id.secure_connect_scan:
            // Launch the DeviceListActivity to see devices and do scan
            serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
            return true;
        case R.id.insecure_connect_scan:
            // Launch the DeviceListActivity to see devices and do scan
            serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
            return true;
        case R.id.discoverable:
            // Ensure this device is discoverable by others
            ensureDiscoverable();
            return true;
        }
        return false;
    }
    
    public void onSensorChanged(int arg0, float[] values) {
        if (mChatService == null) {
            return;
        }
        
        if (mChatService.getState() != BluetoothService.STATE_CONNECTED) {
            return;
        }
        
        if (mStop == 1) {
            return;
        }
        //throw new UnsupportedOperationException("Not supported yet.");
        long curTime = System.currentTimeMillis();
        
        if (curTime - lastUpdate < mSendDuration)
            return;
        lastUpdate = curTime;
        ToyData data = new ToyData();
        double diagLen = Math.sqrt(values[0] * values[0] + 
                                  values[1] * values[1] +
                                  values[2] * values[2]);
        double xangle,yangle,zangle;
        double yConvertAngle, zConvertAngle;
       
        xangle = Math.acos((values[0])/diagLen); // xangle is used to determine which
                                                 // direction y axis turns,
                                                 // left(<0) or right(>0)
        yangle = Math.acos(Math.abs(values[1])/diagLen); 
        yConvertAngle = Math.PI/2 + (xangle<0?-1:1)*yangle; 
        
        zangle = Math.acos(Math.abs(values[2])/diagLen);
        zConvertAngle = zangle;
        
        data.setAngle(convertAngle(yConvertAngle));
        data.setSpeed(mSpeed);
        data.setChannel(0);
        sendBytes(data.getData());
        
        data.setAngle(convertAngle(zConvertAngle));
        data.setSpeed(mSpeed);
        data.setChannel(1);
        sendBytes(data.getData());
        
        
    }

    public void onAccuracyChanged(int arg0, int arg1) {
        //throw new UnsupportedOperationException("Not supported yet.");
    }
    
    private void sendBytes(byte[] send) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (send.toString().length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            mChatService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
       
    }  
    
    
    private String toHexString(byte [] bytes) {
        String result = new String();
        for (int i=0; i < bytes.length; i++) {
            result = result.concat(Integer.toHexString((bytes[i]>>4) & 0x0F));
            result = result.concat(Integer.toHexString(bytes[i] & 0x0F));            
        }
        
        return result;
    }
    
    private void sendFile(String file) {
        FileReader reader;
        try {
            reader = new FileReader(file);

            BufferedReader br = new BufferedReader(reader);
            String s = null;
            try {
                while ((s = br.readLine()) != null) {
                    String [] msgs = s.split(";");
                    for (int i=0; i < msgs.length; i++) {
                        sendBytes(stringToHex(msgs[i]));    
                    }
                }
                br.close();
                reader.close();
            } catch (IOException ex) {
                Logger.getLogger(BluetoothChat.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(BluetoothChat.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
        
    public static byte [] stringToHex(String s){
        if ("0x".equals(s.substring(0, 2)))
        {
            s = s.substring(2);
        }
        else {
            return s.getBytes();
        }
        
        byte[] baKeyword = new byte [s.length()/2];
        for(int i=0; i < baKeyword.length; i++){
            try {
                baKeyword[i] = (byte)(0xff & Integer.parseInt(s.substring(i*2, i*2+2),16));
                
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        return baKeyword;
    }
// translate the angle from radian to degree
// and tranform the angle rangle to Pi/4 -- 3Pi/4
    public int convertAngle(double angle) {
        return (int) (( Math.PI/4 + (angle/2))*180/Math.PI);
    }

}
