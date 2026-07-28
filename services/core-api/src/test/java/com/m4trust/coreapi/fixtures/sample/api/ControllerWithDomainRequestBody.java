package com.m4trust.coreapi.fixtures.sample.api;

import com.m4trust.coreapi.fixtures.sample.domain.DomainRequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ControllerWithDomainRequestBody {
  @PostMapping("/fixture-domain-body")
  public void accept(@RequestBody DomainRequestBody body) {}
}
