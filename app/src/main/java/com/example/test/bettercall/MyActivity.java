package com.example.test.bettercall;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.widget.DrawerLayout;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;

import com.example.test.bettercall.ttt.ArduinoService;
import com.example.test.bettercall.ttt.ErrorDetection;


public class MyActivity extends Activity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;

    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitle;

    private int lastMessage;
    private int lastMessageCounter=0;
    private int checksumErrorCounter=0;

    public static final int LAST_MESSAGE_MAX_RETRY_TIMES= 5; //Number of times that last message is repeated

    public static final int ARDUINO_PROTOCOL_ARQ		= 20; //Automatic repeat request
    public static final int ARDUINO_PROTOCOL_ACK		= 21; //Message received acknowledgment

    private Handler mHandler;
    private ArduinoService mArduinoS;
    private Button button01;
    private Button sendB;

    public MyActivity() {
        this.mHandler = new Handler(){
            public void handleMessage(Message msg) {
                messageReceived(msg);
            }
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle();

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));

        button01 = (Button) findViewById(R.id.button);
        button01.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                debug_info("OK");
                startStop();
            }
        });
        sendB = (Button) findViewById(R.id.button2);
        sendB.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    TextView txt = (TextView) findViewById(R.id.textView2);
                    int number = Integer.parseInt(""+txt.getText());
                    debug_info("Send " + number);
                    sendMessage(number);
                } catch (Exception e) {
                    debug_info("ERROR happened, check number format");
                }
            }
        });
    }


    private void startStop(){
        RadioButton radio = (RadioButton) findViewById(R.id.radioButton);
        if (radio.isChecked()){
            // stop
            stop();
            debug_info(">>Service stoped");
        } else {
            // start
            start();
            debug_info(">>Service started");
        }
        // update UI
        radio.setChecked(! radio.isChecked());
    }
    //-----------------------------------------------
    public void stop(){
        // STOP the arduino service
        if (this.mArduinoS != null)
            this.mArduinoS.stopAndClean();
    }

    public void start(){
        // START the arduino service
        this.mArduinoS = new ArduinoService(this.mHandler);
        new Thread(this.mArduinoS).start();
    }

    private void debug_info(String str) {
        TextView txt = (TextView) findViewById(R.id.textView_debuginfo);
        String info = txt.getText().toString();
        info = str + "\n" + info;
        txt.setText(info);
    }

    private void sendLastMessage(){
        if (lastMessageCounter> LAST_MESSAGE_MAX_RETRY_TIMES) {
            // stop repeating last message, ERROR
            this.debug_info("ERROR sending" + this.lastMessage);
            this.writeMessage(ARDUINO_PROTOCOL_ACK); // send ack to avoid ARQ
            lastMessageCounter=0;
        } else {
            this.sendMessage(lastMessage);
            lastMessageCounter++;
        }
    }

    private void sendMessage(int number){
        checksumErrorCounter=0;
        this.lastMessage = number;
        this.writeMessage(number);
    }

    private void writeMessage(int number){
        this.mArduinoS.write(number);
        debug_info("W:" + number);
    }

    private void messageReceived(Message msg) {
        int value = msg.arg1;
        debug_info("...." + msg.getWhen() + "Msg Received msg=" + value);
        switch (value) {
            case ARDUINO_PROTOCOL_ARQ:
                checksumErrorCounter=0;
                debug_info("....ARQ " + value);
                sendLastMessage();
                break;
            case ErrorDetection.CHECKSUM_ERROR:
                checksumErrorCounter++;
                if (checksumErrorCounter>1){
                    writeMessage(ARDUINO_PROTOCOL_ARQ); //ARQ is two consecutive CHK ERROR received
                    debug_info("....CHK ERROR " + value);
                    checksumErrorCounter=0;
                } else
                    debug_info("....CHK ERROR skipped" + value);
                break;
            case ArduinoService.RECORDING_ERROR:
                checksumErrorCounter=0;
                debug_info("RECORDING ERROR " + value);
                break;
            default:
                checksumErrorCounter=0;
                debug_info("R:" + value);
                this.writeMessage(ARDUINO_PROTOCOL_ACK);
                break;
        }
    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {
        // update the main content by replacing fragments
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.container, PlaceholderFragment.newInstance(position + 1))
                .commit();
    }

    public void onSectionAttached(int number) {
        switch (number) {
            case 1:
                mTitle = getString(R.string.title_section1);
                debug_info("启动");
                button01.setVisibility(View.VISIBLE);
                sendB.setVisibility(View.VISIBLE);
                break;
            case 2:
                mTitle = getString(R.string.title_section2);
                debug_info("设备注册");
                button01.setVisibility(View.INVISIBLE);
                sendB.setVisibility(View.INVISIBLE);
                break;
            case 3:
                mTitle = getString(R.string.title_section3);
                debug_info("关于");
                button01.setVisibility(View.INVISIBLE);
                sendB.setVisibility(View.INVISIBLE);
                break;
        }
    }

    public void restoreActionBar() {
        ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.my, menu);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_my, container, false);
            return rootView;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            ((MyActivity) activity).onSectionAttached(
                    getArguments().getInt(ARG_SECTION_NUMBER));
        }
    }

}
