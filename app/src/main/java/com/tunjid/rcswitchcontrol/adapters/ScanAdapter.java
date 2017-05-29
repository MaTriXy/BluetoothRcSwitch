package com.tunjid.rcswitchcontrol.adapters;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.tunjid.androidbootstrap.communications.bluetooth.ScanResultCompat;
import com.tunjid.androidbootstrap.core.text.SpanBuilder;
import com.tunjid.rcswitchcontrol.R;

import java.util.List;

/**
 * Adapter for BLE devices found while sacnning
 */
public class ScanAdapter extends RecyclerView.Adapter<ScanAdapter.ViewHolder> {

    private static final int BLE_DEVICE = 1;

    private List<ScanResultCompat> scanResults;
    private AdapterListener adapterListener;

    public ScanAdapter(AdapterListener adapterListener, List<ScanResultCompat> scanResults) {
        this.adapterListener = adapterListener;
        this.scanResults = scanResults;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        Context context = viewGroup.getContext();
        View itemView = LayoutInflater.from(context).inflate(R.layout.viewholder_scan, viewGroup, false);

        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, final int position) {
        viewHolder.bind(scanResults.get(position), adapterListener);
    }

    @Override
    public int getItemViewType(int position) {
        return BLE_DEVICE;
    }

    @Override
    public int getItemCount() {
        return scanResults.size();
    }

    // ViewHolder for actual content
    static class ViewHolder extends RecyclerView.ViewHolder
            implements
            View.OnClickListener {

        TextView deviceDetails;

        ScanResultCompat result;
        AdapterListener adapterListener;

        ViewHolder(View itemView) {
            super(itemView);

            deviceDetails = (TextView) itemView.findViewById(R.id.text);
            itemView.setOnClickListener(this);
        }

        void bind(ScanResultCompat result, AdapterListener adapterListener) {
            this.result = result;
            this.adapterListener = adapterListener;

            String name = result.getScanRecord() != null ? result.getScanRecord().getDeviceName() : "";
            String address = result.getDevice() != null ? result.getDevice().getAddress() : "";

            deviceDetails.setText(new SpanBuilder(itemView.getContext(), name)
                    .appendNewLine()
                    .appendCharsequence(address)
                    .build()
            );
        }

        @Override
        public void onClick(View v) {
            switch ((v.getId())) {
                case R.id.row_parent:
                    adapterListener.onBluetoothDeviceClicked(result.getDevice());
                    break;
            }
        }
    }

    public interface AdapterListener {
        void onBluetoothDeviceClicked(BluetoothDevice bluetoothDevice);
    }
}
