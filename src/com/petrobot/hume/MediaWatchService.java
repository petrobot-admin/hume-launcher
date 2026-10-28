package com.petrobot.hume;

import android.content.ComponentName;
import android.content.Context;
import android.service.notification.NotificationListenerService;

/**
 * Notification access for Do Not Disturb (Control Center): only a notification listener may
 * change the interruption filter. Continue Watching no longer comes from here; it reads apps'
 * Watch Next lists (see WatchNext). Android withholds this access on low-RAM TVs:
 *   adb shell cmd notification allow_listener com.petrobot.hume/com.petrobot.hume.MediaWatchService
 * The class keeps its name so access granted to earlier versions still applies.
 */
public class MediaWatchService extends NotificationListenerService {

    /** Live instance while the listener is connected (Control Center uses it for Do Not Disturb). */
    static MediaWatchService instance;

    @Override
    public void onListenerConnected() {
        instance = this;
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
    }

    static boolean granted(Context c) {
        android.app.NotificationManager nm = c.getSystemService(android.app.NotificationManager.class);
        return nm != null && nm.isNotificationListenerAccessGranted(new ComponentName(c, MediaWatchService.class));
    }
}
