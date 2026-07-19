package com.m4trust.coreapi.contractintelligence;
import java.net.URI; import com.m4trust.coreapi.api.CorrelationIdFilter; import jakarta.servlet.http.HttpServletRequest; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
@RestControllerAdvice class AnalysisExceptionHandler {
 @ExceptionHandler(AnalysisExceptions.NotFound.class) ResponseEntity<ProblemDetail> notFound(HttpServletRequest r){return response(r,HttpStatus.NOT_FOUND,"DEAL_NOT_FOUND");}
 @ExceptionHandler(AnalysisExceptions.Forbidden.class) ResponseEntity<ProblemDetail> forbidden(HttpServletRequest r){return response(r,HttpStatus.FORBIDDEN,"DEAL_ANALYSIS_REQUEST_FORBIDDEN");}
 @ExceptionHandler(AnalysisExceptions.Conflict.class) ResponseEntity<ProblemDetail> conflict(AnalysisExceptions.Conflict e,HttpServletRequest r){return response(r,HttpStatus.CONFLICT,e.code);}
 private ResponseEntity<ProblemDetail> response(HttpServletRequest r,HttpStatus status,String code){ProblemDetail p=ProblemDetail.forStatusAndDetail(status,"The request conflicts with the current resource state.");p.setType(URI.create("https://problems.m4trust.internal/"+code.toLowerCase().replace('_','-')));p.setTitle(status.getReasonPhrase());p.setInstance(URI.create(r.getRequestURI()));p.setProperty("code",code);Object c=r.getAttribute(CorrelationIdFilter.ATTRIBUTE);p.setProperty("correlationId",c==null?"":c.toString());return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(p);}
}
