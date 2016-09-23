package com.pxmao.king.tamperweinxindb;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

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
                updateWeiXinDB(new File(currentDBPath), password);//修改微信数据库

            }
        }.start();

    }


    /**
     * 更改微信数据库好友的备注信息
     *
     * @param databaseFile
     */
    public void updateWeiXinDB(File databaseFile, String password) {
        String remark = "911";
        String nickname = "船长";

        SQLiteDatabase.loadLibs(this);

        SQLiteDatabaseHook hook = new SQLiteDatabaseHook() {
            @Override
            public void preKey(SQLiteDatabase sqLiteDatabase) {
            }

            @Override
            public void postKey(SQLiteDatabase sqLiteDatabase) {
                //database.rawExecSQL("PRAGMA cipher_migrate")这句最为关键，原因如下：
                //现在SQLCipher for Android已经是3.X版本了，而微信居然还停留在2.X时代，所以这句话是为了能够用3.X的开源库兼容2.X的加密解密方法，如果不加这句话，是无法对数据库进行解密的。
                sqLiteDatabase.rawExecSQL("PRAGMA cipher_migrate ; ");
            }
        };

        SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(databaseFile, password, null, hook);//打开数据库，获得数据库对象
        database.execSQL("update rcontact set conRemark = '" + remark + "' where nickname = '" + nickname + "'");//修改语句


        //查询remark表，看修改了没有
        net.sqlcipher.Cursor cursor = database.query("rcontact", null, null, null, null, null, null, null);
        while (cursor.moveToNext()) {
            String mConRemark = cursor.getString(cursor.getColumnIndex("conRemark"));
            String mNickname = cursor.getString(cursor.getColumnIndex("nickname"));
            Log.d(TAG, " 备注: " + mConRemark + "   昵称: " + mNickname);
        }
        cursor.close();
        database.close();
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
}
