package com.m4trust.coreapi.fixtures.sample.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ControllerWithApiRequestBody {
  @PostMapping("/fixture-api-body")
  public void accept(@RequestBody ApiRequestBody body) {}
}
