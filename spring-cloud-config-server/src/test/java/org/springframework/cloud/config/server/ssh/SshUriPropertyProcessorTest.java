/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.config.server.ssh;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.Map;

import org.eclipse.jgit.transport.SshSessionFactory;
import org.junit.After;
import org.junit.Test;

/**
 * Unit tests for property based SSH config processor
 * @author William Tran
 * @author Ollie Hughes
 */
public class SshUriPropertyProcessorTest {

	private static final String PRIVATE_KEY1 = "privateKey";
	private static final String HOST_KEY1 = "hostKey";
	private static final String ALGO1 = "ssh-rsa";
	private static final String URI1 = "ollie@gitlab1.test.local:project/my-repo";
	private static final String HOST1 = "gitlab1.test.local";
	private static final String PRIVATE_KEY2 = "privateKey2";
	private static final String URI2 = "ssh://git@gitlab2.test.local/wtran/my-repo";
	private static final String HOST2 = "gitlab2.test.local";
	private static final String PRIVATE_KEY3 = "privateKey3";
	private static final String URI3 = "git+ssh://git@gitlab3.test.local/wtran/my-repo";
	private static final String HOST3 = "gitlab3.test.local";

	@After
	public void cleanup() {
		SshSessionFactory.setInstance(null);
	}

	@Test
	public void testSingleSshUriProperties() {
		SshUriPropertyProcessor sshUriPropertyProcessor = new SshUriPropertyProcessor(mainRepoPropertiesFixture());
		Map<String, SshUriProperties> sshKeysByHostname = sshUriPropertyProcessor.getSshKeysByHostname();

		assertThat(sshKeysByHostname.values(), hasSize(1));

		SshUriProperties sshKey = sshKeysByHostname.get(HOST1);
		assertMainRepo(sshKey);
	}

	@Test
	public void testMultipleSshUriPropertiess() {
		SshUriProperties sshUriProperties = mainRepoPropertiesFixture();
		addRepoProperties(sshUriProperties, SshUriProperties.builder()
				.uri(URI2)
				.privateKey(PRIVATE_KEY2)
				.build(), "repo2");
		addRepoProperties(sshUriProperties, SshUriProperties.builder()
				.uri(URI3)
				.privateKey(PRIVATE_KEY3)
				.build(), "repo3");

		SshUriPropertyProcessor sshUriPropertyProcessor = new SshUriPropertyProcessor(sshUriProperties);

		Map<String, SshUriProperties> sshKeysByHostname = sshUriPropertyProcessor.getSshKeysByHostname();

		assertThat(sshKeysByHostname.values(), hasSize(3));

		SshUriProperties sshKey1 = sshKeysByHostname.get(HOST1);
		assertMainRepo(sshKey1);

		SshUriProperties sshKey2 = sshKeysByHostname.get(HOST2);

		assertThat(SshUriPropertyProcessor.getHostname(sshKey2.getUri()), is(equalTo(HOST2)));
		assertThat(sshKey2.getHostKeyAlgorithm(), is(nullValue()));
		assertThat(sshKey2.getHostKey(), is(nullValue()));
		assertThat(sshKey2.getPrivateKey(), is(equalTo(PRIVATE_KEY2)));

		SshUriProperties sshKey3 = sshKeysByHostname.get(HOST3);

		assertThat(SshUriPropertyProcessor.getHostname(sshKey3.getUri()), is(equalTo(HOST3)));
		assertThat(sshKey3.getHostKeyAlgorithm(), is(nullValue()));
		assertThat(sshKey3.getHostKey(), is(nullValue()));
		assertThat(sshKey3.getPrivateKey(), is(equalTo(PRIVATE_KEY3)));
	}

	@Test
	public void testSameHostnameDifferentKeysFirstOneWins() {
		SshUriProperties sshUriProperties = mainRepoPropertiesFixture();
		addRepoProperties(sshUriProperties, SshUriProperties.builder().uri(URI1)
				.privateKey(PRIVATE_KEY1)
				.hostKey(HOST_KEY1)
				.hostKeyAlgorithm(ALGO1)
				.build(), "repo2");

		SshUriPropertyProcessor sshUriPropertyProcessor = new SshUriPropertyProcessor(sshUriProperties);
		Map<String, SshUriProperties> sshKeysByHostname = sshUriPropertyProcessor.getSshKeysByHostname();

		assertThat(sshKeysByHostname.values(), hasSize(1));

		SshUriProperties sshKey = sshKeysByHostname.get(HOST1);
		assertMainRepo(sshKey);
	}

	@Test
	public void testNoSshUriProperties() {
		SshUriPropertyProcessor sshUriPropertyProcessor = new SshUriPropertyProcessor(new SshUriProperties());
		Map<String, SshUriProperties> sshKeysByHostname = sshUriPropertyProcessor.getSshKeysByHostname();
		assertThat(sshKeysByHostname.values(), hasSize(0));
	}

	@Test
	public void testInvalidUriDoesNotAddEntry() {
		SshUriPropertyProcessor sshUriPropertyProcessor = new SshUriPropertyProcessor(SshUriProperties.builder().uri("invalid_uri").build());
		Map<String, SshUriProperties> sshKeysByHostname = sshUriPropertyProcessor.getSshKeysByHostname();
		assertThat(sshKeysByHostname.values(), hasSize(0));
	}

	@Test
	public void testHttpsUriDoesNotAddEntry() {
		SshUriPropertyProcessor sshUriPropertyProcessor = new SshUriPropertyProcessor(SshUriProperties.builder().uri("https://user@github.com/proj/repo.git").build());
		Map<String, SshUriProperties> sshKeysByHostname = sshUriPropertyProcessor.getSshKeysByHostname();
		assertThat(sshKeysByHostname.values(), hasSize(0));
	}

	private SshUriProperties mainRepoPropertiesFixture() {

		return SshUriProperties.builder()
				.uri(URI1)
				.hostKeyAlgorithm(ALGO1)
				.hostKey(HOST_KEY1)
				.privateKey(PRIVATE_KEY1)
				.build();
	}

	private void addRepoProperties(SshUriProperties mainRepoProperties, SshUriProperties repoProperties, String repoName) {
		mainRepoProperties.addRepo(repoName, repoProperties);
	}

	private void assertMainRepo(SshUriProperties sshKey) {
		assertThat(sshKey, is(notNullValue()));
		assertThat(SshUriPropertyProcessor.getHostname(sshKey.getUri()), is(equalTo(HOST1)));
		assertThat(sshKey.getHostKeyAlgorithm(), is(equalTo(ALGO1)));
		assertThat(sshKey.getHostKey(), is(equalTo(HOST_KEY1)));
		assertThat(sshKey.getPrivateKey(), is(equalTo(PRIVATE_KEY1)));
	}

}