package com.m4trust.coreapi.api;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo endpoint proving request validation and the Problem Details error
 * contract. Not a business capability.
 */
@RestController
public class EchoController {

    @PostMapping("/api/v1/echo")
    public EchoRequest echo(@Valid @RequestBody EchoRequest request) {
        return request;
    }
}
