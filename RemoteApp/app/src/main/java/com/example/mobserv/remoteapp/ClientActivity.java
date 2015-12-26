package com.example.mobserv.remoteapp;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.text.Layout;
import android.text.method.ScrollingMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by pacel_000 on 22/10/2015.
 */
public class ClientActivity extends FragmentActivity implements TaskFragment.TaskCallbacks {

    private static final String TAG = ClientActivity.class.getSimpleName();
    private static final boolean DEBUG = true; // Set this to false to disable logs .

    private static final int serverport = 45678;
    private static final String CLIENTS_LIST = "clientsList";
    private static final String TEXT_SCROLL_X = "tScrollX";
    private static final String TEXT_SCROLL_Y = "tScrollY";

    private String serverip = "another dummy IP";
    private TextView text;
    private EditText et;
    private List<String> clientsList;
    private SurfaceView mSurfaceView;
    private ImageView contactImage;
    private CameraPreview preview;
    private boolean nameTaken;

    private static final String TAG_TASK_FRAGMENT = "task_fragment";
    private TaskFragment mTaskFragment;

    private boolean isStreaming = false;
    private ArrayList<String> IpList;

    /* nav drawer */
    private ListView mDrawerList;
    private ArrayAdapter<String> mAdapter;

    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;
    private String mActivityTitle;
    /* nav drawer */

