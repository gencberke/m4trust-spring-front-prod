package com.m4trust.coreapi.contractintelligence;
import java.time.Instant; import java.util.UUID;
record DealDocumentAnalysis(UUID currentDocumentId,String status,Instant requestedAt,Instant processingStartedAt,Instant completedAt,Instant failedAt,Failure failure,Object result){ record Failure(String code,boolean retryRecommended){} }
