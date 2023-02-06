package com.byung8.webhook.controller;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.HttpHeaders;

import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.byung8.common.domain.IResult;
import com.byung8.common.domain.Result;
import com.byung8.webhook.domain.BasicAuth;
import com.byung8.webhook.exception.WebhookException;
import com.byung8.webhook.util.CommonUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1TokenReview;
import io.kubernetes.client.openapi.models.V1TokenReviewSpec;
import io.kubernetes.client.openapi.models.V1TokenReviewStatus;
import io.kubernetes.client.openapi.models.V1UserInfo;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
public class WebhookHandler extends BaseHandler {
	
	@RequestMapping(value = "/authen", method = RequestMethod.POST)
	public ResponseEntity<String> doPostAuth(@RequestBody final V1TokenReview tokenReview) {
		String txid = txId();
		
		return doAuth(txid, tokenReview);
	}
		
	@RequestMapping(value = "/authen", method = RequestMethod.GET)
	public ResponseEntity<String> doGetAuth(@RequestBody final V1TokenReview tokenReview) {
		String txid = txId();
		
		return doAuth(txid, tokenReview);
	}

	public ResponseEntity<String> doAuth(String txid, V1TokenReview tokenReview) {
		String result = null;
		try {
			log.info("txid:"+txid+",POST,event{"+tokenReview+"}");
			V1TokenReview review = new V1TokenReview();
			review.setApiVersion(tokenReview.getApiVersion());
			review.setKind(tokenReview.getKind());
			review.setMetadata(new V1ObjectMeta());
			review.getMetadata().setCreationTimestamp(null);
			
			review.setStatus(new V1TokenReviewStatus());

			review.setSpec(new V1TokenReviewSpec());
			review.getSpec().setToken(tokenReview.getSpec().getToken());
			
			String token = null;

			try {
				if (tokenReview.getSpec() == null) {
					throw new WebhookException("no spec");
				}
				
				if ((token = tokenReview.getSpec().getToken()) == null) {
					throw new WebhookException("no token");
				}
				
				if (!token.contains(":")) {
					throw new WebhookException("Invalid tokenk format : not colon-seperated ("+token+")");
				}
				
				String[] fields = token.split(":");
				if (fields.length < 2) {
					throw new WebhookException("Invalid tokenk format : may not have password field ("+token+")");
				}
				
				review.getStatus().setAuthenticated(true);
				review.getStatus().setUser(new V1UserInfo());
				review.getStatus().getUser().setUid(fields[0]);
				review.getStatus().getUser().setUsername(fields[0]);
				List<String> group = new ArrayList<String>();
				group.add("system:masters");
				review.getStatus().getUser().setGroups(group);
			} catch (WebhookException we) {
				review.getStatus().setAuthenticated(false);
				log.warn("txid:"+txid+",review has a wrong formatted token : "+we.getMessage());
			}
			result = toJson(review);
			log.info("txid:"+txid+",response : "+result);
			
			return new ResponseEntity<String>(result, HttpStatus.OK);
		} catch (Exception e) {
			Result ret = new Result(txid, IResult.ERROR, "tokenReview Error").putValue("error", e);
			return new ResponseEntity<String>(toJson(ret), ret.status());
		}
	}

	@RequestMapping(value = "/basic-auth/{username}", method = RequestMethod.GET)
	public ResponseEntity<String> getBasicAuth(
			@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
			@PathVariable("username") final String username) {
		String txid = txId();
		Gson gson = new GsonBuilder().setPrettyPrinting().setDateFormat("yyyyMMddHHmmss").create();
		try {
			log.info("txid:"+txid+",basic-auth{authorization: "+authorization+",username: "+username+"}");
			BasicAuth basicAuth = new BasicAuth();
			basicAuth.setUser(username);
			basicAuth.setUid(username);
			try {
				if (authorization == null || authorization.isBlank()) {
					throw new WebhookException("authorization is null");
				}
				
				if (!authorization.contains("Basic")) {
					throw new WebhookException("authorization is invalid format(no Basic)");
				}
				
				String[] splitted = authorization.split(" ");
				if (splitted.length < 2) {
					throw new WebhookException("authorization is invalid format(too short)");
				}
				String decoded = new String(Base64.decodeBase64(splitted[1]));
				log.info("txid:"+txid+", decoded: "+decoded);
				String[] fields = decoded.split(":");
				if (fields.length < 2) {
					throw new WebhookException("authorization is invalid format(not colon-seperated)");
				}
				if(fields[0].equals(username)) {
					log.info("txid:"+txid+", "+decoded+" is authoried");
					basicAuth.setAuthenticated(true);
				} else {
					log.info("txid:"+txid+", "+decoded+" is unauthoried{username is not identical}");
					basicAuth.setAuthenticated(false);
				}
			} catch (WebhookException we) {
				basicAuth.setAuthenticated(false);
				log.warn("txid: "+txid+", authorization ("+authorization+"), e:"+we.getMessage());
			} 
			log.info("txid:"+txid+",response {"+basicAuth.toJson()+"}");
			return new ResponseEntity<String>(basicAuth.toJson(), HttpStatus.OK);
		} catch (Exception e) {
			Result ret = new Result(CommonUtil.evtId(), IResult.ERROR, "tokenReview Errpr").putValue("error", e);
			return new ResponseEntity<String>(gson.toJson(ret), ret.status());
		}
	}
}
