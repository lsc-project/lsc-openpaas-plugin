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

import java.util.List;
import java.util.Optional;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.client.filter.HttpBasicAuthFilter;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.lsc.configuration.TaskType;
import org.lsc.plugins.connectors.openpaas.beans.Group;
import org.lsc.plugins.connectors.openpaas.beans.GroupItem;
import org.lsc.plugins.connectors.openpaas.beans.GroupWithMembersEmails;
import org.lsc.plugins.connectors.openpaas.beans.GroupWithMembersEmails.Membership;
import org.lsc.plugins.connectors.openpaas.beans.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenpaasDao {
	
	public static final int GROUPS_LIMIT = Integer.MAX_VALUE;
	public static final int MEMBERS_LIMIT = Integer.MAX_VALUE;
	public static final String GROUP_PATH = "/group/api/groups"; 

	protected static final Logger LOGGER = LoggerFactory.getLogger(OpenpaasDao.class);

	private WebTarget groupClient;

	public OpenpaasDao(String url, String username, String password, TaskType task) {
		groupClient = ClientBuilder.newClient()
				.register(new HttpBasicAuthFilter(username, password))
				.register(JacksonFeature.class)
				.target(url)
				.path(GROUP_PATH);
	}
	
	public List<GroupItem> getGroupList() throws ProcessingException, WebApplicationException {
		WebTarget target = groupClient.path("").queryParam("limit", GROUPS_LIMIT);
		LOGGER.debug("GETting group list: " + target.getUri().toString());
		return target.request().get(new GenericType<List<GroupItem>>(){});
	}

	public GroupWithMembersEmails getGroup(String email) throws ProcessingException, WebApplicationException {
		WebTarget groupTarget = groupClient.queryParam("email", email);
		LOGGER.debug("GETting group: " + groupTarget.getUri().toString());
		List<Group> groups = groupTarget.request().get(new GenericType<List<Group>>(){});
		if (groups.isEmpty()) {
			throw new NotFoundException();
		}
		if (groups.size() > 1) {
			throw new ProcessingException(String.format("More than one group (%d) found for email: %s", groups.size(), email));
		}
		Group group = groups.get(0);
		WebTarget membersTarget = groupClient.path(group.id).path("members").queryParam("limit", MEMBERS_LIMIT);
		LOGGER.debug("GETting group members: " + membersTarget.getUri().toString());
		List<Member> members = membersTarget.request().get(new GenericType<List<Member>>(){});
		return new GroupWithMembersEmails(group, members);
	}

	public boolean createGroup(GroupWithMembersEmails newGroup) {
		WebTarget target = groupClient.path("");
		LOGGER.debug("POSTing group: " + target.getUri().toString());
		Response response = target.request().post(Entity.entity(newGroup, MediaType.APPLICATION_JSON_TYPE));
		String rawResponseBody = response.readEntity(String.class);
		response.close();
		if (checkResponse(response)) {
			LOGGER.debug("POST is successful");
			return true;
		} else {
			LOGGER.error(String.format("Error %d (%s - %s) while creating group: %s",
					response.getStatus(),
					response.getStatusInfo(),
					rawResponseBody,
					target.getUri().toString()));
			return false;
		}
	}
	
	public boolean deleteGroup(String email) {
		String groupId = lookForGroup(email).orElseThrow(() -> new NotFoundException());
		WebTarget target = groupClient.path(groupId);
		LOGGER.debug("DELETing group: " + target.getUri().toString());
		Response response = target.request().delete();
		String rawResponseBody = response.readEntity(String.class);
		response.close();
		if (checkResponse(response)) {
			LOGGER.debug("DELETE is successful");
			return true;
		} else {
			LOGGER.error(String.format("Error %d (%s - %s) while deleting group: %s",
					response.getStatus(),
					response.getStatusInfo(),
					rawResponseBody,
					target.getUri().toString()));
			return false;
		}
	}

	public boolean modifyGroup(GroupWithMembersEmails modifiedGroup) {
		WebTarget target = groupClient.path(modifiedGroup.getId());
		LOGGER.debug("POSTing group: " + target.getUri().toString());
		Response response = target.request().post(Entity.entity(modifiedGroup, MediaType.APPLICATION_JSON_TYPE));
		String rawResponseBody = response.readEntity(String.class);
		response.close();
		if (checkResponse(response)) {
			LOGGER.debug("POST is successful");
			return modifyGroupMembership(target, modifiedGroup);
		} else {
			LOGGER.error(String.format("Error %d (%s - %s) while modifying group: %s",
					response.getStatus(),
					response.getStatusInfo(),
					rawResponseBody,
					target.getUri().toString()));
			return false;
		}
	}

	private static boolean checkResponse(Response response) {
		return Status.Family.familyOf(response.getStatus()) == Status.Family.SUCCESSFUL;
	}
	
	private boolean modifyGroupMembership(WebTarget groupTarget, GroupWithMembersEmails group) {
		return addMembersToGroup(groupTarget, group.getMembersToAdd())
			&& removeMembersToGroup(groupTarget, group.getMembersToRemove());
	}

	private boolean addMembersToGroup(WebTarget groupTarget, List<Membership> membersToAdd) {
		if (membersToAdd.size() == 0) {
			return true;
		}
		WebTarget target = groupTarget.path("members").queryParam("action", "add");
		LOGGER.debug("POSTing group: " + target.getUri().toString());
		Response response = target.request().post(Entity.entity(membersToAdd, MediaType.APPLICATION_JSON_TYPE));
		String rawResponseBody = response.readEntity(String.class);
		response.close();
		if (checkResponse(response)) {
			LOGGER.debug("POST is successful");
			return true;
		} else {
			LOGGER.error(String.format("Error %d (%s - %s) while modifying group: %s",
					response.getStatus(),
					response.getStatusInfo(),
					rawResponseBody,
					target.getUri().toString()));
			return false;
		}
	}

	private boolean removeMembersToGroup(WebTarget groupTarget, List<Membership> membersToRemove) {
		if (membersToRemove.size() == 0) {
			return true;
		}
		WebTarget target = groupTarget.path("members").queryParam("action", "remove");
		LOGGER.debug("POSTing group: " + target.getUri().toString());
		Response response = target.request().post(Entity.entity(membersToRemove, MediaType.APPLICATION_JSON_TYPE));
		String rawResponseBody = response.readEntity(String.class);
		response.close();
		if (checkResponse(response)) {
			LOGGER.debug("POST is successful");
			return true;
		} else {
			LOGGER.error(String.format("Error %d (%s - %s) while modifying group: %s",
					response.getStatus(),
					response.getStatusInfo(),
					rawResponseBody,
					target.getUri().toString()));
			return false;
		}
	}

	private Optional<String> lookForGroup(String email) {
		WebTarget userTarget = groupClient.queryParam("email", email);
		LOGGER.debug("GETting group: " + userTarget.getUri().toString());
		List<Group> groups = userTarget.request().get(new GenericType<List<Group>>(){});
		if (groups.isEmpty()) {
			return Optional.empty();
		}
		if (groups.size() > 1) {
			LOGGER.warn(String.format("Too many groups (%d) found for email: %s", groups.size(), email));
			return Optional.empty();
		}
		return Optional.of(groups.get(0).id);
	}
	
}
