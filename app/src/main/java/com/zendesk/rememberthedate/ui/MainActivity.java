package com.zendesk.rememberthedate.ui;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.zendesk.logger.Logger;
import com.zendesk.rememberthedate.BuildConfig;
import com.zendesk.rememberthedate.R;
import com.zendesk.rememberthedate.model.UserProfile;
import com.zendesk.rememberthedate.push.GcmUtil;
import com.zendesk.rememberthedate.push.RegistrationIntentService;
import com.zendesk.rememberthedate.storage.PushNotificationStorage;
import com.zendesk.rememberthedate.storage.UserProfileStorage;
import com.zendesk.sdk.network.impl.DeviceInfo;
import com.zendesk.sdk.model.access.JwtIdentity;
import com.zendesk.sdk.model.request.CustomField;
import com.zendesk.sdk.network.impl.ZendeskConfig;
import com.zendesk.util.FileUtils;
import com.zendesk.util.StringUtils;
import com.zopim.android.sdk.api.ZopimChat;
import com.zopim.android.sdk.model.VisitorInfo;

import com.zendesk.sdk.model.access.AnonymousIdentity;
import com.zendesk.sdk.model.access.Identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    public static final String EXTRA_VIEWPAGER_POSITION = "extra_viewpager_pos";
    public static final int VIEWPAGER_POS_DATES = 0;
    public static final int VIEWPAGER_POS_HELP = 1;

    private static final long TICKET_FORM_ID = 62599L;
    private static final long TICKET_FIELD_APP_VERSION = 24328555L;
    private static final long TICKET_FIELD_OS_VERSION = 24273979L;
    private static final long TICKET_FIELD_DEVICE_MODEL = 24273989L;
    private static final long TICKET_FIELD_DEVICE_MEMORY = 24273999L;
    private static final long TICKET_FIELD_DEVICE_FREE_SPACE = 24274009L;
    private static final long TICKET_FIELD_DEVICE_BATTERY_LEVEL = 24274019L;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    ViewPager mViewPager;

    private UserProfileStorage mStorage;
    private PushNotificationStorage mPushStorage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Logger.setLoggable(true);
        initialiseSdk();
    }

    @Override
    protected void onResume() {
        super.onResume();
        initialisePush();
    }

    void initialiseSdk() {
        mStorage = new UserProfileStorage(this);
        mPushStorage = new PushNotificationStorage(this);

        final UserProfile profile = mStorage.getProfile();
        if (StringUtils.hasLength(profile.getEmail())) {
            Logger.i("Identity", "Setting identity");
            Identity identity = new AnonymousIdentity.Builder()
                    .withNameIdentifier(profile.getName())
                    .withEmailIdentifier(profile.getEmail())
                    .build();
            ZendeskConfig.INSTANCE.setIdentity(identity);

            // Init Zopim Visitor info
            final VisitorInfo.Builder build = new VisitorInfo.Builder()
                    .email(profile.getEmail());

            if (StringUtils.hasLength(profile.getName())) {
                build.name(profile.getName());
            }

            ZopimChat.setVisitorInfo(build.build());
        }

        ZendeskConfig.INSTANCE.setTicketFormId(TICKET_FORM_ID);
        ZendeskConfig.INSTANCE.setCustomFields(getCustomFields());
    }

    private List<CustomField> getCustomFields(){
        final Map deviceInfo = new DeviceInfo(this).getDeviceInfoAsMapForMetaData();

        final String appVersion = String.format(
                Locale.US,
                "version_%s",
                BuildConfig.VERSION_NAME
        );

        final String osVersion = String.format(
                Locale.US,
                "Android %s, Version %s",
                deviceInfo.get("device_os"), deviceInfo.get("device_api")
        );

        final String deviceModel = String.format(
                Locale.US,
                "%s, %s, %s",
                deviceInfo.get("device_model"), deviceInfo.get("device_name"), deviceInfo.get("device_manufacturer")
        );

        final int totalMemory = bytesToMegabytes(Long.parseLong(deviceInfo.get("device_total_memory").toString()));
        final int usedMemory = bytesToMegabytes(Long.parseLong(deviceInfo.get("device_used_memory").toString()));
        final String memoryUsage = String.format(
                Locale.US,
                this.getString(R.string.rate_my_app_dialog_feedback_device_memory),
                totalMemory,
                usedMemory
        );

        final StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
        final long bytesAvailable = (long)stat.getBlockSize() * (long)stat.getAvailableBlocks();
        final String freeSpace = FileUtils.humanReadableFileSize(bytesAvailable);

        final String batteryLevel = String.format(Locale.US, "%.1f %s", getBatteryLevel(), "%");

        final List<CustomField> customFields = new ArrayList<>();
        customFields.add(new CustomField(TICKET_FIELD_APP_VERSION, appVersion));
        customFields.add(new CustomField(TICKET_FIELD_OS_VERSION, osVersion));
        customFields.add(new CustomField(TICKET_FIELD_DEVICE_MODEL, deviceModel));
        customFields.add(new CustomField(TICKET_FIELD_DEVICE_MEMORY, memoryUsage));
        customFields.add(new CustomField(TICKET_FIELD_DEVICE_FREE_SPACE, freeSpace));
        customFields.add(new CustomField(TICKET_FIELD_DEVICE_BATTERY_LEVEL, batteryLevel));

        return customFields;
    }

    private int bytesToMegabytes(long bytes) {
        return (int)(Math.round(bytes / 1024.0 / 1024.0));
    }

    public float getBatteryLevel() {
        final Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        final int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        final int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        if(level == -1 || scale == -1) {
            return 50.0f;
        }

        return ((float)level / (float)scale) * 100.0f;
    }

    void initialisePush(){
        // Check if we already saved the device' push identifier.
        // If not, enable push.
        if(!mPushStorage.hasPushIdentifier()) {
            enablePush();
        }
    }

    void enablePush(){
        if(GcmUtil.checkPlayServices(this)){
            RegistrationIntentService.start(this);
        }
    }
}
