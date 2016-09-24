package com.pxmao.king.tamperweinxindb;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public List<DataBean> list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


    }

    @Override
    protected void onStart() {
        super.onStart();
        new Thread() {
            @Override
            public void run() {
                isThikerExist();
                String currentDBPath = getCurrentDBPath();//获取微信的数据库路径
                amendFilePermission(currentDBPath);//修改数据的权限
                String password = calculatePsw();//打开数据的密码
                //File databaseFile = getDatabasePath(currentDBPath);

                String data = getData();//读取sd卡配置文件，json字符串
                parserFile(data);// 解析json并封装
                //File databaseFile = getDatabasePath("/data/data/com.tencent.mm/MicroMsg/825c0f0f075dc27d407352dbdaff09cc/EnMicroMsg.db");
                updateWeiXinDB(new File(getCurrentDBPath()), password);//修改微信数据库

            }
        }.start();

    }


    /**
     * 更改微信数据库好友的备注信息
     *
     * @param databaseFile
     */
    public void updateWeiXinDB(File databaseFile, String password) {

        SQLiteDatabase.loadLibs(this);
        SQLiteDatabaseHook hook = new SQLiteDatabaseHook(){
            public void preKey(SQLiteDatabase database){
            }
            public void postKey(SQLiteDatabase database){
                //database.rawExecSQL("PRAGMA cipher_migrate;");//数据库损坏原因：使用3.X的sqlcipher操作微信基于2.X的数据库。
                database.rawExecSQL("PRAGMA cipher_use_hmac = OFF;");
            }
        };

        try {
            SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(databaseFile, password, null, hook);
            int DBversion = db.getVersion();
            Log.d("MainActivity", " DBversion: "+ DBversion);


            //遍历list数据，拿到数据就进行修改.根据用户的昵称来改备注
            for (int i = 0; i < list.size(); i++) {
                String nickname = list.get(i).getNickname();
                String remark = list.get(i).getRemark();
                db.execSQL("update rcontact set conRemark = '" + remark + "' where nickname = '" + nickname + "'");//修改语句
                // update rcontact set conRemark ='weixin2' where nickname = 'J.Yong'
            }


            //查询remark表，看修改了没有
            Cursor cursor = db.query("rcontact", null, null, null, null, null, null);
            while (cursor.moveToNext()) {
                String mConRemark = cursor.getString(cursor.getColumnIndex("conRemark"));
                String mNickname = cursor.getString(cursor.getColumnIndex("nickname"));
                Log.d("MainActivity", " 备注: " + mConRemark + "   昵称: " + mNickname);
            }

            cursor.close();
            db.close();
        } catch (Exception e) {
            Log.e("SQL", e.getMessage());
        }


    }


    /**
     * 获取当前登录账户的微信数据库的路径
     *
     * @return wechant database path
     */
    public String getCurrentDBPath() {
        String dbPath = "";
        try {
            amendFilePermission("/data/data/com.tencent.mm/MicroMsg");

            String uinCode = obtainUinCode();

            String encryptionPath = MD5Utils.get32MD5Value("mm" + uinCode);       //wechat encrypt method :md5(mm + wechat uin code )

            amendFilePermission("/data/data/com.tencent.mm/MicroMsg" + File.separator + encryptionPath);

            dbPath = "/data/data/com.tencent.mm/MicroMsg" + File.separator + encryptionPath + File.separator + "EnMicroMsg.db";

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return dbPath;
    }


    /**
     * 根据传入的文件路径，修改文件权限为可读，可写，可执行
     *
     * @param filePath 文件的路径
     */
    public void amendFilePermission(String filePath) {
        String cmd = " chmod 777 " + filePath;
        Process process = null;
        DataOutputStream os = null;
        //执行命令行语句
        try {
            process = Runtime.getRuntime().exec("su");

            os = new DataOutputStream(process.getOutputStream());

            os.writeBytes(cmd + " \n");
            os.writeBytes(" exit \n");
            os.flush();

            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (os != null) {
                    os.close();
                }
                process.destroy();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    //获取微信的UIN码
    public String obtainUinCode() {
        String value = null;
        InputStream inputStream = null;
        try {
            String uinFile = "/data/data/com.tencent.mm/shared_prefs/system_config_prefs.xml";

            amendFilePermission(uinFile);

            File file = new File(uinFile);

            inputStream = new FileInputStream(file);
            //获取工厂对象，以及通过DOM工厂对象获取DOMBuilder对象
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            //解析XML输入流，得到Document对象，表示一个XML文档
            Document document = builder.parse(inputStream);
            //获得文档中的次以及节点
            Element element = document.getDocumentElement();
            NodeList personNodes = element.getElementsByTagName("int");
            for (int i = 0; i < personNodes.getLength(); i++) {
                Element personElement = (Element) personNodes.item(i);
                value = personElement.getAttribute("value");
                Log.d("MainActivity", "UIN的值是:" + value);
                if (value.equals("0")) {
                    Log.d("MainActivity", "UIN重置了，vule是0");
                }
//                System.out.println(value);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (inputStream != null)
                    inputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return value;
    }

    //获取打开数据密码
    public String calculatePsw() {
        String password = "";

        String imei = obtainIMEICode();

        String uinCode = obtainUinCode();

        String encryptionStr = "";

        if (!TextUtils.isEmpty(imei) && !TextUtils.isEmpty(uinCode)) {
            try {
                encryptionStr = MD5Utils.get32MD5Value(imei + uinCode);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }

        if (!TextUtils.isEmpty(encryptionStr)) {
            password = encryptionStr.substring(0, 7);
        }
        Log.d(TAG, "数据库密码：" + password);
        return password;
    }

    public String obtainIMEICode() {
        String imei;
        imei = ((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId();
        return imei;
    }

    /**
     * 判断微信 Thinker 文件夹是否存在，存在则删除内部文件在修改其权限为不可读，不可写，不可执行状态
     */
    public void isThikerExist() {
        String filePath = "/data/data/com.tencent.mm/tinker";
        amendFilePermission(filePath);
        File thinkerFile = new File(filePath);

        if (thinkerFile.exists()) {

//            Log.d("TAG", "isThikerExist: thinker " + true);

            if (thinkerFile.isDirectory()) {
//                Log.d("TAG", "isThikerExist: Thinker file " + true);
                File files[] = thinkerFile.listFiles();
                if (files == null) {
//                    Log.d("TAG", "isThikerExist: is null");
                } else {
                    for (int i = 0; i < files.length; i++) {
                        amendFilePermission(files[i].getPath());
                        if (files[i].isDirectory()) {
                            executeCMD(" rm " + files[i].getPath());        // 删除内部文件
                        } else {
                            executeCMD(" rmdir " + files[i].getPath());     // 删除内部文件夹
                        }
                    }
                }
            }
            executeCMD(" chmod 000 " + filePath);       // 执行修改Thinker文件夹权限为不可读，不可写，不可执行
        } else {
//            Log.d("TAG", "isThikerExist: thinker " + false);
        }
    }


    private void executeCMD(String command) {
        Process process = null;
        DataOutputStream os = null;

        try {
            String cmd = command;

            process = Runtime.getRuntime().exec("su");

            os = new DataOutputStream(process.getOutputStream());

            os.writeBytes(cmd + " \n");
            os.writeBytes(" exit \n");
            os.flush();

            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (os != null) {
                    os.close();
                }
                process.destroy();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    //读取文件
    public String getData() {
        //读取txt文件
        InputStream in = null;
        try {
            in = new FileInputStream("/sdcard/backups/remark1.txt");

            byte[] b = new byte[1024];
            int length = 0;
            StringBuffer sb = new StringBuffer();
            while ((length = in.read(b)) != -1) {
                //以前在这出现乱码问题，后来在这设置了编码格式
                sb.append(new String(b, 0, length, "UTF-8"));
            }
            return sb.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }

    private void parserFile(String data) {
        try {
            list = new ArrayList<DataBean>();
            JSONObject jsonObject = new JSONObject(data);//解析json
            JSONArray data1 = (JSONArray) jsonObject.get("data");
            Log.d(TAG, "解析出来的json: " + data1);

            for (int i = 0; i < data1.length(); i++) {
                DataBean dataBaen = new DataBean();
                JSONObject listdata = (JSONObject) data1.get(i);
                // Log.d(TAG,"listdata:"+listdata);
                String remark = (String) listdata.get("remark");
                String nickname = (String) listdata.get("nickname");
                //封装到bean
                dataBaen.setNickname(nickname);
                dataBaen.setRemark(remark);
                list.add(dataBaen);
                Log.d(TAG, "备注: " + remark + "   昵称: " + nickname);

            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

}
