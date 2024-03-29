/****************************************************************************
 * Copyright 2019 Ruhr-Universität Bochum.
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

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;


/**
 * A stripped down implementation of a JSON object that allows 
 * for keys to be repetitive and maintains the order of insertion. 
 * For Example
 * 	{@code
 * 		UnsafeJSONObject jo = new UnsafeJSONObject();
 * 		jo.append("a", "1");
 * 		jo.append("b", 2);	
 * 		jo.append("a", "3");
 * 		jo.toString();
 * 	}
 * 	
 * 	returns {@code '{"a": "1", "b": 2, "a": "3"}'}
 */
public class UnsafeJSONObject {

	private ArrayList<Pair<String, Object>> members;

	public UnsafeJSONObject() {
		this.members = new ArrayList<>();
	}

	private void put(String key, Object val) {
		// append an immutable pair
		members.add(Pair.of(key, val));
	}

	public void append(String key, String val) {
		put(key, val);
	}

	public void append(String key, int val) {
		put(key, val);
	}

	public void append(String key, JSONArray val) {
		put(key, val);
	}

	public void append(String key, JSONObject val) {
		put(key, val);
	}

	public List<Pair<String,Object>> get(String key) {
		List<Pair<String,Object>> result = members.stream()
				.filter((k) -> k.getKey().equals(key))
				.collect(Collectors.toList());
		return result;
	}

	public String getAsString(String key) {
		StringBuilder sb = new StringBuilder();
		if (contains(key)) {
			get(key).stream().forEach((p) -> sb.append(p.getValue().toString() + " "));
			sb.setLength(sb.length() - 1);
			return sb.toString();
		}
		return "";
	}

	public Collection<Object> values() {
		List<Object> vals = new ArrayList<>();
		members.forEach((p) -> vals.add(p.getValue()));
		return vals;
	} 

	public boolean contains(String key) {
		return ! get(key).isEmpty();
	}

	public String getMultiKVStringByKey(String key) {
		List<Pair<String, Object>> entries = get(key);

		StringBuilder entryString = new StringBuilder();
		entries.forEach(p -> {
			entryString.append(getEntryString(p));
			entryString.append(", ");
		});
		// remove trailing comma
		entryString.setLength(entryString.length() - 2);

		return entryString.toString();
	}

	public String toJSONString() {
		StringBuilder jsonString = new StringBuilder();

		jsonString.append("{");
		members.forEach(p -> {
			jsonString.append(getEntryString(p));
			jsonString.append((", "));
		});
		// remove trailing comma
		jsonString.setLength(jsonString.length() - 2);
		jsonString.append("}");

		return jsonString.toString();
	}

	@Override
	public String toString() {
		return toJSONString();
	}

	private String getEntryString(Pair<String, Object> p) {
		String k = p.getKey();
		Object v = p.getValue();

		StringBuilder entryString = new StringBuilder();
		entryString.append("\"" + JSONObject.escape(k) + "\": ");
		if (v instanceof String) {
			entryString.append("\"");
			entryString.append(JSONObject.escape(v.toString()));
			entryString.append("\"");
		} else {
			entryString.append(v.toString());
		}

		return entryString.toString();
	}

}
