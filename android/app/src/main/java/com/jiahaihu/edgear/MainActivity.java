package com.jiahaihu.edgear;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CountDownLatch;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);

        Button btn = findViewById(R.id.button);
        EditText ipInput = findViewById(R.id.IPInput);
        EditText portInput = findViewById(R.id.PortInput);
        EditText numberInput = findViewById(R.id.numbersOfFrames);
        EditText timeIntervalInput = findViewById(R.id.timeIntervalInput);

        TextView textView = findViewById(R.id.textView);

        btn.setOnClickListener(view -> {
            String ip = ipInput.getText().toString();
            int port = Integer.parseInt(portInput.getText().toString());
            int numberOfFrames = Integer.parseInt(numberInput.getText().toString());
            int fps = Integer.parseInt(timeIntervalInput.getText().toString());

            // Get the server's ip and port,
            // use the async methods to send the requests at a fixed interval

            String displayString = ip + port + numberOfFrames;
            textView.setText(displayString);

            Date nowTime = new Date();
            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS").format(nowTime);

            MyTask task = new MyTask(ip, port, "/Download/images", numberOfFrames, fps);
            task.start();
            Log.i("onClick", "task started");

//            try {
//                Thread.sleep(3000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }

            try {
                task.join();
                Log.i("onClick", "task completed" );
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

//                try {
//                    Thread.sleep(1000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }

            // arrange the logs
//            try {
//                MyLog.mergeFileLog(now);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }

//            Log.e("INFO: ", "onClick: merge logs finished");

        });

    }
}