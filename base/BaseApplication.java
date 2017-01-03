package com.zcareze.zkyandroidweb.base;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.lidroid.xutils.util.LogcatHelper;
import com.tencent.bugly.crashreport.CrashReport;
import com.zcareze.zkyandroidweb.proxy.ProxyFactoryManager;

/**
 * 源代码: Lxw
 * 伊妹儿: China2021@126.com
 * 时间轴: 2016 年 12 月 01 日 16 : 36
 */

public class BaseApplication extends Application {
    private static final String BUGLY_APPID = "089b2b00ba";
    public static String account = "";
    public static String passWord = "";
    public static String residentId = "";//切换家庭成员的时候赋值

    @Override
    public void onCreate() {
        super.onCreate();
        //腾讯bugly初始化,并开启调试模式!
        CrashReport.initCrashReport(getApplicationContext(), BUGLY_APPID, true);
        //初始化三诺血糖仪的SDK
        LogcatHelper.getInstance(this).start();

        try {
            ApplicationInfo applicationInfo = this.getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            ProxyFactoryManager.URL = applicationInfo.metaData.getString("API_ADDRESS");
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            CrashReport.postCatchedException(new Throwable("解析Api地址失败"));
            ProxyFactoryManager.URL = "http://api.zcareze.com";
        }
    }

}
