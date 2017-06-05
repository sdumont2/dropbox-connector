package com.fikatechnologies.dropbox.impl;

import com.dropbox.core.DbxAppInfo;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxWebAuth;
import com.dropbox.core.v2.DbxClientV2;


/**
 * Created by sean on 6/5/17.
 */
public class DropboxClientFactory {

    private final DbxRequestConfig dbxRequestConfig;
    private final DbxWebAuth dbxWebAuth;

    public DropboxClientFactory(String appKey, String appSecret){
        DbxAppInfo dbxAppInfo = new DbxAppInfo(appKey, appSecret);
        this.dbxRequestConfig  = new DbxRequestConfig("alfresco/2.0");
        this.dbxWebAuth = new DbxWebAuth(dbxRequestConfig, dbxAppInfo);
    }

    public DbxWebAuth getDbxWebAuth() {
        return dbxWebAuth;
    }

    public DbxClientV2 createClient(String accessToken){
        return new DbxClientV2(dbxRequestConfig, accessToken);
    }
}
