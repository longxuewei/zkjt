package com.zcareze.zkyandroidweb.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import com.tencent.bugly.crashreport.CrashReport;
import com.zcareze.core.Zcareze;
import com.zcareze.webview.ZcarezeActivity;
import com.zcareze.zkyandroidweb.bean.MeasureResult;
import com.zcareze.zkyandroidweb.bean.NewMonitorResult;
import com.zcareze.zkyandroidweb.constant.AndroidCommon;
import com.zcareze.zkyandroidweb.constant.BLeStatus;
import com.zcareze.zkyandroidweb.devicepulgin.BlueToothHelper;
import com.zcareze.zkyandroidweb.devicepulgin.PluginManager;
import com.zcareze.zkyandroidweb.devicepulgin.ZigBeePlugin;
import com.zcareze.zkyandroidweb.presenter.MainPresenter;
import com.zcareze.zkyandroidweb.presenter.SettingPresenter;
import com.zcareze.zkyandroidweb.utils.ActivityManagerUtil;
import com.zcareze.zkyandroidweb.utils.L;
import com.zcareze.zkyandroidweb.utils.T;
import com.zcareze.zkyandroidweb.viewinterface.MainViewImpl;
import com.zcareze.zkyandroidweb.viewinterface.OnMonitorDataReceiver;

import org.xwalk.core.XWalkPreferences;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends ZcarezeActivity implements OnMonitorDataReceiver, MainViewImpl {
    private ZigBeePlugin zigBeePlugin;
    private MainPresenter mainPresenter;
    private SettingPresenter settingPresenter;

    /**
     * 主界面的初始化:
     * 1.将两个逻辑接口设置给前台.
     * 2.将ZIGBEE进行初始化, 以便在任何时候.测试ZIGBEE的时候.弹出结果显示.
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityManagerUtil.getInstance().addActivity(this);
        XWalkPreferences.setValue(XWalkPreferences.REMOTE_DEBUGGING, true);
        zigBeePlugin = ZigBeePlugin.getInstance(this, this, ZigBeePlugin.OPERATION_MODE_MONITOR_DARA);
        mainPresenter = MainPresenter.getInstance(this);
        BlueToothHelper.init(this);
        PluginManager.getInstance().init(this);
        mainPresenter.registBroad();
        settingPresenter = SettingPresenter.getInstance(this);
        zigBeePlugin.connectDevice();
        //zig进行连接.
        setJSInterface(mainPresenter);//主页相关逻辑
        setJSInterface(settingPresenter, "SettingPresenter");// 设置界面逻辑
        //开始进行蓝牙扫描
        startScan();
    }


    /**
     * 开始进行扫描
     */
    private void startScan() {
        BLeStatus bLeStatus = BlueToothHelper.scanBluetoothDevice(mainPresenter.scanCallBack, false, true, mainPresenter.receive);
        switch (bLeStatus) {
            case NOTOPEND:
                T.l(this, "蓝牙未开启,请开启蓝牙");
                BlueToothHelper.openBlue(this, AndroidCommon.OPEN_BLE_OF_LAUNCHER);
                break;
            case NOTSUPPORT:
                T.l(this, "本设备不支持低功耗蓝牙");
                break;
        }
    }


    /**
     * 设置数据接收器,这里的数据是接受过来,并封装好的数据,但是并没有进行测量.
     * 这里目前接受的是 zigbee发送过来封装好的数据,用来进行规则引擎的计算.
     *
     * @param result
     */
    @Override
    public void onDataReceive(MeasureResult result, String currentMode) {
        if (currentMode == ZigBeePlugin.OPERATION_MODE_ADDDEVICE || result == null) {
            return;
        }
        if (!(settingPresenter.checkVerecity(result.getMeasureValue())))
            return;
        NewMonitorResult newMonitorResult = mainPresenter.monitorData(this, result.getMeasureCoding(), result.getMeasureValue(), null);
        if (newMonitorResult != null) {
            Map<String, Object> params = new HashMap<>();
            params.put("data", newMonitorResult);
            L.d("自动测量结果:" + newMonitorResult.toString());
            Zcareze.javascript("DeviceMonitor", params);
        } else {
            CrashReport.postCatchedException(new Throwable("测量数据为空"));
        }
    }


    @Override
    public void onConnectStatusChanged(boolean status) {
        L.d(this.getClass().getSimpleName() + "onConnectStatusChanged" + status);
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public void showMessage(String message) {
        if (TextUtils.isEmpty(message))
            return;
        T.s(this, message);
    }

    /**
     * 蓝牙开启结果回调:
     * 1.是开启扫描的时候调用.
     * 2.是开启连接的时候调用.
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //主页进行开启扫描同意
        if (requestCode == AndroidCommon.OPEN_BLE_OF_LAUNCHER && resultCode == RESULT_OK) {
            startScan();
        }
    }

    @Override
    public MainPresenter getPresneter() {
        return mainPresenter;
    }


    @Override
    public Activity getActivity() {
        return this;
    }


}
