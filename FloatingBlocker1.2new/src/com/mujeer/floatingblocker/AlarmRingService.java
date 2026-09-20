package com.mujeer.floatingblocker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;

import java.util.List;

/**
 * Runs while an Alarm is actively ringing: loud looping alarm sound,
 * continuous vibration, a full-screen notification that launches
 * AlarmRingActivity over the lock screen, and its own in-process
 * RING_MINUTES timer (the AlarmManager-scheduled deadline in
 * AlarmScheduler/AlarmPunishmentDeadlineReceiver is the backstop in case
 * this service gets killed before its timer fires).
 */
public class AlarmRingService extends Service {

    private static final int NOTIFICATION_ID = 9001;
    private static final String CHANNEL_ID = "alarm_ring_channel";

    private static AlarmRingService runningInstance;
    private static Runnable stopListener;

    private String alarmId;
    private long occurrenceMillis;
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            onTimeout();
        }
    };

    public static boolean isRingingFor(String alarmId, long occurrenceMillis) {
        return runningInstance != null
                && alarmId.equals(runningInstance.alarmId)
                && runningInstance.occurrenceMillis == occurrenceMillis;
    }

    /** AlarmRingActivity registers this while visible, to be told (on the main thread) when ringing stops for any reason. */
    public static void setStopListener(Runnable r) {
        stopListener = r;
    }

    public static void clearStopListener() {
        stopListener = null;
    }

    /** Called by AlarmRingActivity after a correct barcode scan. */
    public static void notifyDismissed(Context context, String alarmId, long occurrenceMillis) {
        if (isRingingFor(alarmId, occurrenceMillis)) {
            AlarmPunisher.markDismissed(context, alarmId, occurrenceMillis);
        }
    }

    /** Called by AlarmPunisher once an occurrence is resolved (dismissed or punished), from either path. */
    public static void stopIfRingingFor(Context context, String alarmId, long occurrenceMillis) {
        if (isRingingFor(alarmId, occurrenceMillis) && runningInstance != null) {
            runningInstance.stopRingingInternal();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            // A prior ring session's resources (e.g. a very close second
            // Alarm firing before the first's foreground service instance
            // has been torn down) would otherwise leak silently - release
            // them first before starting a fresh one.
            handler.removeCallbacks(timeoutRunnable);
            releasePlaybackResources();

            alarmId = intent.getStringExtra(AlarmRingReceiver.EXTRA_ALARM_ID);
            occurrenceMillis = intent.getLongExtra(AlarmRingReceiver.EXTRA_OCCURRENCE_MILLIS, 0);
            runningInstance = this;

            startForegroundNotification();
            startSoundAndVibration();
            handler.postDelayed(timeoutRunnable, Alarm.RING_MINUTES * 60L * 1000L);
        } catch (Exception e) {
            // Never let a problem here crash the whole app - worst case
            // this specific ring attempt is silently lost rather than
            // taking the process down with it.
            Log.e("AlarmRingService", "onStartCommand failed", e);
        }
        return START_NOT_STICKY;
    }

    private void releasePlaybackResources() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.release();
            } catch (Exception e) {
                // Best effort.
            }
            mediaPlayer = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
    }

    private void startForegroundNotification() {
        Intent fullScreenIntent = new Intent(this, AlarmRingActivity.class);
        fullScreenIntent.putExtra(AlarmRingReceiver.EXTRA_ALARM_ID, alarmId);
        fullScreenIntent.putExtra(AlarmRingReceiver.EXTRA_OCCURRENCE_MILLIS, occurrenceMillis);
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(this, 0, fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.alarm_notification_channel_name), NotificationManager.IMPORTANCE_HIGH);
            channel.setSound(null, null); // we play the alarm sound ourselves
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
            builder.setPriority(Notification.PRIORITY_MAX);
        }

        builder.setContentTitle(getString(R.string.alarm_ringing_title))
                .setContentText(getString(R.string.alarm_ringing_text))
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                // The full-screen takeover only launches automatically when
                // the screen is off/locked - if it's already on and
                // unlocked when the alarm fires, Android shows just this
                // notification instead, so it needs its own obvious,
                // tappable way in rather than relying on the user noticing
                // a plain banner is tappable.
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_lock_idle_alarm,
                        getString(R.string.scan_to_dismiss_button),
                        fullScreenPendingIntent).build());

        startForeground(NOTIFICATION_ID, builder.build());
    }

    private void startSoundAndVibration() {
        // Push the alarm stream to max ONCE, right as it starts ringing -
        // this is a one-time nudge, not an ongoing lock. If the user turns
        // it back down with the volume buttons while it's ringing, that's
        // respected; nothing here ever re-forces it back up.
        try {
            android.media.AudioManager audioManager = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            if (audioManager != null) {
                int maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM);
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, maxVolume, 0);
            }
        } catch (Exception e) {
            // Best effort - the alarm still rings at whatever volume was already set.
        }

        try {
            Uri alarmUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mediaPlayer.setDataSource(this, alarmUri);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            Log.e("AlarmRingService", "Could not play alarm sound", e);
        }

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0, 1000, 1000};
            vibrator.vibrate(pattern, 0);
        }
    }

    private void onTimeout() {
        List<Alarm> alarms = new AlarmsStorage(this).loadAlarms();
        for (Alarm a : alarms) {
            if (a.id.equals(alarmId)) {
                AlarmPunisher.resolveMissed(this, a, occurrenceMillis);
                return;
            }
        }
        // Alarm was deleted while ringing - nothing to punish, just stop.
        stopRingingInternal();
    }

    private void stopRingingInternal() {
        handler.removeCallbacks(timeoutRunnable);
        releasePlaybackResources();
        if (stopListener != null) {
            handler.post(stopListener);
        }
        if (runningInstance == this) {
            runningInstance = null;
        }
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (runningInstance == this) {
            runningInstance = null;
        }
        handler.removeCallbacks(timeoutRunnable);
        releasePlaybackResources();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
