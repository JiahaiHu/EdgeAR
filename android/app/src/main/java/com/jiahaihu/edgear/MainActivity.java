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

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String ip = ipInput.getText().toString();
                int port = Integer.parseInt(portInput.getText().toString());
                int numberOfFrames = Integer.parseInt(numberInput.getText().toString());
                int timeInterval = Integer.parseInt(timeIntervalInput.getText().toString());

                // Get the server's ip and port,
                // use the async methods to send the requests at a fixed interval

                String displayString = ip + port + numberOfFrames;
                textView.setText(displayString);

                final CountDownLatch cdl = new CountDownLatch(numberOfFrames);

                // read the numbers of frames
                Date nowTime = new Date();
                String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS").format(nowTime);

                Mytask[]  tasks= new Mytask[numberOfFrames];
                for(int i=0; i<numberOfFrames; i++){
                    tasks[i] = new Mytask("test", ip, port, "/Download/images",i, "0", now, cdl);
                }

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                tasks[0].start();

                // at a fixed intervals
                for(int i=1;i<numberOfFrames; i++){
                    try {
                        Thread.sleep(timeInterval);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    tasks[i].start();
                }

                Log.e("main:", "all tasks are running");
                for(int i=0; i<numberOfFrames; i++) {
                    try {
                        tasks[i].join();
                        Log.e("TAG", "onClick: " + i + " completed" );
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

//                try {
//                    cdl.await();
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }

                Log.e("INFO: ", "onClick: tasks finished");

//                try {
//                    Thread.sleep(1000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }

                // arrange the logs
                try {
                    MyLog.mergeFileLog(now);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                Log.e("INFO: ", "onClick: merge logs finished");

            }
        });

    }
}