package com.jiahaihu.edgear;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import java.text.SimpleDateFormat;
import java.util.Date;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);

        Button btn = findViewById(R.id.button);
        EditText ipInput = findViewById(R.id.IPInput);
        EditText portInput = findViewById(R.id.PortInput);
        EditText timeInput = findViewById(R.id.timeInput);
        EditText timeIntervalInput = findViewById(R.id.timeIntervalInput);

        btn.setOnClickListener(view -> {
            String ip = ipInput.getText().toString();
            int port = Integer.parseInt(portInput.getText().toString());
            int time = Integer.parseInt(timeInput.getText().toString());
            int fps = Integer.parseInt(timeIntervalInput.getText().toString());

            // Get the server's ip and port,
            // use the async methods to send the requests at a fixed interval

            Date nowTime = new Date();
            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS").format(nowTime);

            MyTask task = new MyTask(ip, port, "/Download/EdgeAR/images/ski", time, fps);
            task.start();
            Log.i("onClick", "task started");

            try {
                task.join();
                Log.i("onClick", "task completed" );
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

    }
}