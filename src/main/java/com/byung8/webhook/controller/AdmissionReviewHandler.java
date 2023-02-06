package com.byung8.webhook.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.kubernetes.client.admissionreview.models.AdmissionResponse;
import io.kubernetes.client.admissionreview.models.AdmissionReview;

import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
public class AdmissionReviewHandler extends BaseHandler {

/*
	public ResponseEntity<String> handleEventGET(HttpEntity<String> httpEntity) {
		String data = httpEntity.getBody();
 		...
 		return new ResponseEntity<String>(toJson(res), HttpStatus.OK);
 	}
 */
	
	@RequestMapping(value = "/admission", method = RequestMethod.POST)
	public ResponseEntity<String> handlePostReview(
			@RequestBody final AdmissionReview admissionReview,
			@RequestParam(value = "timeout", defaultValue = "10s") String timeout) {
		String txid = txId();
		log.info("txid:"+txid+", POST, timeout:"+timeout);
		AdmissionReview res = new AdmissionReview();
		res.setApiVersion(admissionReview.getApiVersion());
		res.setKind(admissionReview.getKind());
		res.setResponse(new AdmissionResponse());
		res.getResponse().setUid(admissionReview.getRequest().getUid());
		res.getResponse().setAllowed(true);
		
		ResponseEntity<String> response = new ResponseEntity<String>(toJson(res), HttpStatus.OK);
		log.info("txid:"+txid+", response {"+toJson(response)+"}");
		return response;
	}

	@RequestMapping(value = "/admission", method = RequestMethod.GET)
	public ResponseEntity<String> handleGetReview(
			@RequestBody final AdmissionReview admissionReview,
			@RequestParam(value = "timeout", defaultValue = "10s") String timeout) {
		String txid = txId();
		log.info("txid:"+txid+", GET, timeout:"+timeout);
		AdmissionReview res = new AdmissionReview();
		res.setApiVersion(admissionReview.getApiVersion());
		res.setKind(admissionReview.getKind());
		res.setResponse(new AdmissionResponse());
		res.getResponse().setUid(admissionReview.getRequest().getUid());
		res.getResponse().setAllowed(true);
		
		ResponseEntity<String> response = new ResponseEntity<String>(toJson(res), HttpStatus.OK);
		log.info("txid:"+txid+", response {"+toJson(response)+"}");
		return response;
	}
}
