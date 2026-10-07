package com.mujeer.floatingblocker;

import android.app.Application;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Installs a default uncaught-exception handler that records the crash
 * (see CrashLogStorage) before letting the normal crash/close behavior
 * happen - this app deliberately avoids relying on logcat (not
 * practically accessible on a non-rooted device), so a real crash needs
 * to write down its own stack trace somewhere it can actually be read:
 * MainActivity shows it on the next open. This never swallows a crash or
 * changes what happens after one - it only adds a write before handing
 * off to whatever handler was already there (the system's default one,
 * which still force-closes the app exactly as before).
 */
public class FloatingBlockerApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        final Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        final Application context = this;

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                try {
                    StringWriter sw = new StringWriter();
                    ex.printStackTrace(new PrintWriter(sw));
                    new CrashLogStorage(context).recordCrash(sw.toString());
                } catch (Exception e) {
                    // Best effort - never let the crash-recording itself block the real crash handling.
                }
                if (previousHandler != null) {
                    previousHandler.uncaughtException(thread, ex);
                }
            }
        });
    }
}
