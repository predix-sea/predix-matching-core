FROM gcc:14-bookworm AS build
RUN apt-get update && apt-get install -y \
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
RUN apt-get update && apt-get install -y libstdc++6 libgrpc++1.51 libprotobuf32 ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /src/build/matching_core /app/matching_core
COPY config/core.yaml /app/config/core.yaml
RUN mkdir -p /var/lib/predix
ENV PREDIX_CORE_CONFIG=/app/config/core.yaml
EXPOSE 50051
ENTRYPOINT ["/app/matching_core"]
