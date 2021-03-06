/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.internal.telephony.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.net.Uri;
import android.provider.Settings;
import android.telephony.SubscriptionManager;

import com.android.internal.R;

import java.util.Arrays;


public class NotificationChannelController {

    /**
     * list of {@link android.app.NotificationChannel} for telephony service.
     */
    public static final String CHANNEL_ID_ALERT = "alert";
    public static final String CHANNEL_ID_CALL_FORWARD = "callForward";
    public static final String CHANNEL_ID_MOBILE_DATA_STATUS = "mobileDataAlertNew";
    public static final String CHANNEL_ID_SMS = "sms";
    public static final String CHANNEL_ID_VOICE_MAIL = "voiceMail";
    public static final String CHANNEL_ID_WFC = "wfc";

    /** deprecated channel, replaced with @see #CHANNEL_ID_MOBILE_DATA_STATUS */
    private static final String CHANNEL_ID_MOBILE_DATA_ALERT_DEPRECATED = "mobileDataAlert";

    /**
     * Creates all notification channels and registers with NotificationManager. If a channel
     * with the same ID is already registered, NotificationManager will ignore this call.
     */
    private static void createAll(Context context) {
        final NotificationChannel alertChannel = new NotificationChannel(
                CHANNEL_ID_ALERT,
                context.getText(R.string.notification_channel_network_alert),
                NotificationManager.IMPORTANCE_DEFAULT);
        alertChannel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI,
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());

        final NotificationChannel mobileDataStatusChannel = new NotificationChannel(
                CHANNEL_ID_MOBILE_DATA_STATUS,
                context.getText(R.string.notification_channel_mobile_data_status),
                NotificationManager.IMPORTANCE_LOW);
        // allow users to block notifications from system
        mobileDataStatusChannel.setBlockableSystem(true);

        context.getSystemService(NotificationManager.class)
                .createNotificationChannels(Arrays.asList(
                new NotificationChannel(CHANNEL_ID_CALL_FORWARD,
                        context.getText(R.string.notification_channel_call_forward),
                        NotificationManager.IMPORTANCE_LOW),
                new NotificationChannel(CHANNEL_ID_SMS,
                        context.getText(R.string.notification_channel_sms),
                        NotificationManager.IMPORTANCE_HIGH),
                new NotificationChannel(CHANNEL_ID_WFC,
                        context.getText(R.string.notification_channel_wfc),
                        NotificationManager.IMPORTANCE_LOW),
                alertChannel,
                mobileDataStatusChannel));
        // only for update
        if (getChannel(CHANNEL_ID_VOICE_MAIL, context) != null) {
            migrateVoicemailNotificationSettings(context);
        }
        // after channel has been created there is no way to change the channel setting
        // programmatically. delete the old channel and create a new one with a new ID.
        if (getChannel(CHANNEL_ID_MOBILE_DATA_ALERT_DEPRECATED, context) != null) {
            context.getSystemService(NotificationManager.class)
                    .deleteNotificationChannel(CHANNEL_ID_MOBILE_DATA_ALERT_DEPRECATED);
        }
    }

    public NotificationChannelController(Context context) {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_LOCALE_CHANGED);
        intentFilter.addAction(Intent.ACTION_SIM_STATE_CHANGED);
        context.registerReceiver(mBroadcastReceiver, intentFilter);
        createAll(context);
    }

    public static NotificationChannel getChannel(String channelId, Context context) {
        return context.getSystemService(NotificationManager.class)
                .getNotificationChannel(channelId);
    }

    /**
     * migrate deprecated voicemail notification settings to initial notification channel settings
     * {@link VoicemailNotificationSettingsUtil#getRingTonePreference(Context)}}
     * {@link VoicemailNotificationSettingsUtil#getVibrationPreference(Context)}
     * notification settings are based on subId, only migrate if sub id matches.
     * otherwise fallback to predefined voicemail channel settings.
     * @param context
     */
    private static void migrateVoicemailNotificationSettings(Context context) {
        final NotificationChannel voiceMailChannel = new NotificationChannel(
                CHANNEL_ID_VOICE_MAIL,
                context.getText(R.string.notification_channel_voice_mail),
                NotificationManager.IMPORTANCE_DEFAULT);
        voiceMailChannel.enableVibration(
                VoicemailNotificationSettingsUtil.getVibrationPreference(context));
        Uri sound = VoicemailNotificationSettingsUtil.getRingTonePreference(context);
        voiceMailChannel.setSound(
                (sound == null) ? Settings.System.DEFAULT_NOTIFICATION_URI : sound,
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
        context.getSystemService(NotificationManager.class)
                .createNotificationChannel(voiceMailChannel);
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_LOCALE_CHANGED.equals(intent.getAction())) {
                // rename all notification channels on locale change
                createAll(context);
            } else if (Intent.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {
                // migrate voicemail notification settings on sim load
                if (SubscriptionManager.INVALID_SUBSCRIPTION_ID !=
                        SubscriptionManager.getDefaultSubscriptionId()) {
                    migrateVoicemailNotificationSettings(context);
                }
            }
        }
    };
}
