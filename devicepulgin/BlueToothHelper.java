package com.zcareze.zkyandroidweb.devicepulgin;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.tencent.bugly.crashreport.CrashReport;
import com.zcareze.zkyandroidweb.constant.BLeStatus;
import com.zcareze.zkyandroidweb.sanheyi.OnDataReceive;
import com.zcareze.zkyandroidweb.sanheyi.SanHeYiBluetoothService;
import com.zcareze.zkyandroidweb.utils.L;
import com.zcareze.zkyandroidweb.utils.T;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Set;

/**
 * 源代码: Lxw
 * 伊妹儿: China2021@126.com
 * 时间轴: 2016 年 11 月 28 日 18 : 00
 */


@SuppressWarnings("ALL")
public class BlueToothHelper {
    private static final long SCAN_TIME_OUT = 10000;
    private static BlueToothHelper instance;
    private static Context context;
    private static BluetoothAdapter adapter;
    public static final String CONNECT_TYPE_MONITOR = "com.zcareze.zkyandroidweb.devicepulgin.BleToothHelper.monitor";
    public static final String CONNECT_TYPE_BIND = "com.zcareze.zkyandroidweb.devicepulgin.BleToothHelper.bind";
    public static boolean isBind = false;//当前是否处于绑定状态.
    public static boolean isMainPage = true;
    private static SanHeYiBluetoothService sanHeYiBluetoothService;

    public static void init(Context context) {
        adapter = getAdapter(context);
    }


    /**
     * 停止扫描
     *
     * @param callback
     */
    public static void stopScan(BluetoothAdapter.LeScanCallback callback) {
        if (adapter != null) {
            adapter.stopLeScan(callback);
            L.e("------蓝牙扫描停止!");
        }
        if (sanHeYiBluetoothService != null) {
            sanHeYiBluetoothService.stop();
            L.e("------2.0蓝牙扫描停止!");
        }
    }


    /**
     * 连接蓝牙
     *
     * @param address
     * @param mGattCallback
     */
    public static BLeStatus connect(String address, BluetoothGattCallback mGattCallback) {

        try {
            if (adapter == null)
                return BLeStatus.NOTSUPPORT;
            if (!adapter.enable())
                return BLeStatus.NOTOPEND;
            BluetoothDevice remoteDevice = adapter.getRemoteDevice(address);
            remoteDevice.connectGatt(context, true, mGattCallback);
            return BLeStatus.NORMAL;
        } catch (Exception e) {
            L.d("连接蓝牙失败");
            CrashReport.postCatchedException(new Throwable("连接异常"));
            return BLeStatus.OUTHER;
        }
    }


    /**
     * 监测是否支持BLE设备
     *
     * @param context
     * @return
     */
    private static BluetoothAdapter getAdapter(Context context) {
        boolean isSupportBle = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
        if (isSupportBle) {
            BluetoothManager mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            return mBluetoothManager.getAdapter();
        } else {
            return null;
        }
    }


    /**
     * 三合一血糖仪.
     *
     * @param context
     * @param onDataReceive 数据上传的回调
     */
    public static void startSanheyi(Context context, OnDataReceive onDataReceive) {
        BluetoothDevice mDevice = null;
        if (adapter != null) {
            Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
            for (BluetoothDevice device : bondedDevices) {
                if (device.getName().startsWith(SanHeYiBluetoothService.DEVICE_NAME)) {//如果绑定名是以beneCheck开头
                    mDevice = device;
                }
            }
        }
        if (mDevice != null) {
            sanHeYiBluetoothService = new SanHeYiBluetoothService(onDataReceive);
            sanHeYiBluetoothService.start(mDevice);
        } else {
            L.e("外部没有绑定名为 BeneCheck 的蓝牙设备,请在设置界面进行绑定该蓝牙设备");
            return;
        }
    }


    /**
     * 是否开启三合一的蓝牙.
     *
     * @param callback
     * @param isBind
     * @param hasSanheyi
     * @return
     */
    public static BLeStatus scanBluetoothDevice(BluetoothAdapter.LeScanCallback callback, boolean isBind, boolean hasSanheyi, OnDataReceive onDataReceive) {
        if (adapter.isEnabled()) {
            if (hasSanheyi) {
                startSanheyi(context, onDataReceive);
                L.e("------2.0蓝牙启动扫描!");
            }
            BlueToothHelper.isBind = isBind;
            if (!isBind && !PluginManager.getInstance().hasBindDevice()) {//如果没有绑定任何设备,主页不进行扫描
                return BLeStatus.OUTHER;
            }
            if (adapter == null)
                return BLeStatus.NOTSUPPORT;
            adapter.startLeScan(callback);
            L.e("------蓝牙启动扫描!");
            return BLeStatus.NORMAL;
        } else {
            return BLeStatus.NOTOPEND;
        }
    }


    public static void openBlue(Activity activity, int request) {
        boolean isSupportBle = activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
        if (isSupportBle) {
            BluetoothManager mBluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = mBluetoothManager.getAdapter();
            if (!adapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                activity.startActivityForResult(enableBtIntent, request);
            }
        } else {
            T.l(activity, "您的机器不支持BLE");
        }
    }


}
