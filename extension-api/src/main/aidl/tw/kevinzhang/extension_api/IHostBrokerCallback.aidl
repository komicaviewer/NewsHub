package tw.kevinzhang.extension_api;

import android.os.ParcelFileDescriptor;

oneway interface IHostBrokerCallback {
    void onResult(long requestId, int status, in ParcelFileDescriptor payload);
}
