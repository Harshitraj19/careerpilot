package com.placify.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.placify.web.gateway.GatewayClient;
import com.placify.web.gateway.GatewayException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** Public page opened from the QR code on a placement certificate. */
@Controller
@RequiredArgsConstructor
public class VerifyController {

    private final GatewayClient gateway;

    @GetMapping("/verify")
    public String form(@RequestParam(required = false) String code) {
        if (code != null && code.matches("[A-Za-z0-9-]{4,64}")) {
            return "redirect:/verify/" + code;
        }
        return "common/verify";
    }

    @GetMapping("/verify/{code}")
    public String verify(@PathVariable String code, HttpServletRequest request, Model model) {
        model.addAttribute("code", code);
        if (!code.matches("[A-Za-z0-9-]{4,64}")) {
            model.addAttribute("error", "That is not a valid certificate code.");
            return "common/verify";
        }
        try {
            JsonNode r = gateway.json(HttpMethod.GET, "/api/placement/certificates/verify/" + code, null, null, request);
            model.addAttribute("valid", r.path("valid").asBoolean());
            model.addAttribute("message", r.path("message").asText());
            JsonNode c = r.path("certificate");
            if (!c.isMissingNode() && !c.isNull()) {
                model.addAttribute("cert", new Certificate(c.path("studentName").asText(), c.path("regNo").asText(),
                        c.path("branch").asText(), c.path("companyName").asText(), c.path("role").asText(),
                        c.path("ctcLpa").asText(""), c.path("issuedAt").asText(""), c.path("verificationCode").asText()));
            }
        } catch (GatewayException ex) {
            model.addAttribute("error", ex.getStatus() == 404 ? "No certificate found with this code." : ex.display());
        }
        return "common/verify";
    }

    public record Certificate(String studentName, String regNo, String branch, String companyName, String role,
                              String ctcLpa, String issuedAt, String code) {
    }
}
