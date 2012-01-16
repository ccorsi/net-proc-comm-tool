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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Claudio Corsi
 *
 */
public class Client {
	
	private static final Logger logger = LoggerFactory.getLogger(Client.class);
	
	private CountDownLatch shutdown = new CountDownLatch(1);

	private ServiceManager<Client, Server> manager = null;

	public void main() throws Exception {
		// This is the main point of entry that we will be using to 
		// test the new feature...
		int port = Integer.getInteger("service.manager.port", 0);
		if (port <= 0) {
			logger.info("No service manager port number was defined");
			System.exit(1);
		}
		Socket socket = new Socket("localhost", port);
		try {
			logger.info("Creating Service Manager instance");
			manager =  new ServiceManager<Client, Server>(socket, this);
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

	public void shutdown() {
		this.shutdown.countDown();
	}

	/**
	 * @return the manager
	 */
	public ServiceManager<Client, Server> getManager() {
		return manager;
	}

}
