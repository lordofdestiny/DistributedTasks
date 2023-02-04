package rs.ac.bg.etf.kdp.utils;

import java.util.EventListener;

public interface ConnectionListener extends EventListener {
	void onConnected();
    void onPingComplete(long ping);
    void onConnectionLost();
    void onReconnecting();
    void onReconnected(long ping);
    void onReconnectionFailed();
}
