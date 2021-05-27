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

import java.util.HashMap;

import javax.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stringtemplate.v4.ST;


/**
 *
 * @author Tobias Wich
 */
@ApplicationScoped
public class TemplateEngine {

//	private final VelocityEngine engine;
	private static Logger logger = LoggerFactory.getLogger(TemplateEngine.class.getPackage() + ".TemplateEngine");

	public String eval(HashMap<String, Object> context, String template) {

		ST st = new ST(template, '§','§');
		context.entrySet().stream().forEach(entry -> {st.add(entry.getKey(), entry.getValue());});

		String result = st.render();
		logger.info(result);
		return result;
	}

}
