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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to process the individual commands that will perform actions on the
 * passed destination instance passed to the constructor. </br>
 * 
 * This implementation does not take into account the possibility that the passed Socket
 * instance is using non-blocking instead of blocking communication.  This does not mean
 * that the non-blocking socket will not work in this case but that it is possible that
 * it might not.  This needs to be tested but then it is possible that this will require
 * changes to the interface to provide this ability.  </br>
 * 
 * The other aspect that has not been tested is the condition that we would require the
 * use of a secure socket implementation.  Again, this is something that might work but
 * again needs to be tested.  In the end, this requirement might not even be required.
 * 
 * @author Claudio Corsi
 *
 */
public class ServiceManager<S, R> {

	private static final Logger logger = LoggerFactory.getLogger(ServiceManager.class);

	private Socket socket;
	private Thread readCommands;
	private Thread writeCommands;
	private Thread executeCommands;

	private BlockingQueue<CommandExecutor<S, R>> incomingCommands = new LinkedBlockingQueue<CommandExecutor<S, R>>();
	private BlockingQueue<CommandExecutor<R, S>> outgoingCommands = new LinkedBlockingQueue<CommandExecutor<R, S>>();

	private S destination;

	private boolean stopping;
	
	public ServiceManager(Socket socket, S destination) throws IOException {
		this.socket = socket;
		this.destination = destination;
				
		logger.debug("Starting the different threads");
		this.readCommands = new Thread() {
			{
				{
					logger.debug("Setting up the read commands thread");
					setName("ServiceManagerReadCommands-" + getName());
					setDaemon(true);
					start();
					logger.debug("Called the start method for the read commands thread");
				}
			}
			
			@SuppressWarnings("unchecked")
			public void run() {
				CommandExecutor<S, R> command = null;
				try {
					logger.debug("Getting a reference to the input stream");
					ObjectInputStream ois = new ObjectInputStream(ServiceManager.this.socket.getInputStream());
					logger.debug("Entering the while loop for the read commands thread");
					while(!ServiceManager.this.stopping) {
						command = (CommandExecutor<S, R>) ois.readObject();
						// If the returned command is null then break out of the while loop...
						// TODO: This might be a problem when using non-blocking sockets.
						if (command == null) break;
						logger.debug("Received an incoming command: {}", command);
						// Add command to a queue so that another thread can apply the
						// changes...We do not want to process this because we might need
						// to read the next command over the wire and we do not want it to
						// wait...Still the use of the offer call is not a guarantee that the
						// received command will be included as part of the incomingCommands
						// container...maybe create a temporary container in the run method
						// and add those that have not be accepted by the incomingCommands
						// container....
						if (ServiceManager.this.incomingCommands.offer(command) == false) {
							logger.error("Unable to add the given command [{}] to the incoming queue.", 
									command);
						}
					}
				} catch (Throwable e) {
					if (!ServiceManager.this.stopping) {
						logger.error(
								"An exception was generated when processing a Command",
								e);
					}
				}
				logger.info("Exiting the read command thread");
			}
		};
		
		this.writeCommands = new Thread() {
			{
				{
					logger.debug("Setting up the write commands thread");
					setName("ServiceManagerWriteCommands-" + getName());
					setDaemon(true);
					start();
					logger.debug("Called the start method of the write commands thread");
				}
			}
			
			public void run() {
				try {
					logger.debug("Getting a reference to the output stream");
					ObjectOutputStream oos = new ObjectOutputStream(ServiceManager.this.socket.getOutputStream());
					logger.debug("Entering the while loop of the write commands thread");
					while (!ServiceManager.this.stopping) {
						logger.debug("Calling take command for outgoing commands");
						// Get the next command from the blocking queue....
						CommandExecutor<R, S> command = ServiceManager.this.outgoingCommands
								.take();
						logger.debug("Sending on out going command: {}", command);
						// Send the command to the other JVM...
						oos.writeObject(command);
						logger.debug("Out going command has been sent");
					}
				} catch (Exception e) {
					if (!ServiceManager.this.stopping) {
						logger.error("An exception was raised while sending commands", e);
					}
				}
				logger.info("Exiting the write commands thread");
			}
		};
		
		this.executeCommands = new Thread() {
			{
				{
					logger.debug("Setting up the execute commands thread");
					setName("ServiceManagerExecuteCommands-" + getName());
					setDaemon(true);
					start();
					logger.debug("Called the start method of the execute command thread");
				}
			}
			
			public void run() {
				try {
					logger.debug("Entering the execute commands thread while loop");
					while (!ServiceManager.this.stopping) {
						logger.debug("Calling take for incoming commands");
						CommandExecutor<S, R> command = ServiceManager.this.incomingCommands
								.take();
						logger.debug("Applying command to destination");
						CommandExecutor<R, S> result = command
								.apply(ServiceManager.this.destination);
						logger.debug("Command has been applied");
						if (result != null) {
							logger.debug("The applied command resulted in an out going reply");
							// Shouldn't I be using add instead of put???
							ServiceManager.this.outgoingCommands.put(result);
						}
					}
				} catch (InterruptedException e) {
					if (!ServiceManager.this.stopping) {
						logger.error("An exception was raised when processing incoming commands", e);
					}
				}
				logger.info("Exiting the execute commands thread");
			}
		};
	}
	
	public void enqueue(CommandExecutor<R, S> command) {
		logger.debug("Adding command to queue");
		this.outgoingCommands.add(command);
	}
	
	public void stop() {
		this.stopping = true;
		// Yield to allow the other threads to update the stopping attribute
		Thread.yield();
		logger.debug("Stopping the different threads");
		// Interrupt the threads....
		this.readCommands.interrupt();
		logger.info("Interrupting write command thread");
		this.writeCommands.interrupt();
		logger.info("interrupting execute command thread");
		this.executeCommands.interrupt();
		logger.info("closing socket");
		try {
			this.socket.close();
		} catch (IOException e) {
			logger.warn("An exception was raised while closing the socket.", e);
		}
		if (this.writeCommands.isAlive()) {
			StackTraceElement stackTrace[] = this.writeCommands.getStackTrace();
			Throwable t = new Throwable();
			t.setStackTrace(stackTrace);
			logger.info("Thread not stopped", t);
			this.writeCommands.interrupt();
		}
		logger.debug("Completed the process of stopping all of the threads");
	}
}
