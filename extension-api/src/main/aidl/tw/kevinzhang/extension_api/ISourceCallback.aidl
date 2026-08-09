package tw.kevinzhang.extension_api;

import android.os.ParcelFileDescriptor;

/** Results are delivered through a fresh, one-way pipe. */
oneway interface ISourceCallback {
    void onResult(long requestId, int status, in ParcelFileDescriptor payload);
}
