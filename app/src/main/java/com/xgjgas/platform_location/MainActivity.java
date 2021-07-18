package com.xgjgas.platform_location;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class MainActivity extends Activity {

    private TextView edt_username ;
    private TextView edi_password ;
    private CheckBox chk_remember ;
    private Button btn_login ;
    //private TextView edt_info ;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //获取用户名密码输入框的值
        edt_username = (TextView)findViewById(R.id.username) ;
        edi_password = (TextView)findViewById(R.id.password) ;
        chk_remember = (CheckBox)findViewById(R.id.rem_pwd) ;
        btn_login = (Button)findViewById(R.id.btn_login) ;
        //edt_info = (TextView) findViewById(R.id.edt_info) ;

        //回显已经保存到内存中的登录信息
        Map<String, String> infoMap = Utils.readInfo(MainActivity.this);
        if(infoMap != null){
            edt_username.setText(infoMap.get("username"));
            edi_password.setText(infoMap.get("password"));

            //自动登录
            //btn_login.performClick();
        }

    }

    //登录
    @SuppressLint("WrongConstant")
    public void login(View v) throws IOException {
        String username = edt_username.getText().toString().trim() ;
        String password = edi_password.getText().toString().trim() ;

        //判断用户名和密码是否为空
        if(TextUtils.isEmpty(username) || TextUtils.isEmpty(password)){
            Toast.makeText(MainActivity.this,"用户名或密码不能为空",1).show();
        }else{
            //判断“记住密码”单选框是否选择
            if(chk_remember.isChecked()){
                //保存用户名密码到内存中————data/data/项目包（com.example.locate）/info.txt 文件中
                Boolean result = Utils.saveUserInfo(MainActivity.this,username,password) ;
                if(result){
                    Log.i("保存密码：","保存用户名密码成功。") ;
                }else{
                    Log.i("保存信息：","保存用户名密码失败") ;
                }
            }
            //发送登录请求
            try {
                Log.i("发送登录请求：","");
                showCallResult() ;
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }


    //调用接口的方法
    @SuppressLint("WrongConstant")
    public void showCallResult() throws  Exception{
        Log.i("开始调用登录接口","") ;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    //String path = "http://fanchaojian.vicp.io/restful2/MyTest/getPerson.action" ;
                    String username = edt_username.getText().toString().trim() ;
                    String password = edi_password.getText().toString().trim() ;
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
                        String loginResult = Utils.getJsonValueByName("condition",result) ;

                        if(loginResult.trim() == "0"){
                            //跳转到定位activity
                            Intent intent = new Intent() ;
                            Bundle bundle = new Bundle() ;
                            bundle.putString("UserName", edt_username.getText().toString().trim()) ;
                            intent.putExtras(bundle) ;
                            intent.setClass(MainActivity.this,location.class) ;

                            startActivity(intent);
                        }else{
                            Looper.prepare();
                            Toast.makeText(MainActivity.this,"用户名或密码错误！",1).show();
                            Looper.loop();
                        }

                    }else{
                        Looper.prepare();
                        Toast.makeText(MainActivity.this,"请求失败",1).show();
                        Looper.loop();
                    }
                }catch(Exception e){
                    e.printStackTrace(); ;
                }

            }
        }).start();

    }
}