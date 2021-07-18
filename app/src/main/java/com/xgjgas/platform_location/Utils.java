package com.xgjgas.platform_location;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

public class Utils extends Service {
    int locationCount = 0  ;
    private static final String TAG = "LocationService";
    AMapLocationClient mLocationClient;
    public AMapLocationClientOption mLocationOption;

    String lnglat = ""  ;

    //保存登录信息到内存中，用于“记住用户名密码”
    public static Boolean saveUserInfo(Context context, String username, String password){
        String loginInfo = username+"##"+password ;
        File file = new File(context.getFilesDir(),"info.txt") ;
        try {
            FileOutputStream fos = new FileOutputStream(file) ;
            fos.write(loginInfo.getBytes());
            fos.close();
            return true ;
        } catch (Exception e) {
            e.printStackTrace();
            return false ;
        }

    }

    //读取已经保存的用户信息
    public static Map<String ,String> readInfo(Context context){
        try {
            Map<String,String> infoMap = new HashMap<String,String>() ;
            //输入流
            //File file = new File("/data/data/com.example.locate/info.txt") ;
            File file = new File(context.getFilesDir(),"info.txt") ;
            FileInputStream fis = new FileInputStream(file) ;
            BufferedReader br = new BufferedReader(new InputStreamReader(fis)) ;
            String userinfo = br.readLine();
            String[] userinfoArr = userinfo.split("##");
            infoMap.put("username",userinfoArr[0]) ;
            infoMap.put("password",userinfoArr[1]) ;

            return infoMap ;
        } catch (Exception e) {
            e.printStackTrace();
            return null ;
        }
    }

    //调用接口方法
    public static String execute(String path,String method,String token)  {
        try{
            Log.i("execute_URL",path) ;
            Log.i("execute_Method",method) ;
            Log.i("execute_token",token) ;
            URL url = new URL(path);
            //创建HttpURLConnection对象，用于发送或者接收数据
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(5000);
            if(token != ""){
                conn.setRequestProperty("Authorization","Bearer "+token);
            }
            //获取服务器返回的状态码
            int code = conn.getResponseCode() ;
            //如果状态码为200，说明请求成功
            if(code == 200){
                InputStream is = conn.getInputStream();
                String result = Utils.readStrean(is) ;
                Log.i("请求状态Utils.Execute","请求成功，"+result) ;
                return result ;
            }else{
                return "failure" ;
            }
        }catch (Exception e){
            Log.i("方法execute","程序发生异常") ;
            e.printStackTrace();
            return "exception" ;
        }

    }


    //获取token，指定的用户名密码，SF api
    public static String getAccessToken(Context context) {
        try{
            //获取用户名密码
            Map<String, String> userinfo = Utils.readInfo(context);
            String username = userinfo.get("username") ;
            String password = userinfo.get("password") ;

            Log.i("获取token执行","*****************************") ;
            Log.i("用户名",""+username) ;
            Log.i("密码",""+password) ;
            Log.i("*****************","*****************************") ;
            //String username = "xgjapi@xgjgas.com" ;
            //String password = "xgjzlxxsf2020" ;

            String path = "https://cloud.yunpuhuaxing.com/xgjUser.logindata1.do?username="+username+"&password="+password+"&type=1";
            URL url = new URL(path);
            //创建HttpURLConnection对象，用于发送或者接收数据
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            //设置发送get请求
            conn.setRequestMethod("GET");

            //设置请求超时时间
            conn.setConnectTimeout(5000);
            //获取服务器返回的状态码
            int code = conn.getResponseCode() ;
            //如果状态码为200，说明请求成功
            if(code == 200){
                //获取服务器返回的数据，以流的形式返回
                InputStream is = conn.getInputStream();
                //使用工具类把返回的流  == 》 str
                String result = Utils.readStrean(is) ;
                //edt_info.setText(result);

                //获取登录状态信息
                String loginResult = Utils.getJsonValueByName("accessToken",result) ;
                Log.i("得到的token",""+loginResult) ;
                return loginResult ;
            }else{
                Log.i("获取token","调用接口发生错误，请求码："+conn.getResponseCode()) ;
                return "error" ;
            }
        }catch(Exception e){
            Log.i("获取token","程序发生异常"+e.getMessage()) ;
            return "error"  ;
        }

    }


