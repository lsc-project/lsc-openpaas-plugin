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

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.with;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lsc.LscDatasets;
import org.lsc.beans.IBean;
import org.lsc.configuration.PluginConnectionType;
import org.lsc.configuration.PluginDestinationServiceType;
import org.lsc.configuration.ServiceType.Connection;
import org.lsc.configuration.TaskType;
import org.lsc.plugins.connectors.openpaas.generated.OpenpaasGroupService;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import io.restassured.RestAssured;
import io.restassured.authentication.PreemptiveBasicAuthScheme;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;

public class OpenpaasGroupDstServiceTest {
	private static final int ESN_PORT = 8080;
	private static final boolean FROM_SAME_SERVICE = true;
    
	private static DockerComposeContainer<?> esn;
	
	private OpenpaasGroupDstService testee;
	private static TaskType task;
	
    @BeforeAll
    static void setup() {
		esn = new DockerComposeContainer<>(new File("src/test/resources/docker-compose.yml"));
		esn.withEnv("PROVISION", "true")
        	.withExposedService("esn_1", ESN_PORT, 
        			Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(120)))
        	.start();
		
		OpenpaasGroupService openpaasGroupService = mock(OpenpaasGroupService.class);
		PluginDestinationServiceType pluginDestinationService = mock(PluginDestinationServiceType.class);
		PluginConnectionType openpaasConnection = mock(PluginConnectionType.class);
		Connection connection = mock(Connection.class);
		task = mock(TaskType.class);
		
		when(openpaasConnection.getUrl()).thenReturn("http://localhost:"  + ESN_PORT);
		when(openpaasConnection.getUsername()).thenReturn("admin@open-paas.org");
		when(openpaasConnection.getPassword()).thenReturn("secret");
		when(connection.getReference()).thenReturn(openpaasConnection);
		when(openpaasGroupService.getConnection()).thenReturn(connection);
		when(task.getBean()).thenReturn("org.lsc.beans.SimpleBean");
		when(task.getPluginDestinationService()).thenReturn(pluginDestinationService);
		when(pluginDestinationService.getAny()).thenReturn(ImmutableList.of(openpaasGroupService));

