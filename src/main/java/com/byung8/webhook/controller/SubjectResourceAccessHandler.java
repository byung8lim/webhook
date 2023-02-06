package com.byung8.webhook.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.byung8.common.domain.IResult;
import com.byung8.common.domain.Result;
import com.byung8.webhook.exception.WebhookException;

import io.kubernetes.client.openapi.models.V1SubjectAccessReview;
import io.kubernetes.client.openapi.models.V1SubjectAccessReviewSpec;
import io.kubernetes.client.openapi.models.V1SubjectAccessReviewStatus;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
public class SubjectResourceAccessHandler extends BaseHandler {
	
	@RequestMapping(value = "/authoz", method = RequestMethod.POST)
	public ResponseEntity<String> doPostAuth(@RequestBody final V1SubjectAccessReview subjectAccessReview,
			@RequestParam(value = "timeout", defaultValue = "10s") String timeout) {
		String txid = txId();
		log.info("txid:"+txid+", POST, timeout:"+timeout);
		return doAuth(txid, subjectAccessReview, timeout);
	}

	@RequestMapping(value = "/authoz", method = RequestMethod.GET)
	public ResponseEntity<String> dGetAuth(@RequestBody final V1SubjectAccessReview subjectAccessReview,
			@RequestParam(value = "timeout", defaultValue = "10s") String timeout) {
		String txid = txId();
		log.info("txid:"+txid+", GET, timeout:"+timeout);
		
		return doAuth(txid, subjectAccessReview, timeout);
	}

	private ResponseEntity<String> doAuth(String txid, V1SubjectAccessReview subjectAccessReview, String timeout) {
		V1SubjectAccessReview review = null;

		try {
			log.info("txid:"+txid+", starts , timeout="+timeout+" {"+toJson(subjectAccessReview)+"}");
			try {
				if (subjectAccessReview.getSpec() == null) {
					throw new WebhookException("No Spec");
				}
				if (subjectAccessReview.getSpec().getResourceAttributes() == null && 
						subjectAccessReview.getSpec().getNonResourceAttributes() == null) {
					throw new WebhookException("Unknown Attributes");
				}
				if (subjectAccessReview.getSpec().getResourceAttributes() != null && 
						subjectAccessReview.getSpec().getNonResourceAttributes() != null) {
					throw new WebhookException("Illegal Attributes Definition");
				}
				
				review = handleAccessReview(txid, subjectAccessReview.getSpec());
				review.setApiVersion(subjectAccessReview.getApiVersion());
				review.setKind(subjectAccessReview.getKind());
			} catch (WebhookException we) {
				review = new V1SubjectAccessReview();
				review.setApiVersion(subjectAccessReview.getApiVersion());
				review.setKind(subjectAccessReview.getKind());
				review.setStatus(new V1SubjectAccessReviewStatus());
				review.getStatus().setAllowed(false);
				review.getStatus().setDenied(true);
				review.getStatus().setReason(we.getMessage());
			}
			log.info("txid:"+txid+", response {"+toJson(review));
			return new ResponseEntity<String>(toJson(review), HttpStatus.OK);
		} catch (Exception e) {
			Result result = new Result(txid, IResult.ERROR, "subjectAccessReview Error").putValue("error", e);
			return new ResponseEntity<String>(result.toJson(), result.status());
		}
	}
	
	private V1SubjectAccessReview handleAccessReview(String txid, V1SubjectAccessReviewSpec spec) throws WebhookException {
		V1SubjectAccessReview review = new V1SubjectAccessReview();
		review.setStatus(new V1SubjectAccessReviewStatus());
		if (spec.getUser() == null || spec.getUser().isBlank()) {
			review.getStatus().setAllowed(false);
			review.getStatus().setDenied(true);
			review.getStatus().setReason("Unknown User not allowed and denied");
		} else {
			if (spec.getResourceAttributes() != null) {
				log.info("txid:"+txid+"ResourceAccessReview {"+spec.getResourceAttributes()+"}");
			} else {
				log.info("txid:"+txid+"NonResourceAccessReview {"+spec.getNonResourceAttributes()+"}");
			}
			if (spec.getUser().equalsIgnoreCase("guest")) {
				review.getStatus().setAllowed(false);
				review.getStatus().setDenied(null);
				review.getStatus().setReason("Guest not allowed");
			} else {
				review.getStatus().setAllowed(true);
				review.getStatus().setDenied(null);
			}
		}
		return review;
	}
}