    //将流信息转化为字符串并返回
    public static String readStrean(InputStream in) throws  Exception{
        ByteArrayOutputStream baos = new ByteArrayOutputStream() ;
        int len = -1 ;
        byte[] buffer = new byte[1024];
        while ((len = in.read(buffer)) != -1){
            baos.write(buffer,0,len);
        }
        String content = new String(baos.toByteArray());
        return content ;
    }

    //接口调用通用接口

    public  static String getLocationStr(AMapLocation location) {
        if (null == location) {
            return null;
        }
        StringBuffer sb = new StringBuffer();
        //errCode等于0代表定位成功，其他的为定位失败，具体的可以参照官网定位错误码说明
        if (location.getErrorCode() == 0) {
            sb.append("定位成功" + "\n");
            sb.append("定位类型: " + location.getLocationType() + "\n");
            sb.append("经    度: " + location.getLongitude() + "\n");
            sb.append("纬    度: " + location.getLatitude() + "\n");
            sb.append("精    度: " + location.getAccuracy() + "米" + "\n");
            if (location.getProvider().equalsIgnoreCase(
                    android.location.LocationManager.GPS_PROVIDER)) {
                // 以下信息只有提供者是GPS时才会有
                // 获取当前提供定位服务的卫星个数
                sb.append("星    数: "
                        + location.getSatellites() + "\n");
            }

            //逆地理信息
            sb.append("地    址: " + location.getAddress() + "\n");
            //定位完成的时间
            sb.append("定位时间: " + formatUTC(location.getTime(), "yyyy-MM-dd HH:mm:ss") + "\n");

        } else {
            //定位失败
            sb.append("定位失败" + "\n");
            sb.append("错 误 码:" + location.getErrorCode() + "\n");
            sb.append("错误信息:" + location.getErrorInfo() + "\n");
            sb.append("错误描述:" + location.getLocationDetail() + "\n");
        }
        //定位之后的回调时间
        sb.append("回调时间: " + formatUTC(System.currentTimeMillis(), "yyyy-MM-dd HH:mm:ss") + "\n");
        return sb.toString();
    }

    private static SimpleDateFormat sdf = null;
    public synchronized static String formatUTC(long l, String strPattern) {
        if (TextUtils.isEmpty(strPattern)) {
            strPattern = "yyyy-MM-dd HH:mm:ss";
        }
        if (sdf == null) {
            try {
                sdf = new SimpleDateFormat(strPattern, Locale.CHINA);
            } catch (Throwable e) {
            }
        } else {
            sdf.applyPattern(strPattern);
        }
        return sdf == null ? "NULL" : sdf.format(l);
    }

    //获取指定JSON数据中指定项的值
    public static String getJsonValueByName(String name, String jsonStr){
        try{
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(jsonStr);
            //获取name字段值
            JsonNode result = jsonNode.get(name);
            String s = result.asText();
            System.out.println(s);
            return s ;
        }catch(Exception e){
            return e.getMessage() ;
        }

    }


    //检测某个指定的服务是否正在运行
    public static boolean isServiceWork(Context mContext, String serviceName) {
        boolean isWork = false;
        ActivityManager myAM = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> myList = myAM.getRunningServices(40);
        if (myList.size() <= 0) {
            return false;
        }
        for (int i = 0; i < myList.size(); i++) {
            String mName = myList.get(i).service.getClassName().toString();
            if (mName.equals(serviceName)) {
                isWork = true;
                break;
            }
        }
        return isWork;
    }

    //判断定位权限是否开启
    public static  boolean GPSisOPen(Context context) {
        if (context==null){
            return true;
        }
        int locationMode = 0;
        String locationProviders;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
                return false;
            }
            return locationMode != Settings.Secure.LOCATION_MODE_OFF;
        } else {
            locationProviders = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
            return !TextUtils.isEmpty(locationProviders);
        }
    }


    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE" };
    //判断程序内存读写的权限是否开启
    public static boolean readDisk(Context context){

        int permission = ActivityCompat.checkSelfPermission(context,
                "android.permission.WRITE_EXTERNAL_STORAGE");
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // 没有写的权限，去申请写的权限，会弹出对话框
            return false ;
        }else{
            return true ;
        }
    }

    //获取手机型号
    public static String getSystemModel() {
        return android.os.Build.MODEL;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
