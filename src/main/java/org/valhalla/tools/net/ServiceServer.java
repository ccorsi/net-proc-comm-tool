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
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This abstract class represents the common functionality that would need to be
 * implemented by implementors with the ServiceManager instance. </br>
 * 
 * The implementor will only need to implement the serviceStarted and serviceStopped
 * methods. </br>
 * 
 * @author Claudio Corsi
 * 
 */
public abstract class ServiceServer<S, C> {
	
	private static final Logger logger = LoggerFactory.getLogger(ServiceServer.class);

	private enum State {
		CREATED, ACCEPTING, STARTING, STARTED, STOPPING, STOPPED
	};

	private ServerSocket serverSocket;
	private int port;
	private ServiceManager<S, C> manager = null;
	private Thread thread;
	private S server;
	private State state = State.CREATED;
	private AtomicBoolean threadStarted;

	/**
	 * This constructor will create a socket server instance that will be used
	 * to wait for a connection request from the client socket.
	 * It will throw an IOException if it was unable to create a socket server.
	 * 
	 * @throws IOException
	 */
	public ServiceServer() throws IOException {
		serverSocket = new ServerSocket(0);
		port = serverSocket.getLocalPort();
	}

	/**
	 * This method is used to setup the service manager property that will be
	 * sent to the client process that is used to determine what port is used
	 * to communicate between this process and the client process.
	 * 
	 * @param properties the properties instance that the property will be set
	 * 
	 */
	public final void setServiceManagerPort(Properties properties) {
		properties.setProperty(ServiceConstants.SERVICE_MANAGER_PORT_NAME, String.valueOf(port));
	}

	/**
	 * This method will return a Map.Entry instance that contains the key and value
	 * required by the ServiceClient to be able to retrieve and connect to this 
	 * ServiceServer instance.
	 * 
	 * @return A Map entry that contains the key/value pair stored in a properties 
	 *    instance.
	 */
	public Map.Entry<String, String> getServiceManagerPortEntry() {
		return new Map.Entry<String, String>() {

			private String value = String.valueOf(port);
			
			@Override
			public String getKey() {
				return ServiceConstants.SERVICE_MANAGER_PORT_NAME;
			}

			@Override
			public String getValue() {
				return value;
			}

			@Override
			public String setValue(String value) {
				throw new UnsupportedOperationException("The setValue operation is not supported.");
			}
			
		};
	}
	/**
	 * This method will return the property name used to communicate the port
	 * number of this instance server socket.
	 * 
	 * @return the name of the service manager port name
	 * 
	 */
	public String getServiceManagerPortPropertyName() {
		return ServiceConstants.SERVICE_MANAGER_PORT_NAME;
	}
	
	/**
	 * This method will return the port of the server socket that is expecting a
	 * client connection that will be used to communicate between this process
	 * and the other java process.
	 * 
	 * @return the port number used by this instance server socket
	 */
	public final int getPort() {
		return port;
	}

	/**
	 * This method is used to enqueue a command that will be transfered over the 
	 * wire and be applied to the client java process.
	 * 
	 * @param command the command that will be executed on the other java process
	 */
	public final void enqueue(CommandExecutor<C, S> command) {
		this.manager.enqueue(command);
	}

	/**
	 * This method is used to execute the process of communicating with the 
	 * client process and this process.  The passed server instance will have
	 * the returned CommandExecutor instance that is received from the client
	 * process. </br>
	 * 
	 * TODO: This method needs to check that this method can only be called once 
	 *   and any time that this method is called again should probably generate an
	 *   exception stating that this method was already called.
	 *  
	 * @param server The instance that the received CommandExecutor instance from
	 *    the client process.
	 */
	public final void execute(S server) {
		if (server == null) {
			logger.error("The passed server instance can not be null");
			throw new IllegalArgumentException("Can not pass a null instance of the server");
		}
		if (threadStarted.compareAndSet(false,true) == false) {
			logger.error("This method can only be called once per instance");
			throw new IllegalStateException("This method can be called only once per instance");
		}
		this.server = server;
		thread = new Thread(new Runnable() {
			public void run() {
				try {
					logger.debug("Started ServerCallback thread: {}", 
							ServiceServer.this.thread.getName());
					ServiceServer.this.state = State.ACCEPTING;
					Socket socket = ServiceServer.this.serverSocket.accept();
					ServiceServer.this.state = State.STARTING;
					// Create a ServiceManager and pass the given Socket
					// that will be used to communicate between processes.
					ServiceServer.this.createServiceManager(socket);
					logger.debug("Calling serviceStarted method");
					ServiceServer.this.serviceStarted();
					ServiceServer.this.state = State.STARTED;
					logger.debug("Service manager has been started");
				} catch (IOException e) {
					// Something happened but let us not worry about this just
					// yet.  Still we will warn the calling method of this exception.
					logger.warn(
							"An exception was raised during the process of setting up a service manager instance",
							e);
				} finally {
					ServiceServer.this.state = State.STOPPING;
					logger.debug("Calling the serviceStopped method");
					ServiceServer.this.serviceStopped();
					ServiceServer.this.state = State.STOPPED;
					logger.debug("Called the serviceStopped method and exiting Server Callback thread");
				}
			}
		}) {
			{
				setName("ServerCallback-" + getName());
				setDaemon(true);
				start();
			}
		};
	}

	/**
	 * This method is used to terminate the thread that was started by this instance.
	 * It will interrupt the started thread if it is not being stopped or stopped.
	 * All other states will cause this method to interrupt the thread.
	 */
	public final void stop() {
		switch (this.state) {
		case CREATED:
		case STOPPED:
		case STOPPING:
			break;
		default:
			logger.info("Interrupting thread: {}", thread);
			// Interrupt the waiting thread and return....
			this.thread.interrupt();
			break;
		}
	}

	/**
	 * This method is used to create an instance of the createServiceManager instance 
	 * for the given socket.  The passed socket is the client process network communication
	 * used to communicate the CommandExecutor instances between processes.
	 * 
	 * @param socket the connection instance to the client process
	 * 
	 * @throws IOException Whenever the instantiating ServiceManager is unable to 
	 *   use the passed socket to setup the communication process between this 
	 *   process and the client process.
	 *   
	 */
	protected final void createServiceManager(Socket socket) throws IOException {
		logger.debug("Creating ServiceManager instance");
		manager = new ServiceManager<S, C>(socket, server);
	}

	/**
	 * This method will be called by this class as soon as the ServiceManager
	 * instance has been initialized. This is to allow the system to react as
	 * soon as this is ready and remove the need to guess when the
	 * ServiceManager has been initialized and started.
	 */
	protected abstract void serviceStarted();

	/**
	 * This method will be called when the ServiceManager instance has been
	 * stopped. Not sure if we would need a preStopping method callback but it
	 * might not be necessary or possible.
	 */
	protected abstract void serviceStopped();

}
