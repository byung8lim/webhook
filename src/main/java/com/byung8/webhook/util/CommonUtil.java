package com.byung8.webhook.util;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import com.byung8.webhook.exception.WebhookException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CommonUtil {
	private final static SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd");
	private final static SimpleDateFormat timeMiliFormat = new SimpleDateFormat("HH:mm:ss.SSS");
	private final static SimpleDateFormat timeMicroSeconds = new SimpleDateFormat("HH:mm:ss.SSSSSS");
	private final static SimpleDateFormat dayTimeMiliSeconds = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
	private final static SimpleDateFormat dayTimeMicroSeconds = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS");
	
	public static String evtId() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	public static long getTimestampHourAgo(long hour) {
		long time = System.currentTimeMillis() - (hour*60*60*1000);
		return time;
	}
	
	public static long getTimestampMinuteAgo(long min) {
		long time = System.currentTimeMillis() - (min*60*1000);
		return time;
	}
	
	public static long getTimestampSecondAgo(long second) {
		long time = System.currentTimeMillis() - (second*1000);
		return time;
	}
	
	public static Date dateFromLinuxStamp(long timestamp) {
		return Date.from(Instant.ofEpochMilli(timestamp));
	}
	
	public static int checkSecondLength(String daytime) {
		return daytime.length() - daytime.lastIndexOf(".") - 1;
	}
	
	public static String detetimeFormFromLinuxStamp(long timestamp) {
		Date d = dateFromLinuxStamp(timestamp);
		dayFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		String startString=dayFormat.format(timestamp)+"T"+timeMiliFormat.format(d);
		return startString;
	}

	public static String detetimeFormFromLinuxStampMicro(long timestamp) {
		Date d = dateFromLinuxStamp(timestamp);
		dayFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		String startString=dayFormat.format(timestamp)+"T"+timeMicroSeconds.format(d);
		return startString;
	}
	
	public static String detetimeFormFromLinuxStampZ(long timestamp) {
		return detetimeFormFromLinuxStamp(timestamp)+"Z";
	}
	public static String detetimeFormFromLinuxStampMicroZ(long timestamp) {
		return detetimeFormFromLinuxStampMicro(timestamp)+"Z";
	}
	
	public static long getLinuxStamp(String daytime) throws WebhookException{
		String tmp = daytime.replace("Z", "").replace("T", " ");
		try {
		if (checkSecondLength(tmp) > 3) {
			return dayTimeMicroSeconds.parse(tmp).getTime();
		} else {
			return dayTimeMiliSeconds.parse(tmp).getTime();
		}
		} catch (Exception e) {
			throw new WebhookException(e);
		}
	}
	
	
	public static long getLastModified(String filePath) {
		File file = new File(filePath);
		return file.lastModified();
	}
}
