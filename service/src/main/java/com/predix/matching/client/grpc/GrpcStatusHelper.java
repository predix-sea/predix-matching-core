package com.predix.matching.client.grpc;

import com.predix.matching.exception.BusinessException;
import com.predix.matching.exception.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

public final class GrpcStatusHelper {

    private GrpcStatusHelper() {
    }

    public static boolean isUncertain(StatusRuntimeException exception) {
        return switch (exception.getStatus().getCode()) {
            case DEADLINE_EXCEEDED, UNAVAILABLE, CANCELLED, UNKNOWN, ABORTED -> true;
            default -> false;
        };
    }

    public static BusinessException toBusinessException(String operation, StatusRuntimeException exception) {
        Status status = exception.getStatus();
        ErrorCode code = isUncertain(exception)
                ? ErrorCode.MATCHING_CORE_UNCERTAIN
                : ErrorCode.MATCHING_CORE_UNAVAILABLE;
        String detail = operation + ": " + status.getCode() + " — " + status.getDescription();
        return new BusinessException(code, detail);
    }
}
