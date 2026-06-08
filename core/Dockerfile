FROM gcc:14-bookworm AS build
RUN sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list \
    && sed -i 's/security.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list \
    && apt-get update && apt-get install -y \
    cmake \
    pkg-config \
    libgrpc++-dev \
    protobuf-compiler-grpc \
    libprotobuf-dev \
    libgtest-dev \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY CMakeLists.txt .
COPY proto proto
COPY include include
COPY src src
COPY tests tests
COPY config config
RUN cmake -B build -DCMAKE_BUILD_TYPE=Release && cmake --build build -j && ctest --test-dir build --output-on-failure

FROM debian:bookworm-slim
ARG GRPCURL_VERSION=1.9.2
RUN sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list \
    && sed -i 's/security.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list \
    && apt-get update && apt-get install -y libstdc++6 libgrpc++1.51 libprotobuf32 ca-certificates wget \
    && wget -qO- "https://github.com/fullstorydev/grpcurl/releases/download/v${GRPCURL_VERSION}/grpcurl_${GRPCURL_VERSION}_linux_x86_64.tar.gz" \
       | tar xz -C /usr/local/bin grpcurl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /src/build/matching_core /app/matching_core
COPY config/core.yaml /app/config/core.yaml
RUN mkdir -p /var/lib/predix
ENV PREDIX_CORE_CONFIG=/app/config/core.yaml
EXPOSE 50051
HEALTHCHECK --interval=5s --timeout=5s --retries=12 --start-period=10s \
  CMD grpcurl -plaintext -d '{}' 127.0.0.1:50051 predix.matching.MatchingCore/Health | grep -q '"healthy": true'
ENTRYPOINT ["/app/matching_core"]
