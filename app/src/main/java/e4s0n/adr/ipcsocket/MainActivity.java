package e4s0n.adr.ipcsocket;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int MESSAGE_RECEIVE_NEW_MSG = 1;
    private static final int MESSAGE_SOCKET_CONNECTED = 2;

    private List<Msg> msgList = new ArrayList<Msg>();
    private EditText inputText;
    private Button send;
    private RecyclerView msgRecyclerView;
    private  MsgAdapter adapter;
    private Socket mClientSocket = null;
    private PrintWriter printWriter = null;
    private Handler handler = null;

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        inputText = (EditText)findViewById(R.id.input_text);
        send = (Button)findViewById(R.id.send);
        send.setEnabled(false);
        msgRecyclerView = (RecyclerView)findViewById(R.id.msg_recycler_view);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        msgRecyclerView.setLayoutManager(layoutManager);
        adapter = new MsgAdapter(msgList);
        msgRecyclerView.setAdapter(adapter);
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String content = inputText.getText().toString();
                if(!"".equals(content))
                {
                    new Thread(){
                        @Override
                        public void run() {
                            printWriter.println(content);
                        }
                    }.start();
                    Msg msg = new Msg(content,Msg.TYPE_SENT);
                    msgList.add(msg);
                    adapter.notifyItemInserted(msgList.size()-1);//当有新消息时刷新RecyclerView中的显示
                    msgRecyclerView.scrollToPosition(msgList.size()-1);//将RecyclerView定位到最后一行
                    inputText.setText("");// 清空输入框中的内容
                }
            }
        });
        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MESSAGE_RECEIVE_NEW_MSG: {
                        msgList.add(new Msg((String)msg.obj, Msg.TYPE_RECEIVED));
                        adapter.notifyItemInserted(msgList.size()-1);//当有新消息时刷新RecyclerView中的显示
                        msgRecyclerView.scrollToPosition(msgList.size()-1);//将RecyclerView定位到最后一行
                        break;
                    }
                    case MESSAGE_SOCKET_CONNECTED: {
                        send.setEnabled(true);
                        break;
                    }
                    default:
                        break;
                }
            }
        };
        new Thread() {
            @Override
            public void run() {
                connectTCPServer();
            }
        }.start();

    }
    private void connectTCPServer() {
        Socket socket = null;
        boolean tryreconnect = true;
        while (socket == null) {
            try {
                String url[] =loadurl().split(":");
                socket = new Socket(url[0], Integer.valueOf(url[1]));
                mClientSocket = socket;
                printWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                handler.sendEmptyMessage(MESSAGE_SOCKET_CONNECTED);
                System.out.println("connect server success");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(),"连接成功",Toast.LENGTH_SHORT).show();
                    }
                });
                tryreconnect = true;
            } catch (IOException e) {
                SystemClock.sleep(1000);
                System.out.println("connect tcp server failed, retry...");
                if (tryreconnect)
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(),"连接重连中",Toast.LENGTH_SHORT).show();
                    }
                });
                tryreconnect = false;
            }
        }

        try {
            // 接收服务器端的消息
            BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            while (!this.isFinishing()) {
                String msg = br.readLine();
                if (msg != null) {
                    handler.obtainMessage(MESSAGE_RECEIVE_NEW_MSG, msg).sendToTarget();
                }
            }
            printWriter.close();
            br.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @Override
    protected void onDestroy() {
        if (mClientSocket != null) {
            try {
                mClientSocket.shutdownInput();
                mClientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        super.onDestroy();
    }
    public String loadurl() {
        String url = "localhost:8688";
        File file = new File("sdcard/PQurl.txt");
        if (!file.exists())
        {
            try {
                file.createNewFile();
                FileOutputStream outputStream = new FileOutputStream(file,true);
                outputStream.write(url.getBytes());
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else{
            StringBuilder content = new StringBuilder();
            try {
                FileInputStream in = new FileInputStream(file);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                String line = "";
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            url = content.toString();
        }

        return url;
    }
}
