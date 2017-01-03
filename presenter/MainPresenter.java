package com.zcareze.zkyandroidweb.presenter;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.alibaba.fastjson.JSON;
import com.tencent.bugly.crashreport.CrashReport;
import com.zcareze.core.Zcareze;
import com.zcareze.domain.regional.dictionary.MeterModes;
import com.zcareze.domain.regional.dictionary.MonitorSuites;
import com.zcareze.webview.Javascript;
import com.zcareze.zkyandroidweb.bean.BLEDeviceInfo;
import com.zcareze.zkyandroidweb.bean.InputModeData;
import com.zcareze.zkyandroidweb.bean.MainDeviceItem;
import com.zcareze.zkyandroidweb.bean.MainItem;
import com.zcareze.zkyandroidweb.bean.MeasureResult;
import com.zcareze.zkyandroidweb.bean.MonitorEngine;
import com.zcareze.zkyandroidweb.bean.MonitorValue;
import com.zcareze.zkyandroidweb.bean.NewMonitorResult;
import com.zcareze.zkyandroidweb.constant.AccountListInfo;
import com.zcareze.zkyandroidweb.constant.ResidentInfo;
import com.zcareze.zkyandroidweb.db.MeterConnectedDao;
import com.zcareze.zkyandroidweb.db.MeterModesDao;
import com.zcareze.zkyandroidweb.db.MonitorSuitesDao;
import com.zcareze.zkyandroidweb.db.QueryServiceUtil;
import com.zcareze.zkyandroidweb.devicepulgin.BaseBlePlugin;
import com.zcareze.zkyandroidweb.devicepulgin.BlueToothHelper;
import com.zcareze.zkyandroidweb.devicepulgin.PluginManager;
import com.zcareze.zkyandroidweb.proxy.ProxyFactoryManager;
import com.zcareze.zkyandroidweb.sanheyi.OnDataReceive;
import com.zcareze.zkyandroidweb.utils.ActivityManagerUtil;
import com.zcareze.zkyandroidweb.utils.ConnectProcessThread;
import com.zcareze.zkyandroidweb.utils.L;
import com.zcareze.zkyandroidweb.utils.SPUtil;
import com.zcareze.zkyandroidweb.utils.TimeUtil;
import com.zcareze.zkyandroidweb.viewinterface.LoginViewImpl;
import com.zcareze.zkyandroidweb.viewinterface.MainViewImpl;

import org.xwalk.core.JavascriptInterface;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 源代码: Lxw
 * 伊妹儿: China2021@126.com
 * 时间轴: 2016 年 11 月 12 日 11 : 15
 * MainActivity的逻辑层
 */

public class MainPresenter implements Javascript {
    private static MainPresenter instance;
    private MainViewImpl mainViewImpl;
    private final Object statusLock = new Object();

    private MainPresenter(MainViewImpl mainViewImpl) {
        this.mainViewImpl = mainViewImpl;
    }

    public static MainPresenter getInstance(MainViewImpl mainViewImpl) {
        if (instance == null) {
            synchronized (LoginPresenter.class) {
                if (instance == null) {
                    instance = new MainPresenter(mainViewImpl);
                }
            }
        }
        return instance;
    }

    /**
     * 利用新的规则引擎. 算出结果
     * ZigBeePlugin---->MainActivity--->this.
     * 将数据传到该方法进行规则引擎的处理.算出提示语.
     *
     * @return
     */
    public NewMonitorResult monitorData(Context context, String macAddress, Map<Integer, String> monitorValue, @Nullable String monitorPart) {
        MonitorEngine service = new MonitorEngine(context);
        String residentId = (String) SPUtil.get(context, ResidentInfo.ID, "");
        String gender = (String) SPUtil.get(context, ResidentInfo.GENDER, "1");
        Date date = TimeUtil.paresDateByStr((String) SPUtil.get(context, ResidentInfo.BIRTHDAY, ""));
        NewMonitorResult monitorResult = service.monitorProcess(macAddress, monitorValue, residentId, Integer.parseInt(gender), date, monitorPart);
        if (monitorResult != null) {
            //去除";"分号
            if (monitorResult.getHint().contains(";"))
                monitorResult.setHint(monitorResult.getHint().replace(";", ""));
            String hint = monitorResult.getHint();
            if (!TextUtils.isEmpty(hint) && !hint.endsWith("。"))
                monitorResult.setHint(monitorResult.getHint() + "。");
            monitorResult.setDeviceMac(macAddress);//加上mac地址,方便切换部位.
        } else {
            return null;
        }
        return monitorResult;
    }


