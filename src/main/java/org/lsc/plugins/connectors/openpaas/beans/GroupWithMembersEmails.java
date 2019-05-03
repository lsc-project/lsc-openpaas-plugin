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
package org.lsc.plugins.connectors.openpaas.beans;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.lsc.LscDatasets;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

@JsonIgnoreProperties({"membersToAdd", "membersToRemove"})
public class GroupWithMembersEmails {
	private final String id;
	private final String name;

	private final String email;
	private final String creator;
	private final List<String> members;
	
	private final List<String> membersToAdd;
	private final List<String> membersToRemove;
	
	public GroupWithMembersEmails(Group group, List<Member> members) {
		id = group.id;
		name = group.name;
		email = group.email;
		creator = group.creator;
		this.members = members.stream()
			.map(Member::getEmail)
			.collect(Collectors.toList());
		membersToAdd = ImmutableList.of();
		membersToRemove = ImmutableList.of();
	}
	
	private GroupWithMembersEmails(String id, String name, String email, List<String> membersEmails, List<String> membersToAdd, List<String> membersToRemove) {
		this.id = id;
		this.name = name;
		this.email = email;
		this.members = membersEmails;
		this.membersToAdd = membersToAdd;
		this.membersToRemove = membersToRemove;
		this.creator = null;
	}

	public static GroupWithMembersEmails fromModifications(Map<String, List<Object>> modificationsItems) {
		String name = getFirstValueAsString(modificationsItems, "name", null);
		String email = getFirstValueAsString(modificationsItems, "email", null);
		List<String> membersEmails = getMembers(modificationsItems);
		String id = null;
		return new GroupWithMembersEmails(id, name, email, ImmutableList.copyOf(membersEmails), ImmutableList.of(), ImmutableList.of());
	}

	public GroupWithMembersEmails modify(Map<String, List<Object>> modificationsItems) {
		String name = getFirstValueAsString(modificationsItems, "name", this.name);
		String email = getFirstValueAsString(modificationsItems, "email", this.email);
		List<String> newMembers = getMembers(modificationsItems);
		List<String> membersToAdd = Lists.newArrayList(newMembers);
		membersToAdd.removeAll(members);
		List<String> membersToRemove = Lists.newArrayList(members);
		membersToRemove.removeAll(newMembers);
		return new GroupWithMembersEmails(id, name, email, ImmutableList.copyOf(newMembers), ImmutableList.copyOf(membersToAdd), ImmutableList.copyOf(membersToRemove));
	}

	private static String getFirstValueAsString(Map<String, List<Object>> modificationsItems, String key, String defaultValue) {
		return Optional.ofNullable(modificationsItems.get(key))
			.filter(values -> values.size() > 0)
			.map(List::iterator)
			.map(Iterator::next)
			.map(String::valueOf)
			.orElse(defaultValue);
	}

	private static List<String> getMembers(Map<String, List<Object>> modificationsItems) {
		return Optional.ofNullable(modificationsItems.get("members"))
			.map(list -> list.stream()
				.map(String::valueOf)
				.collect(Collectors.toList()))
			.orElse(ImmutableList.of());
	}
	
	public LscDatasets toDatasets() {
		LscDatasets datasets = new LscDatasets();
		datasets.put("id", getId());
		datasets.put("name", name);
		datasets.put("email", email);
		datasets.put("creator", creator);
		datasets.put("members", members);
		return datasets;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public String getCreator() {
		return creator;
	}

	public List<String> getMembers() {
		return members;
	}
	
	public List<Membership> getMembersToAdd() {
		return membersToAdd.stream()
			.map(Membership::fromEmail)
			.collect(Collectors.toList());
	}
	
	public List<Membership> getMembersToRemove() {
		return membersToRemove.stream()
			.map(Membership::fromEmail)
			.collect(Collectors.toList());
	}
	
	public static class Membership {
		private final String member;
		private final String objectType;
		
		private Membership(String member, String objectType) {
			this.member = member;
			this.objectType = objectType;
		}
		
		public static Membership fromEmail(String email) {
			return new Membership(email, "email");
		}
		
		public static Membership fromId(String id) {
			return new Membership(id, "user");
		}
		
		public String getObjectType() {
			return objectType;
		}
		
		public String getId() {
			return member;
		}
	}
}
