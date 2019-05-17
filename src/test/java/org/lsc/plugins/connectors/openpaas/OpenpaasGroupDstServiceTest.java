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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lsc.LscDatasetModification;
import org.lsc.LscDatasetModification.LscDatasetModificationType;
import org.lsc.LscDatasets;
import org.lsc.LscModificationType;
import org.lsc.LscModifications;
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
			.param("limit", String.valueOf(Integer.MAX_VALUE))
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
		createGroup(groupName, groupEmail);
		
        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> listPivots = testee.getListPivots();
        
        assertThat(listPivots).containsOnlyKeys(groupEmail);
        assertThat(listPivots.get(groupEmail).getStringValueAttribute("email")).isEqualTo(groupEmail);
	}

	@Test
	public void getListPivotsShouldReturnTwoWhenTwoEmptyGroups() throws Exception {
		String name1 = "test group";
		String email1 = "test-group@open-paas.org";
		String name2 = "test group2";
		String email2 = "test-group2@open-paas.org";
		createGroup(name1, email1);
		createGroup(name2, email2);
		
        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> listPivots = testee.getListPivots();
        
        assertThat(listPivots).containsOnlyKeys(email1, email2);
        assertThat(listPivots.get(email1).getStringValueAttribute("email")).isEqualTo(email1);
        assertThat(listPivots.get(email2).getStringValueAttribute("email")).isEqualTo(email2);
	}

	@Test
	public void getListPivotsShouldReturnOneWhenOneGroupWithMembers() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		createGroup(groupName, groupEmail, ImmutableList.of("member1@open-paas.org", "member2@open-paas.org"));
		
        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> listPivots = testee.getListPivots();
        
        assertThat(listPivots).containsOnlyKeys(groupEmail);
        assertThat(listPivots.get(groupEmail).getStringValueAttribute("email")).isEqualTo(groupEmail);
	}

	@Test
	public void getListPivotsShouldReturnOneWhenOneGroupWithInternalMember() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		createGroup(groupName, groupEmail, ImmutableList.of("user0@open-paas.org"));
		
        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> listPivots = testee.getListPivots();
        
        assertThat(listPivots).containsOnlyKeys(groupEmail);
        assertThat(listPivots.get(groupEmail).getStringValueAttribute("email")).isEqualTo(groupEmail);
	}

	@Test
	public void getListPivotsShouldReturnOneWhenOneGroupWithSubgroup() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		String subGroupEmail = "subgroup@open-paas.org";
		createGroup("subgroup", subGroupEmail, ImmutableList.of());
		createGroup(groupName, groupEmail, ImmutableList.of(subGroupEmail));
		
        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> listPivots = testee.getListPivots();
        
        assertThat(listPivots).containsOnlyKeys(groupEmail, subGroupEmail);
        assertThat(listPivots.get(groupEmail).getStringValueAttribute("email")).isEqualTo(groupEmail);
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
	public void getBeanShouldReturnMainIndentifierSetToEmail() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		createGroup(groupName, groupEmail);

        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> pivots = testee.getListPivots();
        IBean bean = testee.getBean("id", pivots.get(groupEmail), FROM_SAME_SERVICE);
        
        assertThat(bean.getMainIdentifier()).isEqualTo(groupEmail);
	}
	
	@Test
	public void getBeanShouldReturnExternalMemberWhenPresent() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		String member = "member@example.com";
		createGroup(groupName, groupEmail, ImmutableList.of(member));

        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> pivots = testee.getListPivots();
        IBean bean = testee.getBean("id", pivots.get(groupEmail), FROM_SAME_SERVICE);
        
        assertThat(bean.getDatasetFirstValueById("name")).isEqualTo(groupName);
        assertThat(bean.getDatasetFirstValueById("email")).isEqualTo(groupEmail);
        assertThat(bean.getDatasetById("members")).containsOnly(member);
	}
	
	@Test
	public void getBeanShouldReturnExternalMembersWhenPresent() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		String member1 = "member1@example.com";
		String member2 = "member2@example.com";
		createGroup(groupName, groupEmail, ImmutableList.of(member1, member2));

        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> pivots = testee.getListPivots();
        IBean bean = testee.getBean("id", pivots.get(groupEmail), FROM_SAME_SERVICE);
        
        assertThat(bean.getDatasetFirstValueById("name")).isEqualTo(groupName);
        assertThat(bean.getDatasetFirstValueById("email")).isEqualTo(groupEmail);
        assertThat(bean.getDatasetById("members")).containsOnly(member1, member2);
	}
	
	@Test
	public void getBeanShouldReturnInternalMemberWhenPresent() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		String member = "user1@open-paas.org";
		createGroup(groupName, groupEmail, ImmutableList.of(member));

        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> pivots = testee.getListPivots();
        IBean bean = testee.getBean("id", pivots.get(groupEmail), FROM_SAME_SERVICE);
        
        assertThat(bean.getDatasetFirstValueById("name")).isEqualTo(groupName);
        assertThat(bean.getDatasetFirstValueById("email")).isEqualTo(groupEmail);
        assertThat(bean.getDatasetById("members")).containsOnly(member);
	}
	
	@Test
	public void getBeanShouldReturnInternalMembersWhenPresent() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		String member1 = "user1@open-paas.org";
		String member2 = "user2@open-paas.org";
		createGroup(groupName, groupEmail, ImmutableList.of(member1, member2));

        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> pivots = testee.getListPivots();
        IBean bean = testee.getBean("id", pivots.get(groupEmail), FROM_SAME_SERVICE);
        
        assertThat(bean.getDatasetFirstValueById("name")).isEqualTo(groupName);
        assertThat(bean.getDatasetFirstValueById("email")).isEqualTo(groupEmail);
        assertThat(bean.getDatasetById("members")).containsOnly(member1, member2);
	}
	
	@Test
	public void getBeanShouldReturnSubgroupWhenPresent() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		String subGroupEmail = "subgroup@open-paas.org";
		createGroup("subgroup", subGroupEmail, ImmutableList.of());
		createGroup(groupName, groupEmail, ImmutableList.of(subGroupEmail));

        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> pivots = testee.getListPivots();
        IBean bean = testee.getBean("id", pivots.get(groupEmail), FROM_SAME_SERVICE);
        
        assertThat(bean.getDatasetFirstValueById("name")).isEqualTo(groupName);
        assertThat(bean.getDatasetFirstValueById("email")).isEqualTo(groupEmail);
        assertThat(bean.getDatasetById("members")).containsOnly(subGroupEmail);
	}

	@Test
	public void getBeanShouldReturnMixedMembersWhenPresent() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		String internalMember = "user1@open-paas.org";
		String externalMember = "member@example.com";
		String subGroupEmail = "subgroup@open-paas.org";
		createGroup("subgroup", subGroupEmail, ImmutableList.of());
		createGroup(groupName, groupEmail, ImmutableList.of(internalMember, externalMember, subGroupEmail));

        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> pivots = testee.getListPivots();
        IBean bean = testee.getBean("id", pivots.get(groupEmail), FROM_SAME_SERVICE);
        
        assertThat(bean.getDatasetFirstValueById("name")).isEqualTo(groupName);
        assertThat(bean.getDatasetFirstValueById("email")).isEqualTo(groupEmail);
        assertThat(bean.getDatasetById("members")).containsOnly(internalMember, externalMember, subGroupEmail);
	}

	@Test
	public void createShouldFailWithoutName() throws Exception {
		LscModifications modifications = new LscModifications(LscModificationType.CREATE_OBJECT);
		String groupEmail = "new-group-email@open-paas.org";
		LscDatasetModification emailModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "email", ImmutableList.of(groupEmail));
		modifications.setLscAttributeModifications(ImmutableList.of(emailModification));

		testee = new OpenpaasGroupDstService(task);
		
		boolean applied = testee.apply(modifications);
		
		assertThat(applied).isFalse();
	}
	
	@Test
	public void createShouldFailWithoutEmail() throws Exception {
		LscModifications modifications = new LscModifications(LscModificationType.CREATE_OBJECT);
		String groupName = "new group name";
		LscDatasetModification nameModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "name", ImmutableList.of(groupName));
		modifications.setLscAttributeModifications(ImmutableList.of(nameModification));

		testee = new OpenpaasGroupDstService(task);
		
		boolean applied = testee.apply(modifications);
		
		assertThat(applied).isFalse();
	}
	
	@Test
	public void createShouldCreateGroupWhenCreated() throws Exception {
		LscModifications modifications = new LscModifications(LscModificationType.CREATE_OBJECT);
		String groupName = "new group name";
		LscDatasetModification nameModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "name", ImmutableList.of(groupName));
		String groupEmail = "new-group-email@open-paas.org";
		LscDatasetModification emailModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "email", ImmutableList.of(groupEmail));
		modifications.setLscAttributeModifications(ImmutableList.of(nameModification, emailModification));

		testee = new OpenpaasGroupDstService(task);
		
		boolean applied = testee.apply(modifications);
		
		assertThat(applied).isTrue();
		
		Map<String, LscDatasets> pivots = testee.getListPivots();
        IBean bean = testee.getBean("id", pivots.get(pivots.keySet().iterator().next()), FROM_SAME_SERVICE);
        
        assertThat(bean.getDatasetFirstValueById("name")).isEqualTo(groupName);
        assertThat(bean.getDatasetFirstValueById("email")).isEqualTo(groupEmail);
	}
	
	@Test
	public void createShouldCreateGroupWithMembersWhenCreated() throws Exception {
		LscModifications modifications = new LscModifications(LscModificationType.CREATE_OBJECT);
		String groupName = "new group name";
		LscDatasetModification nameModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "name", ImmutableList.of(groupName));
		String groupEmail = "new-group-email@open-paas.org";
		LscDatasetModification emailModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "email", ImmutableList.of(groupEmail));
		String internalMember = "user1@open-paas.org";
		String externalMember = "member@example.com";
		String subGroupEmail = "subgroup@open-paas.org";
		createGroup("subgroup", subGroupEmail, ImmutableList.of());
		LscDatasetModification memberModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "members", ImmutableList.of(internalMember, externalMember, subGroupEmail));
		modifications.setLscAttributeModifications(ImmutableList.of(nameModification, emailModification, memberModification));

		testee = new OpenpaasGroupDstService(task);
		
		boolean applied = testee.apply(modifications);
		
		assertThat(applied).isTrue();
		
		Map<String, LscDatasets> pivots = testee.getListPivots();
        IBean bean = testee.getBean("id", pivots.get(groupEmail), FROM_SAME_SERVICE);
        
        assertThat(bean.getDatasetFirstValueById("name")).isEqualTo(groupName);
        assertThat(bean.getDatasetFirstValueById("email")).isEqualTo(groupEmail);
        assertThat(bean.getDatasetById("members")).containsOnly(internalMember, externalMember, subGroupEmail);
	}

	@Test
	public void createShouldCreateGroupWithExternalMemberWithEmailObjectTypeWhenCreated() throws Exception {
		LscModifications modifications = new LscModifications(LscModificationType.CREATE_OBJECT);
		String groupName = "new group name";
		LscDatasetModification nameModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "name", ImmutableList.of(groupName));
		String groupEmail = "new-group-email@open-paas.org";
		LscDatasetModification emailModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "email", ImmutableList.of(groupEmail));
		String externalMember = "member@example.com";
		LscDatasetModification memberModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "members", ImmutableList.of(externalMember));
		modifications.setLscAttributeModifications(ImmutableList.of(nameModification, emailModification, memberModification));

		testee = new OpenpaasGroupDstService(task);
		
		boolean applied = testee.apply(modifications);
		
		assertThat(applied).isTrue();
		
		given()
			.param("email", groupEmail)
		.when()
			.get("")
		.then()
			.body("[0].members[0].member.objectType", equalTo("email"));
	}

	@Test
	public void createShouldCreateGroupWithInternalMemberWithUserObjectTypeWhenCreated() throws Exception {
		LscModifications modifications = new LscModifications(LscModificationType.CREATE_OBJECT);
		String groupName = "new group name";
		LscDatasetModification nameModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "name", ImmutableList.of(groupName));
		String groupEmail = "new-group-email@open-paas.org";
		LscDatasetModification emailModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "email", ImmutableList.of(groupEmail));
		String internalMember = "user1@open-paas.org";
		LscDatasetModification memberModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "members", ImmutableList.of(internalMember));
		modifications.setLscAttributeModifications(ImmutableList.of(nameModification, emailModification, memberModification));

		testee = new OpenpaasGroupDstService(task);
		
		boolean applied = testee.apply(modifications);
		
		assertThat(applied).isTrue();
		
		given()
			.param("email", groupEmail)
		.when()
			.get("")
		.then()
			.body("[0].members[0].member.objectType", equalTo("user"));
	}
	
	@Test
	@Disabled("Bug in OpenPaaS 1.4.6")
	public void createShouldCreateGroupWithSubGroupMemberWithGroupObjectTypeWhenCreated() throws Exception {
		LscModifications modifications = new LscModifications(LscModificationType.CREATE_OBJECT);
		String groupName = "new group name";
		LscDatasetModification nameModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "name", ImmutableList.of(groupName));
		String groupEmail = "new-group-email@open-paas.org";
		LscDatasetModification emailModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "email", ImmutableList.of(groupEmail));
		String subGroupEmail = "subgroup@open-paas.org";
		createGroup("subgroup", subGroupEmail, ImmutableList.of());
		LscDatasetModification memberModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "members", ImmutableList.of(subGroupEmail));
		modifications.setLscAttributeModifications(ImmutableList.of(nameModification, emailModification, memberModification));

		testee = new OpenpaasGroupDstService(task);
		
		boolean applied = testee.apply(modifications);
		
		assertThat(applied).isTrue();
		
		given()
			.param("email", groupEmail)
		.when()
			.get("")
		.then()
			.body("[0].members[0].member.objectType", equalTo("group"));
	}
	
	@Test
	public void deleteShouldDeleteGroupWhenDeleted() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		createGroup(groupName, groupEmail, ImmutableList.of());

		LscModifications modifications = new LscModifications(LscModificationType.DELETE_OBJECT);
		modifications.setMainIdentifer(groupEmail);

		testee = new OpenpaasGroupDstService(task);
		
		boolean applied = testee.apply(modifications);
		
		assertThat(applied).isTrue();
		
		Map<String, LscDatasets> pivots = testee.getListPivots();
		assertThat(pivots).isEmpty();
	}

	@Test
	public void applyShouldModifyGroupNameWhenModified() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		createGroup(groupName, groupEmail, ImmutableList.of());

		LscModifications modifications = new LscModifications(LscModificationType.UPDATE_OBJECT);
		modifications.setMainIdentifer(groupEmail);
		String newGroupName = "test group new name";
		LscDatasetModification nameModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "name", ImmutableList.of(newGroupName));
		modifications.setLscAttributeModifications(ImmutableList.of(nameModification));

		testee = new OpenpaasGroupDstService(task);

		boolean applied = testee.apply(modifications);
		
		assertThat(applied).isTrue();

		Map<String, LscDatasets> pivots = testee.getListPivots();
		IBean bean = testee.getBean("id", pivots.get(groupEmail), FROM_SAME_SERVICE);

		assertThat(bean.getDatasetFirstValueById("name")).isEqualTo(newGroupName);
	}

	@Test
	public void applyShouldModifyGroupEmailWhenModified() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		createGroup(groupName, groupEmail, ImmutableList.of());

		LscModifications modifications = new LscModifications(LscModificationType.UPDATE_OBJECT);
		modifications.setMainIdentifer(groupEmail);
		String newGroupEmail = "test-group-modified@open-paas.org";
		LscDatasetModification emailModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "email", ImmutableList.of(newGroupEmail));
		modifications.setLscAttributeModifications(ImmutableList.of(emailModification));

		testee = new OpenpaasGroupDstService(task);

		boolean applied = testee.apply(modifications);
		
		assertThat(applied).isTrue();

		Map<String, LscDatasets> pivots = testee.getListPivots();
		IBean bean = testee.getBean("id", pivots.get(newGroupEmail), FROM_SAME_SERVICE);

		assertThat(bean.getDatasetFirstValueById("email")).isEqualTo(newGroupEmail);
	}

	@Test
	public void applyShouldAddInternalMemberWhenAdded() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		createGroup(groupName, groupEmail, ImmutableList.of());

		LscModifications modifications = new LscModifications(LscModificationType.UPDATE_OBJECT);
		modifications.setMainIdentifer(groupEmail);
		String newInternalMember = "user1@open-paas.org";
		LscDatasetModification membersModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "members", ImmutableList.of(newInternalMember));
		modifications.setLscAttributeModifications(ImmutableList.of(membersModification));

		testee = new OpenpaasGroupDstService(task);

		boolean applied = testee.apply(modifications);
		
		assertThat(applied).isTrue();

		Map<String, LscDatasets> pivots = testee.getListPivots();
		IBean bean = testee.getBean("id", pivots.get(groupEmail), FROM_SAME_SERVICE);

		assertThat(bean.getDatasetById("members")).containsOnly(newInternalMember);
	}

	@Test
	public void applyShouldAddExternalMemberWhenAdded() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		createGroup(groupName, groupEmail, ImmutableList.of());

		LscModifications modifications = new LscModifications(LscModificationType.UPDATE_OBJECT);
		modifications.setMainIdentifer(groupEmail);
		String newExternalMember = "user@example.com";
		LscDatasetModification membersModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "members", ImmutableList.of(newExternalMember));
		modifications.setLscAttributeModifications(ImmutableList.of(membersModification));

		testee = new OpenpaasGroupDstService(task);

		boolean applied = testee.apply(modifications);
		
		assertThat(applied).isTrue();

		Map<String, LscDatasets> pivots = testee.getListPivots();
		IBean bean = testee.getBean("id", pivots.get(groupEmail), FROM_SAME_SERVICE);

		assertThat(bean.getDatasetById("members")).containsOnly(newExternalMember);
	}

	@Test
	public void applyShouldAddSubgroupMemberWhenAdded() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		createGroup(groupName, groupEmail, ImmutableList.of());

		LscModifications modifications = new LscModifications(LscModificationType.UPDATE_OBJECT);
		modifications.setMainIdentifer(groupEmail);
		String newSubGroupMember = "subgroup@open-paas.org";
		createGroup("subgroup", newSubGroupMember, ImmutableList.of());
		LscDatasetModification membersModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "members", ImmutableList.of(newSubGroupMember));
		modifications.setLscAttributeModifications(ImmutableList.of(membersModification));

		testee = new OpenpaasGroupDstService(task);

		boolean applied = testee.apply(modifications);
		
		assertThat(applied).isTrue();

		Map<String, LscDatasets> pivots = testee.getListPivots();
		IBean bean = testee.getBean("id", pivots.get(groupEmail), FROM_SAME_SERVICE);

		assertThat(bean.getDatasetById("members")).containsOnly(newSubGroupMember); 
	}

	@Test
	public void applyShouldAddSubgroupMemberWithGroupObjectTypeWhenAdded() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		createGroup(groupName, groupEmail, ImmutableList.of());

		LscModifications modifications = new LscModifications(LscModificationType.UPDATE_OBJECT);
		modifications.setMainIdentifer(groupEmail);
		String newSubGroupMember = "subgroup@open-paas.org";
		createGroup("subgroup", newSubGroupMember, ImmutableList.of());
		LscDatasetModification membersModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "members", ImmutableList.of(newSubGroupMember));
		modifications.setLscAttributeModifications(ImmutableList.of(membersModification));

		testee = new OpenpaasGroupDstService(task);

		boolean applied = testee.apply(modifications);
		
		assertThat(applied).isTrue();

		given()
		.when()
		    .param("email", groupEmail)
			.get("")
		.then()
			.body("[0].members[0].member.objectType", equalTo("group"));
	}

	@Test
	public void applyShouldAddInternalAndExternalMemberWhenAdded() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		createGroup(groupName, groupEmail, ImmutableList.of());

		LscModifications modifications = new LscModifications(LscModificationType.UPDATE_OBJECT);
		modifications.setMainIdentifer(groupEmail);
		String newInternalMember = "user1@open-paas.org";
		String newExternalMember = "user@example.com";
		LscDatasetModification membersModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "members", ImmutableList.of(newInternalMember, newExternalMember));
		modifications.setLscAttributeModifications(ImmutableList.of(membersModification));

		testee = new OpenpaasGroupDstService(task);

		boolean applied = testee.apply(modifications);
		
		assertThat(applied).isTrue();

		Map<String, LscDatasets> pivots = testee.getListPivots();
		IBean bean = testee.getBean("id", pivots.get(groupEmail), FROM_SAME_SERVICE);

		assertThat(bean.getDatasetById("members")).containsOnly(newInternalMember, newExternalMember);
	}

	@Test
	public void applyShouldRemoveInternalMemberWhenRemoved() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		createGroup(groupName, groupEmail, ImmutableList.of("user1@open-paas.org", "user2@open-paas.org"));

		LscModifications modifications = new LscModifications(LscModificationType.UPDATE_OBJECT);
		modifications.setMainIdentifer(groupEmail);
		String remainingInternalMember = "user1@open-paas.org";
		LscDatasetModification membersModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "members", ImmutableList.of(remainingInternalMember));
		modifications.setLscAttributeModifications(ImmutableList.of(membersModification));

		testee = new OpenpaasGroupDstService(task);

		boolean applied = testee.apply(modifications);
		
		assertThat(applied).isTrue();

		Map<String, LscDatasets> pivots = testee.getListPivots();
		IBean bean = testee.getBean("id", pivots.get(groupEmail), FROM_SAME_SERVICE);

		assertThat(bean.getDatasetById("members")).containsOnly(remainingInternalMember);
	}

	@Test
	public void applyShouldRemoveExternalMemberWhenRemoved() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		createGroup(groupName, groupEmail, ImmutableList.of("external1@example.com", "external2@example.com"));

		LscModifications modifications = new LscModifications(LscModificationType.UPDATE_OBJECT);
		modifications.setMainIdentifer(groupEmail);
		String remainingExternalMember = "external1@open-paas.org";
		LscDatasetModification membersModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "members", ImmutableList.of(remainingExternalMember));
		modifications.setLscAttributeModifications(ImmutableList.of(membersModification));

		testee = new OpenpaasGroupDstService(task);

		boolean applied = testee.apply(modifications);
		
		assertThat(applied).isTrue();

		Map<String, LscDatasets> pivots = testee.getListPivots();
		IBean bean = testee.getBean("id", pivots.get(groupEmail), FROM_SAME_SERVICE);

		assertThat(bean.getDatasetById("members")).containsOnly(remainingExternalMember);
	}

	@Test
	public void applyShouldRemoveSubgroupWhenRemoved() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		createGroup(groupName, groupEmail, ImmutableList.of());
		String newSubGroupMember = "subgroup@open-paas.org";
		createGroup("subgroup", newSubGroupMember, ImmutableList.of("external1@example.com"));
		String newSubGroupMember2 = "subgroup2@open-paas.org";
		createGroup("subgroup2", newSubGroupMember2, ImmutableList.of("user1@open-paas.org"));
		
		LscModifications initialModifications = new LscModifications(LscModificationType.UPDATE_OBJECT);
		initialModifications.setMainIdentifer(groupEmail);
		LscDatasetModification initialAdd = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "members", ImmutableList.of(newSubGroupMember, newSubGroupMember2));
		initialModifications.setLscAttributeModifications(ImmutableList.of(initialAdd));

		testee = new OpenpaasGroupDstService(task);

		boolean initiallyApplied = testee.apply(initialModifications);

		assertThat(initiallyApplied).isTrue();

		LscModifications modifications = new LscModifications(LscModificationType.UPDATE_OBJECT);
		modifications.setMainIdentifer(groupEmail);
		String remainingSubGroup = newSubGroupMember;
		LscDatasetModification membersModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "members", ImmutableList.of(remainingSubGroup));
		modifications.setLscAttributeModifications(ImmutableList.of(membersModification));

		testee = new OpenpaasGroupDstService(task);

		boolean applied = testee.apply(modifications);
		
		assertThat(applied).isTrue();

		Map<String, LscDatasets> pivots = testee.getListPivots();
		IBean bean = testee.getBean("id", pivots.get(groupEmail), FROM_SAME_SERVICE);

		assertThat(bean.getDatasetById("members")).containsOnly(remainingSubGroup);
	}

	@Test
	public void applyShouldRemoveMixedInternalAndExternalMemberWhenRemoved() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		createGroup(groupName, groupEmail,
			ImmutableList.of("user1@open-paas.org", "user2@open-paas.org", "external1@example.com", "external2@example.com"));

		LscModifications modifications = new LscModifications(LscModificationType.UPDATE_OBJECT);
		modifications.setMainIdentifer(groupEmail);
		String remainingInternalMember = "user1@open-paas.org";
		String remainingExternalMember = "external2@open-paas.org";
		LscDatasetModification membersModification = new LscDatasetModification(LscDatasetModificationType.REPLACE_VALUES, "members", ImmutableList.of(remainingInternalMember, remainingExternalMember));
		modifications.setLscAttributeModifications(ImmutableList.of(membersModification));

		testee = new OpenpaasGroupDstService(task);

		boolean applied = testee.apply(modifications);
		
		assertThat(applied).isTrue();

		Map<String, LscDatasets> pivots = testee.getListPivots();
		IBean bean = testee.getBean("id", pivots.get(groupEmail), FROM_SAME_SERVICE);

		assertThat(bean.getDatasetById("members")).containsOnly(remainingInternalMember, remainingExternalMember);
	}

	@Test
	public void getListPivotsShouldReturnOneWhenOneGroupWith1000Members() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		List<String> members = IntStream.range(0, 1000)
			.mapToObj(i -> "user" + i + "@example.com")
			.collect(Collectors.toList());
		createGroup(groupName, groupEmail, members);
		
        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> listPivots = testee.getListPivots();
        
        assertThat(listPivots).containsOnlyKeys(groupEmail);
        assertThat(listPivots.get(groupEmail).getStringValueAttribute("email")).isEqualTo(groupEmail);
	}

	@Test
	public void getBeanShouldReturn1000MembersWhenOneGroupWith1000Members() throws Exception {
		String groupName = "test group";
		String groupEmail = "test-group@open-paas.org";
		List<String> members = IntStream.range(0, 1000)
			.mapToObj(i -> "user" + i + "@example.com")
			.collect(Collectors.toList());
		createGroup(groupName, groupEmail, members);

        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> pivots = testee.getListPivots();
        IBean bean = testee.getBean("id", pivots.get(groupEmail), FROM_SAME_SERVICE);
        
        assertThat(bean.getDatasetFirstValueById("name")).isEqualTo(groupName);
        assertThat(bean.getDatasetFirstValueById("email")).isEqualTo(groupEmail);
        assertThat(bean.getDatasetById("members")).hasSize(1000);
	}
	
	@Test
	public void getListPivotsShouldReturn100When100Groups() throws Exception {
		List<String> groupEmails = new ArrayList<>(100);
		IntStream.range(0, 100).forEach(i -> {
			String groupEmail = "test-group" + i + "@open-paas.org";
			createGroup("test group" + i, groupEmail);
			groupEmails.add(groupEmail);
		});
		
        testee = new OpenpaasGroupDstService(task);

        Map<String, LscDatasets> listPivots = testee.getListPivots();
        
        assertThat(listPivots).hasSize(100);
        assertThat(listPivots).containsOnlyKeys(groupEmails.toArray(new String[] {}));
	}

	private void createGroup(String name, String email) {
    	with()
			.body("{"
				+ "\"name\":\"" + name + "\","
				+ "\"email\":\"" + email + "\","
				+ "\"members\":[]}")
		.post("")
		.then();
	}

	private void createGroup(String groupName, String groupEmail, List<String> members) {
		StringJoiner membersAsString = new StringJoiner("\", \"", "\"", "\"")
			.setEmptyValue("");
		members.forEach(membersAsString::add);
    	with()
			.body("{"
				+ "\"name\":\"" + groupName + "\","
				+ "\"email\":\"" + groupEmail + "\","
				+ "\"members\":[" + membersAsString.toString() + "]}")
		.post("")
		.then();
	}
}
