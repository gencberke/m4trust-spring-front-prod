package com.m4trust.coreapi.fixtures.sample.api;

import com.m4trust.coreapi.fixtures.sample.apimodel.NearMissRequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ControllerWithNearMissRequestBody {
  @PostMapping("/fixture-near-miss-body")
  public void accept(@RequestBody NearMissRequestBody body) {}
}
