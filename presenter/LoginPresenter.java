package com.zcareze.zkyandroidweb.presenter;

import android.text.TextUtils;

import com.zcareze.core.Zcareze;
import com.zcareze.core.service.result.AccountResult;
import com.zcareze.domain.core.AccountList;
import com.zcareze.domain.regional.dictionary.BaseDictionaryDomain;
import com.zcareze.domain.regional.dictionary.Dictionary;
import com.zcareze.domain.regional.resident.ResidentList;
import com.zcareze.regional.service.param.BaseDictionaryParam;
import com.zcareze.regional.service.result.BaseDictionaryDataResult;
import com.zcareze.result.Result;
import com.zcareze.rpc.util.HttpClientUtil;
import com.zcareze.zkyandroidweb.activity.LoginActivity;
import com.zcareze.zkyandroidweb.base.BaseApplication;
import com.zcareze.zkyandroidweb.constant.AccountListInfo;
import com.zcareze.zkyandroidweb.constant.AndroidCommon;
import com.zcareze.zkyandroidweb.constant.ResidentInfo;
import com.zcareze.zkyandroidweb.constant.ServiceCommon;
import com.zcareze.zkyandroidweb.db.BaseDictionaryDataHelper;
import com.zcareze.zkyandroidweb.db.DataBaseHelper;
import com.zcareze.zkyandroidweb.db.DictionaryDao;
import com.zcareze.zkyandroidweb.devicepulgin.BlueToothHelper;
import com.zcareze.zkyandroidweb.proxy.ProxyFactoryManager;
import com.zcareze.zkyandroidweb.utils.ActivityManagerUtil;
import com.zcareze.zkyandroidweb.utils.L;
import com.zcareze.zkyandroidweb.utils.SPUtil;
import com.zcareze.zkyandroidweb.viewinterface.LoginViewImpl;

import java.util.ArrayList;
import java.util.List;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * 源代码: Lxw
 * 伊妹儿: China2021@126.com
 * 时间轴: 2016 年 11 月 10 日 17 : 28
 * <p>
 * 本类用于处理登陆界面逻辑
 */

public class LoginPresenter {

    private static LoginPresenter instance;
    private static LoginViewImpl loginViewImpl;

    private LoginPresenter() {
    }

    public static LoginPresenter getInstance(LoginViewImpl loginViewImpl) {
        if (instance == null) {
            synchronized (LoginPresenter.class) {
                if (instance == null) {
                    instance = new LoginPresenter();
                    LoginPresenter.loginViewImpl = loginViewImpl;
                }
            }

        }
        return instance;
    }


