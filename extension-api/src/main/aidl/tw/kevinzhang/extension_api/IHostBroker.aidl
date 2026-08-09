package tw.kevinzhang.extension_api;

import android.os.ParcelFileDescriptor;
import tw.kevinzhang.extension_api.IHostBrokerCallback;

/** A revocable, source-scoped Host capability. There is no exported generic broker service. */
oneway interface IHostBroker {
    void execute(long requestId, in ParcelFileDescriptor request, in IHostBrokerCallback callback);
    void cancel(long requestId);
}
