package x;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Finds this device's own Wireless Debugging port (random, changes) via mDNS. Stale records are possible, so returns all candidates. */
class Nsd {
    static List<Integer> findPorts(Context c, final String myIp) {
        final NsdManager m = (NsdManager) c.getSystemService(Context.NSD_SERVICE);
        final List<Integer> ports = Collections.synchronizedList(new ArrayList<Integer>());
        final LinkedList<NsdServiceInfo> queue = new LinkedList<NsdServiceInfo>();
        final boolean[] busy = {false};
        final CountDownLatch done = new CountDownLatch(1);
        final Object lock = new Object();
        final NsdManager.ResolveListener[] rl = new NsdManager.ResolveListener[1];
        final Runnable[] next = new Runnable[1];
        next[0] = new Runnable() { public void run() {
            NsdServiceInfo s;
            synchronized (lock) { s = queue.poll(); busy[0] = s != null; }
            if (s != null) try { m.resolveService(s, rl[0]); } catch (Exception e) { next[0].run(); }
        }};
        rl[0] = new NsdManager.ResolveListener() {
            public void onResolveFailed(NsdServiceInfo s, int e) { Log.i("AdbWidget", "nsd resolve failed " + e); next[0].run(); }
            public void onServiceResolved(NsdServiceInfo s) {
                String h = s.getHost() == null ? null : s.getHost().getHostAddress();
                Log.i("AdbWidget", "nsd resolved " + s.getServiceName() + " " + h + ":" + s.getPort());
                if (myIp.equals(h) && !ports.contains(s.getPort())) ports.add(s.getPort());
                next[0].run();
            }
        };
        NsdManager.DiscoveryListener dl = new NsdManager.DiscoveryListener() {
            public void onStartDiscoveryFailed(String t, int e) { done.countDown(); }
            public void onStopDiscoveryFailed(String t, int e) {}
            public void onDiscoveryStarted(String t) {}
            public void onDiscoveryStopped(String t) {}
            public void onServiceFound(NsdServiceInfo s) {
                boolean start;
                synchronized (lock) { queue.add(s); start = !busy[0]; if (start) busy[0] = true; }
                if (start) next[0].run();
            }
            public void onServiceLost(NsdServiceInfo s) {}
        };
        try {
            m.discoverServices("_adb-tls-connect._tcp", NsdManager.PROTOCOL_DNS_SD, dl);
            done.await(4, TimeUnit.SECONDS);
            m.stopServiceDiscovery(dl);
        } catch (Exception e) { Log.w("AdbWidget", "nsd", e); }
        return new ArrayList<Integer>(ports);
    }
}
