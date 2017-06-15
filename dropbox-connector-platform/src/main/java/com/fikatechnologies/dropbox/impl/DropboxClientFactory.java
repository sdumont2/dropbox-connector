/*
 * Copyright 2011-2012 Alfresco Software Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file is part of an unsupported extension to Alfresco.
 *
 */
package com.fikatechnologies.dropbox.impl;

import com.dropbox.core.DbxAppInfo;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxWebAuth;
import com.dropbox.core.v2.DbxClientV2;

/**
 * Client factory to ingest app key and secret in order to create a functional web auth object and create a client to perform api calls
 * @author Sean Dumont
 */
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
