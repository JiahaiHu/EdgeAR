package com.jiahaihu.edgear;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class MyLog {
    private String MYLOG_PATH_DIR = "/Download/logs/";
    private static String MYLOG_SUFFIX = ".txt";
    private String FILE_NAME;
    private static SimpleDateFormat myLogSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public MyLog() {
        Date nowTime = new Date();
        this.FILE_NAME = myLogSdf.format(nowTime);
    }

    public MyLog(String nowTime, int index) throws IOException {
        FILE_NAME = nowTime + "-" + index;

        File externalDir = Environment.getExternalStorageDirectory();
        Log.d(" ", "writeFileTolog: the external Dirs is: " + externalDir.toString());

        MYLOG_PATH_DIR = externalDir.toString() + MYLOG_PATH_DIR;
        File dirsFile = new File(MYLOG_PATH_DIR);
        Log.d(" ", "writeFileTolog: the external Dirs is: " + MYLOG_PATH_DIR);

        if(!dirsFile.exists()) {
            Log.d(" ", "writeFileToLog: try to create dirs");
            dirsFile.createNewFile();
        } else{
            Log.d(" ", "writeFileToLog: dirsFile exists");
        }

        File logFile = new File(dirsFile.toString(), this.FILE_NAME+MYLOG_SUFFIX);
        Log.d(" ", "writeFileToLog: " + logFile.toString());
        if(!logFile.exists()) {
            Log.d(" ", "writeFileToLog: try to create files");
            logFile.createNewFile();
        }

    }

    public void writeFileToLog(String text) throws IOException {

        File dirsFile = new File(MYLOG_PATH_DIR);
        File logFile = new File(dirsFile.toString(), this.FILE_NAME+MYLOG_SUFFIX);

        FileWriter filerWriter = new FileWriter(logFile, true);
        BufferedWriter bufWriter = new BufferedWriter(filerWriter);
        bufWriter.write(text);
        bufWriter.newLine();
        bufWriter.close();
        filerWriter.close();
        Log.i("INFO", "writeFileToLog: success writing a file");
    }


    public static void mergeFileLog(String subTitle) throws IOException {
        String dir = "/storage/emulated/0/Download/logs/";
        File dirsFile = new File(dir);
        File[] tmpList = dirsFile.listFiles();

        if(tmpList == null) {
            Log.e("error", "mergeFileLog: the logs dir is empty ");
            return;
        }

        // order the files by alphabet
        List fileList = Arrays.asList(tmpList);
        Collections.sort(fileList, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2){
                if (o1.isDirectory() && o2.isFile())
                    return -1;
                if (o1.isFile() && o2.isDirectory())
                    return 1;
                return o1.getName().compareTo(o2.getName());
            }
        });

        String fileOutName = dir + subTitle + ".txt";
        Log.d("TAG", "mergeFileLog: " + fileOutName);
        File fileOut = new File(fileOutName);

        if(!fileOut.exists()) {
            Log.d(" ", "mergeFileLog: try to create files");
            fileOut.createNewFile();
        }

        BufferedWriter bw = new BufferedWriter(new FileWriter(fileOutName));

        for (Object f: fileList) {
            if(((File) f).getName().contains(subTitle)){
                Log.e("TAG", "mergeFileLog: " + ((File) f).getName());
                BufferedReader br = new BufferedReader(new FileReader((File) f));
                String line;
                while((line= br.readLine()) != null) {
                    bw.write(line);
                    bw.newLine();
                }
                br.close();

                // delete the file
                ((File) f).delete();
            }
        }
        bw.close();
        Log.e("TAG", "mergeFileLog: file merge ends" );
    }

}
