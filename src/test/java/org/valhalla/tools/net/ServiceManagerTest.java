/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.valhalla.tools.net;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.valhalla.tools.process.Spawner;

/**
 * Unit test for simple ServiceManager.
 */
public class ServiceManagerTest implements Callback 
{
	private static final Logger logger = LoggerFactory.getLogger(ServiceManagerTest.class);

	private Server server;
	private CountDownLatch latch = new CountDownLatch(1);
	private boolean initialized;
	
	@Before public void createServer() throws IOException {
		// Create a new instance of Server....
		server = new Server();
		// Start it so that it is ready to receive a request...
		server.execute(this);
	}
	
	@After public void destroyServer() throws IOException {
		// Shutdown Server...
		server.shutdown();
	}
	
	@Test public void SimpleNetTest() throws Exception {
		Spawner spawner = createAndStartClientProcess("SimpleNetTestSpawner");
		// Send and receive messages between the two systems....
		// but in this case we are just going to send a shutdown command...
		// keeping it simple for this test but obviously making the next
		// more complicated....
		server.enqueue(createClientCommandExecutor(1));
		shutdownClientProcess(spawner);
	}

	@Test public void MultipleCommandsNetTest() throws Exception {
		Spawner spawner = createAndStartClientProcess("MultipleCommandsNetTest");
		server.enqueue(createClientCommandExecutor(1001));
		shutdownClientProcess(spawner);
	}

	@Test public void MultipleParallelCommandsNetTest() throws Exception {
		Spawner spawner = createAndStartClientProcess("MultipleParallelCommandsNetTest");
		server.enqueue(createClientCommandExecutor(3, true));
		shutdownClientProcess(spawner);
	}
	
	@Test public void MultipleParallelCommandsNetTestWithBigCount() throws Exception {
		Spawner spawner = createAndStartClientProcess("MultipleParallelCommandsNetTest");
		server.enqueue(createClientCommandExecutor(20, true));
		shutdownClientProcess(spawner, 300);
	}
	
	/**
	 * @param name
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private Spawner createAndStartClientProcess(String name) throws IOException,
			InterruptedException {
		Spawner spawner = new Spawner(Client.class.getName(), "main");
		List<String> jvmArgs = new LinkedList<String>();
		jvmArgs.add("-Dservice.manager.port=" + String.valueOf(server.getPort()));
		spawner.setJVMArgs(jvmArgs);
		spawner.setIdentifier(name);
		logger.info("Starting the spawned process");
		// Start a new jvm that will execute the Client class....
		spawner.spawnProcess();
		logger.info("Waiting to be notified that the spawned process has started succesfully");
		// Wait until the new java process connects to this jvm...
		this.latch.await();
		Assert.assertTrue("The ServiceManager was not initialized", initialized);
		return spawner;
	}

	/**
	 * @param spawner
	 * @throws InterruptedException
	 */
	private void shutdownClientProcess(Spawner spawner)
			throws InterruptedException {
		shutdownClientProcess(spawner, 5);
	}

	/**
	 * @param spawner
	 * @param maxCount
	 * @throws InterruptedException
	 */
	private void shutdownClientProcess(Spawner spawner, int maxCount)
			throws InterruptedException {
		boolean processExited = spawner.isProcessExited();
		int count = 0;
		while (!processExited) {
			Thread.sleep(1000);
			count++;
			if (count >= maxCount)
				break;
			processExited = spawner.isProcessExited();
		}
		spawner.stopProcess();
		Assert.assertTrue(
				"The Client side did not shutdown after receiving the shutdown command",
				processExited);
	}

	@Override
	public void initialized() {
		this.initialized = true;
		this.latch.countDown();
	}

	@Override
	public void done() {
		this.latch.countDown();
	}
	
	/**
	 * @param count
	 * @return
	 */
	public static CommandExecutor<Client, Server> createClientCommandExecutor(final int count) {
		return ServiceManagerTest.createClientCommandExecutor(count, false);
	}
	
	/**
	 * @param count
	 * @param create
	 * @return
	 */
	public static CommandExecutor<Client, Server> createClientCommandExecutor(final int count, final boolean create) {
		return new CommandExecutor<Client, Server>() {

			private static final long serialVersionUID = -4734135412198165740L;

			private int clientCount = count;
			
			@Override
			public CommandExecutor<Server, Client> apply(Client client) {
				clientCount--;
				logger.debug("Current client count: {}", clientCount);
				CommandExecutor<Server, Client> serverCommand = null;
				if (clientCount <= 0) {
					// Shutdown and return nothing...
					client.shutdown();
				} else {
					serverCommand = createServerCommandExecutor(clientCount);
					if (create) {
						ServiceManager<Client, Server> manager = client.getManager();
						if (manager != null) {
							manager.enqueue(ServiceManagerTest.createServerCommandExecutorWithoutShutdown(clientCount-1));
						} else {
							logger.error("What the manager instance is NULL!!!!");
							logger.info("We would be at client count {}", clientCount-1);
						}
					}
				}
				return serverCommand;
			}
		};
	}


	/**
	 * @return
	 */
	private static CommandExecutor<Server, Client> createServerCommandExecutor(final int count) {
		return new CommandExecutor<Server, Client>() {

			private static final long serialVersionUID = -313055602491132604L;
			
			private int serverCount = count;

			@Override
			public CommandExecutor<Client, Server> apply(
					Server destination) {
				serverCount--;
				logger.debug("Current server count: {}", serverCount);
				CommandExecutor<Client, Server> command = null;
				if (serverCount >= 0) {
					command = createClientCommandExecutor(serverCount);
				}
				return command;
			}
			
		};
	}
	
	/**
	 * @param count
	 * @return
	 */
	public static CommandExecutor<Client, Server> createClientCommandExecutorWithoutShutdown(final int count) {
		return new CommandExecutor<Client, Server>() {

			private static final long serialVersionUID = -4734135412198165740L;

			private int clientCount = count;
			
			@Override
			public CommandExecutor<Server, Client> apply(Client client) {
				clientCount--;
				logger.debug("Current no shutdown client count: {}", clientCount);
				CommandExecutor<Server, Client> serverCommand = null;
				if (clientCount > 0) {
					serverCommand = createServerCommandExecutorWithoutShutdown(clientCount--);
					client.getManager().enqueue(createServerCommandExecutorWithoutShutdown(clientCount));
				}
				return serverCommand;
			}
		};
	}


	/**
	 * @return
	 */
	private static CommandExecutor<Server, Client> createServerCommandExecutorWithoutShutdown(final int count) {
		return new CommandExecutor<Server, Client>() {

			private static final long serialVersionUID = -313055602491132604L;
			
			private int serverCount = count;

			@Override
			public CommandExecutor<Client, Server> apply(
					Server server) {
				serverCount--;
				logger.debug("Current no shutdown server count: {}", serverCount);
				CommandExecutor<Client, Server> command = null;
				if (serverCount > 0) {
					command = createClientCommandExecutorWithoutShutdown(serverCount--);
					server.getManager().enqueue(createClientCommandExecutorWithoutShutdown(serverCount));
				}
				return command;
			}
			
		};
	}
}
