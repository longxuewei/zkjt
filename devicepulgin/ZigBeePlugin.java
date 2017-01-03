package com.zcareze.zkyandroidweb.devicepulgin;

import android.content.Context;
import android.text.TextUtils;
import android.util.SparseArray;


import com.usbhid.UsbHidEvent;
import com.usbhid.UsbHidUtil;
import com.zcareze.zkyandroidweb.bean.MeasureResult;
import com.zcareze.zkyandroidweb.constant.DeviceType;
import com.zcareze.zkyandroidweb.utils.L;
import com.zcareze.zkyandroidweb.utils.T;
import com.zcareze.zkyandroidweb.viewinterface.OnMonitorDataReceiver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 源代码: Lxw
 * 伊妹儿: China2021@126.com
 * 时间轴: 2016 年 08 月 26 日 16 : 20
 */
public class ZigBeePlugin implements IPlugin {

    public static final String OPERATION_MODE_ADDDEVICE = "com.zcareze.zkyandroidweb.devicepulgin.addevice";
    public static final String OPERATION_MODE_MONITOR_DARA = "com.zcareze.zkyandroidweb.devicepulgin.monitor.data";
    private MeasureResult result;
    private UsbHidUtil usbHidUtil;
    private boolean connectResult = false;
    private Map<Integer, String> msgMap = new HashMap<>();
    private static ZigBeePlugin instance;
    private List<OnMonitorDataReceiver> receivers = new ArrayList<>();
    private Context context;
    private static String CURRENT_MODE = OPERATION_MODE_MONITOR_DARA;

    private ZigBeePlugin(Context context) {
        this.context = context;

    }

    public static ZigBeePlugin getInstance(Context context, OnMonitorDataReceiver onMonitorDataReceiver, String operationMode) {
        CURRENT_MODE = operationMode;
        synchronized (ZigBeePlugin.class) {
            if (instance == null) {
                instance = new ZigBeePlugin(context);
            }
            instance.addReceiver(onMonitorDataReceiver);
        }

        return instance;
    }

    private void addReceiver(OnMonitorDataReceiver onMonitorDataReceiver) {
        if (!receivers.contains(onMonitorDataReceiver)) {
            receivers.add(onMonitorDataReceiver);
        }
    }


    @Override
    public boolean connectDevice() {
        usbHidUtil = new UsbHidUtil(context);
        try {
            usbHidUtil.start();
        } catch (Exception e) {
            T.s(context, "2.4G通讯设备初始化失败,请插上USB通讯设备");
        }
        usbHidUtil.usbhidevent = new UsbHidEvent() {
            @Override
            public void connected() {
                //设备连接
                if (receivers != null && receivers.size() > 0) {
                    refreshStatus(true);
                }
            }

            @Override
            public void disconnect() {
                if (receivers != null && receivers.size() > 0) {
                    refreshStatus(false);
                }
            }

            @Override
            public void recive(byte[] bytes, String s) {
                parseData(s);
            }

            @Override
            public void exception(String s) {
                if (receivers != null && receivers.size() > 0) {
                    refreshStatus(false);
                }
            }
        };
        usbHidUtil.FindDvice();
        return connectResult;
    }

    @Override
    public void parseData(String s) {
        if (TextUtils.isEmpty(s))
            return;
        /**
         *测量结果回调
         */

        //测量设备类型
        String deviceType = s.substring(0, 2);
        //测量设备唯一编码
        /**
         * 设备唯一编码.包含前两位...2016-9-7 15:37:07
         */
        String deviceCoding = s.substring(0, 10);

        L.d(s + "---" + deviceType + "---" + deviceCoding);
        if (result == null) {
            result = new MeasureResult();
        }
        result.setMeasureID(deviceType);
        result.setMeasureCoding(deviceCoding);
        msgMap.clear();
        switch (deviceType) {
            case DeviceType.SKIN_MOISTURE_SIGNAL_FLAG://皮肤水分仪:水分,油脂
                msgMap.put(1, cleanData(s.substring(11, 15)));
                msgMap.put(2, cleanData(s.substring(16, 20)));
                break;
            case DeviceType.BLOOD_OXYGEN_SIGNAL_FLAG:  //血氧仪:血氧,心率
                String value1 = cleanData(s.substring(13, 16));
                String value2 = cleanData(s.substring(10, 13));
                if ("000".equals(value1) || "000".equals(value2))
                    return;  //血氧 心率是 000 的 直接返回,过滤 设备启动时发送偶来的消息.
                msgMap.put(1, value1);
                msgMap.put(2, value2);
                break;
            case DeviceType.BLOOD_PRESSURE_SIGNAL_FLAG://血压仪:收缩压,舒张压
                msgMap.put(1, cleanData(s.substring(10, 13)));
                msgMap.put(2, cleanData(s.substring(13, 16)));
                msgMap.put(3, cleanData(s.substring(16, 19)));
                break;
            case DeviceType.BODY_TEMPERATURE_SIGNAL_FLAG:  //体温仪,耳温
                msgMap.put(1, cleanData(s.substring(14, 18)));
                break;
            case DeviceType.BLOOD_SUGAR_SIGNAL_FLAG:
                double value = (double) (Math.round(Double.parseDouble(s.substring(10, 14)) / 18 * 10)) / 10;// 这是 单血糖仪的绑定
                msgMap.put(1, value + "");
                break;
            default:

                break;
        }
        L.i(msgMap.toString());
        result.setMeasureValue(msgMap);

        if (receivers != null && receivers.size() > 0) {
            sendData(result);
        }
    }


    private void sendData(MeasureResult result) {

        for (OnMonitorDataReceiver receiver : receivers) {
            receiver.onDataReceive(result, CURRENT_MODE);
        }

    }


    private void refreshStatus(boolean status) {
        for (OnMonitorDataReceiver receiver : receivers) {
            receiver.onConnectStatusChanged(status);
        }
    }


    @Override
    public void disConnectDivice() {
        if (usbHidUtil != null)
            usbHidUtil.destory();
    }

    public void onChangeMode(String currentMode) {
        CURRENT_MODE = currentMode;
    }


    /**
     * 去除数据前面的0
     *
     * @param sourceData 截取出来的数据
     * @return 新数据不带0
     */
    private String cleanData(String sourceData) {
        if (sourceData.startsWith("0")) {
            sourceData = sourceData.substring(1);
        }
        return sourceData;
    }
}
