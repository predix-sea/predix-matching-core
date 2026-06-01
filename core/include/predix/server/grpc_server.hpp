#pragma once

#include "predix/engine/matching_core.hpp"

#include <memory>
#include <string>

namespace predix::server {

struct CoreConfig {
    std::string listen_address = "0.0.0.0:50051";
    std::size_t shard_count = 4;
    std::string wal_path = "/var/lib/predix/wal.log";
};

class GrpcServer {
public:
    GrpcServer(CoreConfig config, std::shared_ptr<engine::MatchingCore> core);
    ~GrpcServer();

    GrpcServer(const GrpcServer&) = delete;
    GrpcServer& operator=(const GrpcServer&) = delete;

    void start();
    void wait();
    void shutdown();

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

}  // namespace predix::server
