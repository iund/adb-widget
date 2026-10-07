package x;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

/** API 26+: implicit network broadcasts are gone, so ask the system to fire a PendingIntent on wifi changes. */
class Net26 {
    static void register(Context c) {
        PendingIntent pi = PendingIntent.getBroadcast(c, 2,
            new Intent(c, AdbWidget.class).setAction("x.adbwifi.REFRESH"),
            PendingIntent.FLAG_UPDATE_CURRENT | (android.os.Build.VERSION.SDK_INT >= 31 ? 0x02000000 : 0));
        ((ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE)).registerNetworkCallback(
            new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), pi);
    }
}
