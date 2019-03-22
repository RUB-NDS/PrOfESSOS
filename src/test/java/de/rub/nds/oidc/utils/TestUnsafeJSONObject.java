
package de.rub.nds.oidc.utils;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.apache.commons.lang3.tuple.Pair;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;

@Test(groups = {"unittests"})
public class TestUnsafeJSONObject {

	private ArrayList<Pair<String,Object>> referenceList;
	private UnsafeJSONObject jo;

	TestUnsafeJSONObject() {
		this.referenceList = new ArrayList<>();
		referenceList.add(Pair.of("foo", "bar"));
		referenceList.add(Pair.of("one",1));
		referenceList.add(Pair.of("foo", "baz"));

		this.jo = new UnsafeJSONObject();
		jo.append("foo", "bar");
		jo.append("one",1);
		jo.append("foo", "baz");

		JSONArray arr = new JSONArray();
		arr.appendElement(42);
		JSONObject o = new JSONObject();
		o.put("two", 2);
		arr.appendElement(o);

		jo.append("array", arr);
	}

	public void testGet() {

		referenceList.remove(1);
		Assert.assertEquals(jo.get("foo"), referenceList);
		Assert.assertEquals(jo.get("one").get(0).getValue(), 1);

		Object res = jo.get("array").get(0).getValue();
		Assert.assertTrue(res instanceof JSONArray);

		Assert.assertTrue(((JSONArray) res).get(1) instanceof JSONObject);
	}

	public void testContains() {
		Assert.assertTrue(jo.contains("one"));
		Assert.assertTrue(jo.contains("foo"));
		Assert.assertFalse(jo.contains("doh"));
	}

	public void testGetMultiKVStringByKey() {
		String expct = "\"foo\": \"bar\", \"foo\": \"baz\"";
		Assert.assertEquals(jo.getMultiKVStringByKey("foo"), expct);
	}

	public void testToJSONString() {
		String expct = "{\"foo\": \"bar\", \"one\": 1, \"foo\": \"baz\", \"array\": [42,{\"two\":2}]}";
		Assert.assertEquals(jo.toJSONString(), expct);
	}
}
