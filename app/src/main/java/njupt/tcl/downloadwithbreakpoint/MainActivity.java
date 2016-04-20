package njupt.tcl.downloadwithbreakpoint;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity implements View.OnClickListener {
    public static final int Download_Finish = 100;
    public static final int Download_progress = 101;
    private TextView tv;
    private Button button;
    private EditText et;
    private static int threadcount = 3;
    private static int runningThreadTag = 3;
    private ProgressBar pb;
    private int progressCount = 0;
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Download_Finish:
                    Toast.makeText(getApplicationContext(), "下载完成！", 0).show();
                    break;

                case Download_progress:
                    tv.setText("当前下载进度为：" + (pb.getProgress() * 100 / pb.getMax()) + "%");
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        button = (Button) findViewById(R.id.buttondownload);
        pb = (ProgressBar) findViewById(R.id.progressBar);
        tv = (TextView) findViewById(R.id.textViewProgress);
        et = (EditText) findViewById(R.id.editText);
        button.setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.buttondownload:
                final String path = et.getText().toString().trim();
                new Thread() {
                    @Override
                    public void run() {

                        try {
                            URL url = new URL(path);
                            HttpURLConnection conn = (HttpURLConnection) url
                                    .openConnection();
                            conn.setConnectTimeout(3000);
                            conn.setReadTimeout(3000);
                            conn.setRequestMethod("GET");
                            if (conn.getResponseCode() == 200) {
                                int filelength = conn.getContentLength();
                                // 生成临时文件（文件名称，文件模式）
                                RandomAccessFile raf = new RandomAccessFile(
                                        "/sdcard/setuppart.exe", "rw");
                                // 设置临时文件大小
                                raf.setLength(filelength);
                                // 设置进度条最大长度
                                pb.setMax(filelength);
                                // 为每个线程设置下载位置
                                int blocksize = filelength / threadcount;
                                for (int threadID = 1; threadID <= threadcount; threadID++) {
                                    int startindex = (threadID - 1) * blocksize;
                                    int endindex = threadID * blocksize - 1;
                                    if (threadID == threadcount) {
                                        endindex = filelength;
                                    }
                                    System.out.println("线程" + threadID + ":下载"
                                            + startindex + "到" + endindex + "的资源");
                                    // 针对每段资源开启子线程
                                    new DownLoadThread(threadID, startindex,
                                            endindex, path).start();
                                }

                            } else {
                                System.out.println("返回码错误！");
                            }
                        } catch (Exception e) {
                            System.out.println("连接网络错误！");
                            e.printStackTrace();
                        }

                    }
                }.start();

                break;

            default:
                break;
        }
    }

    // 内部类定义子线程
    public class DownLoadThread extends Thread {
        private int threadID;
        private int startindex;
        private int endindex;
        private String path;


        public DownLoadThread(int threadID, int startindex, int endindex,
                              String path) {
            this.threadID = threadID;
            this.startindex = startindex;
            this.endindex = endindex;
            this.path = path;
        }

        @Override
        public void run() {

            try {
                // 检查是否存在断点文件
                File checkBreakFile = new File("/sdcard/" + threadID + ".txt");
                if (checkBreakFile.exists() && checkBreakFile.length() > 0) {
                    // 读出下载断点即已经下载的资源量
                    int breakDownLoad = 0;
                    String checkBreakInfo = null;
                    try {
                        FileReader inread = new FileReader(checkBreakFile);
                        BufferedReader buin = new BufferedReader(inread);

                        while ((checkBreakInfo = buin.readLine()) != null) {

                            breakDownLoad = Integer.parseInt(checkBreakInfo
                                    .toString());
                            System.out.println("线程" + threadID + "记录已经下载的位置"
                                    + breakDownLoad);
                        }
                        buin.close();
                        inread.close();
                        // 计算进度已经完成的部分
                        progressCount = (breakDownLoad - startindex) + progressCount;

                        // 修正下载断点即已经下载的位置
                        startindex = breakDownLoad;
                        System.out.println("线程" + threadID + ":真实的下载"
                                + startindex + "到" + endindex + "的资源");
                    } catch (IOException e) {
                        System.out.println("检查是否存在断点文件异常");
                        e.printStackTrace();
                    }
                }
                URL url = new URL(path);
                HttpURLConnection conn = (HttpURLConnection) url
                        .openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestMethod("GET");
                // 使用Http的Range头字段指定从资源的下载部分(头字段、部分资源起止位置)
                conn.setRequestProperty("Range", "bytes=" + startindex + "-"
                        + endindex);
                int respondCode = conn.getResponseCode();
                if (respondCode == 206) {

                    InputStream ins = conn.getInputStream();
                    // 找到临时文件（文件名称，文件模式）
                    RandomAccessFile raf = new RandomAccessFile(
                            "/sdcard/setuppart.exe", "rw");
                    // 定位开始写的位置
                    raf.seek(startindex);
                    int length = 0;
                    // 创建1K大小的字节数组
                    byte[] buffer = new byte[1024];
                    // 创建断点续存文件已经下载位置
                    int breaklength = 0;

                    // is输入流每次输出一个1K大小的字节数组至buffer[]中
                    while ((length = ins.read(buffer)) != -1) {
                        // 创建断电续存临时文件（文件名称，文件模式）
                        RandomAccessFile breakinfo = new RandomAccessFile(
                                "/sdcard/" + threadID + ".txt", "rw");
                        // 将buffer[]内容写入内存输出流
                        raf.write(buffer, 0, length);
                        breaklength = breaklength + length;
                        // 覆盖临时文件
                        breakinfo.write(Integer.toString(
                                breaklength + startindex).getBytes());
                        breakinfo.close();

                        // 同步代码块，保证每个子线程在此段代码必须原子性
                        synchronized (MainActivity.this) {
                            progressCount = progressCount + length;
                            pb.setProgress(progressCount);
                            // 可以复用消息池中的旧消息，避免多次创建消息对象
                            Message msg = Message.obtain();
                            msg.what = Download_progress;
                            handler.sendMessage(msg);
                        }


                    }
                    ins.close();
                    raf.close();


                    System.out.println("子线程" + threadID + "下载完成！");
                } else {
                    System.out.println("子线程" + threadID + "中返回码错误！");
                }

            } catch (Exception e) {
                System.out.println("子线程" + threadID + "中连接网络错误！");
                e.printStackTrace();
            } finally {
                // 线程运行结束执行
                synchronized (MainActivity.this) {
                    runningThreadTag--;
                    System.out.println("子线程" + threadID + "执行完毕");
                    if (runningThreadTag == 0) {
                        System.out.println("子线程全部执行完毕，删除所有临时文件！");
                        for (int i = 1; i <= threadcount; i++) {
                            File checkBreakFile = new File("/sdcard/" + i
                                    + ".txt");
                            checkBreakFile.delete();

                        }
                        Message msg = Message.obtain();
                        msg.what = Download_Finish;
                        handler.sendMessage(msg);
                    }

                }
            }

        }
    }
}
