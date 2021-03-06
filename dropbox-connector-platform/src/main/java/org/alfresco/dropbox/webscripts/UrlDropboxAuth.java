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

package org.alfresco.dropbox.webscripts;


import com.dropbox.core.DbxSessionStore;
import com.dropbox.core.DbxStandardSessionStore;
import com.fikatechnologies.dropbox.DropboxConnector;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.webscripts.*;
import org.springframework.extensions.webscripts.servlet.WebScriptServletRuntime;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

/**
 * Change to update from dropbox service class to dropbox connector class
 * Change to update auth
 *
 * @author Jared Ottley
 */
public class UrlDropboxAuth
    extends DeclarativeWebScript
{
    private static Log logger  = LogFactory.getLog(UrlDropboxAuth.class);

    private static final String CALLBACK_WEBSCRIPT = "/share/service/dropbox/account/verifier";
    private static final String CALLBACK_PARAM     = "callback";
    private static final String AUTHURL            = "authURL";

    private DropboxConnector dropboxConnector;


    public void setDropboxConnector(DropboxConnector dropboxConnector)
    {
        this.dropboxConnector = dropboxConnector;
    }


    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        Map<String, Object> model = new HashMap<String, Object>();

        if (req.getParameter(CALLBACK_PARAM) != null)
        {

            String callbackUrl = req.getParameter(CALLBACK_PARAM) + CALLBACK_WEBSCRIPT;

            HttpServletRequest httpReq = WebScriptServletRuntime.getHttpServletRequest(req);
            HttpSession session = httpReq.getSession(true);
            String sessionKey = "dropbox-auth-csrf-token";
            DbxSessionStore csrfTokenStore = new DbxStandardSessionStore(session, sessionKey);
            String authUrl = dropboxConnector.getAuthorizeUrl(callbackUrl, csrfTokenStore);
            logger.debug("Authorization url: "+authUrl);
            model.put(AUTHURL, authUrl);
        }
        else
        {
            throw new WebScriptException(Status.STATUS_NOT_ACCEPTABLE, "Missing Callback Parameter");
        }

        return model;
    }

}
