package com.tunjid.rcswitchcontrol.fragments;


import android.content.Intent;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.tunjid.androidbootstrap.communications.nsd.DiscoveryListener;
import com.tunjid.androidbootstrap.communications.nsd.NsdHelper;
import com.tunjid.androidbootstrap.communications.nsd.ResolveListener;
import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment;
import com.tunjid.rcswitchcontrol.adapters.NsdAdapter;
import com.tunjid.rcswitchcontrol.services.ClientNsdService;

import java.util.ArrayList;
import java.util.List;

import static android.support.v7.widget.DividerItemDecoration.VERTICAL;

/**
 * A {@link Fragment} listing supported NSD servers
 */
public class NsdScanFragment extends BaseFragment
        implements
        NsdAdapter.ServiceClickedListener {

    private static final long SCAN_PERIOD = 10000;    // Stops scanning after 10 seconds.

    private boolean isScanning;

    private RecyclerView recyclerView;

    private NsdHelper nsdHelper;

    private List<NsdServiceInfo> services = new ArrayList<>();

    public NsdScanFragment() {
        // Required empty public constructor
    }

    public static NsdScanFragment newInstance() {
        NsdScanFragment fragment = new NsdScanFragment();
        Bundle bundle = new Bundle();

        fragment.setArguments(bundle);
        return fragment;
    }

    private DiscoveryListener discoveryListener = new DiscoveryListener() {
        @Override
        public void onServiceFound(NsdServiceInfo service) {
            super.onServiceFound(service);
            nsdHelper.resolveService(service);
        }
    };


    private ResolveListener resolveListener = new ResolveListener() {
        @Override
        public void onServiceResolved(NsdServiceInfo service) {
            super.onServiceResolved(service);

            if (!services.contains(service)) services.add(service);

            // Runs on a diifferent thread, post here
            if (recyclerView != null) recyclerView.post(new Runnable() {
                @Override
                public void run() {
                    recyclerView.getAdapter().notifyDataSetChanged();
                }
            });
        }
    };


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        nsdHelper = NsdHelper.getBuilder(getContext())
                .setDiscoveryListener(discoveryListener)
                .setResolveListener(resolveListener)
                .build();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_nsd_scan, container, false);

        recyclerView = (RecyclerView) rootView.findViewById(R.id.list);

        recyclerView.setAdapter(new NsdAdapter(this, services));
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(getActivity(), VERTICAL));

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        scanDevices(true);
        recyclerView.getAdapter().notifyDataSetChanged();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recyclerView = null;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_nsd_scan, menu);

        menu.findItem(R.id.menu_stop).setVisible(isScanning);
        menu.findItem(R.id.menu_scan).setVisible(!isScanning);

        if (!isScanning) {
            menu.findItem(R.id.menu_refresh).setVisible(false);
        }
        else {
            menu.findItem(R.id.menu_refresh).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(R.layout.actionbar_indeterminate_progress);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                services.clear();
                recyclerView.getAdapter().notifyDataSetChanged();
                scanDevices(true);
                return true;
            case R.id.menu_stop:
                scanDevices(false);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onServiceClicked(NsdServiceInfo serviceInfo) {
        Intent intent = new Intent(getContext(), ClientNsdService.class);
        intent.putExtra(ClientNsdService.NSD_SERVICE_INFO_KEY, serviceInfo);
        getContext().startService(intent);

        showFragment(ClientNsdFragment.newInstance());
    }

    @Override
    public boolean isSelf(NsdServiceInfo serviceInfo) {
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        nsdHelper.tearDown();
    }

    private void scanDevices(boolean enable) {
        isScanning = enable;

        if (enable) nsdHelper.discoverServices();
        else nsdHelper.stopServiceDiscovery();

        getActivity().invalidateOptionsMenu();

        // Stops  after a pre-defined scan period.
        if (enable) {
            recyclerView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    isScanning = false;
                    nsdHelper.stopServiceDiscovery();
                    if (getActivity() != null) getActivity().invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);
        }
    }
}
