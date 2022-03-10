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

public class MyTask extends Thread{
    private String serverIP;
    private int serverPort;
    private String imagePath;
    private int currentIndex;
    private String nowTime;
    private CountDownLatch latch;
    private int certainIndex = 0;

    public MyTask(String IP, int port, String path, int index, String now, CountDownLatch cdl) {
        serverIP = IP;
        serverPort = port;
        imagePath = path;
        currentIndex = index;
        nowTime = now;
        latch = cdl;
    }

    @Override
    public void run() {

        try {
            Socket socket = new Socket(serverIP, serverPort);
            // socket.setKeepAlive(true);

            String fileDirPath = Environment.getExternalStorageDirectory().toString()  + imagePath;
            File fileDir = new File(fileDirPath);

            File[] fileList = fileDir.listFiles();
            assert fileList != null;

            // send the size of image
            int fileLength = (int) fileList[certainIndex].length();
            Log.d("MyTask", "image file path: " + fileList[certainIndex].toString());
            byte[] fileLengthByte = bigEndian(fileLength);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.write(fileLengthByte);

            // read image
            FileInputStream in =  new FileInputStream(fileList[certainIndex].toString());
            byte[] buf = new byte[fileLength];
            int len = in.read(buf);

            // send image to server
            long timeStampBegin = System.currentTimeMillis();
            String sendTimeBegin = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS").format(new Date());
            out.write(buf, 0, len);
            out.flush();
            String sendTimeEnd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS").format(new Date());

            socket.shutdownOutput();
            Log.i("MyTask", "index: " + currentIndex + " run: upload image success");

            // receive result from server
            try (BufferedReader socket_in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                Log.d("MyTask", "start receiving result from server");
                StringBuilder msg = new StringBuilder();

                while (true) {
                    String newMsg = socket_in.readLine();
                    if (newMsg == null) {
                        break;
                    }
                    msg.append(newMsg);
                }
//                Log.d("MyTask", "result: " + msg.toString());

                // write result to log file
                long timeStampEnd = System.currentTimeMillis();
                String timeMsg =  currentIndex + "," + sendTimeBegin + "," + sendTimeEnd + "," + timeStampBegin + "," + timeStampEnd + "," + (timeStampEnd - timeStampBegin) ;
                MyLog myLog = new MyLog(nowTime, currentIndex);
//                myLog.writeFileToLog(timeMsg);
            }

            in.close();
            out.close();
            socket.close();
            Log.i("MyTask", currentIndex + " task is completed");
            latch.countDown();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // convert to big-endian byte order
    public static byte[] bigEndian(int n) {
        byte[] b = new byte[4];
        b[3] = (byte) (n & 0xff);
        b[2] = (byte) (n >> 8 & 0xff);
        b[1] = (byte) (n >> 16 & 0xff);
        b[0] = (byte) (n >> 24 & 0xff);
        return b;
    }

}
