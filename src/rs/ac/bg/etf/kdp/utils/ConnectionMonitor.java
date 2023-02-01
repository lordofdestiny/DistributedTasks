package rs.ac.bg.etf.kdp.utils;

import java.rmi.*;
import java.util.*;

public class ConnectionMonitor implements Runnable {
    public interface Pingable extends Remote {
        void ping(UUID uuid) throws RemoteException;
    }
    private final Pingable server;
    private final UUID uuid;
    private final int interval;
    private final Thread thread;
    private long lastOnlineTime;
    private boolean connected = true;
    private final ArrayList<ConnectionListener> listeners = new ArrayList<>();

    public ConnectionMonitor(Pingable server, int pingInterval, UUID uuid) {
        this.server = server;
        this.uuid = uuid;
        this.interval = pingInterval;
        thread = new Thread(this, "ConnectionMonitor-"+uuid);
        addEventListener(new DefaultListener());
    }

    public void start() {
        thread.start();
    }

    @Override
    public void run() {
        while (true) {
            final var ping = pingServer();
            if (ping.isPresent()) {
                // Ping successful
                listeners.forEach(l -> l.onPingComplete(ping.get()));
                try {
                    //noinspection BusyWait
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    if (Thread.interrupted()) return;
                }
                continue;
            }
            // Ping failed
            listeners.forEach(ConnectionListener::onConnectionLost);
        }
    }

    private void reconnectToServer() {
        while (System.currentTimeMillis() - lastOnlineTime < 60 * 1000) {
            // Reconnecting
            listeners.forEach(ConnectionListener::onReconnecting);
            final var ping = pingServer();
            if (ping.isPresent()) {
                listeners.forEach(l->l.onReconnected(ping.get()));
                return;
            }
        }
        // Reconnection failed
        listeners.forEach(ConnectionListener::onReconnectionFailed);
    }

    private Optional<Long> pingServer() {
        try {
            final var start = System.currentTimeMillis();
            server.ping(uuid);
            final var end = System.currentTimeMillis();
            return Optional.of(end - start);
        } catch (RemoteException e) {
            return Optional.empty();
        }
    }

    private synchronized void setConnected(boolean connected) {
        this.connected = connected;
    }

    public synchronized boolean connected() {
        return connected;
    }

    public void stop() {
        thread.interrupt();
    }

    public void addEventListener(ConnectionListener listener) {
        listeners.add(listener);
    }
    public void removeEventListener(ConnectionListener listener) {
        listeners.remove(listener);
    }

    private class DefaultListener implements ConnectionListener{
        @Override
        public void onPingComplete(long ping) {
            lastOnlineTime = System.currentTimeMillis();
        }
        @Override
        public void onConnectionLost() {
            setConnected(false);
            reconnectToServer();
        }
        @Override
        public void onReconnecting() {
        }
        @Override
        public void onReconnected(long ping) {
            setConnected(true);
        }
        @Override
        public void onReconnectionFailed() {
            setConnected(false);
            stop();
        }
    }
}
