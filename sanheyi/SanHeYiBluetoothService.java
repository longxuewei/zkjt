package com.zcareze.zkyandroidweb.sanheyi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.util.Log;

import com.zcareze.zkyandroidweb.utils.L;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for incoming
 * connections, a thread for connecting with a device, and a thread for
 * performing data transmissions when connected.
 */
public class SanHeYiBluetoothService {
    // Debugging
    private static final String TAG = "BluetoothService";
    private static final boolean D = true;
    private OnDataReceive receive;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothDevice device;
    // Member fields
    private BluetoothAdapter mAdapter;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    public static final String DEVICE_NAME = "BeneCheck";
    // 可被访问的蓝牙联机状态
    private static int connState;// 连接状态
    // 蓝牙的可访问地址
    public static String macAddress;// 蓝牙设备的地址
    // Constants that indicate the current connection state
    private static final int STATE_NONE = 0; // we're doing nothing
    private static final int STATE_CONNECTING = 2; // now initiating an outgoing
    // connection
    private static final int STATE_CONNECTED = 3; // now connected to a remote
    // device
    private int attemptCount = 0;

    /**
     * 初始化,设置状态 NONE
     *
     * @param context
     */
    public SanHeYiBluetoothService(OnDataReceive receive) {
        this.receive = receive;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        connState = 1;
    }

    public boolean checkBindDevice(Context context) {
        BluetoothAdapter adapter = getAdapter(context);
        if (adapter.isEnabled()) {
            Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
            if (bondedDevices.size() <= 0) {
                Log.d(TAG, "没有绑定的设备");
                return false;
            } else {
                for (BluetoothDevice device : bondedDevices) {
                    if (device.getName().startsWith(DEVICE_NAME)) {// 如果是"百捷"开头的.
                        this.device = device;// 只会有一个设备.
                        return true;
                    }
                }
            }
        } else {
            // 蓝牙没开启
            return false;
        }
        return device == null;
    }

    public BluetoothDevice getDevice() {
        return device;
    }

    private BluetoothAdapter getAdapter(Context context) {
        boolean isSupportBle = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
        if (isSupportBle) {
            BluetoothManager mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            return mBluetoothManager.getAdapter();
        } else {
            return null;
        }

    }

    /**
     * 开始去连接.
     */
    public synchronized void start(BluetoothDevice device) {
        if (device == null)
            return;
        // 取消连接
        L.d("三合一血糖仪尝试次数:" + attemptCount++);
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        // 断开连接
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        /**
         * 开始一个新的连接.
         */
        if (mConnectThread == null) {
            mConnectThread = new ConnectThread(device);
            mConnectThread.start();
        }
        // TODO 设置正在连接中.
        setState(STATE_CONNECTING);

    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) {
        if (D)
            Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);

        // 获取设备地址
        macAddress = device.getAddress() + "connect 1";

    }

    /**
     * 当与设备连接上了,进行数据管理.此方法将等待结果上传.
     *
     * @param socket
     * @param device
     */
    private synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket, device);
        mConnectedThread.start();
        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */

    public synchronized void stop() {
        if (D)
            Log.d(TAG, "stop");
        setState(STATE_NONE);
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
    }

    /**
     * 连接获取socket失败.进行重试,所以还是连接中.
     */
    private void connectionFailed() {
        setState(STATE_CONNECTING);
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        Log.d(TAG, "连接失败了");
    }

    /**
     * 连接线程, 本方法是去尝试和一个设备进行连接.返回一个socket. 这是一个阻塞的方法.只有返回一个已连接的状态,或者是抛出异常.
     * 抛出异常之后,进行重新连接.
     *
     * @author sa
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            // 获取socket
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "create() failed", e);
            }
            mmSocket = tmp;
        }

        @Override
        public void run() {
            try {
                // 阻塞方法.等待连接,或者异常.
                mmSocket.connect();
            } catch (IOException e) {
                connectionFailed();//连接失败.设置状态为:连接中,因为要重新连接.
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                // 连接失败了,进行重试!
                SanHeYiBluetoothService.this.start(mmDevice);
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (SanHeYiBluetoothService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    /**
     * 此线程 用于 管理 ,数据交互.对上传数据进行解析.发送出去.
     *
     * @author sa
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private BluetoothDevice device;

        public ConnectedThread(BluetoothSocket socket, BluetoothDevice device) {
            this.device = device;
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        @Override
        public void run() {
            Log.e(TAG, "BEGIN mConnectedThread");
            int bytes;
            List<Byte> list = new ArrayList<Byte>();
            while (true) {
                try {
                    byte[] buffer = new byte[1024];
                    bytes = mmInStream.read(buffer);

                    if (bytes > 0) {
                        byte[] bytedata = new byte[bytes];
                        for (int i = 0; i < bytes; i++) {
                            bytedata[i] = buffer[i];
                            list.add(buffer[i]);
                        }


                        //将数据传出去.
                        if (receive != null) {
                            receive.onDataReceive(bytedata, list);
                        }

                        if (list.size() >= 22) {
                            list.clear();
                        }


                    } else {
                        //没数据,进行重新连接.
                        connectionLost();
                        if (mState != STATE_NONE) {
                            SanHeYiBluetoothService.this.start(device);
                        }
                        break;
                    }
                } catch (IOException e) {
                    //重新连接
                    connectionLost();
                    if (mState != STATE_NONE) {
                        SanHeYiBluetoothService.this.start(device);
                    }
                    break;
                }
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    public String printHexString(byte[] b) {
        String result = "";
        for (int i = 0; i < b.length; i++) {
            String hex = Integer.toHexString(b[i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            result = result + hex.toUpperCase();
        }
        return result;
    }

    /**
     * 设置当前的连接的状态.
     *
     * @param state
     */
    private synchronized void setState(int state) {
        if (D)
            Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;
        // 修改状态
        connState = state;
    }

    /**
     * 获取当前连接状态.
     *
     * @return
     */
    public synchronized int getState() {
        return mState;
    }

}
