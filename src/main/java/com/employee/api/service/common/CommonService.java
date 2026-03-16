package com.employee.api.service.common;

import com.employee.api.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;

import java.util.function.Supplier;

public class CommonService {
    //public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier)
    //Supplier의 추상메서드  T get()
    private static Supplier<ResourceNotFoundException> getNotFoundExceptionSupplier(String msg,
                                                                                    Long departmentId) {
        return () -> new ResourceNotFoundException(msg + departmentId, HttpStatus.NOT_FOUND);
    }
}
