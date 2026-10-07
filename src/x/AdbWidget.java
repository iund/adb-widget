package x;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;
import android.widget.RemoteViews;
import java.io.*;
import java.net.*;
import java.util.*;
import x.adbwifi.R;

public class AdbWidget extends BroadcastReceiver {
    static final String TAG = "AdbWidget";

    static boolean su(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            return p.waitFor() == 0;
        } catch (Exception e) { Log.w(TAG, "su failed", e); return false; }
    }

    static String ip(Context c) {
        try {
            int a = ((WifiManager) c.getSystemService(Context.WIFI_SERVICE)).getConnectionInfo().getIpAddress();
            if (a != 0) return (a & 255) + "." + ((a >> 8) & 255) + "." + ((a >> 16) & 255) + "." + ((a >> 24) & 255);
        } catch (Exception e) { Log.w(TAG, "wifi ip failed", e); }
        try {
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces()))
                for (InetAddress a : Collections.list(n.getInetAddresses()))
                    if (!a.isLoopbackAddress() && a instanceof Inet4Address) return a.getHostAddress();
        } catch (Exception e) { Log.w(TAG, "iface ip failed", e); }
        return null;
    }

    static boolean listening(String ip, int port) {
        Socket s = new Socket();
        try { s.connect(new InetSocketAddress(ip, port), 800); return true; }
        catch (Exception e) { return false; }
        finally { try { s.close(); } catch (Exception e) {} }
    }

    /** Returns true if done via root; otherwise falls back to the Wireless Debugging setting (needs pm grant WRITE_SECURE_SETTINGS). */
    static boolean applyAdb(Context c, boolean on, int port) {
        if (su("setprop service.adb.tcp.port " + (on ? port : -1) + "; stop adbd; start adbd")) return true;
        try { android.provider.Settings.Global.putInt(c.getContentResolver(), "adb_wifi_enabled", on ? 1 : 0); }
        catch (Throwable t) { Log.w(TAG, "no root and no WRITE_SECURE_SETTINGS", t); }
        return false;
    }

    static boolean wirelessOn(Context c) {
        try { return android.provider.Settings.Global.getInt(c.getContentResolver(), "adb_wifi_enabled", 0) == 1; }
        catch (Throwable t) { return false; }
    }

    static final int GREY = 0xCC555555, DARK = 0xCC1A1A1A, BLUE = 0xCC2F5D8A;

    static void render(Context c, String text, int bg) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        v.setTextViewText(R.id.t, text);
        v.setInt(R.id.t, "setBackgroundColor", bg);
        v.setOnClickPendingIntent(R.id.t, PendingIntent.getBroadcast(c, 0,
            new Intent(c, AdbWidget.class).setAction("x.adbwifi.TOGGLE"),
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? 0x04000000 : 0)));
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        m.updateAppWidget(m.getAppWidgetIds(new ComponentName(c, AdbWidget.class)), v);
    }

    static void later(Context c, int code, int ms, int n) {
        Intent i = new Intent(c, AdbWidget.class).setAction("x.adbwifi.REFRESH").putExtra("n", n);
        ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).set(AlarmManager.RTC,
            System.currentTimeMillis() + ms, PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT));
    }

    @Override public void onReceive(final Context c, final Intent i) {
        final String act = i.getAction() == null ? "" : i.getAction();
        new Thread(new Runnable() { public void run() { synchronized (AdbWidget.class) {
            SharedPreferences sp = c.getSharedPreferences("p", 0);
            int port = sp.getInt("port", 5555);
            boolean enabled = sp.getBoolean("enabled", true);
            int n = i.getIntExtra("n", 0);
            if (Build.VERSION.SDK_INT >= 26 && !act.equals("x.adbwifi.REFRESH") && !act.equals("x.adbwifi.TOGGLE")) try { Net26.register(c); } catch (Throwable t) { Log.w(TAG, "netcb", t); }

            if (act.equals("x.adbwifi.CONFIG")) {
                port = i.getIntExtra("port", port);
                sp.edit().putInt("port", port).putBoolean("enabled", true).commit();
                enabled = true;
                sp.edit().putBoolean("root", applyAdb(c, true, port)).commit();
                later(c, 3, 3000, 0);
            } else if (act.equals(Intent.ACTION_BOOT_COMPLETED)) {
                if (enabled && sp.getBoolean("root", true)) applyAdb(c, true, port);
            } else if (act.equals("x.adbwifi.TOGGLE")) {
                String ip0 = ip(c);
                render(c, ip0 == null ? "No network" : ip0 + "\n ", GREY);   // working: address only, second line blank
                boolean root = sp.getBoolean("root", true);
                enabled = root ? !enabled : !wirelessOn(c);
                sp.edit().putBoolean("enabled", enabled).commit();
                applyAdb(c, enabled, port);
                later(c, 3, 3000, 0);
            }

            String ip = ip(c);
            if (!sp.getBoolean("root", true)) {
                // no root: wireless debugging, random port
                enabled = wirelessOn(c);
                if (ip != null && enabled) {
                    if (!listening(ip, port) && Build.VERSION.SDK_INT >= 16)
                        for (int cand : Nsd.findPorts(c, ip))
                            if (listening(ip, cand)) { port = cand; sp.edit().putInt("port", port).commit(); break; }
                }
            }
            boolean on = ip != null && listening(ip, port);
            Log.i(TAG, act + " ip=" + ip + " port=" + port + " enabled=" + enabled + " on=" + on);
            String text; int bg;
            if (ip == null) { text = "No network"; bg = GREY; }
            else if (on) { text = ip + ":" + port + "\nON"; bg = BLUE; }
            else { text = ip + ":" + port + "\nOFF"; bg = DARK; }

            render(c, text, bg);

            // not settled yet (wifi/adbd still coming up): look again shortly
            if ((ip == null || on != enabled) && n < 12) later(c, 1, 10000, n + 1);
        }}}).start();
    }
}
