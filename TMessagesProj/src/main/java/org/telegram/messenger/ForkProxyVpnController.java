package org.telegram.messenger;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

import org.telegram.tgnet.ConnectionsManager;

/**
 * Tele iOS: when a VPN is active on the device the Telegram proxy is usually
 * redundant (and can even break the connection), so while a VPN transport is
 * up the proxy is temporarily disabled and restored as soon as the VPN goes
 * away. The user's "proxy_enabled" preference is never touched — only the
 * live connection state is, exactly like ProxyRotationController does.
 */
public final class ForkProxyVpnController extends ConnectivityManager.NetworkCallback {

    private static final String PREF_KEY = "forkProxyDisableOnVpn";

    private static volatile boolean registered;
    private static volatile boolean suppressActive;

    private ForkProxyVpnController() {}

    public static boolean isEnabled() {
        return MessagesController.getGlobalMainSettings().getBoolean(PREF_KEY, false);
    }

    public static void setEnabled(boolean value) {
        MessagesController.getGlobalMainSettings().edit().putBoolean(PREF_KEY, value).commit();
        // applying the setting must be reflected right away, not only on the
        // next VPN up/down event
        AndroidUtilities.runOnUIThread(ForkProxyVpnController::evaluate);
    }

    public static void init() {
        if (registered) {
            return;
        }
        registered = true;
        try {
            ConnectivityManager cm = getConnectivityManager();
            if (cm != null) {
                NetworkRequest request = new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
                cm.registerNetworkCallback(request, new ForkProxyVpnController());
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        // a VPN can already be connected when the app starts — onAvailable will
        // not fire again for it
        AndroidUtilities.runOnUIThread(ForkProxyVpnController::evaluate);
    }

    @Override
    public void onAvailable(Network network) {
        AndroidUtilities.runOnUIThread(ForkProxyVpnController::evaluate);
    }

    @Override
    public void onLost(Network network) {
        AndroidUtilities.runOnUIThread(ForkProxyVpnController::evaluate);
    }

    private static boolean isVpnConnected() {
        try {
            ConnectivityManager cm = getConnectivityManager();
            if (cm == null) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network[] networks = cm.getAllNetworks();
                if (networks != null) {
                    for (Network network : networks) {
                        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                        if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                            return true;
                        }
                    }
                }
                return false;
            }
            NetworkInfoCompat info = NetworkInfoCompat.getActive(cm);
            return info != null && info.isVpn();
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    private static void evaluate() {
        boolean vpnConnected = isVpnConnected();
        if (vpnConnected) {
            if (!isEnabled() || suppressActive) {
                return;
            }
            if (SharedConfig.isProxyEnabled()) {
                suppressActive = true;
                applyProxy(false);
            }
        } else {
            if (!suppressActive) {
                return;
            }
            suppressActive = false;
            // the user may have switched the proxy off while the VPN was up —
            // only restore when they still want it
            if (MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled", false) && SharedConfig.currentProxy != null) {
                applyProxy(true);
            }
        }
    }

    private static void applyProxy(boolean enabled) {
        if (SharedConfig.currentProxy == null && enabled) {
            return;
        }
        ConnectionsManager.setProxySettings(enabled, SharedConfig.currentProxy != null ? SharedConfig.currentProxy.settings : null);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
    }

    private static ConnectivityManager getConnectivityManager() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return null;
        }
        return (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    // tiny shim so the VPN check also works below API 23 where getAllNetworks is missing
    private static final class NetworkInfoCompat {
        private final android.net.NetworkInfo.State state;
        private final int type;

        private NetworkInfoCompat(android.net.NetworkInfo info) {
            this.state = info != null ? info.getState() : null;
            this.type = info != null ? info.getType() : -1;
        }

        static NetworkInfoCompat getActive(ConnectivityManager cm) {
            return new NetworkInfoCompat(cm.getActiveNetworkInfo());
        }

        boolean isVpn() {
            return state == android.net.NetworkInfo.State.CONNECTED && type == android.net.ConnectivityManager.TYPE_VPN;
        }
    }
}
