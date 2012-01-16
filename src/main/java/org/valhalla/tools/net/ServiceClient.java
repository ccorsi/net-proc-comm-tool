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

import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This ServiceClient class is used to communicate with the ServiceServer instance and
 * implements all of the required calls to be able to receive and send CommandExecutor
 * instances between this process and the server process.
 *   
 * @author Claudio Corsi
 *
 */
public class ServiceClient<C, S> {

	private static final Logger logger = LoggerFactory.getLogger(ServiceClient.class);
	
	private CountDownLatch       shutdown = new CountDownLatch(1);
	private ServiceManager<C, S> manager  = null;
	private String               hostname;
	private AtomicBoolean        started;
	
	/**
	 * This default constructor will assume that the service server instance is located
	 * on the same host as this process.  This is similar to passing "localhost" to the
	 * constructor that expects a hostname.
	 */
	public ServiceClient() {
		this("localhost");
	}
	
	/**
	 * This method will create an instance of the service client that will be used to 
	 * communicate to the service server instance that is located within the passed
	 * host name.
	 * 
	 * @param hostname the name of the host that the service server instance is located
	 * 
	 */
	public ServiceClient(String hostname) {
		this.hostname = hostname;
	}
	
	/**
	 * This method is used to start the process of communicating with the service
	 * server instance for the passed host name.  This method will setup the 
	 * socket communication and instantiate the ServiceManager instance that is
	 * used to communicate with the service manager instance of the service 
	 * server. </br>
	 * 
	 * This method can only be called once per instance.  Any subsequent calls to
	 * this method will generate and IllegalArgumentException. 
	 * 
	 * @param client the client instance that received CommandExecutor instances
	 *   are applied to.
	 *   
	 * @throws Exception If any exceptions are raised when trying to setup the 
	 *   socket communication between this service client and the service server.
	 *   
	 */
	public void execute(final C client) throws Exception {
		if (started.compareAndSet(false, true) == false) {
			logger.error("The execute method was called multiple times which is not allowed.");
			throw new IllegalStateException("This method can only be called once");
		}
		
		// TODO: Should we be creating a thread that will then perform a callback
		//       action to inform the calling process that the ServiceManager was
		//       successfully created and is ready to perform CommandExecutor
		//       processing.
		//       It might not be necessary since the ServiceManager instance already
		//       spawns a thread to process the CommandExecutor actions.
		
		// This is the main point of entry that we will be using to 
		// test the new feature...
		int port = Integer.getInteger(ServiceConstants.SERVICE_MANAGER_PORT_NAME, 0);
		if (port <= 0) {
			logger.info("No service manager port number was defined");
			throw new IllegalArgumentException("The service manager port is invalid: " + port);
		}
		Socket socket = new Socket(hostname, port);
		try {
			logger.info("Creating Service Manager instance");
			manager =  new ServiceManager<C, S>(socket, client);
			// Now that we started the manager we just wait until we are told to 
			// finish....
			logger.info("Waiting to be told that I am done");
			shutdown.await();
		} finally {
			logger.info("Exiting client application");
			if (manager != null) {
				// Closing the manager causes the socket to be closed also...
				manager.stop();
			} else {
				// Close the opened socket...
				socket.close();
			}
		}
	}

	/**
	 * This method is called whenever you want to shutdown the communication between
	 * this client service manager and the server service manager.
	 * 
	 */
	public void shutdown() {
		this.shutdown.countDown();
	}

	/**
	 * This method will return this instance service manager instance.
	 * 
	 * @return the service manager instance
	 */
	public ServiceManager<C, S> getManager() {
		return manager;
	}

}
