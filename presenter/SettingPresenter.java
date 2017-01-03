package com.zcareze.zkyandroidweb.presenter;

import android.text.TextUtils;

import com.alibaba.fastjson.JSON;
import com.tencent.bugly.crashreport.CrashReport;
import com.zcareze.core.Zcareze;
import com.zcareze.domain.regional.dictionary.MeterConnected;
import com.zcareze.domain.regional.dictionary.MeterList;
import com.zcareze.webview.Javascript;
import com.zcareze.zkyandroidweb.bean.DeviceInfo;
import com.zcareze.zkyandroidweb.bean.MeasureResult;
import com.zcareze.zkyandroidweb.bean.NewMonitorResult;
import com.zcareze.zkyandroidweb.constant.AndroidCommon;
import com.zcareze.zkyandroidweb.constant.BLeStatus;
import com.zcareze.zkyandroidweb.db.MeterConnectedDao;
import com.zcareze.zkyandroidweb.db.MeterListDao;
import com.zcareze.zkyandroidweb.devicepulgin.BaseBlePlugin;
import com.zcareze.zkyandroidweb.devicepulgin.BlueToothHelper;
import com.zcareze.zkyandroidweb.devicepulgin.PluginManager;
import com.zcareze.zkyandroidweb.devicepulgin.ZigBeePlugin;
import com.zcareze.zkyandroidweb.utils.L;
import com.zcareze.zkyandroidweb.viewinterface.MainViewImpl;
import com.zcareze.zkyandroidweb.viewinterface.OnMonitorDataReceiver;

import org.xwalk.core.JavascriptInterface;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 源代码: Lxw
 * 伊妹儿: China2021@126.com
 * 时间轴: 2016 年 11 月 17 日 12 : 02
 * <p>
 * 设置界面的逻辑处理类.
 */

public class SettingPresenter implements Javascript {

    private static SettingPresenter instance;
    private MainViewImpl mainViewImpl;
    private ZigBeePlugin zigBeePlugin;

    private SettingPresenter(MainViewImpl mainViewImpl) {
        this.mainViewImpl = mainViewImpl;
    }

    public static SettingPresenter getInstance(MainViewImpl mainViewImpl) {
        if (instance == null) {
            synchronized (SettingPresenter.class) {
                if (instance == null) {
                    instance = new SettingPresenter(mainViewImpl);
                }
            }
        }
        return instance;
    }


    /**
     * 开始绑定,前台在点击某一种设备之后,根据设备类型调用该方法.
     * 进行区分初始化.
     *
     * @param jsonInfo 前台的json数据
     */
    @JavascriptInterface
    public void onBindingStart(String jsonInfo) {
        if (TextUtils.isEmpty(jsonInfo))
            return;
        DeviceInfo deviceInfo = JSON.parseObject(jsonInfo, DeviceInfo.class);
        if (deviceInfo.getConnectMode().equals("ZGB")) {
            initZGB(deviceInfo.getDeviceType());
        } else if (deviceInfo.getConnectMode().equals("BLE")) {
            initBLE(deviceInfo.getDeviceType());
        }
    }


    /**
     * 前端主动停止扫描
     */
    @JavascriptInterface
    public void stopScan() {
        BlueToothHelper.stopScan(mainViewImpl.getPresneter().scanCallBack);
    }


    /**
     * 初始化ZGB状态进行接收数据,并根据解析结果和传入的设备类型进行判断数据的正确性,
     * 以及对ZGB的初始化之后状态 的回调监听.
     * 并对上传的数据进行数据监测. 如果任何一项监测指标为0那么直接不计算.返回
     *+
     * @param deviceType 设备类型:ZGB的类型是signal_flag.设备头两位
     */
    private void initZGB(final String deviceType) {
        zigBeePlugin = ZigBeePlugin.getInstance(mainViewImpl.getContext(), new OnMonitorDataReceiver() {
            @Override
            public void onDataReceive(MeasureResult result, String currentMode) {
                if (currentMode == ZigBeePlugin.OPERATION_MODE_MONITOR_DARA)
                    return;
                if (result.getMeasureID().equals(deviceType)) {
                    if (!checkVerecity(result.getMeasureValue()))
                        return;
                    NewMonitorResult monitorResult = mainViewImpl.getPresneter().onAddDeviceDataReceive(result);
                    if (monitorResult != null) {
                        Map<String, Object> params = new HashMap<>();
                        params.put("data", monitorResult);
                        Zcareze.javascript("onAnalysisDone", params);
                    } else {
                        CrashReport.postCatchedException(new Throwable("绑定设备之后,解析数据为空"));
                    }
                } else {
                    L.d("测量失败: 选中的设备类型和实际测量收到的设备信息不匹配");
                    DeviceInfo info = new DeviceInfo(result.getMeasureCoding(), "ZGB", "2");
                    Map<String, Object> params = new HashMap<>();
                    params.put("data", info);
                    Zcareze.javascript("onAnalysisFail", params);
                }
            }

            @Override
            public void onConnectStatusChanged(boolean status) {
                L.d(SettingPresenter.class.getSimpleName() + " onConnectStatusChanged:" + status);
                if (status) {
                    Zcareze.javascript("onPrepareDone", null);
                } else {
                    Zcareze.javascript("onPrepareFail", null);
                }
            }
        }, ZigBeePlugin.OPERATION_MODE_ADDDEVICE);
        zigBeePlugin.connectDevice();

    }


    /**
     * 初始化蓝牙设备.
     *
     * @param deviceType 蓝牙设备的类型: 以meter_code为准
     */
    private void initBLE(String deviceType) {
        if (!TextUtils.isEmpty(deviceType)) {
            BLeStatus status = BlueToothHelper.scanBluetoothDevice(mainViewImpl.getPresneter().scanCallBack, true, true, mainViewImpl.getPresneter().receive);
            if (status != BLeStatus.NORMAL) {
                Zcareze.javascript("onBleOffLine",null);
            }
        }
    }