    /**
     * 部位被改变,重新计算!
     * 切换部位要看是手动输入的数据, 还是自动输入的数据.
     * 他们之间区别在于有没有MeasureResult
     *
     * @param oldMonitorData
     * @return
     */
    @JavascriptInterface
    public String onPartChanged(String oldMonitorData) {
        if (TextUtils.isEmpty(oldMonitorData))
            return null;
        NewMonitorResult oldResult = JSON.parseObject(oldMonitorData, NewMonitorResult.class);
        //取出来封装成Map参数.
        List<MonitorValue> values = oldResult.getValues();
        Map<Integer, String> params = new HashMap<>();
        for (MonitorValue value : values) {
            params.put(value.getSeqNo(), value.getValue());
        }

        NewMonitorResult newResult;


        //手动
        if (TextUtils.isEmpty(oldResult.getDeviceMac())) {//如果没有设备的mac说明是手动输入的
            //手动输入格式不正确 ,需要转换成 手动输入的json数据格式.
            InputModeData data = new InputModeData();
            data.setMonitorPart(oldResult.getCurrentParts());
            Map<String, Double> param = new HashMap<>();
            List<MonitorValue> oldValues = oldResult.getValues();
            for (MonitorValue oldValue : oldValues) {
                param.put(oldValue.getSeqNo() + "", Double.parseDouble(oldValue.getValue()));
            }
            data.setMonitorData(param);
            data.setSuiteId(oldResult.getSuiteId());
            newResult = JSON.parseObject(inputMonitorData(JSON.toJSONString(data)), NewMonitorResult.class);
            //自动
        } else {//设备测试的
            newResult = monitorData(mainViewImpl.getContext(), oldResult.getDeviceMac(), params, oldResult.getCurrentParts());
        }


        //前台将测量的设备数据修改了,导致不能重新计算;测量设备 不能识别, 或未绑定.
        if (newResult == null)
            return null;
        Map<String, Object> viewParams = new HashMap<>();
        viewParams.put("data", newResult);
        L.d("改变部位:" + JSON.toJSONString(viewParams));
        String s = JSON.toJSONString(viewParams);
        return s;
    }

    @JavascriptInterface
    public String getDeviceInfo() {
        //一个仪器有多重运行模式,取交集!
        long start = System.currentTimeMillis();
        List<MonitorSuites> monitorSuites = MonitorSuitesDao.getInstance(mainViewImpl.getContext()).queryAll();
        if (monitorSuites != null) {
            List<MainDeviceItem> items = new ArrayList<>();
            for (MonitorSuites mainDeviceItem : monitorSuites) {
                int check = MeterConnectedDao.getInstance(mainViewImpl.getContext()).check(mainDeviceItem.getId());
                MainDeviceItem deviceItem = new MainDeviceItem(check, mainDeviceItem.getName(), mainDeviceItem.getId());
                items.add(deviceItem);
            }
            MainItem item = new MainItem(items,
                    SPUtil.getUserInfo(mainViewImpl.getContext()),
                    (String) SPUtil.get(mainViewImpl.getContext(),
                            ResidentInfo.COOKVALUE, ""),
                    ProxyFactoryManager.URL, ProxyFactoryManager.API_VERSION, SPUtil.getAccountInfo(mainViewImpl.getContext()), (String) SPUtil.get(mainViewImpl.getContext(), AccountListInfo.CLOUDID, ""));
            return JSON.toJSONString(item);
        } else {
            CrashReport.postCatchedException(new Throwable("MonitorMode表中无数据"));
            return null;
        }
    }


