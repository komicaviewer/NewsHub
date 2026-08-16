package tw.kevinzhang.extension_api;

import android.os.ParcelFileDescriptor;
import tw.kevinzhang.extension_api.IHostBroker;
import tw.kevinzhang.extension_api.ISourceCallback;

/** Breaking replacement for in-process Source class loading. */
oneway interface ISourceService {
    void execute(
        long requestId,
        int operation,
        in ParcelFileDescriptor request,
        in ISourceCallback callback,
        in IHostBroker broker
    );
    void cancel(long requestId);
    void close();
}