    /**
     * 开启蓝牙
     */
    @JavascriptInterface
    public void openBLE() {
        //这里开启之后不管,不给前台任何返回值, 默认让用户重新操作.
        BlueToothHelper.openBlue(mainViewImpl.getActivity(), AndroidCommon.OPEN_BLE_OF_BIND);
    }


    /**
     * 取消绑定设备,包括在扫描状态下的取消绑定.
     *
     * @param deviceType 设备类型.
     */
    @JavascriptInterface
    public void onUnBindDevice() {
        if (zigBeePlugin != null) {
            zigBeePlugin.onChangeMode(ZigBeePlugin.OPERATION_MODE_MONITOR_DARA);
        }
        //如果出现了结果,那么就代表用户连接了设备, 连接设备的时候,蓝牙已经停止扫描了.
        BlueToothHelper.stopScan(mainViewImpl.getPresneter().scanCallBack);
    }


    /**
     * 用户测量完成之后,点击绑定该设备.
     * 此方法,根据设备类型来绑定设备.
     * 如果是蓝牙设备,那么meterCode去检测数据库是否支持该设备.如果是ZGB设备那么就用signal_flag.
     * 绑定设备的唯一码是:蓝牙是mac地址,zgb是唯一码
     *
     * @param jsonDeviceAddress 前台json数据 deviceType: 分为meterCode或者是signal_flag.
     * @return 0失败, 1成功, 2已绑定.
     */
    @JavascriptInterface
    public int bindDevice(String jsonDeviceAddress) {
        if (zigBeePlugin != null) {
            zigBeePlugin.onChangeMode(ZigBeePlugin.OPERATION_MODE_MONITOR_DARA);
        }
        L.d(this.getClass().getSimpleName() + "connectDevice " + jsonDeviceAddress);
        if (TextUtils.isEmpty(jsonDeviceAddress))
            return 0;
        DeviceInfo deviceInfo = JSON.parseObject(jsonDeviceAddress, DeviceInfo.class);
        if (deviceInfo.getConnectMode().equals("2")) {
            String meterCode = MeterListDao.getInstance(mainViewImpl.getContext()).queryMeterCodeBySignalFlag(deviceInfo.getMacAddress().substring(0, 2));
            if (TextUtils.isEmpty(meterCode)) {
                L.d("服务器不知此设备.");
                return 0;
            }
            MeterConnected connected = new MeterConnected();
            connected.setMeterCode(meterCode);
            connected.setSignalKind(Integer.parseInt(deviceInfo.getConnectMode()));
            connected.setIdCode(deviceInfo.getMacAddress());
            return MeterConnectedDao.getInstance(mainViewImpl.getContext()).bindDevice(connected);
        } else if (deviceInfo.getConnectMode().equals("1")) {
            String meterCode = deviceInfo.getDeviceType();
            MeterList meterList = MeterListDao.getInstance(mainViewImpl.getContext()).query(meterCode);
            if (meterList != null) {
                MeterConnected meterConnected = new MeterConnected(meterList.getCode(), meterList.getSignalKind(), deviceInfo.getMacAddress());
                int i = MeterConnectedDao.getInstance(mainViewImpl.getContext()).bindDevice(meterConnected);
                if (i == 1) {
                    //TODO 如果再刷新的同时主页还有未断开的连接
                    PluginManager.getInstance().refresh(mainViewImpl.getContext());
//                    BlueToothHelper.getInstance(mainViewImpl.getContext()).stopScan();
                    return i;
                }
                return i;
            } else {
                L.d("服务器不知此设备.");
                return 0;
            }
        }
        return 0;
    }


    /**
     * 前台获取所有已经绑定的设备信息
     *
     * @return
     */
    @JavascriptInterface
    public String getAllConnectedDevice() {
        List<MeterConnected> meterConnecteds = MeterConnectedDao.getInstance(mainViewImpl.getContext()).queryAll();
        return meterConnecteds == null ? null : JSON.toJSONString(meterConnecteds);
    }


    /**
     * 用户在扫描到蓝牙之后,点击某一个蓝牙进行绑定
     *
     * @param macAddress 蓝牙的mac地址;
     * @param code       蓝牙的MeterCode;
     */
    @JavascriptInterface
    public void connectDevice(String macAddress, String meterCode) {
        //前台在选择连接的时候,进行了暂停.
        BlueToothHelper.stopScan(mainViewImpl.getPresneter().scanCallBack);
        BaseBlePlugin baseBlePlugin = PluginManager.getInstance().addPlugin(macAddress, meterCode, mainViewImpl.getContext(),true);
        BLeStatus connect = baseBlePlugin.connect();
        switch (connect) {
            case OUTHER:
            case CONNECTED:
            case NOTOPEND:
                //TODO 连接的时候未开启蓝牙.提醒用户是否开启.
                Zcareze.javascript("onBTOffLine", null);
                break;
        }
    }


    /**
     * 验证数据的正确性, 一旦数据上传到平板上面,那么校验他的正确性,
     * 如果任何一项数据的值为0 ,那么 直接就不处理了.
     *
     * @param measureValue 测量结果的值集.
     * @return 是否是正确数据
     */
    public boolean checkVerecity(Map<Integer, String> measureValue) {
        if (measureValue != null && measureValue.size() > 0) {
            int size = measureValue.size();
            for (int i = 1; i <= size; i++) {
                try {
                    if (Integer.parseInt(measureValue.get(i)) == 0)
                        return false;
                } catch (Exception e) {
                    if (Double.parseDouble(measureValue.get(i)) == 0)
                        return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }
}
