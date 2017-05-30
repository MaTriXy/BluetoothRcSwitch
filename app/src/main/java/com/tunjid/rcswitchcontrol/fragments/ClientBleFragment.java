package com.tunjid.rcswitchcontrol.fragments;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.Snackbar;
import android.support.transition.AutoTransition;
import android.support.transition.TransitionManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.tunjid.androidbootstrap.core.components.ServiceConnection;
import com.tunjid.androidbootstrap.core.view.ViewHider;
import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment;
import com.tunjid.rcswitchcontrol.activities.MainActivity;
import com.tunjid.rcswitchcontrol.adapters.RemoteSwitchAdapter;
import com.tunjid.rcswitchcontrol.dialogfragments.NameServiceDialogFragment;
import com.tunjid.rcswitchcontrol.dialogfragments.RenameSwitchDialogFragment;
import com.tunjid.rcswitchcontrol.model.RcSwitch;
import com.tunjid.rcswitchcontrol.services.ClientBleService;
import com.tunjid.rcswitchcontrol.services.ServerNsdService;

import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

import static android.content.Context.MODE_PRIVATE;
import static com.tunjid.rcswitchcontrol.Application.isServiceRunning;
import static com.tunjid.rcswitchcontrol.model.RcSwitch.SWITCH_PREFS;
import static com.tunjid.rcswitchcontrol.services.ClientBleService.ACTION_CONTROL;
import static com.tunjid.rcswitchcontrol.services.ClientBleService.ACTION_SNIFFER;
import static com.tunjid.rcswitchcontrol.services.ClientBleService.BLUETOOTH_DEVICE;
import static com.tunjid.rcswitchcontrol.services.ServerNsdService.SERVICE_NAME_KEY;

