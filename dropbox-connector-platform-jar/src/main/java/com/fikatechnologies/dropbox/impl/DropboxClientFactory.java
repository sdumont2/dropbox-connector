package com.fikatechnologies.dropbox.impl;

import com.dropbox.core.DbxAppInfo;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxWebAuth;
import com.dropbox.core.v2.DbxClientV2;


public class DropboxClientFactory {

    private DbxRequestConfig dbxRequestConfig;
    private DbxWebAuth dbxWebAuth;

    public DropboxClientFactory(String appKey, String appSecret){
        DbxAppInfo dbxAppInfo = new DbxAppInfo(appKey, appSecret);
        this.dbxRequestConfig  = new DbxRequestConfig("alfresco/2.0");
        this.dbxWebAuth = new DbxWebAuth(dbxRequestConfig, dbxAppInfo);
    }

    public DbxWebAuth getDbxWebAuth() {
        return this.dbxWebAuth;
    }

    public DbxClientV2 createClient(String accessToken){
        return new DbxClientV2(this.dbxRequestConfig, accessToken);
    }
}
