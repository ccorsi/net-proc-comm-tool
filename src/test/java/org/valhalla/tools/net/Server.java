/**
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
import java.net.ServerSocket;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Claudio Corsi
 *
 */
public class Server {
	
	private static final Logger logger = LoggerFactory.getLogger(Server.class);
	
	private ServerSocket server;
	private int port;
	private ServiceManager<Server, Client> manager = null;
	private Thread thread;
	
	public Server() throws IOException {
		server = new ServerSocket(0);
		port = server.getLocalPort();
		logger.info("Created a server socket instance for port: {}", port);
	}
	
	public void execute(final Callback callback) {
		thread = new Thread(new Runnable() {
			public void run() {
				try {
					logger.info("Waiting for a client to connect back to me");
					Socket socket = Server.this.server.accept();
					// Create a ServiceManager and pass the given Socket
					// that will be used to communicate between processes.
					logger.info("Creating a instance of a ServiceManager");
					Server.this.manager = new ServiceManager<Server, Client>(socket, Server.this);
					logger.info("Calling the passed callback initialized method");
					callback.initialized();
				} catch (IOException e) {
					// Something happened but let us not worry about this just
					// yet.
					logger.info("An exception was raised during the process of setting up a service manager instance", e);
				} finally {
					callback.done();
				}
				logger.info("Exiting the callback thread");
			}
		})
		{
			{
				setName("ServerCallback-" + getName());
				setDaemon(true);
				start();
			}
		};
	}

	/**
	 * @return the port
	 */
	public int getPort() {
		return port;
	}

	public void enqueue(CommandExecutor<Client, Server> command) {
		logger.debug("Adding Command: {} to the out going queue.", command );
		this.manager.enqueue(command);
	}

	public void shutdown() throws IOException {
		logger.info("Shutting down the Server system");
		if (thread != null && thread.isAlive()) {
			// Interrupt the thread only if it is still possibly active....
			thread.interrupt();
		}
		// Close the server socket connection so that this will sever the
		// connection between the two processes.
		this.server.close();
	}

	/**
	 * @return the manager
	 */
	public ServiceManager<Server, Client> getManager() {
		return manager;
	}
	
}
