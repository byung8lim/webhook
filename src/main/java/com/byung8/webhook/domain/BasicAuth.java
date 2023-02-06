package com.byung8.webhook.domain;

import com.byung8.common.domain.Response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class BasicAuth extends Response{
	private String user;
	private String uid;
	private Boolean authenticated;
}
