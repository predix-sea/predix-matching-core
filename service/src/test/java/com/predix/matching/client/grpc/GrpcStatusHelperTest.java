package com.predix.matching.client.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcStatusHelperTest {

    @Test
    void deadlineExceeded_isUncertain() {
        assertThat(GrpcStatusHelper.isUncertain(statusException(Status.Code.DEADLINE_EXCEEDED))).isTrue();
    }

    @Test
    void invalidArgument_isNotUncertain() {
        assertThat(GrpcStatusHelper.isUncertain(statusException(Status.Code.INVALID_ARGUMENT))).isFalse();
    }

    private static StatusRuntimeException statusException(Status.Code code) {
        return Status.fromCode(code).withDescription("test").asRuntimeException();
    }
}
