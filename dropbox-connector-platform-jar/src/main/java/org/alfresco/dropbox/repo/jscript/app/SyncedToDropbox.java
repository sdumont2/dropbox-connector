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

package org.alfresco.dropbox.repo.jscript.app;


import com.fikatechnologies.dropbox.DropboxConnector;
import org.alfresco.repo.jscript.app.BasePropertyDecorator;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;

import java.io.Serializable;

/**
 * 
 *
 * @author Jared Ottley
 */
public class SyncedToDropbox
    extends BasePropertyDecorator
{

    DropboxConnector dropboxConnector;


    public void setDropboxConnector(DropboxConnector dropboxConnector)
    {
        this.dropboxConnector = dropboxConnector;
    }


    @SuppressWarnings("unchecked")
    public JSONAware decorate(QName propertyName, NodeRef nodeRef, Serializable value)
    {
        JSONObject map = new JSONObject();

        boolean synced = dropboxConnector.isSynced(nodeRef);
        map.put("userIsSyncedToDropbox", synced);

        return map;
    }
}
