package com.donglan.chrona;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PowerManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.RemoteViews;
import com.donglan.chrona.zju.ZjuSnapshot;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** New-work information only. Closing a notice never edits or completes homework. */
public final class HomeworkNotice extends BroadcastReceiver {
    static final int ID = 73112;
    static final long DURATION = 10 * 60_000L;
    private static final String CHANNEL = "zju_homework_updates";
    private static final String PREFS = "homework_notice";
    private static final DateTimeFormatter DEADLINE = DateTimeFormatter.ofPattern("M月d日 HH:mm");

    static synchronized void show(Context context, List<ZjuSnapshot.Homework> items, List<Long> ids) {
        if (items.size() != ids.size()) throw new IllegalArgumentException("Homework identities do not match notice items");
        refresh(context);
        // Several fetches may finish before the first wake. Keep every still-unseen item.
        java.util.LinkedHashMap<Long, ZjuSnapshot.Homework> pending = new java.util.LinkedHashMap<>();
        if (awaitingScreen(context)) {
            try {
                org.json.JSONArray saved = new org.json.JSONArray(context.getSharedPreferences(PREFS, 0).getString("items", "[]"));
                for (int i = 0; i < saved.length(); i++) {
                    org.json.JSONObject item = saved.getJSONObject(i);
                    pending.put(item.getLong("id"), savedItem(item));
                }
            } catch (org.json.JSONException failure) { throw new IllegalStateException("Invalid pending homework notice", failure); }
        }
        for (int i = 0; i < items.size(); i++) pending.put(ids.get(i), items.get(i));
        publish(context, new java.util.ArrayList<>(pending.values()), new java.util.ArrayList<>(pending.keySet()),
                System.currentTimeMillis() + DURATION, false, !interactive(context));
    }

    static boolean awaitingScreen(Context context) {
        return context.getSharedPreferences(PREFS, 0).getBoolean("waiting_for_screen", false);
    }

    private static boolean interactive(Context context) {
        PowerManager power = context.getSystemService(PowerManager.class);
        return power != null && power.isInteractive();
    }

    /** Record the broadcast's actual time before queued notification/DB work can delay it. */
    static synchronized void screenOn(Context context, long at) {
        var prefs = context.getSharedPreferences(PREFS, 0);
        if (!prefs.getBoolean("waiting_for_screen", false)) return;
        if (!prefs.edit().putBoolean("waiting_for_screen", false).putLong("expiry", at + DURATION)
                .putBoolean("needs_post", true).commit()) {
            android.util.Log.w("ChronaHomework", "Unable to persist first screen-on time");
            return;
        }
        android.util.Log.i("ChronaHomework", "first_screen_on at=" + at + " expiry=" + (at + DURATION));
        action(context, "close", at + DURATION);
        // Arm the durable deadline in the broadcast itself. The process may end
        // before the queued card refresh, but unseen work must not linger then.
        scheduleExpiry(context, at + DURATION, false);
        AgendaReminder.requestRefresh(context);
    }

