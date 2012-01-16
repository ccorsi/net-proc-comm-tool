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

import java.io.Serializable;

/**
 * This is the main interface that all users of this library will be required to
 * implement.  This interface implementation will have intimate knowledge of what
 * actions need to be performed on the passed instances.  The current library 
 * has no claims on this and leaves it to the user of this library.
 * 
 * @author Claudio Corsi
 *
 */
public interface CommandExecutor<S, R> extends Serializable {

	/**
	 * This method will apply the given changes to the passed instance.
	 * It will return a reply to be sent to the remote process.  The
	 * returned instance can be null.
	 * 
	 * @param destination The instance that this instance will perform
	 *        the given request/reply
	 *        
	 * @return An instance of a CommandExecutor that will be sent to
	 *         the requesting/replying process.  This can be null
	 */
	CommandExecutor<R, S> apply(S destination);
	
}
