/****************************************************************************
 * Copyright 2016 Ruhr-Universität Bochum.
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
 ***************************************************************************/

package de.rub.nds.oidc.learn;

import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.log.LogChute;
import org.slf4j.Logger;


/**
 *
 * @author Tobias Wich
 */
public class Slf4jLogger implements LogChute {
    
    private final Logger logger;

    public Slf4jLogger(Logger logger) {
	this.logger = logger;
    }

    @Override
    public void init(RuntimeServices rs) throws Exception {
	// nothing to do
    }

    @Override
    public void log(int level, String message) {
	switch (level) {
	    case LogChute.TRACE_ID:
		logger.trace(message);
		break;
	    case LogChute.DEBUG_ID:
		logger.debug(message);
		break;
	    case LogChute.INFO_ID:
		logger.info(message);
		break;
	    case LogChute.WARN_ID:
		logger.warn(message);
		break;
	    case LogChute.ERROR_ID:
		logger.error(message);
		break;
	    default:
		logger.error("Unknown log level requested ({}).", level);
		logger.error("Message: {}", message);
	}
    }

    @Override
    public void log(int level, String message, Throwable t) {
	switch (level) {
	    case LogChute.TRACE_ID:
		logger.trace(message, t);
		break;
	    case LogChute.DEBUG_ID:
		logger.debug(message, t);
		break;
	    case LogChute.INFO_ID:
		logger.info(message, t);
		break;
	    case LogChute.WARN_ID:
		logger.warn(message, t);
		break;
	    case LogChute.ERROR_ID:
		logger.error(message, t);
		break;
	    default:
		logger.error("Unknown log level requested ({}).", level);
		logger.error("Message: " + message, t);
	}
    }

    @Override
    public boolean isLevelEnabled(int level) {
	switch (level) {
	    case LogChute.TRACE_ID:
		return logger.isTraceEnabled();
	    case LogChute.DEBUG_ID:
		return logger.isTraceEnabled();
	    case LogChute.INFO_ID:
		return logger.isTraceEnabled();
	    case LogChute.WARN_ID:
		return logger.isTraceEnabled();
	    case LogChute.ERROR_ID:
		return logger.isTraceEnabled();
	    default:
		return false;
	}
    }

}
