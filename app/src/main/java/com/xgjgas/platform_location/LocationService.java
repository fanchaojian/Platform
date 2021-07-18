package com.xgjgas.platform_location;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.model.LatLng;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class LocationService extends Service {
    PowerManager.WakeLock mWakeLock;// 电源锁
    int locationCount = 0  ;
    private static final String TAG = "LocationService";
    AMapLocationClient mLocationClient;
    public AMapLocationClientOption mLocationOption;
    public static String userName = "" ;
    public static String lnglat = "" ;
    public int status = 1 ;               //设置正在定位的状态，如果定位停止或者service销毁，此状态为 2


    public List<LatLng> points = new ArrayList<>();

    public LocationService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        //throw new UnsupportedOperationException("Not yet implemented");

        return new MyBinder();  //返回MyBinder服务对象
    }
    public class MyBinder extends Binder {  //创建MyBinder内部类并获取服务对象与Service状态
        public LocationService getService() {  //创建获取Service的方法
            return LocationService.this;       //返回当前Service类
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        //CPU锁屏唤醒
        acquireWakeLock() ;

        getPosition();
        //获取登录用户名
        Map<String, String> infoMap = Utils.readInfo(LocationService.this);
        if(infoMap != null){
            userName = infoMap.get("username");
            Log.i("登录用户",userName) ;
        }
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // TODO Auto-generated method stub
        Intent activityIntent = new Intent(this, location.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplication(), 0, activityIntent, 0);
        Notification notification = new Notification.Builder(getApplication()).setAutoCancel(true).
                setSmallIcon(R.drawable.logo_desktop).setTicker("前台Service启动").setContentTitle("前台Service运行中").
                setContentText("正在定位您的位置，请勿关闭！").setWhen(System.currentTimeMillis()).setContentIntent(pendingIntent).build();
        startForeground(1, notification);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i("定位Service","onDestroy方法执行了") ;
        super.onDestroy();
        //记录最后一次定位
        saveLocation();

        if (null != mLocationClient) {
            mLocationClient.disableBackgroundLocation(true);
            mLocationClient.stopLocation();
            mLocationClient.unRegisterLocationListener(mLocationListener);
            mLocationClient.onDestroy();
            mLocationClient = null;
            mLocationClient = null;

            //结束定位
            stopLocation();
        }
    }

    public void getPosition() {
        Log.i("开始定位","开始定位") ;
        stopLocation();
        //初始化定位
        if(null == mLocationClient){
            mLocationClient = new AMapLocationClient(this.getApplicationContext());
        }

        // 初始化AMapLocationClientOption对象
        mLocationOption = new AMapLocationClientOption();
        // 设置定位模式为AMapLocationMode.Hight_Accuracy，高精度模式Hight_Accuracy,低功耗模式Battery_Saving。
        mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Battery_Saving);
        //设置定位间隔,单位毫秒,2分钟
        mLocationOption.setInterval(2*60*1000);
        // 获取一次定位结果： //该方法默认为false。
        mLocationOption.setOnceLocation(false);
        mLocationOption.setOnceLocationLatest(false);
        //给定位客户端对象设置定位参数
        mLocationClient.setLocationOption(mLocationOption);
        // 设置定位回调监听
        mLocationClient.setLocationListener(mLocationListener);
        // 启动定位
        mLocationClient.startLocation();

    }



    public void stopLocation(){
        Log.i("结束定位","结束定位stopLocation()方法执行了") ;
        if(null != mLocationClient){
            mLocationClient.stopLocation();
        }

        //判断LocationService是否正在运行中
        boolean isRunning = Utils.isServiceWork(getApplicationContext(), "com.xgjgas.platform_location.LocationService");
        //记录最后一次定位
        status = isRunning ? 1:2 ;
        saveLocation();
    }

    // 声明定位回调监听器
    public AMapLocationListener mLocationListener = new AMapLocationListener() {
        @Override
        public void onLocationChanged(AMapLocation amapLocation) {
            lnglat = amapLocation.getLongitude()+","+amapLocation.getLatitude() ;
            Log.i("定位回调监听","定位回调监听") ;
            boolean isRunning = Utils.isServiceWork(getApplicationContext(), "com.xgjgas.platform_location.LocationService");
            if(isRunning){
                sendLocationBroadcast(amapLocation) ;
            }
            status = isRunning ? 1:2 ;
            Log.i("@@@@@@@@@@@@@结束定位标识",""+status) ;

            //保存定位信息到数据库
            //=============================================
            if(userName != ""){
                //通过用户名获取姓名，忙闲状态，维修、通气任务总数
                //如果已经正确定位（定位成功，不成功经纬度将会是“0.0,0.0”）,没有正确定位则继续定位
                saveLocation() ;
            }

        }
    };

    //发送广播消息
    private void sendLocationBroadcast(AMapLocation aMapLocation) {
        //记录信息并发送广播
        locationCount++;
        long callBackTime = System.currentTimeMillis();
        StringBuffer sb = new StringBuffer();
        sb.append("定位状态: 持续定位中\n") ;
        sb.append("定位完成: 第" + locationCount + "次\n");
        if (null == aMapLocation) {
            sb.append("定位失败：location is null!!!!!!!");
        } else {
            sb.append(Utils.getLocationStr(aMapLocation));
        }

        Log.i("广播结果：",sb.toString()) ;
        Intent mIntent = new Intent(location.RECEIVER_ACTION);
        mIntent.putExtra("result", sb.toString());

        //发送广播
        sendBroadcast(mIntent);
    }

    public void saveLocation(){
        Log.i("保存位置","保存位置") ;
        //调用接口获取用户基本信息、忙闲状态、维修通气任务总和
        /*
        * 功能:通过用户名获取员工姓名，忙闲状态，未完成的维修、通气任务总和
        * 返回结果，如：凡朝剑 # wait/busy # 4
        * */
        new Thread(new Runnable() {
            @SuppressLint({"SimpleDateFormat", "WrongConstant"})
            @Override
            public void run() {
                //获取token
                String token = Utils.getAccessToken(getApplicationContext())  ;
                Log.i("获取到的token",token);
                //判断token是否正确
                if(token != "error" && token != "false"){
                    //获取登录用户名
                    String path = "https://xgjpro.secure.force.com/services/apexrest/PlatformUserWorkInfoRest/WorkingInfo?username="+LocationService.userName ;
                    String result = Utils.execute(path, "GET",token);

                    if(result != "failure" && result != "exception" && result.contains("error") != true){
                        result = result.substring(1,result.length()-1) ;
                        Log.i("请求SF结果",result) ;
                        //处理返回结果数据
                        String[] resultArr = result.split("#");  //如：凡朝剑 | wait | 10

                        //调用接口保存位置

                        String name = resultArr[0]+"-"+resultArr[1] ;
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        String dataStr = sdf.format(new Date());

                        //String savePath = "https://2019dev-xgjpro.cs114.force.com//services/apexrest/PlatformUserLocateRest/saveLocation?name="+name+"&userName="+userName+"&latLng="+lnglat+"&taskCount="+resultArr[2]+"&createDate="+dataStr+"&status="+status ;
                        String savePath = "https://cloud.yunpuhuaxing.com/xgjUser.actioTrackSave.do?name="+name+"&userName="+userName+"&latLng="+lnglat+"&taskCount="+resultArr[2]+"&flag="+status ;
                        Log.i("保存位置地址",savePath) ;
                        String savePosResult = Utils.execute(savePath, "GET","");
                        Log.i("保存位置",savePosResult) ;

                    }else{
                        Log.i("获取用户信息","调用接口错误或程序产生异常") ;
                        Looper.prepare();
                        Toast.makeText(LocationService.this,"获取用户信息错误",5).show(); ;
                        Looper.loop();
                    }
                }else{
                    Looper.prepare();
                    Toast.makeText(LocationService.this,"获取token错误",5).show();
                    Looper.loop();
                }

            }
        }).start();
    }

    //锁屏唤醒service
    @SuppressLint("InvalidWakeLockTag")
    private void acquireWakeLock(){
        if (null == mWakeLock) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK
                    | PowerManager.ON_AFTER_RELEASE, "myService");
            if (null != mWakeLock) {
                mWakeLock.acquire();
            }
        }
    }

    //结束锁屏唤醒CPU
    private void releaseWakeLock() {
        if (null != mWakeLock) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

}
