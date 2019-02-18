package de.rub.nds.oidc.utils;

//import javafx.util.Pair;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
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