public class ClientBleFragment extends BaseFragment
        implements
        //ServiceConnection,
        View.OnClickListener,
        RemoteSwitchAdapter.SwitchListener,
        RenameSwitchDialogFragment.SwitchNameListener,
        NameServiceDialogFragment.ServiceNameListener {

    private static final String TAG = ClientBleFragment.class.getSimpleName();

    private boolean isDeleting;

    private BluetoothDevice bluetoothDevice;

    private View progressBar;
    private Button sniffButton;
    private TextView connectionStatus;
    private RecyclerView switchList;

    private ViewHider viewHider;

    private List<RcSwitch> switches;

    private RcSwitch.SwitchCreator switchCreator;

    private final IntentFilter intentFilter = new IntentFilter();
    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            switch (action) {
                case ClientBleService.ACTION_GATT_CONNECTED:
                case ClientBleService.ACTION_GATT_CONNECTING:
                case ClientBleService.ACTION_GATT_DISCONNECTED:
                    onConnectionStateChanged(action);
                    break;
                case ACTION_CONTROL: {
                    byte[] rawData = intent.getByteArrayExtra(ClientBleService.DATA_AVAILABLE_CONTROL);

                    toggleProgress(rawData[0] == 0);
                    break;
                }
                case ACTION_SNIFFER: {
                    byte[] rawData = intent.getByteArrayExtra(ClientBleService.DATA_AVAILABLE_SNIFFER);

                    switch (switchCreator.getState()) {
                        case RcSwitch.ON_CODE:
                            switchCreator.withOnCode(rawData);
                            break;
                        case RcSwitch.OFF_CODE:
                            RcSwitch rcSwitch = switchCreator.withOffCode(rawData);
                            rcSwitch.setName("Switch " + (switches.size() + 1));

                            if (!switches.contains(rcSwitch)) {
                                switches.add(rcSwitch);
                                switchList.getAdapter().notifyDataSetChanged();

                                RcSwitch.saveSwitches(switches);
                            }
                            break;
                    }

                    toggleSniffButton();
                    toggleProgress(false);
                    break;
                }
            }

            Log.i(TAG, "Received data for: " + action);
        }
    };

    private final ServiceConnection<ClientBleService> bleConnection = new ServiceConnection<>(
            ClientBleService.class,
            new ServiceConnection.BindCallback<ClientBleService>() {
                @Override
                public void onServiceBound(ClientBleService service) {
                    onConnectionStateChanged(service.getConnectionState());
                }
            }
    );

    private final ServiceConnection<ServerNsdService> serverConnection = new ServiceConnection<>(
            ServerNsdService.class,
            new ServiceConnection.BindCallback<ServerNsdService>() {
                @Override
                public void onServiceBound(ServerNsdService service) {
                    getActivity().invalidateOptionsMenu();
                }
            }
    );

    public static ClientBleFragment newInstance(BluetoothDevice bluetoothDevice) {
        ClientBleFragment fragment = new ClientBleFragment();
        Bundle args = new Bundle();
        args.putParcelable(BLUETOOTH_DEVICE, bluetoothDevice);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        bluetoothDevice = getArguments().getParcelable(BLUETOOTH_DEVICE);

        intentFilter.addAction(ClientBleService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(ClientBleService.ACTION_GATT_CONNECTING);
        intentFilter.addAction(ClientBleService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(ClientBleService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(ClientBleService.ACTION_CONTROL);
        intentFilter.addAction(ClientBleService.ACTION_SNIFFER);
        intentFilter.addAction(ClientBleService.DATA_AVAILABLE_UNKNOWN);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        switchCreator = new RcSwitch.SwitchCreator();
        switches = RcSwitch.getSavedSwitches();

        View rootView = inflater.inflate(R.layout.fragment_ble_client, container, false);
        AppBarLayout appBarLayout = (AppBarLayout) rootView.findViewById(R.id.app_bar_layout);

        sniffButton = (Button) rootView.findViewById(R.id.sniff);
        progressBar = rootView.findViewById(R.id.progress_bar);

        connectionStatus = (TextView) rootView.findViewById(R.id.connection_status);
        switchList = (RecyclerView) rootView.findViewById(R.id.switch_list);

        viewHider = new ViewHider(rootView.findViewById(R.id.button_container), ViewHider.BOTTOM);

        sniffButton.setOnClickListener(this);

        switchList.setAdapter(new RemoteSwitchAdapter(this, switches));
        switchList.setLayoutManager(new LinearLayoutManager(getActivity()));
        switchList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy == 0) return;
                if (dy > 0) viewHider.hide();
                else viewHider.show();
            }
        });

        ItemTouchHelper helper = new ItemTouchHelper(swipeCallBack);
        helper.attachToRecyclerView(switchList);

        final AtomicInteger lastOffSet = new AtomicInteger(0);

        appBarLayout.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                if (verticalOffset == 0) return;
                if (verticalOffset > lastOffSet.get()) viewHider.hide();
                else viewHider.show();

                lastOffSet.set(verticalOffset);
            }
        });

        toggleSniffButton();
        return rootView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(gattUpdateReceiver, intentFilter);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        getToolBar().setTitle(R.string.switches);

        Activity activity = getActivity();

        Bundle extras = new Bundle();
        extras.putParcelable(BLUETOOTH_DEVICE, getArguments().getParcelable(BLUETOOTH_DEVICE));

        bleConnection.with(activity).setExtras(extras).bind();

        if (activity.getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE).getBoolean(ServerNsdService.SERVER_FLAG, false)) {
            serverConnection.with(activity).start();
            serverConnection.with(activity).bind();
            getActivity().invalidateOptionsMenu();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // If the service is already bound, there will be no service connection callback
        if (bleConnection.isBound()) {
            bleConnection.getBoundService().onAppForeGround();
            onConnectionStateChanged(bleConnection.getBoundService().getConnectionState());
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_ble_client, menu);

        if (bleConnection.isBound()) {
            ClientBleService clientBleService = bleConnection.getBoundService();
            menu.findItem(R.id.menu_connect).setVisible(!clientBleService.isConnected());
            menu.findItem(R.id.menu_disconnect).setVisible(clientBleService.isConnected());
        }
        menu.findItem(R.id.menu_start_nsd).setVisible(!isServiceRunning(ServerNsdService.class));
        menu.findItem(R.id.menu_restart_nsd).setVisible(isServiceRunning(ServerNsdService.class));

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (bleConnection.isBound()) {
            switch (item.getItemId()) {
                case R.id.menu_connect:
                    bleConnection.getBoundService().connect(bluetoothDevice);
                    return true;
                case R.id.menu_disconnect:
                    bleConnection.getBoundService().disconnect();
                    return true;
                case R.id.menu_start_nsd:
                    NameServiceDialogFragment.newInstance().show(getChildFragmentManager(), "");
                    break;
                case R.id.menu_restart_nsd:
                    if (serverConnection.isBound()) serverConnection.getBoundService().restart();
                    break;
                case R.id.menu_forget:
                    LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(new Intent(ServerNsdService.ACTION_STOP));

                    ClientBleService clientBleService = bleConnection.getBoundService();

                    clientBleService.disconnect();
                    clientBleService.close();

                    getActivity().getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE).edit()
                            .remove(ClientBleService.LAST_PAIRED_DEVICE)
                            .remove(ServerNsdService.SERVER_FLAG).apply();

                    Intent intent = new Intent(getActivity(), MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    startActivity(intent);
                    getActivity().finish();
                    return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (bleConnection.isBound()) bleConnection.getBoundService().onAppBackground();
    }

    @Override
    public void onDestroyView() {
        // Do not receive broadcasts when view is destroyed
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(gattUpdateReceiver);

        switchList = null;
        progressBar = null;
        sniffButton = null;
        connectionStatus = null;

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        bleConnection.unbindService();
        serverConnection.unbindService();
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.sniff:
                toggleProgress(true);

                if (bleConnection.isBound()) {
                    bleConnection.getBoundService().writeCharacteristicArray(
                            ClientBleService.C_HANDLE_CONTROL,
                            new byte[]{ClientBleService.STATE_SNIFFING});
                }
                break;
        }
    }

    @Override
    public void onLongClicked(RcSwitch rcSwitch) {
        RenameSwitchDialogFragment.newInstance(rcSwitch).show(getChildFragmentManager(), "");
    }

    @Override
    public void onSwitchToggled(RcSwitch rcSwitch, boolean state) {
        if (bleConnection.isBound()) {
            bleConnection.getBoundService().writeCharacteristicArray(
                    ClientBleService.C_HANDLE_TRANSMITTER,
                    rcSwitch.getTransmission(state));
        }
    }

    @Override
    public void onSwitchRenamed(RcSwitch rcSwitch) {
        switchList.getAdapter().notifyItemChanged(switches.indexOf(rcSwitch));
        RcSwitch.saveSwitches(switches);
    }

    @Override
    public void onServiceNamed(String name) {
        Activity activity = getActivity();

        activity.getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE)
                .edit().putString(SERVICE_NAME_KEY, name)
                .putBoolean(ServerNsdService.SERVER_FLAG, true).apply();

        serverConnection.with(activity).start();
        serverConnection.with(activity).bind();
    }

    private void onConnectionStateChanged(String newState) {
        getActivity().invalidateOptionsMenu();
        String text = null;
        switch (newState) {
            case ClientBleService.ACTION_GATT_CONNECTED:
                text = getString(R.string.connected);
                break;
            case ClientBleService.ACTION_GATT_CONNECTING:
                text = getString(R.string.connecting);
                break;
            case ClientBleService.ACTION_GATT_DISCONNECTED:
                text = getString(R.string.disconnected);
                break;
        }
        connectionStatus.setText(getResources().getString(R.string.connection_state, text));
    }

    private void toggleSniffButton() {
        String state = switchCreator.getState().equals(RcSwitch.ON_CODE)
                ? getString(R.string.on)
                : getString(R.string.off);
        sniffButton.setText(getResources().getString(R.string.sniff_code, state));
    }

    private void toggleProgress(boolean show) {
        TransitionManager.beginDelayedTransition((ViewGroup) sniffButton.getParent(), new AutoTransition());

        sniffButton.setVisibility(show ? View.INVISIBLE : View.VISIBLE);
        progressBar.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
    }

    private ItemTouchHelper.SimpleCallback swipeCallBack = new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

        @Override
        public int getDragDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            return 0;
        }

        @Override
        public int getSwipeDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            if (isDeleting) return 0;
            return super.getSwipeDirs(recyclerView, viewHolder);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {

            if (isDeleting) return;
            isDeleting = true;

            View rootView = getView();

            if (rootView != null) {
                int position = viewHolder.getAdapterPosition();
                DeletionHandler deletionHandler = new DeletionHandler(position, switches.size());

                deletionHandler.push(switches.get(position));
                switches.remove(position);

                switchList.getAdapter().notifyItemRemoved(position);

                Snackbar.make(rootView, R.string.deleted_switch, Snackbar.LENGTH_LONG)
                        .addCallback(deletionHandler)
                        .setAction(R.string.undo, deletionHandler)
                        .show();
            }
        }
    };

    /**
     * Handles queued deletion of a Switch
     */
    private class DeletionHandler extends Snackbar.Callback implements View.OnClickListener {

        int originalPosition;
        int originalListSize;

        private Stack<RcSwitch> deletedItems = new Stack<>();

        DeletionHandler(int originalPosition, int originalListSize) {
            this.originalPosition = originalPosition;
            this.originalListSize = originalListSize;
        }

        @Override
        public void onDismissed(Snackbar snackbar, int event) {
            isDeleting = false;
            RcSwitch.saveSwitches(switches);
        }

        @Override
        public void onClick(View v) {
            if (!deletedItems.isEmpty()) {
                switches.add(originalPosition, pop());
                switchList.getAdapter().notifyItemInserted(originalPosition);
            }
            isDeleting = false;
        }

        RcSwitch push(RcSwitch item) {
            return deletedItems.push(item);
        }

        RcSwitch pop() {
            return deletedItems.pop();
        }
    }
}
