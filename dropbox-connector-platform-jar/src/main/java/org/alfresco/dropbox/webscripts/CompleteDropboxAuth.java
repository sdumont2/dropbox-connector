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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.fikatechnologies.dropbox.DropboxConnector;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.servlet.WebScriptServletRuntime;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

/**
 * 
 *
 * @author Jared Ottley
 */
public class CompleteDropboxAuth
    extends DeclarativeWebScript
{
    private static Log          logger   = LogFactory.getLog(CompleteDropboxAuth.class);

    private DropboxConnector dropboxConnector;

    private static final String VERIFIER = "verifier";
    private static final String CALLBACKURL = "/share/service/dropbox/account/verifier";


    public void setDropboxConnector(DropboxConnector dropboxConnector)
    {
        this.dropboxConnector = dropboxConnector;
    }


    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        Map<String, Object> model = new HashMap<String, Object>();

        boolean authenticated = false;

        if (req.getParameter(VERIFIER) != null)
        {
            logger.error("Verifier: " + req.getParameter(VERIFIER));
            HttpServletRequest httpReq = WebScriptServletRuntime.getHttpServletRequest(req);
            HttpSession session = httpReq.getSession(true);
            String sessionKey = "dropbox-auth-csrf-token";
            DbxSessionStore csrfTokenStore = new DbxStandardSessionStore(session, sessionKey);

            authenticated = dropboxConnector.completeAuthentication(req.getServerPath()+CALLBACKURL, csrfTokenStore, httpReq.getParameterMap());
            logger.debug("Dance Complete: " + authenticated);
        }

        model.put("success", authenticated);

        return model;
    }
}