    /**
     * 登陆
     *
     * @param account
     * @param password
     */
    public void login(final String account, final String password, Subscriber<Result> subscriber) {
        Observable.create(new Observable.OnSubscribe<Result>() {
            @Override
            public void call(Subscriber<? super Result> subscriber) {
                AccountResult result = ProxyFactoryManager.getCoreServiceInstance().login(account, password, "", "", "", "", ServiceCommon.IDENTITY);
                checkLoginResult(subscriber, result, account, password);
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread()).subscribe(subscriber);
    }


    public void reLogin() {
        Observable.create(new Observable.OnSubscribe<Result>() {
            @Override
            public void call(Subscriber<? super Result> subscriber) {
                AccountResult result = ProxyFactoryManager.getCoreServiceInstance().reLogin(
                        BaseApplication.account,
                        BaseApplication.passWord,
                        (String) SPUtil.get(loginViewImpl.getContext(), AccountListInfo.CLOUDID, ""),
                        ServiceCommon.IDENTITY,
                        BaseApplication.residentId);
                checkLoginResult(subscriber, result, BaseApplication.account, BaseApplication.passWord);
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread()).subscribe(new Subscriber<Result>() {
            @Override
            public void onCompleted() {
                this.unsubscribe();
            }

            @Override
            public void onError(Throwable e) {
                Zcareze.javascript("onLoginFail", null);
            }

            @Override
            public void onNext(Result result) {
                if (result.getCode() == 1) {
                    Zcareze.javascript("onLoginSuccess", null);
                    //退出登录的时候关闭蓝牙扫描!
                } else {
                    Zcareze.javascript("onLoginFail", null);
                }
            }
        });
    }

    public void changeFamily(final String residentId) {

        Observable.create(new Observable.OnSubscribe<Result>() {
            @Override
            public void call(Subscriber<? super Result> subscriber) {
                AccountResult accountResult = ProxyFactoryManager.getCoreServiceInstance().changeAccount(residentId);
                checkLoginResult(subscriber, accountResult, BaseApplication.account, BaseApplication.passWord);
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread()).subscribe(new Subscriber<Result>() {
            @Override
            public void onCompleted() {
                this.unsubscribe();
            }

            @Override
            public void onError(Throwable e) {
                Zcareze.javascript("onChangeFail", null);
            }

            @Override
            public void onNext(Result result) {
                if (result.getCode() == 1) {
                    Zcareze.javascript("onChangeSuccess", null);
                } else {
                    Zcareze.javascript("onChangeFail", null);
                }
            }
        });


//
//        AccountResult accountResult = ProxyFactoryManager.getCoreServiceInstance().changeAccount(residentId);
//        checkLoginResult(new Subscriber<Result>() {
//            @Override
//            public void onCompleted() {
//
//            }
//
//            @Override
//            public void onError(Throwable e) {
//                Zcareze.javascript("onChangeFail", null);
//            }
//
//            @Override
//            public void onNext(Result result) {
//                if (result.getCode() == 1) {
//                    Zcareze.javascript("onChangeSuccess", null);
//                } else {
//                    Zcareze.javascript("onChangeFail", null);
//                }
//            }
//        }, accountResult, BaseApplication.account, BaseApplication.passWord);
    }


    /**
     * 检查登陆结果 多个区域等问题.
     *
     * @param subscriber
     * @param result
     * @param account
     */
    public void checkLoginResult(Subscriber<? super Result> subscriber, AccountResult result, String account, String password) {
        Integer code = result.getCode();
        if (code == 1) {//登陆成功
            SPUtil.put(loginViewImpl.getContext(), ResidentInfo.COOKVALUE, HttpClientUtil.cookieValue);
            L.d("保存cookie地址: " + HttpClientUtil.cookieValue);
            saveResidentInfo(result.getResidentList());
            saveAccountListInfo(result.one, result.getCloudId());
            //1:预留是否调用保存电话号码的方法.
            //2:预留是否调用保存

            //初始业务数据库,并添加要更新的表
            DataBaseHelper.getInstance(loginViewImpl.getContext());
            //初始化基础字典表的数据
            BaseDictionaryDataHelper.getInstance(loginViewImpl.getContext());
            List<Dictionary> dictionaries = DictionaryDao.getInstance(loginViewImpl.getContext()).queryAll();
            if (dictionaries != null && dictionaries.size() > 0) {
                List<BaseDictionaryDomain> updateTables = new ArrayList<>();
                for (Dictionary tables : dictionaries) {
                    if (tables.getIsSync() == 1)
                        updateTables.add(new BaseDictionaryDomain(tables.getTableCode(), tables.getVersion()));
                }
                BaseDictionaryParam updateParams = new BaseDictionaryParam();
                updateParams.setLists(updateTables);
                BaseDictionaryDataResult bd = ProxyFactoryManager.getBaseDictionaryServiceInstance().getDictionaryTableDataByTableCodeAndVersion(updateParams);
                DataBaseHelper.getInstance(loginViewImpl.getContext()).updateTableData(bd);//服务器的数据更新到本地!
            }
            BaseApplication.account = account;
            BaseApplication.passWord = password;
            subscriber.onNext(result);
            subscriber.onCompleted();
        } else {
            //TODO,激活多区域的.还未做处理!
            subscriber.onError(new Throwable(result.getMessage()));
        }
    }


    /**
     * 保存账户信息
     *
     * @param one
     */
    private void saveAccountListInfo(AccountList one, String cloudId) {
        SPUtil.saveAccountInfo(one, cloudId, loginViewImpl.getContext());
    }


    /**
     * 保存居民信息
     *
     * @param residentList
     */
    private void saveResidentInfo(ResidentList residentList) {
        SPUtil.saveResidentInfo(residentList, loginViewImpl.getContext());
    }
}
