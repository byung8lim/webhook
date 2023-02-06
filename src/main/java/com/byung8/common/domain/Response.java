package com.byung8.common.domain;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Response {
	public String toJson() {
		return toJson(true);
	}

	public String toJson(boolean isPretty) {
		Gson gson = null;
		if (isPretty) {
			gson = new GsonBuilder().setPrettyPrinting().setDateFormat("yyyyMMddHHmmss").create();
		} else {
			gson = new GsonBuilder().setDateFormat("yyyyMMddHHmmss").create();
		}
		
		String json = gson.toJson(this);

		return json;
	}
	
}
