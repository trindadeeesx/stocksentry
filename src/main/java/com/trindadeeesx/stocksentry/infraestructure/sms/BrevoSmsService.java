package com.trindadeeesx.stocksentry.infraestructure.sms;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class BrevoSmsService {
    @Value("${brevo.api-key}")
    private String apiKey;

    @Value("${brevo.sms-sender}")
    private String sender;

    private final RestClient restClient;

    public BrevoSmsService(RestClient restClient) {
        this.restClient = restClient;
    }

    public void send(String to, String message) {
        String payload = """
                {
                  "sender": "%s",
                  "recipient": "%s",
                  "content": "%s"
                }
                """.formatted(sender, sanitizePhone(to), message);

        restClient.post()
                .uri("https://api.brevo.com/v3/transactionalSMS/sms")
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    private String sanitizePhone(String phone) {
        String digits = phone.replaceAll("\\D", "");
        if (!digits.startsWith("55")) {
            digits = "55" + digits;
        }
        return "+" + digits;
    }
}
