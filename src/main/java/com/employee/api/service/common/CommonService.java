package com.employee.api.service.common;

import com.employee.api.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;

import java.util.function.Supplier;

public class CommonService {
    //public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier)
    //Supplier의 추상메서드  T get()
    public static Supplier<ResourceNotFoundException> getNotFoundExceptionSupplier(String msg,
                                                                                   Long entityId) {
        return () -> new ResourceNotFoundException(msg + entityId, HttpStatus.NOT_FOUND);
    }

    //Variable Argument(가변적 인자 ) ...
    public static Supplier<ResourceNotFoundException> getNotFoundExceptionSupplier(String msg, Object... args) {
        return () -> {
            StringBuilder fullMsg = new StringBuilder(msg);
            for (Object arg : args) {
                fullMsg.append(arg);
            }
            return new ResourceNotFoundException(fullMsg.toString(), HttpStatus.NOT_FOUND);
        };
    }
}