    /**
     * @param json
     * @param monitorPart
     * @return
     */
    @JavascriptInterface
    public String inputMonitorData(String json) {
        if (TextUtils.isEmpty(json)) {
            return null;
        }
        NewMonitorResult oldResult = parseInputData(JSON.parseObject(json, InputModeData.class));

        if (oldResult != null) {
            String suiteID = oldResult.getSuiteId();
            if (!TextUtils.isEmpty(suiteID)) {
                Map<Integer, String> map = new HashMap<>();
                for (MonitorValue value : oldResult.getValues()) {
                    map.put(value.getSeqNo(), value.getValue());
                }
                MonitorEngine engine = new MonitorEngine(mainViewImpl.getContext());
                String residentId = (String) SPUtil.get(mainViewImpl.getContext(), ResidentInfo.ID, "");
                String gender = (String) SPUtil.get(mainViewImpl.getContext(), ResidentInfo.GENDER, "1");
                Date date = TimeUtil.paresDateByStr((String) SPUtil.get(mainViewImpl.getContext(), ResidentInfo.BIRTHDAY, ""));
                NewMonitorResult monitorResult = engine.monitorProcessByManualdrive(suiteID, map, residentId, Integer.parseInt(gender), date, TextUtils.isEmpty(oldResult.getCurrentParts()) ? null : oldResult.getCurrentParts());
                engine = null;
                return JSON.toJSONString(monitorResult);
            } else {
                L.d("suiteID 未找到!");
                CrashReport.postCatchedException(new Throwable("suiteId未找到"));
            }
        } else {
            CrashReport.postCatchedException(new Throwable("旧数据解析失败"));
        }
        return null;
    }


    /**
     * 手动输入的数据,切换部位上传回来的数据是不同的. 要解析成相同格式!
     *
     * @param inputModeData
     * @return
     */
    private NewMonitorResult parseInputData(InputModeData inputModeData) {
        NewMonitorResult oldData = new NewMonitorResult();
//        oldData.setDeviceType(inputModeData.getDeviceType());
        oldData.setSuiteId(inputModeData.getSuiteId());
        List<MonitorValue> values = new ArrayList<>();
        for (Map.Entry<String, Double> data : inputModeData.getMonitorData().entrySet()) {
            MonitorValue value = new MonitorValue();
            value.setSeqNo(Integer.parseInt(data.getKey()));
            value.setValue(data.getValue() + "");
            values.add(value);
        }
        oldData.setValues(values);
        oldData.setCurrentParts(inputModeData.getMonitorPart());
        return oldData;
    }


    /**
     * 设备绑定,在进行规制逻辑引擎计算的时候,是查不到数据库的记录的,所以以手动模式!
     *
     * @param result
     * @return
     */
    public NewMonitorResult onAddDeviceDataReceive(MeasureResult result) {
        String suiteID = QueryServiceUtil.getInstance().querySuiteIDByDeviceType(mainViewImpl.getContext(), result.getMeasureID());
        MonitorEngine engine = new MonitorEngine(mainViewImpl.getContext());
        String residentId = (String) SPUtil.get(mainViewImpl.getContext(), ResidentInfo.ID, "");
        String gender = (String) SPUtil.get(mainViewImpl.getContext(), ResidentInfo.GENDER, "1");
        Date date = TimeUtil.paresDateByStr((String) SPUtil.get(mainViewImpl.getContext(), ResidentInfo.BIRTHDAY, ""));
        NewMonitorResult monitorResult = engine.monitorProcessByManualdrive(suiteID, result.getMeasureValue(), residentId, Integer.parseInt(gender), date, null);
        monitorResult.setDeviceMac(result.getMeasureCoding());
        return monitorResult;
    }


