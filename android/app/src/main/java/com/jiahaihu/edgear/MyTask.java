package com.jiahaihu.edgear;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.CountDownLatch;

public class Mytask extends Thread{
    private Thread t;
    private String threadName = "test";
    private String serverIP = "192.168.31.67";
    private int serverPort = 8999;
    private String PhotoPath="/Download/images";
    private int currentIndex;
    private String myUUID;
    private String nowTime;
    private CountDownLatch latch;
    private int certainIndex = 0;

    public Mytask(int index, String uuid) {
        currentIndex = index;
        myUUID = uuid;
    }

    public Mytask(String name, String IP, int port, String path, int index, String uuid, String now, CountDownLatch cdl) {
        threadName = name;
        serverIP = IP;
        serverPort = port;
        PhotoPath = path;
        currentIndex = index;
        myUUID = uuid;
        nowTime = now;
        latch = cdl;
    }

    public Mytask(){

    }

    @Override
    public void run() {

        Log.e("INFO: ", currentIndex + " : run: a task is running");

        try {
            Socket socket = new Socket(serverIP, serverPort);
            // socket.setKeepAlive(true);


            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            String fileDirPath = Environment.getExternalStorageDirectory().toString()  + PhotoPath;
            File fileDir = new File(fileDirPath);

            File[] tmpList = fileDir.listFiles();
            assert tmpList != null;
            Log.i("INFO", "run: " + tmpList.length);

//            for (int i = 0; i< Objects.requireNonNull(tmpList).length; i++){
//                if(tmpList[i].isFile()) {
//                    Log.d("INFO", "run: " + tmpList[i].toString());
//                }
//            }

            // first send the size of img
            int fileLength = (int) tmpList[certainIndex].length();

            byte[] fileLengthByte = toHH(fileLength);

            Log.w("warning", "run: " + fileLength );
            Log.w("warning", "run: " + Arrays.toString(fileLengthByte));
            out.write(fileLengthByte);

            Log.e("stage", "cur-"+currentIndex + " run: 1");
            FileInputStream in =  new FileInputStream(tmpList[certainIndex].toString());

            Log.e("stage", "cur-"+currentIndex+ " run: 1.5, the next is read" );
            byte[] buf = new byte[fileLength];
            int len = in.read(buf);


            long timeStampBegin = System.currentTimeMillis();
            String sndTimeBegin = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS").format(new Date());

            // while(true) {

            Log.e("stage", "cur-" + currentIndex + " run: 1.7, the next is write, the len: " + len);
//                if(len == -1){
//                    break;
//                }
            out.write(buf, 0, len);
            out.flush();
            String sndTimeEnd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS").format(new Date());
            // }
            Log.e("stage", "cur-"+currentIndex + " run: 2");


            socket.shutdownOutput();
            Log.e("stage", "cur: " + currentIndex + " run: upload image success");




//            // receive
            try (BufferedReader socket_in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                StringBuilder msg = new StringBuilder();
                char[] cbuf = new char[1024];
                len = 0;


                MyLog myLog = new MyLog(nowTime, currentIndex);

                while (true){
                    String newMsg = socket_in.readLine();
                    Log.e("info", "cur: " + currentIndex + "run: try to read from socket" + "len: " + newMsg);
                    if(newMsg == null){
                        Log.e("info", "cur: " + currentIndex + "run: newMsg is null");
                        break;
                    }
                    msg.append(newMsg);
                }

//                while((len=socket_in.read(cbuf, 0, 1024))!=-1) {
//                    msg.append(new String(cbuf, 0, len));
//                }

                Log.e("info", "cur: " + currentIndex + "run: try to record to log");


                long timeStampEnd = System.currentTimeMillis();
                String timeMsg =  currentIndex + "," + sndTimeBegin + "," + sndTimeEnd + "," + timeStampBegin + "," + timeStampEnd + "," + (timeStampEnd - timeStampBegin) ;
                myLog.writeFileToLog(timeMsg);
                Log.e("info: ", msg.toString());

                // msg is ignored
                //myLog.writeFileToLog(msg.toString());

            }

            in.close();
            out.close();
            socket.close();
            Log.e("INFO: ", currentIndex + " : run: a task is completed");
            latch.countDown();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        if (t==null) {
            t = new Thread(this, threadName);
            t.start();
        }
    }

    public static byte[] toLH(int n) {
        byte[] b = new byte[4];
        b[0] = (byte) (n & 0xff);
        b[1] = (byte) (n >> 8 & 0xff);
        b[2] = (byte) (n >> 16 & 0xff);
        b[3] = (byte) (n >> 24 & 0xff);
        return b;
    }

    public static byte[] toHH(int n) {
        byte[] b = new byte[4];
        b[3] = (byte) (n & 0xff);
        b[2] = (byte) (n >> 8 & 0xff);
        b[1] = (byte) (n >> 16 & 0xff);
        b[0] = (byte) (n >> 24 & 0xff);
        return b;
    }

}
