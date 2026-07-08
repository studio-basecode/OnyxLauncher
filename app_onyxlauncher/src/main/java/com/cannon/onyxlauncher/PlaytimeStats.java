package com.cannon.onyxlauncher;

import android.content.Context;
import android.content.SharedPreferences;

public final class PlaytimeStats {
    private static final String PREFS_ONYX_DATA = "OnyxData";
    private static final long ONE_WEEK_MS = 7L * 24L * 60L * 60L * 1000L;

    private static final String KEY_TOTAL_MS = "stats_total_play_time_ms";
    private static final String KEY_WEEK_MS = "stats_weekly_play_time_ms";
    private static final String KEY_WEEK_RESET_TS = "stats_weekly_reset_ts";
    private static final String KEY_LONGEST_SESSION_MS = "stats_longest_session_ms";
    private static final String KEY_LAST_SESSION_MS = "stats_last_session_ms";
    private static final String KEY_LAST_PLAYED_TS = "stats_last_played_ts";
    private static final String KEY_SESSION_COUNT = "stats_session_count";
    private static final String KEY_INTERRUPTED_COUNT = "stats_interrupted_session_count";
    private static final String KEY_LEGACY_MIGRATED = "stats_legacy_session_migrated";

    private static final String KEY_ACTIVE_START_MS = "stats_active_session_start_ms";
    private static final String KEY_ACTIVE_RECORDED_MS = "stats_active_session_recorded_ms";
    private static final String KEY_ACTIVE_LAST_SEEN_MS = "stats_active_session_last_seen_ms";

    private PlaytimeStats() {}

    public static void startSession(Context context, long startMs) {
        if (context == null || startMs <= 0) return;
        migrateLegacyStatsIfNeeded(context);
        recoverInterruptedSession(context);
        prefs(context).edit()
                .putLong(KEY_ACTIVE_START_MS, startMs)
                .putLong(KEY_ACTIVE_RECORDED_MS, 0L)
                .putLong(KEY_ACTIVE_LAST_SEEN_MS, startMs)
                .commit();
    }

    public static void saveSessionProgress(Context context, long startMs, boolean finalSave) {
        if (context == null || startMs <= 0) return;
        long now = System.currentTimeMillis();
        long sessionDurationMs = Math.max(0L, now - startMs);
        SharedPreferences prefs = prefs(context);
        long recordedMs = prefs.getLong(KEY_ACTIVE_RECORDED_MS, 0L);
        long deltaMs = Math.max(0L, sessionDurationMs - recordedMs);

        SharedPreferences.Editor editor = prefs.edit();
        addPlaytimeDelta(prefs, editor, deltaMs, now);
        editor.putLong(KEY_ACTIVE_RECORDED_MS, sessionDurationMs);
        editor.putLong(KEY_ACTIVE_LAST_SEEN_MS, now);
        if (finalSave) {
            finishSession(prefs, editor, sessionDurationMs, now, false);
            clearActiveSession(editor);
        }
        editor.commit();
    }

    public static boolean recoverInterruptedSession(Context context) {
        if (context == null) return false;
        migrateLegacyStatsIfNeeded(context);
        SharedPreferences prefs = prefs(context);
        long startMs = prefs.getLong(KEY_ACTIVE_START_MS, 0L);
        if (startMs <= 0L) return false;

        long recordedMs = Math.max(0L, prefs.getLong(KEY_ACTIVE_RECORDED_MS, 0L));
        long lastSeenMs = Math.max(startMs, prefs.getLong(KEY_ACTIVE_LAST_SEEN_MS, startMs));
        long recoveredDurationMs = Math.max(recordedMs, lastSeenMs - startMs);
        long missingDeltaMs = Math.max(0L, recoveredDurationMs - recordedMs);

        SharedPreferences.Editor editor = prefs.edit();
        addPlaytimeDelta(prefs, editor, missingDeltaMs, lastSeenMs);
        finishSession(prefs, editor, recoveredDurationMs, lastSeenMs, true);
        clearActiveSession(editor);
        return editor.commit();
    }

