
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
package org.dropbox;

import com.dropbox.core.DbxSessionStore;
import com.dropbox.core.DbxWebAuth;
import com.dropbox.core.v2.DbxClientV2;

import java.util.Map;

public interface DropboxConnector
{
	// Start Auth on link account button

	//auth

	//save auth then build client and start using client

	//delink auth

	//client
	public DbxClientV2 getClient();
	public String getAuthorizeUrl(String callbackUrl, DbxSessionStore csrfTokenStore);
	public boolean completeAuthentication(String callbackUrl, DbxSessionStore csrfTokenStore, Map<String,String[]> request);
	public DbxWebAuth getDbxWebAuth();

	//user info call

	//file stuff
	//get
	//put
	//post
	//delete

}
