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

import de.rub.nds.oidc.test_model.ParameterType;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 *
 * @author Tobias Wich
 */
@Immutable
public class InstanceParameters {

	private final Map<String, String> params;

	public InstanceParameters(Map<String, String> params) {
		this.params = Collections.unmodifiableMap(params);
	}

	public InstanceParameters(List<ParameterType> params) {
		this.params = Collections.unmodifiableMap(params.stream()
				.collect(Collectors.toMap(ParameterType::getKey, ParameterType::getValue)));
	}

	public boolean containsKey(String key) {
		return params.containsKey(key);
	}

	public Map<String, String> getMap() {
		return params;
	}

	@Nullable
	public String get(String key) {
		return params.get(key);
	}

	@Nonnull
	public boolean getBool(String key) {
		return Boolean.parseBoolean(get(key));
	}

	public void forEach(BiConsumer<? super String, ? super String> action) {
		params.forEach(action);
	}

}
