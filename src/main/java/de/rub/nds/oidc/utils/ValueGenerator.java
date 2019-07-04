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

package de.rub.nds.oidc.utils;

import de.rub.nds.oidc.server.ProfConfig;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;


/**
 *
 * @author Tobias Wich
 */
@ApplicationScoped
public class ValueGenerator {

	private final SecureRandom rand;

	@Inject
	ProfConfig cfg;

	public ValueGenerator() throws NoSuchAlgorithmException {
		rand = new SecureRandom();
	}


	public String generateTestId() {
//		byte[] data = new byte[16];
//		rand.nextBytes(data);
//		String testId = Base64.getUrlEncoder().withoutPadding().encodeToString(data);
//		return testId;
		return generateRandString(cfg.getTestIdLength());
	}

	public String generateRandString(int length) {
		byte[] data = new byte[length];
		rand.nextBytes(data);
		String testId = Base64.getUrlEncoder().withoutPadding().encodeToString(data);
		return testId;
	}

}
