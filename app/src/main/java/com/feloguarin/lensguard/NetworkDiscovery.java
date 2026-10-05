package com.feloguarin.lensguard;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.SystemClock;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Asks the connected Wi-Fi network which devices answer standard ONVIF (WS-Discovery) and UPnP
 * (SSDP) discovery requests. It listens for a few seconds and never connects to a device.
 */
final class NetworkDiscovery {
    interface Listener {
        /** Called on the main thread for each reply. */
        void onReply(DiscoveryMessages.Reply reply);
        /** Called once on the main thread; failure is null after a normal listening period. */
        void onFinished(int replies, String failure);
    }

    static final long LISTEN_MS = 6_000;
    private static final long RESEND_MS = 1_500;
    private static final int MAX_REPLIES = 128;

    private final Context context;
    private final Handler main;
    private volatile boolean cancelled;
    private volatile DatagramSocket socket;

    NetworkDiscovery(Context context, Handler main) {
        this.context = context.getApplicationContext();
        this.main = main;
    }

    /** Returns false, without sending anything, when no Wi-Fi network is connected. */
    boolean start(Listener listener) {
        Network network = wifiNetwork();
        if (network == null) return false;
        Thread thread = new Thread(() -> run(network, listener), "LensGuard-discovery");
        thread.start();
        return true;
    }

    void stop() {
        cancelled = true;
        DatagramSocket open = socket;
        if (open != null) open.close();
    }

    @SuppressWarnings("deprecation") // getAllNetworks() finds Wi-Fi even when mobile data is the default route.
    private Network wifiNetwork() {
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity == null) return null;
        try {
            Network active = connectivity.getActiveNetwork();
            if (isWifi(connectivity, active)) return active;
            for (Network network : connectivity.getAllNetworks()) if (isWifi(connectivity, network)) return network;
        } catch (RuntimeException unavailable) {
            return null;
        }
        return null;
    }

    private static boolean isWifi(ConnectivityManager connectivity, Network network) {
        if (network == null) return false;
        NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    private void run(Network network, Listener listener) {
        int replies = 0;
        String failure = null;
        DatagramSocket open = null;
        try {
            open = new DatagramSocket();
            socket = open;
            if (cancelled) return;
            network.bindSocket(open);
            open.setSoTimeout(300);
            InetAddress group = InetAddress.getByName(DiscoveryMessages.MULTICAST_GROUP);
            sendRequests(open, group);
            long started = SystemClock.elapsedRealtime();
            boolean resent = false;
            byte[] buffer = new byte[DiscoveryMessages.MAX_REPLY_BYTES];
            while (!cancelled && SystemClock.elapsedRealtime() - started < LISTEN_MS && replies < MAX_REPLIES) {
                // UDP requests can be lost; a second round follows the protocols' own guidance.
                if (!resent && SystemClock.elapsedRealtime() - started > RESEND_MS) {
                    sendRequests(open, group);
                    resent = true;
                }
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    open.receive(packet);
                } catch (SocketTimeoutException quiet) {
                    continue;
                }
                String sender = packet.getAddress() == null ? null : packet.getAddress().getHostAddress();
                String payload = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                DiscoveryMessages.Reply reply = DiscoveryMessages.parse(payload, sender);
                if (reply == null) continue;
                replies++;
                main.post(() -> { if (!cancelled) listener.onReply(reply); });
            }
        } catch (IOException | RuntimeException error) {
            if (!cancelled) failure = error.getClass().getSimpleName();
        } finally {
            if (open != null) open.close();
            socket = null;
            final int count = replies;
            final String result = failure;
            main.post(() -> { if (!cancelled) listener.onFinished(count, result); });
        }
    }

    private static void sendRequests(DatagramSocket socket, InetAddress group) throws IOException {
        for (String types : new String[] {"dn:NetworkVideoTransmitter", "tds:Device"}) {
            byte[] probe = DiscoveryMessages.wsDiscoveryProbe(UUID.randomUUID().toString(), types);
            socket.send(new DatagramPacket(probe, probe.length, group, DiscoveryMessages.WS_DISCOVERY_PORT));
        }
        byte[] search = DiscoveryMessages.ssdpSearch();
        socket.send(new DatagramPacket(search, search.length, group, DiscoveryMessages.SSDP_PORT));
    }
}
