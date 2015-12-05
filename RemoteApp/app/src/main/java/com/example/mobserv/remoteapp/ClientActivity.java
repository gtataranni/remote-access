package com.example.mobserv.remoteapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.Layout;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Base64;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.PatternSyntaxException;
/**
 * Created by pacel_000 on 22/10/2015.
 */
public class ClientActivity extends Activity implements LocationListener {

    private Socket socket = null;
    private PrintWriter out;
    private static final int serverport = 45678;
    private String serverip = "";
    private TextView text;
    private Handler updateConversationHandler;
    private EditText et;
    private Thread th;
    private String myName;
    private Location location;
    private String provider;
    private SurfaceView mSurfaceView;
    private ImageView contactImage;
    CameraPreview preview;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);
        Intent it = getIntent();
        text = (TextView) findViewById(R.id.idClientText);
        text.setMovementMethod(new ScrollingMovementMethod());

        et = (EditText) findViewById(R.id.idClientEditText);
        updateConversationHandler = new Handler();

        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        contactImage = (ImageView) findViewById(R.id.photo);
        preview = new CameraPreview(this, (SurfaceView) findViewById(R.id.surfaceView));
        preview.setKeepScreenOn(true);

        if(this.serverip.isEmpty()) {
            this.serverip = it.getStringExtra("serverip");
            et.setFocusable(false);
            myName = null;
            th = new Thread(new ClientThread());
            th.start();

            // TODO avoid reconnect when activity is created again, for ex. after rotation
            // (I tried using this if statement but is not effective)
            // an idea could be keep the bg thread alive somehow, and start it only when the
            // 'connect' button in the main activity is pressed
            // TODO: rotation also erases the text in the textview, which is the 'current conversation'
            // temporary fix: forbid rotation
        }
    }

    public void onClick(View view) {
        String str = et.getText().toString();
        sendMsg(str);
        if (myName == null){
            myName = str;
            Log.d("debug", "My name as client: " + myName);
        }
        et.setText(null);
    }

    /** Write the string on the socket, no matter what is the format.
     *  So the 'msg' string received need to be already in the right format
     * @param msg
     */
    public void sendMsg(String msg){
        out.write(msg);
        out.flush();
        updateConversationHandler.post(new updateUIThread(msg));
    }

    public void onClickWrite(View view){
        String tmp = et.getText().toString();
        tmp += "/write/";
        et.setText(tmp);
        et.setSelection(et.getText().toString().length());
    }

    public void onClickRead(View view){
        String tmp = et.getText().toString();
        tmp += "/read/";
        et.setText(tmp);
        et.setSelection(et.getText().toString().length());
    }

    public void onClickExec(View view){
        String tmp = et.getText().toString();
        tmp += "/exec/";
        et.setText(tmp);
        et.setSelection(et.getText().toString().length());
    }

    class ClientThread implements Runnable {
        BufferedReader inputStream;

        @Override
        public void run() {
            runOnUiThread(new makeToast("Connecting to " + serverip + ":" + serverport + "..."));
            try {
                InetAddress serverAddr = InetAddress.getByName(serverip);
                socket = new Socket(serverAddr, serverport);
                this.inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())),
                        true);

             // success =)
                runOnUiThread(new makeToast("Connected to " + serverAddr + " " + serverport));
                runOnUiThread(new Runnable() {@Override public void run() {et.setFocusableInTouchMode(true);}});
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(new makeToast("ERROR:\n" + e.getMessage()));
                finish();
                return;
            }

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String read = inputStream.readLine();
                    updateConversationHandler.post(new updateUIThread(read));

                    boolean isOK = checkReceivedMessageFormat(read);
                    if(!isOK){
                        runOnUiThread(new makeToast(read));
                    } else {
                        String senderName = read.substring(1, read.indexOf(">"));
                        String[] args = read.substring(read.indexOf(">")+2, read.length()).split("/");
                        messageDispatch(senderName, args);
                        //runOnUiThread(new makeToast(senderName));
                        //runOnUiThread(new makeToast(TextUtils.join("/", args)));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    if(!socket.isClosed())
                        runOnUiThread(new makeToast("ERROR:\n" + e.getMessage()));
                    else
                        runOnUiThread(new makeToast(e.getMessage()));
                    finish();
                    return;
                }
            }
        }

        /** Check if received message should be dispatched or not
         * (if it is a protocol-like message or human-like message)
         * @param msg the message to be checked
         * @return true if it is protocol-like, false otherwise
         */
        private boolean checkReceivedMessageFormat(String msg) {
            try {
                String splits1[] = msg.split(" ");
                if(splits1[1] == null){
                    Log.d("ReceivedMessageFormat", "second part is null :: " + msg);
                    return false;
                }
                if(!splits1[0].matches("^<.*>$")) {
                    Log.d("ReceivedMessageFormat", "format of first part does not match :: " + msg);
                    return false;
                }
                if(!splits1[1].matches("[^/]*/[^/]+/[^/]+.*")) {
                    Log.d("ReceivedMessageFormat", "format of second part does not match :: " + msg);
                    return false;
                }
            } catch (NullPointerException | IndexOutOfBoundsException | PatternSyntaxException e){
                //TODO: it enters here when image is being transferring, although the image is retrieved in messageIsWrite
                Log.d("ReceivedMessageFormat", "Exception: " + e.getClass().getName() + " " + e.getMessage());
                Log.d("ReceivedMessageFormat", "Exception :: " + msg);
                return false;
            }
            return true;
        }

        public void messageDispatch(String senderName, String[] args){
            boolean isBroadcast = false;

            if(!args[1].equals(myName))
                isBroadcast = true;

            switch (args[2]){
                case "read":
                    messageIsRead(senderName, args);
                    break;
                case "write":
                    // TODO: 11/30/15
                    messageIsWrite(senderName, args);
                    break;
                case "exec":
                    // TODO: 11/30/15
                    messageIsExec(senderName, args);
                    break;
                default:
                    // this should never happen if the server is well behaved
                    runOnUiThread(new makeToast("Unknown message:\n" + TextUtils.join("/", args)));
                    break;
            }

        }
        public void messageIsWrite(String senderName, String[] args){
            LinkedList<String> reply = new LinkedList<>();
            switch (args[3]){
                case "photo":
                    //TODO: show the received photo
                    String encodedImage;
                    StringBuilder total = new StringBuilder();
                    String line;
                    try {
                        while ((line = inputStream.readLine()) != null) {
                            if (line.length() >= 5){
                                if(line.substring(line.length() - 5,line.length()).compareTo("_end_") == 0) {
                                    //total.append(total.substring(0,total.length()-6));
                                    break;
                                }
                            }
                            total.append(line+"\n");
                        }
                        encodedImage = total.toString();
                        byte[] decodedString = Base64.decode(encodedImage, Base64.DEFAULT);
                        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                        updateConversationHandler.post(new updateUIImage(decodedByte));

                        /*File file = new File(Environment.getExternalStorageDirectory() + File.separator + "test.jpg");
                        file.createNewFile();
                        try {
                            OutputStream fOut = new FileOutputStream(file);

                            decodedByte.compress(Bitmap.CompressFormat.JPEG, 85, fOut); // saving the Bitmap to a file compressed as a JPEG with 85% compression rate
                            fOut.flush();
                            fOut.close(); // do not forget to close the stream

                            MediaStore.Images.Media.insertImage(getContentResolver(), file.getAbsolutePath(), file.getName(), file.getName());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        */
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
            }
        }
        public void messageIsExec(String senderName, String[] args){

        }
        public void messageIsRead(String senderName, String[] args){
            // TODO: 11/30/15
            LinkedList<String> reply = new LinkedList<>();
            String data = null;
            switch (args[3]){
                case "gps":
                    // TODO check if the geo service actually works
                    if (ContextCompat.checkSelfPermission(getApplicationContext(),
                            Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                            ContextCompat.checkSelfPermission(getApplicationContext(),
                                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        Log.i("Permission: ", "To be checked");
                        ActivityCompat.requestPermissions(getParent(),
                                new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION},
                                0);
                        return;
                    } else
                        Log.i("Permission: ", "GRANTED");
                    Criteria criteria = new Criteria();
                    LocationManager locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
                    provider = locationManager.getBestProvider(criteria, false);
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, (LocationListener) getBaseContext());
                    location = locationManager.getLastKnownLocation(provider);
                    reply.add("OK");
                    reply.add(String.valueOf(location.getLatitude()));
                    reply.add(String.valueOf(location.getLongitude()));
                    break;
                case "clientlist":
                    int numOfClients = Integer.parseInt(args[4]);
                    List<String> clients = new LinkedList<>();
                    clients.addAll(Arrays.asList(args).subList(5, args.length));
                    Log.d("msgIsRead", "Parsed list of clients: " + numOfClients + " " + clients.toString());
                    // TODO: update list of clients
                    break;
                case "photo":
                    //PHOTO Part
                    //final Looper[] mLooper = new Looper[1];
                    if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                            new makeToast("No camera on this device");
                    } else {
                        try {
                            preview.setCamera();
                            preview.openSurface();

                            String encodedImage = preview.takePicture();
                            reply.add("write");
                            reply.add("photo");
                            data = encodedImage;
                        } catch (Exception e) {
                            Log.d("ERROR", "Failed to config the camera: " + e.getMessage());
                        } finally {
                            preview.closeSurface();
                        }
                    }
                    break;
                default:
                runOnUiThread(new makeToast("Unknown message:\n"+ TextUtils.join("/", args)));
                break;
            }
            if(reply.size() != 0) {
                String msg = composeMsg(senderName, reply);
                sendMsg(msg);
                if(data != null){
                    try {
                        Thread.sleep(500);
                        out.write(data);
                        out.flush();
                        Thread.sleep(500);
                        out.write("_end_");
                        out.flush();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                Log.d("SendMsg", msg);
            }
        }
        public String composeMsg(String to, LinkedList<String> content){
            String msg = "/"; // <-- leaving field 0 empty
            // Log.d("composeMsg", "to: "+ to+" Content: "+content.toString());
            msg += to;
            if(content == null)
                return msg;
            for(String arg : content){
                msg += "/" + arg;
            }
            return msg;
        }

    }

    class updateUIThread implements Runnable {
        private String msg;
        public updateUIThread(String str) { this.msg = str; }
        @Override
        public void run() {
            text.setText(text.getText().toString() + msg + "\n");
            // code below just makes the text scroll on update/receive of messages
            final Layout layout = text.getLayout();
            if(layout != null){
                int scrollDelta = layout.getLineBottom(text.getLineCount() - 1)
                        - text.getScrollY() - text.getHeight();
                if(scrollDelta > 0)
                    text.scrollBy(0, scrollDelta);
            }
        }
    }

    class updateUIImage implements Runnable {
        private Bitmap bitmap;
        public updateUIImage(Bitmap bitmap) {this.bitmap = bitmap; }
        @Override
        public void run() {
            contactImage.setImageBitmap(bitmap);
            // code below just makes the text scroll on update/receive of messages
            /*final Layout layout = text.getLayout();
            if(layout != null){
                int scrollDelta = layout.getLineBottom(text.getLineCount() - 1)
                        - text.getScrollY() - text.getHeight();
                if(scrollDelta > 0)
                    text.scrollBy(0, scrollDelta);
            }*/
        }
    }

    class makeToast implements Runnable{
        private String msg;
        public makeToast(String msg){ this.msg = msg; }
        @Override
        public void run() {
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle("Really Exit?")
                .setMessage("Are you sure you want to close connection to server?")
                .setNegativeButton(android.R.string.no, null)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface arg0, int arg1) {
                        ClientActivity.super.onBackPressed();
                        // close connection here
                        try {
                            th.interrupt();
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                            runOnUiThread(new makeToast("ERROR:\n" + e.getMessage()));
                        }
                    }
                }).create().show();
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.i("Location", "LOCATION CHANGED!!!");
        this.location = location;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(String provider) {
        Toast.makeText(this, "Enabled new provider " + provider,
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProviderDisabled(String provider) {
        Toast.makeText(this, "Disabled provider " + provider,
                Toast.LENGTH_SHORT).show();
    }

    public void updateClientsListView(Integer numOfClients, List<String> clientsList){
        // TODO: fill in the clientListView with buttons
        // when clicked, these buttons should insert into the editText
        // the name of the client to whom we want to send the command
    }


    /*
    // try if the two overrides wold preserve the connection, but they don't
    // actually, it would be better to close the connection to the server on exit
    // DONE: 11/30/15 - user is asked for a confirmation before leaving the activity,
    //                  which closes the connection
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState){
        savedInstanceState.putString("ipaddr", serverip);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can restore the view hierarchy
        serverip = (String) savedInstanceState.getString("ipaddr");
        super.onRestoreInstanceState(savedInstanceState);
    }
    */
}