    private List<Subscriber> subscribers;
    final Handler singleTimer = new Handler();
    private List<TimerTask> subscribersTimer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);
        /* nav drawer */
        mDrawerList = (ListView)findViewById(R.id.navList);
        mDrawerLayout = (DrawerLayout)findViewById(R.id.drawer_layout);
        mActivityTitle = getTitle().toString();
        addDrawerItems();
        setupDrawer();
        /* nav drawer */
        text = (TextView) findViewById(R.id.idClientText);
        text.setMovementMethod(new ScrollingMovementMethod());
        et = (EditText) findViewById(R.id.idClientEditText);
        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        contactImage = (ImageView) findViewById(R.id.photo);

        preview = new CameraPreview(this, (SurfaceView) findViewById(R.id.surfaceView));
        preview.setKeepScreenOn(true);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mSurfaceView.setX(metrics.widthPixels + 1);

        FragmentManager fm = getSupportFragmentManager();
        mTaskFragment = (TaskFragment) fm.findFragmentByTag(TAG_TASK_FRAGMENT);

        Intent it = getIntent();
        this.serverip = it.getStringExtra("serverip");

        // If the Fragment is non-null, then it is currently being
        // retained across a configuration change,
        // but otherwise we instantiate a NEW ONE
        if (mTaskFragment == null) {
            Bundle bd = new Bundle();
            bd.putString("serverip", serverip);
            mTaskFragment = new TaskFragment();
            mTaskFragment.setArguments(bd);
            fm.beginTransaction().add(mTaskFragment, TAG_TASK_FRAGMENT).commit();
        }


        if(savedInstanceState == null) { // IF first launch of the activity
            et.setFocusable(false);
        }

        subscribers = new LinkedList<>();
        subscribersTimer  = new LinkedList<>();
    }

    public void onClick(View view) {
        String str = et.getText().toString();
        mTaskFragment.sendMsg(str);
        et.setText(null);
    }

    /**
     * Takes the name of the button and concatenates it to
     * the current composing message
     * @param view (the button)
     */
    public void onClickEnterText(View view) {
        String tmp = et.getText().toString();
        tmp += "/" + ((Button) view).getText().toString();
        et.setText(tmp);
        et.setSelection(et.getText().toString().length());
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
                        // close connection here --> kill the fragment
                        onFragmentCancel();
                    }
                }).create().show();
    }

    public void setClientsList(List<String> clientsList){ this.clientsList = clientsList; }

    class updateUIClientsList implements Runnable{
        Integer numOfClients;
        List<String> clientsList;
        public updateUIClientsList(Integer numOfClients, List<String> clientsList) {
            this.clientsList = clientsList;
            this.numOfClients = numOfClients;
        }
        @Override
        public void run() {
            ViewGroup linearLayout = (ViewGroup) findViewById(R.id.clientsLinearLayout);
            linearLayout.removeAllViews();
            for (String clientName : clientsList){
                // let's keep also own name so we can send msgs to ourselves for debugging purposes
                //if ( !clientName.equalsIgnoreCase(myName) ) {
                    Button bt = new Button(getApplicationContext());
                    bt.setText(clientName);
                    bt.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            onClickEnterText(v);
                        }
                    });
                    bt.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                    linearLayout.addView(bt);
                //}
            }
            setClientsList(clientsList);
        }
    }

    public void setNameTaken(boolean val){
        this.nameTaken = val;
    }

    public void videoClick(View view){
        Intent in1 = new Intent(this, LiveActivity.class);
        in1.putStringArrayListExtra("ipList", IpList);
        startActivity(in1);
    }

    class createNameDialog implements Runnable {
        Boolean alreadyTaken;
        ClientActivity activity;
        public createNameDialog(Boolean alreadyTaken) {
            this.alreadyTaken = alreadyTaken;
            this.activity = ClientActivity.this;
        }

        @Override
        public void run() {
            final EditText name = new EditText(activity);
            name.setHint("Name...");
            if (!alreadyTaken) {
                new AlertDialog.Builder(activity)
                        .setTitle("Please choose a username")
                        .setView(name)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface arg0, int arg1) {
                                mTaskFragment.sendMsg(name.getText().toString());
                            }
                        }).create().show();
            } else {
                new AlertDialog.Builder(activity)
                        .setTitle("Please choose another username")
                        .setMessage("The name you chose had already been picked")
                        .setView(name)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface arg0, int arg1) {
                                mTaskFragment.sendMsg(name.getText().toString());
                            }
                        }).create().show();
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if(clientsList != null)
            outState.putStringArrayList(CLIENTS_LIST, new ArrayList<String>(clientsList));
        outState.putBoolean("nameTaken", nameTaken);
        outState.putInt(TEXT_SCROLL_X, text.getScrollX());
        outState.putInt(TEXT_SCROLL_Y, text.getScrollY());

    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        // code below just makes the text scroll on update/receive of messages
            /*Layout layout = text.getLayout();
            if(layout != null){
                int scrollDelta = layout.getLineBottom(text.getLineCount() - 1)
                        - text.getScrollY() - text.getHeight();
                if(scrollDelta > 0)
                    text.scrollBy(0, scrollDelta);
                Log.d("onRestore", "delta is "+scrollDelta);
            }*/
        final int x = savedInstanceState.getInt(TEXT_SCROLL_X);
        final int y = savedInstanceState.getInt(TEXT_SCROLL_Y);
        text.post(new Runnable() {
            @Override
            public void run() {
                text.scrollTo(x, y);
            }
        });
        List<String> cl = savedInstanceState.getStringArrayList(CLIENTS_LIST);
        if(cl != null)
            runOnUiThread(new updateUIClientsList(cl.size(), cl));
        nameTaken = savedInstanceState.getBoolean("nameTaken");
        if (!nameTaken)
            runOnUiThread(new createNameDialog(false));
    }

    @Override
    public void onShowToast(String str){
        runOnUiThread(new makeToast(str));
    }

    @Override
    public void onChooseName(Boolean taken) {
        runOnUiThread(new createNameDialog(taken));
    }

    @Override
    public void onConnected() {
        runOnUiThread(new makeToast("Connected to " + serverip + " " + serverport));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                et.setFocusableInTouchMode(true);
            }
        });
        setNameTaken(false);
    }

    @Override
    public void onFragmentCancel() {
        mTaskFragment.closeSocket();
        FragmentManager fm = getSupportFragmentManager();
        fm.beginTransaction().remove(mTaskFragment).commit();
        finish();
    }

    @Override
    public void onTextReceived(String str) {
        runOnUiThread(new updateUIThread(str));
    }

    @Override
    public void onImageReceived(byte[] imageByte) {
        //Convert to byte array
        Intent in1 = new Intent(this, PhotoActivity.class);
        in1.putExtra("image",imageByte);
        startActivity(in1);
    }

    @Override
    public String onImageRequested() {
        String result = null;
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            onShowToast("No camera on this device");
        } else {
            try {
                preview.setCamera();
                preview.openSurface();
                result = preview.takePicture();
            } catch (Exception e) {
                Log.d("ERROR", "Failed to config the camera: " + e.getMessage());
            } finally {
                preview.closeSurface();
            }
        }
        return result;
    }

    @Override
    public void onClientListReceived(int numOfClients, List<String> clients) {
        runOnUiThread(new updateUIClientsList(numOfClients, clients));
    }

    @Override
    public void onIpListReceived(int numOfIps, ArrayList<String> ips) {
        Log.d(TAG, "number of ips " + numOfIps);
        //runOnUiThread(new notifyTabStripChanged(1, numOfIps));
        IpList = ips;
    }

    @Override
    public void onWelcome(){
        setNameTaken(true);
    }

    @Override
    public void onExecReceived(String subscriberName, String service) {
        final Subscriber s = new Subscriber(subscriberName,service);
        subscribers.add(s);
        Timer timer = new Timer();
        subscribersTimer.add(new TimerTask() {
            @Override
            public void run() {
                singleTimer.post(new Runnable() {
                    public void run() {
                        // Toast.makeText(getBaseContext(), "try timer", Toast.LENGTH_SHORT).show();
                        LinkedList<String> reply = new LinkedList<>();
                        reply.add("write");
                        reply.add("gps");
                        reply.add(String.valueOf(mTaskFragment.gpsTracker.longitude));
                        reply.add(String.valueOf(mTaskFragment.gpsTracker.latitude));
                        reply.add(String.valueOf(mTaskFragment.gpsTracker.altitude));
                        reply.add("subscription"); // otherwise maps keep opening
                        String msg = composeMsg(s.name, reply);
                        mTaskFragment.sendMsg(msg);
                    }
                });
            }
        });
        timer.schedule(subscribersTimer.get(subscribersTimer.size() - 1), 0, 60000); //it executes this every 60000ms ( 1 minute ) TODO time should be passed
    }

    @Override
    public void onStopTimers() {
        for ( TimerTask t : subscribersTimer ){
            t.cancel();
        }
    }

    public String composeMsg(String to, LinkedList<String> content) {
        String msg = "/"; // <-- leaving field 0 empty
        // Log.d("composeMsg", "to: "+ to+" Content: "+content.toString());
        msg += to;
        if (content == null)
            return msg;
        for (String arg : content) {
            msg += "/" + arg;
        }
        return msg;
    }

    private class Subscriber {
        public String name, service;
        public Subscriber(String n, String s) {
            name = n;
            service = s;
        }
    }

    @Override
    public String onLiveRequested() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            new makeToast("No camera on this device");
        } else{
            if(isStreaming) new makeToast("This device is streaming");
            isStreaming = true;
            preview.liveSetId();
            preview.openSurface();
            preview.onResume();
            return preview.getIpServer() + ":" + String.valueOf(preview.getPortServer());
        }
        return null;
    }

    /************************/
    /***** LOGS & STUFF *****/
    /************************/

    @Override
    protected void onStart() {
        if (DEBUG) Log.i(TAG, "onStart()");
        super.onStart();
    }

    @Override
    protected void onResume() {
        if (DEBUG) Log.i(TAG, "onResume()");
        super.onResume();
    }

    @Override
    protected void onPause() {
        if (DEBUG) Log.i(TAG, "onPause()");
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (DEBUG) Log.i(TAG, "onStop()");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (DEBUG) Log.i(TAG, "onDestroy()");
        super.onDestroy();
    }

    /*
    nav drawer
     */

    private void addDrawerItems() {
        String[] osArray = {"Live Streaming", "Photos"};
        mAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, osArray);
        mDrawerList.setAdapter(mAdapter);

        mDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position){
                    case 0:
                        Intent in1 = new Intent(getBaseContext(), LiveActivity.class);
                        in1.putStringArrayListExtra("ipList", IpList);
                        startActivity(in1);
                        break;
                    case 1:
                        Toast.makeText(getBaseContext(), "no photos yet", Toast.LENGTH_SHORT).show();
                        /*Intent in2 = new Intent(getBaseContext(), PhotoActivity.class);
                        startActivity(in2);*/
                        break;
                }

            }
        });
    }

    private void setupDrawer() {
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close) {
            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
            }
            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
            }
        };
        mDrawerToggle.setDrawerIndicatorEnabled(true);
        mDrawerLayout.setDrawerListener(mDrawerToggle);
    }

    /*
    nav drawer http://blog.teamtreehouse.com/add-navigation-drawer-android
     */

}
