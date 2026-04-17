package com.trindadeeesx.stocksentry.domain.push;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VapidPublicKeyResponse {
	private String publicKey;
}