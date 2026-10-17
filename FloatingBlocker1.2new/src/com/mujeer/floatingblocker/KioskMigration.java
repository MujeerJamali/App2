package com.mujeer.floatingblocker;

import android.content.Context;

import java.util.ArrayList;

/**
 * Decision #16: full app-wide fresh-start reset, run exactly once on the
 * first run after this update (see KioskMigrationStorage). Not a
 * surgical/partial migration - wipes Blocks, Holiday Breaks, Alarms,
 * Confirmations, all punishment history, AND Lock Schedule's own
 * configured hours completely clean. Until Lock Schedule is
 * reconfigured, nothing is locked at all and the new Kiosk lockdown has
 * no trigger.
 */
public class KioskMigration {

    public static void runIfNeeded(Context context) {
        KioskMigrationStorage migrationStorage = new KioskMigrationStorage(context);
        if (migrationStorage.hasRunFreshStartReset()) {
            return;
        }

        new BlocksStorage(context).saveBlocks(new ArrayList<Block>());
        new HolidayBreaksStorage(context).saveBreaks(new ArrayList<HolidayBreak>());
        new AlarmsStorage(context).saveAlarms(new ArrayList<Alarm>());
        new ConfirmationsStorage(context).saveConfirmations(new ArrayList<Confirmation>());
        new BlockPunishmentStorage(context).clearEverything();
        new LockScheduleStorage(context).saveRanges(new ArrayList<TimeRange>());

        migrationStorage.markFreshStartResetDone();
    }
}
