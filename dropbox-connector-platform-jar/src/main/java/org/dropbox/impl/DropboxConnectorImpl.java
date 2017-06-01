
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
package org.dropbox.impl;

import com.dropbox.core.*;
import com.dropbox.core.http.OkHttp3Requestor;
import com.dropbox.core.v2.DbxClientV2;
import org.alfresco.repo.admin.SysAdminParams;
import org.alfresco.repo.policy.BehaviourFilter;
import org.alfresco.service.cmr.oauth2.OAuth2CredentialsStoreService;
import org.alfresco.service.cmr.remotecredentials.OAuth2CredentialsInfo;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.cmr.site.SiteService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dropbox.DropboxConnector;
import org.dropbox.DropboxConstants;

public class DropboxConnectorImpl implements DropboxConnector
{
	private static Log logger = LogFactory.getLog(DropboxConnectorImpl.class);

	private PersonService personService;
	private NodeService nodeService;
	private PermissionService permissionService;
	private ContentService contentService;
	private SysAdminParams sysAdminParams;
	private AuthorityService authorityService;
	private SiteService siteService;
	private BehaviourFilter behaviourFilter;
	private OAuth2CredentialsStoreService oAuth2CredentialsStoreService;
	private String appKey = "a4yx43gwuxvrtsm";
	private String appSecret = "mwnu3vea9myezhr";

	private static DbxClientV2 client;
	private final DbxAppInfo dbxAppInfo = new DbxAppInfo(appKey, appSecret);
	private final DbxRequestConfig dbxRequestConfig = DbxRequestConfig.newBuilder("alfresco/2.0")
			.withHttpRequestor(new OkHttp3Requestor(OkHttp3Requestor.defaultOkHttpClient()))
			.build();
	private final DbxWebAuth dbxWebAuth = new DbxWebAuth(dbxRequestConfig, dbxAppInfo);

	//client
	public void initClient(){
		if(client == null){
			String accessToken;
			OAuth2CredentialsInfo credsInfo = oAuth2CredentialsStoreService.getPersonalOAuth2Credentials(DropboxConstants.REMOTE_SYSTEM);
			if(credsInfo!=null) {
				accessToken = credsInfo.getOAuthAccessToken();

				client = new DbxClientV2(dbxRequestConfig, accessToken);
			}
		}
	}

	@Override
	public DbxClientV2 getClient() {
		if (client == null) {
			throw new IllegalStateException("Client not initialized.");
		}
		return client;
	}

	@Override
	public DbxWebAuth getDbxWebAuth(){
		return dbxWebAuth;
	}

	@Override
	public String getAuthorizeUrl(String callbackUrl, DbxSessionStore csrfTokenStore){
		String authorizeUrl = null;
		if(callbackUrl != null){

			DbxWebAuth.Request authRequest = DbxWebAuth.newRequestBuilder()
					.withRedirectUri(callbackUrl, csrfTokenStore).build();

			authorizeUrl = dbxWebAuth.authorize(authRequest);
		}
		return authorizeUrl;
	}

	// Start Auth on link account button

	//auth

	//save auth then build client and start using client

	//delink auth

	//user info call

	//file stuff
	//get
	//put
	//post
	//delete

}