    public static Snapshot getSnapshot(Context context) {
        migrateLegacyStatsIfNeeded(context);
        SharedPreferences prefs = prefs(context);
        long totalMs = prefs.getLong(KEY_TOTAL_MS, 0L);
        long sessionCount = prefs.getLong(KEY_SESSION_COUNT, 0L);
        long activeStartMs = prefs.getLong(KEY_ACTIVE_START_MS, 0L);
        long activeRecordedMs = prefs.getLong(KEY_ACTIVE_RECORDED_MS, 0L);
        long activeSessionMs = 0L;
        if (activeStartMs > 0L) {
            activeSessionMs = Math.max(activeRecordedMs, System.currentTimeMillis() - activeStartMs);
        }
        long averageMs = sessionCount > 0L ? totalMs / sessionCount : 0L;
        return new Snapshot(
                totalMs,
                prefs.getLong(KEY_WEEK_MS, 0L),
                prefs.getLong(KEY_LONGEST_SESSION_MS, 0L),
                prefs.getLong(KEY_LAST_SESSION_MS, 0L),
                sessionCount,
                averageMs,
                prefs.getLong(KEY_LAST_PLAYED_TS, 0L),
                prefs.getLong(KEY_INTERRUPTED_COUNT, 0L),
                activeSessionMs
        );
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(
                PREFS_ONYX_DATA,
                Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
    }

    private static void migrateLegacyStatsIfNeeded(Context context) {
        SharedPreferences prefs = prefs(context);
        if (prefs.getBoolean(KEY_LEGACY_MIGRATED, false)) return;

        long totalMs = prefs.getLong(KEY_TOTAL_MS, 0L);
        long longestMs = prefs.getLong(KEY_LONGEST_SESSION_MS, 0L);
        long sessionCount = prefs.getLong(KEY_SESSION_COUNT, 0L);
        SharedPreferences.Editor editor = prefs.edit();
        if (sessionCount == 0L && totalMs > 0L) {
            long estimateBasisMs = longestMs > 0L ? longestMs : totalMs;
            long estimatedCount = Math.max(1L, (totalMs + estimateBasisMs - 1L) / estimateBasisMs);
            editor.putLong(KEY_SESSION_COUNT, estimatedCount);
            if (prefs.getLong(KEY_LAST_SESSION_MS, 0L) == 0L && longestMs > 0L) {
                editor.putLong(KEY_LAST_SESSION_MS, longestMs);
            }
        }
        editor.putBoolean(KEY_LEGACY_MIGRATED, true);
        editor.commit();
    }

    private static void addPlaytimeDelta(
            SharedPreferences prefs,
            SharedPreferences.Editor editor,
            long deltaMs,
            long now) {
        if (deltaMs <= 0L) return;
        editor.putLong(KEY_TOTAL_MS, prefs.getLong(KEY_TOTAL_MS, 0L) + deltaMs);

        long weekResetTs = prefs.getLong(KEY_WEEK_RESET_TS, 0L);
        long weekMs = prefs.getLong(KEY_WEEK_MS, 0L);
        if (weekResetTs <= 0L || now - weekResetTs > ONE_WEEK_MS) {
            weekResetTs = now;
            weekMs = 0L;
            editor.putLong(KEY_WEEK_RESET_TS, weekResetTs);
        }
        editor.putLong(KEY_WEEK_MS, weekMs + deltaMs);
    }

    private static void finishSession(
            SharedPreferences prefs,
            SharedPreferences.Editor editor,
            long durationMs,
            long endMs,
            boolean interrupted) {
        if (durationMs <= 0L) return;
        editor.putLong(KEY_LAST_SESSION_MS, durationMs);
        editor.putLong(KEY_LAST_PLAYED_TS, endMs);
        editor.putLong(KEY_SESSION_COUNT, prefs.getLong(KEY_SESSION_COUNT, 0L) + 1L);
        if (durationMs > prefs.getLong(KEY_LONGEST_SESSION_MS, 0L)) {
            editor.putLong(KEY_LONGEST_SESSION_MS, durationMs);
        }
        if (interrupted) {
            editor.putLong(KEY_INTERRUPTED_COUNT, prefs.getLong(KEY_INTERRUPTED_COUNT, 0L) + 1L);
        }
    }

    private static void clearActiveSession(SharedPreferences.Editor editor) {
        editor.remove(KEY_ACTIVE_START_MS);
        editor.remove(KEY_ACTIVE_RECORDED_MS);
        editor.remove(KEY_ACTIVE_LAST_SEEN_MS);
    }

    public static final class Snapshot {
        public final long totalPlayTimeMs;
        public final long weeklyPlayTimeMs;
        public final long longestSessionMs;
        public final long lastSessionMs;
        public final long sessionCount;
        public final long averageSessionMs;
        public final long lastPlayedTs;
        public final long interruptedSessionCount;
        public final long activeSessionMs;

        private Snapshot(
                long totalPlayTimeMs,
                long weeklyPlayTimeMs,
                long longestSessionMs,
                long lastSessionMs,
                long sessionCount,
                long averageSessionMs,
                long lastPlayedTs,
                long interruptedSessionCount,
                long activeSessionMs) {
            this.totalPlayTimeMs = totalPlayTimeMs;
            this.weeklyPlayTimeMs = weeklyPlayTimeMs;
            this.longestSessionMs = longestSessionMs;
            this.lastSessionMs = lastSessionMs;
            this.sessionCount = sessionCount;
            this.averageSessionMs = averageSessionMs;
            this.lastPlayedTs = lastPlayedTs;
            this.interruptedSessionCount = interruptedSessionCount;
            this.activeSessionMs = activeSessionMs;
        }
    }
}
