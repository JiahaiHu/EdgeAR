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
    private Socket socket;
    private String logTimestamp;

    public MyTask(String IP, int port, String path, int n, int fps) {
        serverIP = IP;
        serverPort = port;
        imagePath = path;
        numberOfFrames = n;
        interval = (int) 1000/fps;
        logTimestamp = "" + System.currentTimeMillis();
    }

    @Override
    public void run() {
        try {
            socket = new Socket(serverIP, serverPort);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Thread frameSender = new Thread(() -> {
            try {
                String fileDirPath = Environment.getExternalStorageDirectory().toString()  + imagePath;
                File fileDir = new File(fileDirPath);
                File[] fileList = fileDir.listFiles();
                assert fileList != null;

                MyLog myLog = new MyLog("send_" + logTimestamp);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                for (int i=0; i<numberOfFrames; i++) {
                    try {
                        Thread.sleep(interval);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    int fileLength = (int) fileList[i].length();
                    Log.d("frameSender", "image file path: " + fileList[i].toString());
                    Log.d("frameSender", "image file size: " + fileLength);

                    // read image
                    FileInputStream in =  new FileInputStream(fileList[i].toString());
                    byte[] buf = new byte[fileLength];
                    int len = in.read(buf, 0, fileLength);
                    Log.d("frameSender", "read return: " + len);

                    // send the size of image
                    byte[] fileLengthByte = bigEndian(fileLength);
                    out.write(fileLengthByte);
                    out.flush();

                    // send image to server
                    long timeStampBegin = System.currentTimeMillis();
                    String sendTimeBegin = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS").format(new Date());
                    out.write(buf, 0, fileLength);
                    out.flush();
                    long timeStampEnd = System.currentTimeMillis();
                    String sendTimeEnd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS").format(new Date());
                    String timeMsg =  i + "," + sendTimeBegin + "," + sendTimeEnd + "," + timeStampBegin + "," + timeStampEnd;
                    Log.d("frameSender", "timeMsg: " + timeMsg);
                    Log.i("frameSender", "index: " + i + " run: upload image success");
                    myLog.writeFileToLog(timeMsg);

                    in.close();
                }

                out.close();
                Log.i("frameSender", "all frames are sent to server");

            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        frameSender.start();

        // receive result from server
        Thread resultReceiver = new Thread(() -> {
            try {
                MyLog myLog = new MyLog("receive_" + logTimestamp);
                BufferedReader socket_in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                for (int i=0; i<numberOfFrames; i++) {
                    Log.d("resultReceiver", "start receiving result from server");
                    StringBuilder msg = new StringBuilder();

                    while (true) {
                        Log.i("resultReceiver", "start readline");
                        String newMsg = socket_in.readLine();
                        if (newMsg == null) {
                            break;
                        }
                        Log.d("resultReceiver", "newMsg: " + newMsg);
                        msg.append(newMsg);

                        String end = newMsg.substring(newMsg.length()-2);
                        Log.d("resultReceiver", "end: " + end);
                        if (end.equals("**")) {
                            break;
                        }
                    }
                    Log.d("resultReceiver", "result: " + msg.toString());
                    String result = msg.substring(0, msg.length()-2);

                    // write result to log file
                    long timeStamp = System.currentTimeMillis();
                    String receiveTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS").format(new Date());
                    String timeMsg =  i + "," + receiveTime + "," + timeStamp;
                    Log.d("resultReceiver", "timeMsg: " + timeMsg);
                    myLog.writeFileToLog(timeMsg);
                }

                socket_in.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        resultReceiver.start();

        try {
            frameSender.join();
            resultReceiver.join();
            socket.close();
        } catch (IOException | InterruptedException e) {
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
