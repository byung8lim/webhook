package com.byung8.webhook.controller;

import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class BaseHandler {
	
	protected String txId() {
		return UUID.randomUUID().toString();
	}

	String toJson(Object obj) {
		Gson gson = new GsonBuilder().setPrettyPrinting().setDateFormat("yyyyMMddHHmmss").create();
		return gson.toJson(obj);
	}
}
