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

package org.dropbox.webscripts;


import com.dropbox.core.DbxException;
import com.dropbox.core.DbxSessionStore;
import com.dropbox.core.DbxStandardSessionStore;
import com.dropbox.core.v2.DbxClientV2;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.NoSuchPersonException;
import org.alfresco.service.cmr.security.PersonService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dropbox.DropboxConnector;
import org.dropbox.DropboxConstants;
import org.springframework.extensions.webscripts.*;
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
public class DropboxUser
    extends DeclarativeWebScript
{
    private static final Log          logger             = LogFactory.getLog(DropboxUser.class);

    private PersonService       personService;
    private NodeService         nodeService;

    private DropboxConnector dropboxConnector;

    private static final String AUTHENTICATED      = "authenticated";
    private static final String DISPLAY_NAME       = "display_name";
    private static final String QUOTA_NORMAL       = "quota_normal";
    private static final String QUOTA_SHARED       = "quota_shared";
    private static final String QUOTA              = "quota";
    private static final String EMAIL              = "email";

    private static final String AUTH_URL           = "auth_url";
    private static final String CALLBACK_WEBSCRIPT = "dropbox/account/complete/popup/workflow";
    private static final String CALLBACK_PARAM     = "callback";


    public void setPersonService(PersonService personService)
    {
        this.personService = personService;
        this.personService.setCreateMissingPeople(false);
    }


    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }


    public void setDropboxConnector(DropboxConnector dropboxConnector)
    {
        this.dropboxConnector = dropboxConnector;
    }


    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        Map<String, Object> model = new HashMap<String, Object>();

        try
        {
            NodeRef person = personService.getPerson(AuthenticationUtil.getRunAsUser());

            if (nodeService.hasAspect(person, DropboxConstants.Model.ASPECT_DROBOX_OAUTH))
            {
                if (Boolean.valueOf(nodeService.getProperty(person, DropboxConstants.Model.PROP_OAUTH_COMPLETE).toString()))
                {
                    //Changes here
                    DbxClientV2 profile = dropboxConnector.getClient();

                    model.put(AUTHENTICATED, true);

                    try {
                        model.put(DISPLAY_NAME, profile.users().getCurrentAccount().getName());
                        model.put(QUOTA, profile.users().getSpaceUsage().getAllocation().getIndividualValue().getAllocated());
                        model.put(QUOTA_NORMAL, profile.users().getSpaceUsage().getUsed());
                        model.put(QUOTA_SHARED, profile.users().getSpaceUsage().getAllocation().getTeamValue().getAllocated());
                        model.put(EMAIL, profile.users().getCurrentAccount().getEmail());
                    } catch (DbxException e) {
                        e.printStackTrace();
                    }
                }
                else
                {
                    model.put(AUTHENTICATED, false);
                    model.put(AUTH_URL, getAuthURL(req));
                }
            }
            else
            {
                model.put(AUTHENTICATED, false);
                model.put(AUTH_URL, getAuthURL(req));
            }
        }
        catch (NoSuchPersonException nspe)
        {
            logger.debug(nspe.getMessage());
            throw new WebScriptException(Status.STATUS_NOT_FOUND, "User Not Found");
        }


        return model;
    }


    private String getAuthURL(WebScriptRequest req)
    {
        if (req.getParameter(CALLBACK_PARAM) != null)
        {
            String callbackUrl = req.getParameter(CALLBACK_PARAM) + CALLBACK_WEBSCRIPT;

            HttpServletRequest httpReq = WebScriptServletRuntime.getHttpServletRequest(req);
            HttpSession session = httpReq.getSession(true);
            String sessionKey = "dropbox-auth-csrf-token";
            DbxSessionStore csrfTokenStore = new DbxStandardSessionStore(session, sessionKey);

            return dropboxConnector.getAuthorizeUrl(callbackUrl, csrfTokenStore);
        }
        else
        {
            // Removed to not break profile page
            // throw new WebScriptException(Status.STATUS_NOT_ACCEPTABLE,
            // "Missing Callback Parameter");
            return null;
        }
    }
}
