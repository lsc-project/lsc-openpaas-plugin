/*
 ****************************************************************************
 * Ldap Synchronization Connector provides tools to synchronize
 * electronic identities from a list of data sources including
 * any database with a JDBC connector, another LDAP directory,
 * flat files...
 *
 *                  ==LICENSE NOTICE==
 * 
 * Copyright (c) 2008 - 2019 LSC Project 
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:

 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *     * Neither the name of the LSC Project nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *                  ==LICENSE NOTICE==
 *
 *               (c) 2008 - 2019 LSC Project
 *         Raphael Ouazana <rouazana@linagora.com>
 ****************************************************************************
 */
package org.lsc.plugins.connectors.openpaas;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;

import org.apache.commons.lang.NotImplementedException;
import org.lsc.LscDatasets;
import org.lsc.LscModifications;
import org.lsc.beans.IBean;
import org.lsc.configuration.PluginConnectionType;
import org.lsc.configuration.TaskType;
import org.lsc.exception.LscServiceCommunicationException;
import org.lsc.exception.LscServiceConfigurationException;
import org.lsc.exception.LscServiceException;
import org.lsc.plugins.connectors.openpaas.beans.GroupItem;
import org.lsc.plugins.connectors.openpaas.generated.OpenpaasGroupService;
import org.lsc.plugins.connectors.openpaas.generated.OpenpaasService;
import org.lsc.service.IWritableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

public class OpenpaasGroupDstService implements IWritableService {
	
	protected static final Logger LOGGER = LoggerFactory.getLogger(OpenpaasGroupDstService.class);
	/**
	 * Preceding the object feeding, it will be instantiated from this class.
	 */
	private final Class<IBean> beanClass;

	private final OpenpaasService service;
	private final PluginConnectionType connexion;

	private final OpenpaasDao openpaasDao;
	
	/**
	 * Create the service
	 * @param task the task in which the source service settings will be used
	 * @throws LscServiceConfigurationException
	 * @throws LscServiceCommunicationException
	 */
	@SuppressWarnings("unchecked")
	public OpenpaasGroupDstService(final TaskType task) throws LscServiceConfigurationException, LscServiceCommunicationException {
		try {
	        if (task.getPluginDestinationService().getAny() == null || task.getPluginDestinationService().getAny().size() != 1 || !((task.getPluginDestinationService().getAny().get(0) instanceof OpenpaasGroupService))) {
	            throw new LscServiceConfigurationException("Unable to identify the openpaas service configuration " + "inside the plugin source node of the task: " + task.getName());
	        }
	        
        	service = (OpenpaasService) task.getPluginDestinationService().getAny().get(0);
			beanClass = (Class<IBean>) Class.forName(task.getBean());
			connexion = (PluginConnectionType) service.getConnection().getReference();
			
			openpaasDao = new OpenpaasDao(connexion.getUrl(), connexion.getUsername(), connexion.getPassword(), task);
			
		} catch (ClassNotFoundException e) {
			throw new LscServiceConfigurationException(e);
		}
	}
	
	@Override
	public IBean getBean(String pivotName, LscDatasets pivotAttributes, boolean fromSameService)
			throws LscServiceException {
		LOGGER.debug(String.format("Call to getBean(%s, %s, %b)", pivotName, pivotAttributes, fromSameService));
		throw new NotImplementedException();
	}

	@Override
	public Map<String, LscDatasets> getListPivots() throws LscServiceException {
		try {
			List<GroupItem> groupList = openpaasDao.getGroupList();

			Map<String, LscDatasets> listPivots = new HashMap<String, LscDatasets>();
			for (GroupItem group: groupList) {
				listPivots.put(group.id, group.toDatasets());
			}
			return ImmutableMap.copyOf(listPivots);
		} catch (ProcessingException e) {
			LOGGER.error(String.format("ProcessingException while getting pivot list (%s)", e));
			LOGGER.debug(e.toString(), e);
			throw new LscServiceCommunicationException(e);
		} catch (WebApplicationException e) {
			LOGGER.error(String.format("WebApplicationException while getting pivot list (%s)", e));
			LOGGER.debug(e.toString(), e);
			throw new LscServiceException(e);
		}
	}

	@Override
	public boolean apply(LscModifications lm) throws LscServiceException {
		throw new NotImplementedException();
	}

	@Override
	public List<String> getWriteDatasetIds() {
		return service.getWritableAttributes().getString();
	}
}
