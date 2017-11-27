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

import de.rub.nds.oidc.test_model.TestOPConfigType;
import de.rub.nds.oidc.test_model.TestRPConfigType;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import javax.enterprise.context.ApplicationScoped;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 * @author Tobias Wich
 */
@ApplicationScoped
public class TemplateEngine {

	private final VelocityEngine engine;

	public TemplateEngine() {
		this.engine = new VelocityEngine();
		Logger logger = LoggerFactory.getLogger(TemplateEngine.class.getPackage() + ".TemplateEngine");
		this.engine.setProperty(Velocity.RUNTIME_LOG_LOGSYSTEM, new Slf4jLogger(logger));
		this.engine.init();
	}

	public VelocityEngine getEngine() {
		return engine;
	}

	public Context createContext(TestRPConfigType rpCfg) {
		VelocityContext ctx = new VelocityContext();
		ctx.put("rp", rpCfg);
		return ctx;
	}

	public Context createContext(TestOPConfigType opCfg) {
		VelocityContext ctx = new VelocityContext();
		ctx.put("op", opCfg);
		return ctx;
	}

	public String eval(Context ctx, String template) {
		return eval(ctx, new StringReader(template));
	}

	public String eval(Context ctx, Reader template) {
		StringWriter w = new StringWriter();
		engine.evaluate(ctx, w, "opiv", template);
		return w.toString();
	}

}
