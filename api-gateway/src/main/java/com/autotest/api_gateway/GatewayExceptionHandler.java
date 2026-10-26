package com.autotest.api_gateway;

import com.autotest.common_lib.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class GatewayExceptionHandler implements WebExceptionHandler {
    private final ObjectMapper mapper;
    public GatewayExceptionHandler(ObjectMapper mapper) { this.mapper=mapper; }
    @Override public Mono<Void> handle(ServerWebExchange exchange,Throwable error) {
        var response=exchange.getResponse();if(response.isCommitted())return Mono.error(error);
        BaseErrorCode code=error instanceof CommonException common?common.getErrorCode():
            error instanceof org.springframework.web.ErrorResponse http?CommonErrorCode.forStatus(http.getStatusCode().value()):
            hasCause(error,java.util.concurrent.TimeoutException.class)?CommonErrorCode.REQUEST_TIMEOUT:
            hasCause(error,java.net.ConnectException.class)||hasCause(error,java.net.UnknownHostException.class)?CommonErrorCode.SERVICE_UNAVAILABLE:CommonErrorCode.INTERNAL_SERVER_ERROR;
        String trace=UUID.randomUUID().toString();
        LoggerFactory.getLogger(getClass()).error("Gateway request failed code={} traceId={}",code.getCode(),trace,error);
        response.setStatusCode(code.getStatus());response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("X-Trace-Id",trace);response.getHeaders().setCacheControl("no-store");
        try {return response.writeWith(Mono.just(response.bufferFactory().wrap(mapper.writeValueAsBytes(ErrorResponse.of(code,trace)))));}
        catch(Exception failure) {return Mono.error(failure);}
    }
    private static boolean hasCause(Throwable error,Class<?> type) {
        for(int depth=0;error!=null&&depth<20;depth++,error=error.getCause())if(type.isInstance(error))return true;
        return false;
    }
}
