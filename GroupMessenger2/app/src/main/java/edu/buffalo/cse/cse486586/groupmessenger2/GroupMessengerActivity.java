package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import static android.content.ContentValues.TAG;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity
{
    Uri providerUri = Uri.parse("content://edu.buffalo.cse.cse486586.groupmessenger2.provider");
    static final int SERVER_PORT = 10000;
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";

    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    private String currentPort;
    PriorityQueue<MessagePacket> messageQueue = new PriorityQueue<MessagePacket>(11, new MessageComparator());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        
        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        final EditText editText = (EditText) findViewById(R.id.editText1);

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        this.currentPort = myPort;

        findViewById(R.id.button4).setOnClickListener
        (
            new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    String msg = editText.getText().toString() + "\n";
                    editText.setText(""); // This is one way to reset the input box.
//                    TextView remoteTextView = (TextView) findViewById(R.id.textView1);
//                    remoteTextView.append(msg);
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
                }
            }
        );

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        }
        catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        int count = 0;
        int sequence = 0;

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            try
            {

                while (true)
                {
                    Socket socket = serverSocket.accept();
                    InputStreamReader input = new InputStreamReader(socket.getInputStream());
                    BufferedReader buffreader = new BufferedReader(input);
                    String msg = buffreader.readLine();
                    String[] packet = buffreader.readLine().split("_");
                    String seqNo = packet[1];
                    String port = packet[2];
                    Boolean isReady = Boolean.parseBoolean(packet[3]);
                    String msgSeq = packet[4];
                    String failedPort = "";

                    System.out.println("in server packet 1 value: "+seqNo);

                    if(seqNo.equals("failed"))
                    {
                        failedPort = msg;
                        System.out.println("Server side failed port is: "+failedPort);
                        Iterator<MessagePacket> itr = messageQueue.iterator();

                        while (itr.hasNext()) {
                            MessagePacket msgToRemove = itr.next();
                            if (msgToRemove.getMyPort().equals(failedPort) && msgToRemove.getIsReady()==false)
                                messageQueue.remove(msgToRemove);
                        }
                    }

                    System.out.println("in server msg seq value: "+msgSeq);

                    if(msgSeq!=null && msgSeq.equals("first"))
                    {
                        MessagePacket mPacket = new MessagePacket(msg, Integer.parseInt(seqNo),currentPort,isReady);
                        messageQueue.add(mPacket);
                        PrintWriter prwriter = new PrintWriter(socket.getOutputStream(), true);
                        StringBuilder sb = new StringBuilder();
                        sb.append(Integer.toString(sequence++));
                        sb.append("_");
                        sb.append(currentPort);
                        prwriter.println(sb.toString());
//                        sequence++;
                    }
                    else if(msgSeq!=null && msgSeq.equals("second"))
                    {
                        sequence = Math.max(Integer.parseInt(seqNo), sequence);
                        for(MessagePacket mp : messageQueue) {
                            if(mp.getMessage().equals(msg)) {
                                messageQueue.remove(mp);
                                MessagePacket packet1 = new MessagePacket(msg, Integer.parseInt(seqNo), port, isReady);
                                messageQueue.add(packet1);
                            }
                        }

                        while(!messageQueue.isEmpty() && messageQueue.peek().getIsReady()== true)
                        {
                            MessagePacket toSend = messageQueue.poll();
                            if(!toSend.getMyPort().equals(failedPort)) {
                                String msgToSend = toSend.getMessage();

                                ContentValues keyValueToInsert = new ContentValues();
                                keyValueToInsert.put("key", count);
                                count++;
                                keyValueToInsert.put("value", msgToSend);
                                getContentResolver().insert(providerUri, keyValueToInsert);

                                publishProgress(msgToSend);
                            }
                        }
                    }
                }
            }
            catch (IOException e)
            {
                Log.e(TAG, "Connection to server failed");
            }
            return null;
        }
        protected void onProgressUpdate(String...strings)
        {
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strReceived + "\t\n");
            System.out.println("progress");
            System.out.println("Message is: "+strReceived);

            String filename = "GroupMessengerOutput";
            String string = strReceived + "\n";
            FileOutputStream outputStream;
            try {
                outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
                outputStream.write(string.getBytes());
                outputStream.close();
            } catch (Exception e) {
                Log.e(TAG, "File write failed");
            }

            return;
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            String[] remotePort = new String[]{REMOTE_PORT0, REMOTE_PORT1, REMOTE_PORT2, REMOTE_PORT3, REMOTE_PORT4};
            ArrayList<String> sequenceInformation = new ArrayList<String>();
            ArrayList<Integer> sequenceNos = new ArrayList<Integer>();
            ArrayList<Integer> ports = new ArrayList<Integer>();
            String failedPort = "";
            for (String port : remotePort) {
                if (!port.equals(failedPort))
                {
                    try {
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(port));

                        socket.setSoTimeout(3000);
                        System.out.println("started client");
                        String msgToSend = msgs[0];
                        PrintWriter prwriter = new PrintWriter(socket.getOutputStream(), true);
                        StringBuilder packet = new StringBuilder();
                        packet.append(msgToSend + "_");
                        packet.append("0" + "_");
                        packet.append(port + "_");
                        packet.append("false" + "_");
                        packet.append("first");
                        System.out.println("sending msg1 from client");
                        prwriter.println(packet.toString());
                        prwriter.flush();

                        InputStreamReader input = new InputStreamReader(socket.getInputStream());
                        BufferedReader buffreader = new BufferedReader(input);

                        String seq = buffreader.readLine();
                        sequenceInformation.add(seq);
                        socket.close();
                    } catch (SocketTimeoutException e) {
                        System.out.println("Catched exception!!!!");
                        failedPort = port;
                        System.out.println("The failed port is: " + failedPort);

                        Iterator<MessagePacket> itr = messageQueue.iterator();

                        while (itr.hasNext()) {
                            MessagePacket msgToRemove = itr.next();
                            if (msgToRemove.getMyPort().equals(failedPort) && msgToRemove.getIsReady()==false)
                                messageQueue.remove(msgToRemove);
                        }

                        try {
                            for (String port1 : remotePort) {
                                if (!port1.equals(failedPort)) {
                                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                            Integer.parseInt(port1));

                                    PrintWriter prwriter = new PrintWriter(socket.getOutputStream(), true);
                                    StringBuilder packet = new StringBuilder();
                                    packet.append(failedPort + "_");
                                    packet.append("failed");
                                    System.out.println("Client 1 socket expn: "+packet);
                                    prwriter.println(packet);
                                    prwriter.flush();
                                }
                            }

                        } catch (UnknownHostException e1) {
                            Log.e(TAG, "ClientTask UnknownHostException");
                        } catch (IOException e1) {
                            Log.e(TAG, "ClientTask socket IOException");
                        }

                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        System.out.println("Catched IO exception!!!!");
                        failedPort = port;
                        System.out.println("The failed port is: " + failedPort);

                        Iterator<MessagePacket> itr = messageQueue.iterator();

                        while (itr.hasNext()) {
                            MessagePacket msgToRemove = itr.next();
                            if (msgToRemove.getMyPort().equals(failedPort) && msgToRemove.getIsReady()==false)
                                messageQueue.remove(msgToRemove);
                        }

                        try {
                            for (String port1 : remotePort) {
                                if (!port1.equals(failedPort)) {
                                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                            Integer.parseInt(port1));

                                    PrintWriter prwriter = new PrintWriter(socket.getOutputStream(), true);
                                    StringBuilder packet = new StringBuilder();
                                    packet.append(failedPort + "_");
                                    packet.append("failed");
                                    System.out.println("Client 1 IO expn: "+packet);
                                    prwriter.println(packet);
                                    prwriter.flush();
                                }
                            }

                        } catch (UnknownHostException e1) {
                            Log.e(TAG, "ClientTask UnknownHostException");
                        } catch (IOException e1) {
                            Log.e(TAG, "ClientTask socket IOException");
                        }

                    }
                }
            }

            //Part2

            for (String s : sequenceInformation) {
                if(s!=null && !s.isEmpty()) {
                    String[] splits = s.split("_");
                    int value = Integer.parseInt(splits[0]);
                    int key = Integer.parseInt(splits[1]);
                    sequenceNos.add(value);
                    ports.add(key);
                }
            }
            int finalSeq = Collections.max(sequenceNos);

            List<Integer> indexes = new ArrayList<Integer>();
            List<Integer> finalPorts = new ArrayList<Integer>();
            for (int i = 0; i < sequenceNos.size(); i++) {
                if (sequenceNos.get(i) == finalSeq)
                    indexes.add(i);
            }
            for (Integer i : indexes) {
                finalPorts.add(ports.get(i));
            }
            Integer maxPort = Collections.max(finalPorts);

            for (String port1 : remotePort) {
                if (!port1.equals(failedPort)) {
                    try {
                        Socket socket1 = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(port1));

                        socket1.setSoTimeout(3000);
                        System.out.println("started part 2 in client");
                        String msgToSend = msgs[0];
                        PrintWriter prwriter = new PrintWriter(socket1.getOutputStream(), true);
                        StringBuilder packet = new StringBuilder();
                        packet.append(msgToSend + "_");
                        packet.append(Integer.toString(finalSeq) + "_");
                        packet.append(maxPort + "_");
                        packet.append("true" + "_");
                        packet.append("second");
                        System.out.println("sending msg2 from client");
                        prwriter.println(packet.toString());
//                    socket1.close();
                    } catch (SocketTimeoutException e) {
                        System.out.println("Catched exception in part 2!!!!");
                        failedPort = port1;
                        System.out.println("The failed port is: " + failedPort);

                        Iterator<MessagePacket> itr = messageQueue.iterator();

                        while (itr.hasNext()) {
                            MessagePacket msgToRemove = itr.next();
                            if (msgToRemove.getMyPort().equals(failedPort) && msgToRemove.getIsReady()==false)
                                messageQueue.remove(msgToRemove);
                        }

                        try {
                            for (String port2 : remotePort) {
                                if (!port2.equals(failedPort)) {
                                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                            Integer.parseInt(port2));

                                    PrintWriter prwriter = new PrintWriter(socket.getOutputStream(), true);
                                    StringBuilder packet = new StringBuilder();
                                    packet.append(failedPort + "_");
                                    packet.append("failed");
                                    System.out.println("Client 2 socket expn: "+packet);
                                    prwriter.println(packet);
                                    prwriter.flush();
                                }
                            }

                        } catch (UnknownHostException e1) {
                            Log.e(TAG, "ClientTask UnknownHostException");
                        } catch (IOException e1) {
                            Log.e(TAG, "ClientTask socket IOException");
                        }

                    } catch (UnknownHostException e) {
                        Log.e(TAG, "ClientTask UnknownHostException");
                    } catch (IOException e) {
                        System.out.println("Catched IO exception!!!!");
                        failedPort = port1;
                        System.out.println("The failed port is: " + failedPort);

                        Iterator<MessagePacket> itr = messageQueue.iterator();

                        while (itr.hasNext()) {
                            MessagePacket msgToRemove = itr.next();
                            if (msgToRemove.getMyPort().equals(failedPort) && msgToRemove.getIsReady()==false)
                                messageQueue.remove(msgToRemove);
                        }

                        try {
                            for (String port3 : remotePort) {
                                if (!port3.equals(failedPort)) {
                                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                            Integer.parseInt(port3));

                                    PrintWriter prwriter = new PrintWriter(socket.getOutputStream(), true);
                                    StringBuilder packet = new StringBuilder();
                                    packet.append(failedPort + "_");
                                    packet.append("failed");
                                    System.out.println("Client 2 IO expn: "+packet);
                                    prwriter.println(packet);
                                    prwriter.flush();
                                }
                            }

                        } catch (UnknownHostException e1) {
                            Log.e(TAG, "ClientTask UnknownHostException");
                        } catch (IOException e1) {
                            Log.e(TAG, "ClientTask socket IOException");
                        }

                        Log.e(TAG, "ClientTask socket IOException");
                    }
                }
            }
            return null;
        }
    }

    class MessageComparator implements Comparator<MessagePacket>
    {
        public int compare(MessagePacket p1, MessagePacket p2)
        {
            if (p1.getSequenceNo() < p2.getSequenceNo())
                return -1;
            else if (p1.getSequenceNo() > p2.getSequenceNo())
                return 1;
            else
            {
                if(Integer.parseInt(p1.getMyPort()) > Integer.parseInt(p2.getMyPort()))
                    return 1;
                else
                    return -1;
            }
        }
    }
}
