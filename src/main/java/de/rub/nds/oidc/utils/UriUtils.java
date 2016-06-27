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

import java.net.URI;
import javax.ws.rs.core.UriBuilder;


/**
 *
 * @author Tobias Wich
 */
public class UriUtils {

	public static URI normalize(URI uri) {
		uri = uri.normalize();
		UriBuilder b = UriBuilder.fromUri(uri);

		if (uri.getPort() == -1) {
			if ("http".equals(uri.getScheme())) {
				b = b.port(80);
			} else if ("https".equals(uri.getScheme())) {
				b = b.port(443);
			}
		}

		if (uri.getPath() == null || uri.getPath().equals("")) {
			b = b.replacePath("/");
		}

		return b.build();
	}

}