    /**
     * 所有扫描的回调
     */
    public BluetoothAdapter.LeScanCallback scanCallBack = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {

//            if (device == null) {    什么时候加的???
//                BlueToothHelper.scanBluetoothDevice(scanCallBack, false, true, receive);
//                return;
//            }
            L.e("蓝牙扫描回调: " + device.getAddress());
            if (BlueToothHelper.isBind) {
                Map<String, Object> devices = new HashMap<>();
                BLEDeviceInfo deviceInfo = new BLEDeviceInfo(device.getAddress(), "BLE", "1", rssi, TextUtils.isEmpty(device.getName()) ? "未知设备" : device.getName());//第三个参数是 通讯模式
                devices.put("data", deviceInfo);
                Zcareze.javascript("onBTDeviceList", devices);
            } else {
                String newDeviceAddress = device.getAddress();
                if (!TextUtils.isEmpty(newDeviceAddress)) {
                    BaseBlePlugin plugin = PluginManager.getInstance().getPlugin(newDeviceAddress);
                    synchronized (statusLock) {
                        if (plugin == null || plugin.getCurrentStatus() != BluetoothProfile.STATE_DISCONNECTED) {
                            return;
                        }
                        plugin.changeStatus(BluetoothProfile.STATE_CONNECTING);
                    }
                    L.d("扫描到,开始连接,状态:" + plugin.getCurrentStatus() + "-----" + plugin.getClass().getSimpleName());
                    new ConnectProcessThread(plugin).start();
                }
            }
        }
    };


    public OnDataReceive receive = new OnDataReceive() {
        @Override
        public void onDataReceive(byte[] bytedata, List<Byte> list) {
            int monitorType = getMonitorType(list);
            if (monitorType != -1) {
                double result = parseDataBySanheyi(list, monitorType);
                if (result <= 0)//数据过低.
                    return;
                if (monitorType == 2)//如果是尿酸. 乘以1000 .mmol单位.
                    result = result * 1000;
                Map<String, String> params = new HashMap<>();
                params.put("meter_code", "6");  //TODO 写死!
                params.put("mode_code", monitorType + "");
                List<MeterModes> meterModes = MeterModesDao.getInstance(mainViewImpl.getContext()).queryByParams(params);
                String suiteId = null;
                if (meterModes != null && meterModes.size() == 1) {
                    suiteId = meterModes.get(0).getSuiteId();
                }
                if (!TextUtils.isEmpty(suiteId)) {
                    Map<Integer, String> monitorData = new HashMap<>();
                    monitorData.put(1, result + "");
                    monitor(monitorData, suiteId);
                } else {
                    CrashReport.postCatchedException(new Throwable("没有找到三合一血糖仪的suiteid"));
                }
            }
        }
    };


    /**
     * 注册监听屏幕锁定的广播
     */
    public void registBroad() {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                    BlueToothHelper.stopScan(MainPresenter.this.scanCallBack);
                } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                    //监听屏幕打开,需要在主页.
                    if (BlueToothHelper.isMainPage) {
                        BlueToothHelper.scanBluetoothDevice(mainViewImpl.getPresneter().scanCallBack, false, true, receive);
                    }
                } else if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {//监听蓝牙开关.
                    int intExtra = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    switch (intExtra) {
                        case BluetoothAdapter.STATE_OFF:
                            BlueToothHelper.stopScan(MainPresenter.this.scanCallBack);
                            break;
                        case BluetoothAdapter.STATE_ON:
                            if (BlueToothHelper.isMainPage) {
                                BlueToothHelper.scanBluetoothDevice(MainPresenter.this.scanCallBack, false, true, receive);
                            }
                            break;
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

        mainViewImpl.getContext().registerReceiver(receiver, filter);
    }


    /**
     * 前台调用该方法改变所在的页面,进行蓝牙停止
     *
     * @param status 1:主页,0非主页.
     */
    @JavascriptInterface
    public void changeCurrentPage(String status) {
        boolean result = "1".equals(status);
        if (BlueToothHelper.isMainPage == result) {
            return;
        }
        BlueToothHelper.isMainPage = result;
        if (result) {
            BlueToothHelper.scanBluetoothDevice(mainViewImpl.getPresneter().scanCallBack, false, true, receive);
        } else {
            BlueToothHelper.stopScan(MainPresenter.this.scanCallBack);
        }
    }


    /**
     * 注销
     */
    @JavascriptInterface
    public void logout() {
        ActivityManagerUtil.getInstance().exit(mainViewImpl.getContext());
    }


    /**
     * 切换家庭成员
     *
     * @param residentId 成员ID
     */
    @JavascriptInterface
    public void changeFamily(final String residentId) {
        LoginPresenter instance = LoginPresenter.getInstance(new LoginViewImpl() {
            @Override
            public Context getContext() {
                return mainViewImpl.getContext();
            }

            @Override
            public void showMessage(String message) {
                mainViewImpl.showMessage(message);
            }
        });
        instance.changeFamily(residentId);
    }


    /**
     * 静默登陆
     */
    @JavascriptInterface
    public void reLogin() {

        LoginPresenter instance = LoginPresenter.getInstance(new LoginViewImpl() {
            @Override
            public Context getContext() {
                return mainViewImpl.getContext();
            }

            @Override
            public void showMessage(String message) {
                mainViewImpl.showMessage(message);
            }
        });
        instance.reLogin();
    }


    /**
     * 三合一2g解析
     *
     * @param bytedata   原始数据
     * @param datas      原始数据
     * @param deviceType 解析类型: 14血糖,15:尿酸,16:胆固醇.
     * @return
     */
    private static double parseDataBySanheyi(List<Byte> datas, int deviceType) {
        double result = -1;
        if (datas.size() == 31 && datas.get(0) == 0x24) {// 开始
            int ivalue = (datas.get(28) & 0xff) + ((datas.get(29) & 0xff) << 8);
            String values = "";
            int n = String.valueOf(ivalue).length();
            if (n < 4) {
                for (int i = 0; i < (4 - n); i++) {
                    values += "0";
                }
            }
            values += ivalue;

            if (deviceType == 4) {// 血糖处理;
                double temp = ivalue / 18.0f;
                result = (double) (Math.round(temp * 10)) / 10;
            } else if (deviceType == 5) {// "尿酸"
                double temp = ivalue / 168.1f;
                String uaValue = "";
                DecimalFormat df = new DecimalFormat("#.00");
                if (temp < 1) {
                    uaValue = "0" + df.format(temp);
                } else {
                    uaValue = df.format(temp);
                }
                double bgValue = (double) (Math.round((ivalue / 18) * 10)) / 10;
                result = Double.parseDouble(uaValue);

            } else if (deviceType == 6) {// "胆固醇"
                double temp = ivalue / 38.66f;
                double cholValue = (double) (Math.round(temp * 10)) / 10;
                result = cholValue;
            }

            return result;
        }
        return result;
    }


    /**
     * 根据原始数据获取三合一设备上传的数据类型
     *
     * @param datas 原始数据
     * @return 4:血糖,5:尿酸,6:胆固醇. 类型与 meter_modes的seq_no对应.
     */
    private int getMonitorType(List<Byte> datas) {
        int result = -1;
        if (datas.size() == 31 && datas.get(0) == 0x24) {// 开始
            byte deviceID = datas.get(15);
            if (deviceID == 0x41) {
                // 血糖
                result = 1;
            }
            if (deviceID == 0x51) {
                // 尿酸
                result = 2;
            }
            if (deviceID == 0x61) {
                // 胆固醇
                result = 3;
            }
        }
        return result;
    }


    /**
     * 此方法除了三合一血糖仪,没有其他的ble调用此方法.
     *
     * @param monitorData
     * @param suiteId
     */
    protected void monitor(Map<Integer, String> monitorData, String suiteId) {
        MonitorEngine engine = new MonitorEngine(mainViewImpl.getContext());
        String residentId = (String) SPUtil.get(mainViewImpl.getContext(), ResidentInfo.ID, "");
        String gender = (String) SPUtil.get(mainViewImpl.getContext(), ResidentInfo.GENDER, "1");
        Date date = TimeUtil.paresDateByStr((String) SPUtil.get(mainViewImpl.getContext(), ResidentInfo.BIRTHDAY, ""));
        NewMonitorResult monitorResult = engine.monitorProcessByManualdrive(suiteId, monitorData, residentId, Integer.parseInt(gender), date, null);
        if (monitorResult != null) {
            Map<String, Object> params = new HashMap<>();
            params.put("data", monitorResult);
            Zcareze.javascript("DeviceMonitor", params);
        } else {
            Zcareze.javascript("DeviceMonitor", null);
        }
    }
}
