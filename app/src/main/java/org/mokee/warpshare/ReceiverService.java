package org.mokee.warpshare;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import org.mokee.warpshare.airdrop.AirDropManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import okio.BufferedSink;
import okio.Okio;
import okio.Source;

import static android.app.NotificationManager.IMPORTANCE_MIN;
import static android.bluetooth.le.BluetoothLeScanner.EXTRA_CALLBACK_TYPE;
import static android.bluetooth.le.BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT;
import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_FIRST_MATCH;
import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_MATCH_LOST;
import static org.mokee.warpshare.airdrop.AirDropManager.STATUS_OK;

public class ReceiverService extends Service implements AirDropManager.ReceiverListener {

    static final String ACTION_SCAN_RESULT = "org.mokee.warpshare.SCAN_RESULT";

    private static final String TAG = "ReceiverService";

    private static final String NOTIFICATION_CHANNEL_SERVICE = "receiver";

    private static final int NOTIFICATION_ACTIVE = 1;

    private final Set<String> devices = new HashSet<>();

    private boolean mRunning = false;

    private AirDropManager mAirDropManager;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        mAirDropManager = new AirDropManager(this);

        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        final NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_SERVICE,
                getString(R.string.notif_recv_service_channel), IMPORTANCE_MIN);
        channel.enableLights(false);
        channel.enableVibration(false);
        channel.setShowBadge(false);

        Objects.requireNonNull(nm).createNotificationChannel(channel);

        startForeground(NOTIFICATION_ACTIVE, new Notification.Builder(this, NOTIFICATION_CHANNEL_SERVICE)
                .setContentTitle(getString(R.string.notif_recv_active_title))
                .setContentText(getString(R.string.notif_recv_active_description))
                .setSmallIcon(R.drawable.ic_discoverable_24dp)
                .setOngoing(true)
                .build());

        if (mAirDropManager.ready() != STATUS_OK) {
            Log.w(TAG, "Hardware not ready, quit");
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        mAirDropManager.destroy();
        stopForeground(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent == null ? null : intent.getAction();
        if (ACTION_SCAN_RESULT.equals(action)) {
            final int callbackType = intent.getIntExtra(EXTRA_CALLBACK_TYPE, 0);
            final List<ScanResult> results = intent.getParcelableArrayListExtra(EXTRA_LIST_SCAN_RESULT);
            handleScanResult(callbackType, results);
        }
        return START_STICKY;
    }

    private void handleScanResult(int callbackType, List<ScanResult> results) {
        if (results != null) {
            if (callbackType == CALLBACK_TYPE_FIRST_MATCH) {
                for (ScanResult result : results) {
                    devices.add(result.getDevice().getAddress());
                }
            } else if (callbackType == CALLBACK_TYPE_MATCH_LOST) {
                for (ScanResult result : results) {
                    devices.remove(result.getDevice().getAddress());
                }
            }
        }

        if (mRunning && devices.isEmpty()) {
            Log.d(TAG, "Peers lost, sleep");

            mAirDropManager.stopDiscoverable();

            stopSelf();

            mRunning = false;
        } else if (!mRunning && !devices.isEmpty()) {
            Log.d(TAG, "Peers discovering");

            if (mAirDropManager.ready() != STATUS_OK) {
                Log.w(TAG, "Hardware not ready, quit");
                stopSelf();
                return;
            }

            mAirDropManager.startDiscoverable(this);

            mRunning = true;
        }
    }

    @Override
    public void onAirDropRequest(String name, List<String> fileNames, AirDropManager.ReceiverCallback callback) {
        Log.d(TAG, "Asking from " + name);
        callback.accept();
    }

    @Override
    public void onAirDropTransfer(String fileName, InputStream input) {
        Log.d(TAG, "Transferring " + fileName);
        final File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        final File file = new File(downloadDir, fileName);
        try {
            final Source source = Okio.source(input);
            final BufferedSink sink = Okio.buffer(Okio.sink(file));
            sink.writeAll(source);
            sink.flush();
            sink.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed writing file to " + file.getAbsolutePath(), e);
        }
    }

}