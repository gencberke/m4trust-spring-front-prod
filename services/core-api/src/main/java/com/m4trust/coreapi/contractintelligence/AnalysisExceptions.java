package com.m4trust.coreapi.contractintelligence;
class AnalysisExceptions { static class Forbidden extends RuntimeException{} static class NotFound extends RuntimeException{} static class Conflict extends RuntimeException{final String code; Conflict(String c){code=c;}} }
