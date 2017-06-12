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


import com.fikatechnologies.dropbox.DropboxConnector;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.NoSuchPersonException;
import org.alfresco.service.cmr.security.PersonService;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.webscripts.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 
 *
 * @author Jared Ottley
 */
public class LinkDropboxUser
    extends DeclarativeWebScript
{
    private static Log          logger  = LogFactory.getLog(LinkDropboxUser.class);

    private PersonService       personService;
    private NodeService         nodeService;
    private DropboxConnector    dropboxConnector;

    private static final String SUCCESS = "success";
    private static final String AUTH_CODE = "authcode";


    public void setPersonService(PersonService personService)
    {
        this.personService = personService;
        this.personService.setCreateMissingPeople(false);
    }

    public void setDropboxConnector(DropboxConnector dropboxConnector){
        this.dropboxConnector = dropboxConnector;
    }

    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }


    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache)
    {
        Map<String, Object> model = new HashMap<String, Object>();

        Map<String, String> templateArgs = req.getServiceMatch().getTemplateVars();
        String user = templateArgs.get("user");
        try {
            logger.debug("The string is: "+ IOUtils.toString(req.getContent().getInputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try
        {
            NodeRef nodeRef = personService.getPerson(user);

            //TODO AUTH STUFF
            model.put(SUCCESS, true);
        }
        catch (NoSuchPersonException nspe)
        {
            logger.debug(nspe.getMessage());
            throw new WebScriptException(Status.STATUS_NOT_FOUND, "User Not Found");
        }


        return model;
    }
}
