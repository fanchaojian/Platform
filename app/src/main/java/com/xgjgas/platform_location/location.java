package com.xgjgas.platform_location;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.maps.model.Text;

import java.text.SimpleDateFormat;
import java.util.Date;

public class location extends Activity {
    public static final String RECEIVER_ACTION = "location_in_background";
    MapView mMapView = null;
    AMap aMap;
    private TextView tvResult;  //locateResult
    MyLocationStyle myLocationStyle;
    String userName = "" ;
    Object obj = new Object() ;   //同步锁

    //一次定位
    AMapLocationClient mLocationClient;
    public AMapLocationClientOption mLocationOption;
    String lnglat = "" ;   //签到打卡和签退打卡时定位标记，签到为“1.1,1.1” ,签退时为“2.2,2.2”
    String flag = "1" ;   //签到打卡和签退打卡的标记，签退为2

    @SuppressLint("WrongConstant")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);

        checkMyPermission() ;

        //监听按钮点击事件
        Button btn_end = (Button)findViewById(R.id.endWork);
        ImageView img_refresh = (ImageView)findViewById(R.id.refresh) ;

        btn_end.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog alertDialog = new AlertDialog.Builder(location.this)
                        .setTitle("考勤打卡")
                        .setMessage("请您确认签退！")
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                //结束定位、考勤签退
                                /*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++ */
                                signout();

                                Log.i("Location活动","结束工作，endWork()方法执行") ;
                                //判断定位服务是否正在运行中
                                boolean isRunning = Utils.isServiceWork(location.this, "com.xgjgas.platform_location.LocationService");
                                Log.i("定位服务运行状态",""+isRunning) ;
                                if(isRunning){
                                    Intent intent = new Intent(location.this, LocationService.class);
                                    stopService(intent);// 关闭服务
                                }
                                //Toast.makeText(this,"定位服务运行状态："+isRunning,2).show();

                                //销毁闹钟
                                Intent intent = new Intent(location.this, LocationService.class);
                                PendingIntent pendSender = PendingIntent.getService(location.this, 0, intent, 0);
                                AlarmManager am = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
                                am.cancel(pendSender);

                                Log.i("闹钟","已经执行了销毁闹钟cancel()方法。。。") ;
                                /*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++ */
                            }
                        })
                        .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                return;
                            }
                        }).create();
                alertDialog.show();
            }
        });

        //刷新按钮监听事件
        img_refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //开始定位
                tvResult.setText("正在定位...");
                //Toast.makeText(location.this,"正在定位...",2).show();
                //启动定位服务
                Log.i("开始定位：","定位任务开始")  ;
                startAlarm() ;
            }
        });


        //回显定位结果框
        tvResult = (TextView) findViewById(R.id.locateResult);

        //注册广播接收器
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(RECEIVER_ACTION);
        registerReceiver(locationChangeBroadcastReceiver, intentFilter);


        userName = this.getIntent().getStringExtra("UserName").toString();
        Log.i("location用户名：",userName) ;

        tvResult.setText("请点击【开始工作】按钮启动定位\n或者点击右上角【刷新】图标进行定位。");
        //Toast.makeText(location.this,"正在定位...",2).show();


    }



    //按钮“开始工作”
    @SuppressLint("WrongConstant")
    public void startWork(View v){
        //首先进行售后打卡
        signin() ;

        tvResult.setText("正在定位...");
        //Toast.makeText(location.this,"正在定位...",2).show();
        //启动定位服务
        Log.i("开始定位：","定位任务开始")  ;
        startAlarm() ;
    }


    @Override
    protected void onDestroy() {
        Log.i("Service状态","执行了onDestroy方法") ;
        if (locationChangeBroadcastReceiver != null){
            unregisterReceiver(locationChangeBroadcastReceiver); }

        super.onDestroy();


    }

    //售后考勤 --- 签到
    @SuppressLint("WrongConstant")
    public void signin(){
        doSaveLocation("sign") ;
        Log.i("考勤签到","经纬度："+lnglat) ;

         synchronized (obj){
            Log.i("$$$$$$$$$$$$$$$$$$售后考勤","签到方法执行。。") ;
            Toast.makeText(getApplicationContext(),"请求中。。。",5).show();
            //是否有车
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Switch s_hascar = (Switch)findViewById(R.id.hasCar) ;
                    boolean checked = s_hascar.isChecked();
                    String hasCar = (checked==true) ? "有车":"无车" ;
                    Log.i("是否有车2",""+checked) ;

                    String path = "https://ap6.salesforce.com/services/apexrest/PlatformAttendanceRest/attendance?type=signin&name="+userName+"&hasCar="+hasCar ;
                    Log.i("考勤签到地址",""+path) ;

                    String accessToken = Utils.getAccessToken(getApplicationContext());
                    Log.i("考勤签到,token",accessToken);
                    String signResult = Utils.execute(path, "GET", accessToken);
                    if(signResult.contains("success")){
                        doSaveLocation("sign") ;

                        Looper.prepare() ;
                        AlertDialog alertDialog2 = new AlertDialog.Builder(location.this)
                                .setTitle("考勤提示")
                                .setMessage("签到成功！")
                                .setIcon(R.mipmap.success)
                                .setPositiveButton("确定", new DialogInterface.OnClickListener() {//添加"Yes"按钮
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        return ;
                                    }
                                })
                                .create();
                        alertDialog2.show();
                        Looper.loop();

                    }else if(signResult.contains("repeat")){

                        Looper.prepare() ;
                        AlertDialog alertDialog2 = new AlertDialog.Builder(location.this)
                                .setTitle("考勤提示")
                                .setMessage("签到异常或签到重复！")
                                .setIcon(R.mipmap.warning)
                                .setPositiveButton("确定", new DialogInterface.OnClickListener() {//添加"Yes"按钮
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        return ;
                                    }
                                })
                                .create();
                        alertDialog2.show();
                        Looper.loop();
                    }else{
                        Looper.prepare() ;
                        AlertDialog alertDialog2 = new AlertDialog.Builder(location.this)
                                .setTitle("考勤提示")
                                .setMessage("签到接口程序异常！")
                                .setIcon(R.mipmap.error)
                                .setPositiveButton("确定", new DialogInterface.OnClickListener() {//添加"Yes"按钮
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        return ;
                                    }
                                })
                                .create();
                        alertDialog2.show();
                        Looper.loop();
                    }

                }
            }).start();
        }



    }

    //售后考勤 --- 签退
    @SuppressLint("WrongConstant")
    public void signout(){
        doSaveLocation("signout") ;
        synchronized (obj){
            tvResult.setText("您取消了定位。");
            Toast.makeText(getApplicationContext(),"请求中。。。",5).show(); ;
            //是否有车
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Switch s_hascar = (Switch)findViewById(R.id.hasCar) ;
                    boolean checked = s_hascar.isChecked();
                    String path = "https://ap6.salesforce.com/services/apexrest/PlatformAttendanceRest/attendance?type=signout&name="+userName+"&hasCar=" ;
                    Log.i("签退地址",""+path) ;

                    String accessToken = Utils.getAccessToken(getApplicationContext());
                    String signResult = Utils.execute(path, "GET", accessToken);
                    if(signResult.contains("success")){

                        Looper.prepare() ;
                        AlertDialog alertDialog2 = new AlertDialog.Builder(location.this)
                                .setTitle("考勤提示")
                                .setMessage("签退成功！")
                                .setIcon(R.mipmap.success)
                                .setPositiveButton("确定", new DialogInterface.OnClickListener() {//添加"Yes"按钮
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        return ;
                                    }
                                })
                                .create();
                        alertDialog2.show();
                        Looper.loop();
                    }else if(signResult.contains("repeat")){
                        Looper.prepare() ;
                        AlertDialog alertDialog2 = new AlertDialog.Builder(location.this)
                                .setTitle("考勤提示")
                                .setMessage("签退重复！")
                                .setIcon(R.mipmap.warning)
                                .setPositiveButton("确定", new DialogInterface.OnClickListener() {//添加"Yes"按钮
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        return ;
                                    }
                                })
                                .create();
                        alertDialog2.show();
                        Looper.loop();
                    }else{
                        Looper.prepare() ;
                        AlertDialog alertDialog2 = new AlertDialog.Builder(location.this)
                                .setTitle("考勤提示")
                                .setMessage("签退接口程序异常！")
                                .setIcon(R.mipmap.error)
                                .setPositiveButton("确定", new DialogInterface.OnClickListener() {//添加"Yes"按钮
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        return ;
                                    }
                                })
                                .create();
                        alertDialog2.show();
                        Looper.loop();
                    }

                }
            }).start();
        }

    }

    //闹钟，启动闹钟
    public void startAlarm(){
        Intent intent = new Intent(location.this, LocationService.class);
        PendingIntent pendSender = PendingIntent.getService(location.this, 0, intent, 0);
        AlarmManager am = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        am.cancel(pendSender);
        am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), 60*1000, pendSender);
    }


    //接收广播通知，回显数据
    private BroadcastReceiver locationChangeBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(RECEIVER_ACTION)) {
                String locationResult = intent.getStringExtra("result");
                if (null != locationResult && !locationResult.trim().equals("")) {
                    tvResult.setText(locationResult);
                }
            }
        }
    };



    /*public  void getPosition() {
        Log.i("获取第一次定位","getPosition") ;
        //初始化定位
        if(null == mLocationClient){
            mLocationClient = new AMapLocationClient(this.getApplicationContext());
        }

        // 初始化AMapLocationClientOption对象
        mLocationOption = new AMapLocationClientOption();
        // 设置定位模式为AMapLocationMode.Hight_Accuracy，高精度模式Hight_Accuracy,低功耗模式Battery_Saving。
        mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Battery_Saving);
        //设置定位间隔,单位毫秒,2分钟
        //mLocationOption.setInterval(5*60*1000);
        // 获取一次定位结果： //该方法默认为false。
        mLocationOption.setOnceLocation(true);
        //mLocationOption.setOnceLocationLatest(false);
        //给定位客户端对象设置定位参数
        mLocationClient.setLocationOption(mLocationOption);
        // 设置定位回调监听
        mLocationClient.setLocationListener(mLocationListener);
        // 启动定位
        mLocationClient.startLocation();

    }



    // 声明定位回调监听器
    public AMapLocationListener mLocationListener = new AMapLocationListener() {
        @Override
        public void onLocationChanged(AMapLocation amapLocation) {
            Log.i("定位监听器启动","定位监听器启动") ;
            lnglat = amapLocation.getLongitude()+","+amapLocation.getLatitude() ;
            Log.i("当前位置经纬度",""+lnglat) ;
            Log.i("用户名",userName+"") ;
            saveLocation();

        }
    }; */

    public void doSaveLocation(String signType){
        if(signType == "sign"){
            this.lnglat = "1.1,1.1" ;
            this.flag = "1" ;
        }else if(signType == "signout") {
            this.lnglat = "2.2,2.2" ;
            this.flag = "2" ;
        }
        saveLocation() ;
    }
    public void saveLocation(){
        //调用接口获取用户基本信息、忙闲状态、维修通气任务总和
        /*
         * 功能:通过用户名获取员工姓名，忙闲状态，未完成的维修、通气任务总和
         * 返回结果，如：凡朝剑 | wait/busy | 4
         * */
        Log.i("保存位置信息","第一次保存定位位置") ;
        new Thread(new Runnable() {
            @SuppressLint({"SimpleDateFormat", "WrongConstant"})
            @Override
            public void run() {
                //获取token
                String token = Utils.getAccessToken(getApplicationContext())  ;
                Log.i("获取到的token",token);
                //判断token是否正确
                if(token != "error" && token != "false"){
                    //获取登录用户信息
                    String path = "https://xgjpro.secure.force.com/services/apexrest/PlatformUserWorkInfoRest/WorkingInfo?username="+userName ;
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

                        String savePath = "https://cloud.yunpuhuaxing.com/xgjUser.actioTrackSave.do?name="+name+"&userName="+userName+"&latLng="+lnglat+"&taskCount="+resultArr[2]+"&flag="+flag ;
                        Log.i("保存位置地址",savePath) ;
                        String savePosResult = Utils.execute(savePath, "GET","");
                        Log.i("保存位置",savePosResult) ;

                    }else{
                        Log.i("获取用户信息","调用接口错误或程序产生异常") ;
                    }
                }else{
                    Log.i("保存定位信息","获取token错误") ;
                }

            }
        }).start();
    }



    //应用手机相关权限判断
    public void checkMyPermission() {
        boolean gpsIsOpen = Utils.GPSisOPen(this.getApplicationContext());
        boolean canReadDisk = Utils.readDisk(this.getApplicationContext());
        String phoneType = Utils.getSystemModel()  ;

        TextView gps_TV = (TextView)findViewById(R.id.gps) ;
        TextView disk_TV = (TextView)findViewById(R.id.disk) ;
        TextView phone_TV = (TextView)findViewById(R.id.phone) ;

        phone_TV.setText("手机型号："+phoneType);
        if(gpsIsOpen){
            gps_TV.setText("定位权限：已开启");
        }else{
            gps_TV.setText("定位权限：请检查");
        }

        if(canReadDisk){
            disk_TV.setText("内存读写：已开启");
        }else{
            disk_TV.setText("内存读写：未开启");
        }
    }

}