    /** Application lifetime observer also covers wakes when no agenda countdown is active. */
    static void observeScreen(Context context) {
        Context app = context.getApplicationContext();
        AtomicCapsuleController.initialize(app);
        BroadcastReceiver screen = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                long receivedAt = android.os.SystemClock.elapsedRealtime();
                if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    screenOn(app, System.currentTimeMillis());
                    AtomicCapsuleController.screenOn(app, receivedAt);
                } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) AtomicCapsuleController.screenOff(app);
            }
        };
        IntentFilter events = new IntentFilter(Intent.ACTION_SCREEN_ON);
        events.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(screen, events, Context.RECEIVER_NOT_EXPORTED);
        else app.registerReceiver(screen, events);
    }

    private static void publish(Context context, List<ZjuSnapshot.Homework> items, List<Long> ids,
            long expiry, boolean update, boolean waiting) {
        if (items.size() != ids.size()) throw new IllegalArgumentException("Homework identities do not match notice items");
        if (items.isEmpty()) return;
        var binding = new ZjuHomeworkStore(context).state();
        long capture = binding.captureId;
        if (capture <= 0 || !binding.enabled) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "作业更新", NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent details = PendingIntent.getActivity(context, ID,
                new Intent(context, TaskDetailActivity.class).putExtra("task_id", capture).putExtra("candidate_id", ids.get(0).longValue()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent close = action(context, "close", expiry);
        ZjuSnapshot.Homework first = items.get(0);
        RemoteViews card = card(context, items, details, close, false);
        boolean atomic = VivoAtomicNotification.available(context, CHANNEL);
        boolean same = VivoAtomicNotification.active(manager, ID);
        if (same != atomic) VivoAtomicNotification.end(context, manager, ID);
        Notification.Builder builder = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notifications).setContentTitle(first.title)
                .setContentText(summary(items))
                .setContentIntent(details).setDeleteIntent(close)
                .setOngoing(atomic).setAutoCancel(!atomic).setOnlyAlertOnce(update)
                .setCategory(Notification.CATEGORY_EVENT).setVisibility(Notification.VISIBILITY_PUBLIC)
                .setWhen(expiry - DURATION).setShowWhen(false)
                .setTimeoutAfter(waiting ? 0 : Math.max(1, expiry - System.currentTimeMillis()))
                .setStyle(new Notification.DecoratedCustomViewStyle()).setCustomBigContentView(card);
        if (atomic) {
            // Reuse the registered, verified SuperX transport, with a separate information card.
            builder.addExtras(AtomicCapsuleController.decorate(context, ID, "homework:" + expiry,
                    VivoAtomicNotification.extras(context, first.title, "新作业", deadline(first),
                    "新作业", label(items.size()), details, same, card,
                    card(context, items, details, close, true), "{\"animations\":[]}", "新作业")));
        }
        try {
            org.json.JSONArray saved = new org.json.JSONArray();
            for (int i=0;i<items.size();i++) {
                ZjuSnapshot.Homework item=items.get(i);
                saved.put(new org.json.JSONObject().put("id",ids.get(i)).put("title",item.title)
                        .put("body",item.description == null ? "" : item.description)
                        .put("deadline",item.deadline == null ? org.json.JSONObject.NULL : item.deadline));
            }
            // A background job or receiver may finish immediately after this call. Persist
            // the identity before delivery so another process can reconcile the live notice.
            if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("expiry", expiry)
                    .putLong("capture",capture).putString("items",saved.toString())
                    .putBoolean("waiting_for_screen", waiting).putBoolean("needs_post", true).commit()) {
                VivoAtomicNotification.end(context, manager, ID);
                android.util.Log.w("ChronaHomework", "Unable to persist homework notice");
                return;
            }
            // The provisional expiry identifies this pending batch, but does not expire it.
            // Android receives no timeout/alarm until the first real screen-on.
            if (!NotificationDeliveryStatus.read(context, CHANNEL).canPostReminders()) return;
            manager.notify(ID, builder.build());
            android.util.Log.i("ChronaHomework", "notice_post items=" + items.size() + " waiting=" + waiting + " expiry=" + expiry + " update=" + update);
            context.getSharedPreferences(PREFS, 0).edit().putBoolean("needs_post", false).commit();
            scheduleExpiry(context, expiry, waiting);
            if (waiting || atomic) AgendaReminderLiveService.update(context, false, 0);
        } catch (SecurityException | org.json.JSONException failure) {
            android.util.Log.w("ChronaHomework", "Unable to deliver homework notice", failure);
        }
    }

    private static void scheduleExpiry(Context context, long expiry, boolean waiting) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms == null) return;
        PendingIntent expire = action(context, "expire", expiry);
        if (waiting) alarms.cancel(expire);
        else if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms())
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, expiry, expire);
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, expiry, expire);
    }

    static synchronized void refreshCapsule(Context context) {
        var prefs = context.getSharedPreferences(PREFS, 0);
        if (prefs.getLong("expiry", 0) > 0) prefs.edit().putBoolean("needs_post", true).commit();
    }

    static boolean activeAtomic(Context context) {
        var prefs = context.getSharedPreferences(PREFS, 0);
        return prefs.getLong("expiry", 0) > System.currentTimeMillis()
                && VivoAtomicNotification.available(context, CHANNEL);
    }

    /** Completion, deletion and source/account changes remove only the affected notice items. */
    static synchronized void refresh(Context context) {
        android.content.SharedPreferences prefs=context.getSharedPreferences(PREFS,0);
        if (awaitingScreen(context) && interactive(context)) screenOn(context, System.currentTimeMillis());
        long expiry=prefs.getLong("expiry",0);
        if(expiry<=0)return;
        boolean waiting = awaitingScreen(context);
        ZjuHomeworkStore.State state=new ZjuHomeworkStore(context).state();
        if((!waiting && System.currentTimeMillis()>=expiry) || !state.enabled || state.captureId!=prefs.getLong("capture",0)) {
            dismiss(context,expiry);return;
        }
        try(com.donglan.chrona.data.TaskStore store=new com.donglan.chrona.data.TaskStore(context)) {
            org.json.JSONArray saved=new org.json.JSONArray(prefs.getString("items","[]"));
            java.util.ArrayList<ZjuSnapshot.Homework> remaining=new java.util.ArrayList<>();
            java.util.ArrayList<Long> ids=new java.util.ArrayList<>();
            for(int i=0;i<saved.length();i++) {
                org.json.JSONObject item=saved.getJSONObject(i);long id=item.getLong("id");
                // ID zero is reserved for the explicit no-database-write phone preview.
                if(id>0) {
                    var rows=store.getCandidatesByIds(List.of(id));
                    if(rows.isEmpty() || rows.get(0).completed() || !rows.get(0).isHomework())continue;
                }
                ids.add(id);
                remaining.add(savedItem(item));
            }
            if(remaining.isEmpty())dismiss(context,expiry);
            else if(remaining.size()!=saved.length() || prefs.getBoolean("needs_post", false))publish(context,remaining,ids,expiry,true,waiting);
        }catch(org.json.JSONException failure) {
            android.util.Log.w("ChronaHomework","Unable to read active homework notice",failure);
        }
    }

    private static ZjuSnapshot.Homework savedItem(org.json.JSONObject item) throws org.json.JSONException {
        return new ZjuSnapshot.Homework("notice:" + item.getLong("id"), "", item.getString("title"),
                item.getString("body"), "", "", item.isNull("deadline") ? null : item.getLong("deadline"), false);
    }

    static RemoteViews card(Context context, List<ZjuSnapshot.Homework> items,
            PendingIntent details, PendingIntent close, boolean island) {
        ZjuSnapshot.Homework first = items.get(0);
        UiStyle.Palette colors = UiStyle.colors(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.notification_homework_card);
        views.setInt(R.id.homework_card, "setBackgroundColor", colors.surface);
        AtomicAgendaCard.applyBackground(context, views, R.id.homework_background,
                R.id.homework_background_first, R.id.homework_background_second);
        // The title stays below the camera-safe header rather than beside the cutout.
        views.setViewPadding(R.id.homework_header, 0, island ? AtomicAgendaCard.cameraTopPadding(context) : 0, 0, 0);
        views.setTextViewText(R.id.homework_label, island ? label(items.size()) : first.title);
        views.setTextViewTextSize(R.id.homework_label, android.util.TypedValue.COMPLEX_UNIT_SP, island ? 13 : 18);
        views.setTextColor(R.id.homework_label, island ? colors.primary : colors.text);
        views.setTextViewText(R.id.homework_title, first.title);
        views.setTextColor(R.id.homework_title, colors.text);
        views.setViewVisibility(R.id.homework_title, island ? View.VISIBLE : View.GONE);
        String deadline = !island && items.size() > 1 ? summary(items) : deadline(first);
        views.setTextViewText(R.id.homework_deadline, deadline);
        views.setTextColor(R.id.homework_deadline, colors.muted);
        views.setViewVisibility(R.id.homework_deadline, deadline.isEmpty() ? View.GONE : View.VISIBLE);
        String body = items.size() > 1 ? String.join("\n", items.subList(1, items.size()).stream()
                .map(item -> item.title).collect(java.util.stream.Collectors.toList())) : first.description == null ? "" : first.description.trim();
        views.setTextViewText(R.id.homework_body, body);
        views.setTextColor(R.id.homework_body, colors.muted);
        views.setViewVisibility(R.id.homework_body, body.isEmpty() ? View.GONE : View.VISIBLE);
        android.graphics.drawable.Drawable icon = context.getDrawable(R.drawable.ic_x).mutate();
        icon.setTint(colors.muted);
        Bitmap image = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        icon.setBounds(0, 0, 48, 48); icon.draw(new Canvas(image));
        views.setImageViewBitmap(R.id.homework_close, image);
        views.setOnClickPendingIntent(R.id.homework_close, close);
        views.setOnClickPendingIntent(R.id.homework_card, details);
        return views;
    }

    private static String label(int count) { return count == 1 ? "新作业" : "新作业 · " + count + " 项"; }
    private static String summary(List<ZjuSnapshot.Homework> items) {
        String deadline = deadline(items.get(0));
        if (items.size() == 1) return deadline.isEmpty() ? label(1) : deadline;
        return label(items.size()) + (deadline.isEmpty() ? "" : " · " + deadline);
    }
    private static String deadline(ZjuSnapshot.Homework item) {
        return item.deadline == null ? "" : "截止 " + DEADLINE.format(Instant.ofEpochMilli(item.deadline).atZone(ZoneId.systemDefault()));
    }
    private static PendingIntent action(Context context, String action, long expiry) {
        return PendingIntent.getBroadcast(context, "close".equals(action) ? ID : ID + 1,
                new Intent(context, HomeworkNotice.class).setAction(action).putExtra("expiry", expiry),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    @Override public void onReceive(Context context, Intent intent) {
        receive(context,intent);
    }
    private static synchronized void receive(Context context, Intent intent) {
        long expiry = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("expiry", 0);
        if (expiry <= 0 || expiry != intent.getLongExtra("expiry", 0)) return;
        if ("expire".equals(intent.getAction()) && (awaitingScreen(context) || System.currentTimeMillis() < expiry)) return;
        if (!"close".equals(intent.getAction()) && !"expire".equals(intent.getAction())) return;
        dismiss(context,expiry);
    }
    private static void dismiss(Context context,long expiry) {
        android.util.Log.i("ChronaHomework", "notice_dismiss expiry=" + expiry);
        if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove("expiry").remove("capture").remove("items").remove("waiting_for_screen").remove("needs_post").commit())
            android.util.Log.w("ChronaHomework", "Unable to persist homework notice dismissal");
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) VivoAtomicNotification.end(context, manager, ID);
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms != null) alarms.cancel(action(context, "expire", expiry));
        AgendaReminder.requestRefresh(context);
    }
}
