package com.byung8.webhook.controller;

import java.io.FileReader;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.kubernetes.client.admissionreview.models.AdmissionResponse;
import io.kubernetes.client.admissionreview.models.AdmissionReview;
import io.kubernetes.client.admissionreview.models.Status;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;

@RestController
@Configuration
@Slf4j
public class MutatingWebhookHandler extends BaseHandler {

	private static String patchFile;
	
	@Value("${webhook.patchfile}")
	public void setPatchFile(String filename) {
		patchFile = filename;
	}
	
	@RequestMapping(value = "/mutate", method = RequestMethod.POST)
	public ResponseEntity<String> handlePostMutating(
			@RequestBody final AdmissionReview admissionReview,
			@RequestParam(value = "timeout", defaultValue = "10s") String timeout) {
		String txid = txId();
		log.info("txid:"+txid+",handlePostMutating, timeout:"+timeout);
		return handleMutating(txid, admissionReview);
	}

	@RequestMapping(value = "/mutate", method = RequestMethod.GET)
	public ResponseEntity<String> handleGetMutating(
			@RequestBody final AdmissionReview admissionReview,
			@RequestParam(value = "timeout", defaultValue = "10s") String timeout) {
		String txid = txId();
		log.info("txid:"+txid+",handleGetMutating, timeout:"+timeout);
		return handleMutating(txid, admissionReview);
	}
	
	private ResponseEntity<String> handleMutating(String txid, AdmissionReview ar) {
		log.info("txid:"+txid+",admissionReview {"+toJson(ar)+"}");
		AdmissionReview result = new AdmissionReview();
		AdmissionResponse response = new AdmissionResponse();
		JSONParser parser = new JSONParser();
		result.setApiVersion(ar.getApiVersion());
		result.setKind(ar.getKind());
		result.setResponse(response);
		response.setUid(ar.getRequest().getUid());
		try {
			log.info("txid:"+txid+", patchFile:"+patchFile);
			Object obj = parser.parse(new FileReader(patchFile));
			String patchStatement = null;
			if (obj instanceof JSONArray) {
				JSONArray jsonArray = (JSONArray)obj;
				patchStatement = toJson(jsonArray);
				log.info("txid:"+txid+", JsonArray patch {"+patchStatement+"}");
			} else if (obj instanceof JSONObject) {
				JSONObject jsonObject = (JSONObject)obj;
				patchStatement = toJson(jsonObject);
				log.info("txid:"+txid+", JSONObject patch {"+patchStatement+"}");
			} else {
				patchStatement = toJson(obj);
				log.warn("txid:"+txid+", Unkown Object patch {"+patchStatement+"}");
			}
			response.setPatchType("JSONPatch");
			response.setPatch(patchStatement.getBytes());
			response.setAllowed(true);
			log.info("txid:"+txid+",length:"+response.getPatch().length);
		} catch(Exception e) {
			Status s = new Status();
			s.setApiVersion(ar.getApiVersion());
			s.setKind(ar.getKind());
			response.setStatus(s);
			response.setAllowed(false);
			log.error("txid:"+txid+","+e.getClass().getSimpleName()+" {"+e.getMessage()+"}");
			s.setReason(e.getClass().getSimpleName()+":"+e.getMessage());
		} finally {
			log.info("txid:"+txid+",handleMutating result {"+toJson(result)+"}");
			return new ResponseEntity<String>(toJson(result), HttpStatus.OK);
		}
	}
}
