package com.m4trust.coreapi.contracts;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1")
public final class ContractMetadataController {

    private final ContractBundleService contractBundleService;

    public ContractMetadataController(ContractBundleService contractBundleService) {
        this.contractBundleService = contractBundleService;
    }

    @GetMapping(path = "/contracts", produces = MediaType.APPLICATION_JSON_VALUE)
    public ContractBundleMetadata contracts() {
        return contractBundleService.metadata();
    }
}
