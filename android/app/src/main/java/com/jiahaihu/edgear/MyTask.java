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
    private int numberOfFrames;
    private int interval;

    public MyTask(String IP, int port, String path, int n, int fps) {
        serverIP = IP;
        serverPort = port;
        imagePath = path;
        numberOfFrames = n;
        interval = (int) 1000/fps;
    }

    @Override
    public void run() {

        try {
            Socket socket = new Socket(serverIP, serverPort);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

//            socket.setKeepAlive(true);

            String fileDirPath = Environment.getExternalStorageDirectory().toString()  + imagePath;
            File fileDir = new File(fileDirPath);

            File[] fileList = fileDir.listFiles();
            assert fileList != null;

            Date now = new Date();
            MyLog myLog = new MyLog(now.toString());

            for (int i=0; i<numberOfFrames; i++) {
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // send the size of image
                int fileLength = (int) fileList[0].length();
                Log.d("MyTask", "image file path: " + fileList[0].toString());
                Log.d("MyTask", "image file size: " + fileLength);


                // read image
                FileInputStream in =  new FileInputStream(fileList[0].toString());
                byte[] buf = new byte[fileLength];
                int len = in.read(buf, 0, fileLength);
                Log.d("MyTask", "read return: " + len);

                // send image to server
                byte[] fileLengthByte = bigEndian(fileLength);
                out.write(fileLengthByte);
                out.flush();
                long timeStampBegin = System.currentTimeMillis();
                String sendTimeBegin = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS").format(new Date());
                out.write(buf, 0, fileLength);
                out.flush();
                String sendTimeEnd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS").format(new Date());

//                socket.shutdownOutput();
                Log.i("MyTask", "index: " + i + " run: upload image success");

                // receive result from server
                BufferedReader socket_in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                Log.d("MyTask", "start receiving result from server");
                StringBuilder msg = new StringBuilder();

                while (true) {
                    Log.i("MyTask", "start readline");
                    String newMsg = socket_in.readLine();
                    if (newMsg == null) {
                        break;
                    }
                    Log.d("MyTask", "newMsg: " + newMsg);
                    msg.append(newMsg);

                    String end = newMsg.substring(newMsg.length()-2);
                    Log.d("MyTask", "end: " + end);
                    if (end.equals("**")) {
                        break;
                    }
                }
                Log.d("MyTask", "result: " + msg.toString());
                String result = msg.substring(0, msg.length()-2);

                // write result to log file
                long timeStampEnd = System.currentTimeMillis();
//                String timeMsg =  i + "," + sendTimeBegin + "," + sendTimeEnd + "," + timeStampBegin + "," + timeStampEnd + "," + (timeStampEnd - timeStampBegin);
                String timeMsg =  i + "," + (timeStampEnd - timeStampBegin);
                Log.d("MyTask", "timeMsg: " + timeMsg);

                myLog.writeFileToLog(timeMsg);
                in.close();
            }

            out.close();
            socket.close();
            Log.i("MyTask", "task is completed");

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