		PreemptiveBasicAuthScheme basicAuthScheme = new PreemptiveBasicAuthScheme();
		basicAuthScheme.setUserName("admin@open-paas.org");
		basicAuthScheme.setPassword("secret");
        RestAssured.requestSpecification = new RequestSpecBuilder()
        		.setPort(ESN_PORT)
        		.setAuth(basicAuthScheme)
        		.setContentType(ContentType.JSON)
        		.setAccept(ContentType.JSON)
        		.setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
                .setBasePath("/group/api/groups")
                .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }
    
    @AfterEach
    void cleanAllGroups() {
    	List<String> groupIds = with()
    		.get("")
    		.jsonPath()
			.getList("id");
    	groupIds.forEach(this::deleteGroup);
    }
    
    private void deleteGroup(String id) {
    	with()
    		.delete("/{id}", id)
		.then()
			.statusCode(HttpStatus.SC_NO_CONTENT);
    }
    
    @AfterAll
    static void close() {
    	esn.close();
    }
    
    @Test
    void openpaasGroupApiShouldReturnEmptyByDefault() throws Exception {
    	given()
		.when()
			.get("")
		.then()
			.statusCode(HttpStatus.SC_OK)
			.body("", hasSize(0));
    }
    
    @Test
    void openpaasShouldReturnCreatedWhenCreatingAGroup() {
    	given()
    		.body("{"
				+ "\"name\":\"Test Group\","
				+ "\"email\":\"test-group@open-paas.org\","
				+ "\"members\":["
					+ "\"admin@open-paas.org\","
					+ "\"other@open-paas.org\"]}")
    	.when()
    		.post("")
		.then()
			.statusCode(HttpStatus.SC_CREATED)
			.body("name", equalTo("Test Group"))
			.body("email", equalTo("test-group@open-paas.org"))
			.body("members", hasSize(2));
    }

    @Test
	public void getListPivotsShouldReturnEmptyWhenNoGroup() throws Exception {
        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> listPivots = testee.getListPivots();
        
        assertThat(listPivots).isEmpty();
	}

	@Test
	public void getListPivotsShouldReturnOneWhenOneEmptyGroup() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		String groupId = createGroup(groupName, groupEmail);
		
        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> listPivots = testee.getListPivots();
        
        assertThat(listPivots).containsOnlyKeys(groupId);
        assertThat(listPivots.get(groupId).getStringValueAttribute("name")).isEqualTo(groupName);
        assertThat(listPivots.get(groupId).getStringValueAttribute("email")).isEqualTo(groupEmail);
        assertThat(listPivots.get(groupId).getListValueAttribute("members")).isEmpty();
	}

	@Test
	public void getListPivotsShouldReturnTwoWhenTwoEmptyGroups() throws Exception {
		String name1 = "test group";
		String email1 = "test-group@open-paas.org";
		String name2 = "test group2";
		String email2 = "test-group2@open-paas.org";
		String groupId1 = createGroup(name1, email1);
		String groupId2 = createGroup(name2, email2);
		
        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> listPivots = testee.getListPivots();
        
        assertThat(listPivots).containsOnlyKeys(groupId1, groupId2);
        assertThat(listPivots.get(groupId1).getStringValueAttribute("name")).isEqualTo(name1);
        assertThat(listPivots.get(groupId1).getStringValueAttribute("email")).isEqualTo(email1);
        assertThat(listPivots.get(groupId1).getListValueAttribute("members")).isEmpty();
        assertThat(listPivots.get(groupId2).getStringValueAttribute("name")).isEqualTo(name2);
        assertThat(listPivots.get(groupId2).getStringValueAttribute("email")).isEqualTo(email2);
        assertThat(listPivots.get(groupId2).getListValueAttribute("members")).isEmpty();
	}

	@Test
	public void getListPivotsShouldReturnOneWhenOneGroupWithMembers() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		String groupId = createGroup(groupName, groupEmail, ImmutableList.of("member1@open-paas.org", "member2@open-paas.org"));
		
        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> listPivots = testee.getListPivots();
        
        assertThat(listPivots).containsOnlyKeys(groupId);
        assertThat(listPivots.get(groupId).getStringValueAttribute("name")).isEqualTo(groupName);
        assertThat(listPivots.get(groupId).getStringValueAttribute("email")).isEqualTo(groupEmail);
        assertThat(listPivots.get(groupId).getListValueAttribute("members")).hasSize(2);
	}

	@Test
	public void getListPivotsShouldReturnOneWhenOneGroupWithInternalMember() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		String groupId = createGroup(groupName, groupEmail, ImmutableList.of("user0@open-paas.org"));
		
        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> listPivots = testee.getListPivots();
        
        assertThat(listPivots).containsOnlyKeys(groupId);
        assertThat(listPivots.get(groupId).getStringValueAttribute("name")).isEqualTo(groupName);
        assertThat(listPivots.get(groupId).getStringValueAttribute("email")).isEqualTo(groupEmail);
        assertThat(listPivots.get(groupId).getListValueAttribute("members")).hasSize(1);
	}

	@Test
	public void getBeanShouldReturnNullWhenEmptyDataset() throws Exception {
        testee = new OpenpaasGroupDstService(task);

		assertThat(testee.getBean("id", new LscDatasets(), FROM_SAME_SERVICE)).isNull();
	}

	@Test
	public void getBeanShouldReturnNullWhenNoMatchingId() throws Exception {
        testee = new OpenpaasGroupDstService(task);

		LscDatasets nonExistingIdDataset = new LscDatasets(ImmutableMap.of("id", "nonExistingId"));
		assertThat(testee.getBean("id", nonExistingIdDataset, FROM_SAME_SERVICE))
			.isNull();
	}
	
	@Test
	public void getBeanShouldReturnExternalMemberWhenPresent() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		String member = "member@example.com";
		String groupId = createGroup(groupName, groupEmail, ImmutableList.of(member));

        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> pivots = testee.getListPivots();
        IBean bean = testee.getBean("id", pivots.get(groupId), FROM_SAME_SERVICE);
        
        assertThat(bean.getDatasetFirstValueById("name")).isEqualTo(groupName);
        assertThat(bean.getDatasetFirstValueById("email")).isEqualTo(groupEmail);
        assertThat(bean.getDatasetById("membersEmails")).containsOnly(member);
	}
	
	@Test
	public void getBeanShouldReturnExternalMembersWhenPresent() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		String member1 = "member1@example.com";
		String member2 = "member2@example.com";
		String groupId = createGroup(groupName, groupEmail, ImmutableList.of(member1, member2));

        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> pivots = testee.getListPivots();
        IBean bean = testee.getBean("id", pivots.get(groupId), FROM_SAME_SERVICE);
        
        assertThat(bean.getDatasetFirstValueById("name")).isEqualTo(groupName);
        assertThat(bean.getDatasetFirstValueById("email")).isEqualTo(groupEmail);
        assertThat(bean.getDatasetById("membersEmails")).containsOnly(member1, member2);
	}
	
	@Test
	public void getBeanShouldReturnInternalMemberWhenPresent() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		String member = "user1@open-paas.org";
		String groupId = createGroup(groupName, groupEmail, ImmutableList.of(member));

        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> pivots = testee.getListPivots();
        IBean bean = testee.getBean("id", pivots.get(groupId), FROM_SAME_SERVICE);
        
        assertThat(bean.getDatasetFirstValueById("name")).isEqualTo(groupName);
        assertThat(bean.getDatasetFirstValueById("email")).isEqualTo(groupEmail);
        assertThat(bean.getDatasetById("membersEmails")).containsOnly(member);
	}
	
	@Test
	public void getBeanShouldReturnInternalMembersWhenPresent() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		String member1 = "user1@open-paas.org";
		String member2 = "user2@open-paas.org";
		String groupId = createGroup(groupName, groupEmail, ImmutableList.of(member1, member2));

        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> pivots = testee.getListPivots();
        IBean bean = testee.getBean("id", pivots.get(groupId), FROM_SAME_SERVICE);
        
        assertThat(bean.getDatasetFirstValueById("name")).isEqualTo(groupName);
        assertThat(bean.getDatasetFirstValueById("email")).isEqualTo(groupEmail);
        assertThat(bean.getDatasetById("membersEmails")).containsOnly(member1, member2);
	}

	@Test
	public void getBeanShouldReturnMixedMembersWhenPresent() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		String internalMember = "user1@open-paas.org";
		String externalMember = "member@exemple.com";
		String groupId = createGroup(groupName, groupEmail, ImmutableList.of(internalMember, externalMember));

        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> pivots = testee.getListPivots();
        IBean bean = testee.getBean("id", pivots.get(groupId), FROM_SAME_SERVICE);
        
        assertThat(bean.getDatasetFirstValueById("name")).isEqualTo(groupName);
        assertThat(bean.getDatasetFirstValueById("email")).isEqualTo(groupEmail);
        assertThat(bean.getDatasetById("membersEmails")).containsOnly(internalMember, externalMember);
	}

	private String createGroup(String name, String email) {
    	return with()
			.body("{"
				+ "\"name\":\"" + name + "\","
				+ "\"email\":\"" + email + "\","
				+ "\"members\":[]}")
		.post("")
		.then()
			.statusCode(HttpStatus.SC_CREATED)
			.extract()
			.path("id");
	}

	private String createGroup(String groupName, String groupEmail, ImmutableList<String> members) {
		StringJoiner membersAsString = new StringJoiner("\", \"", "\"", "\"")
			.setEmptyValue("");
		members.forEach(membersAsString::add);
    	return with()
			.body("{"
				+ "\"name\":\"" + groupName + "\","
				+ "\"email\":\"" + groupEmail + "\","
				+ "\"members\":[" + membersAsString.toString() + "]}")
		.post("")
		.then()
			.statusCode(HttpStatus.SC_CREATED)
			.extract()
			.path("id");
	}

}
