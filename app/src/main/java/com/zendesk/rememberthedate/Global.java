package com.zendesk.rememberthedate;

import android.app.Application;
import android.util.Log;

import com.zendesk.rememberthedate.ui.MainActivity;
import com.zendesk.sdk.model.access.AnonymousIdentity;
import com.zendesk.sdk.model.access.Identity;
import com.zendesk.sdk.network.impl.ZendeskConfig;
import com.zendesk.sdk.support.SupportActivity;
import com.zopim.android.sdk.api.ZopimChat;

public class Global extends Application {

    private final static String LOG_TAG = Global.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();

        if("replace_me_chat_account_id".equals(getString(R.string.zopim_account_id))){
            Log.w(LOG_TAG, "==============================================================================================================");
            Log.w(LOG_TAG, "Zopim chat is not connected to an account, if you wish to try chat please add your Zopim accountId to 'zd.xml'");
            Log.w(LOG_TAG, "==============================================================================================================");
        }

        ZendeskConfig.INSTANCE.init(this, "https://openbusiness.zendesk.com", "fb8af2aebe40464aa4b94eff5eb87cda917390dedf77c501", "mobile_sdk_client_7d00f07e840588dc5f9c");

        ZopimChat.init(getString(R.string.zopim_account_id));;
    }
}
