#include "predix/engine/matching_core.hpp"
#include "predix/server/grpc_server.hpp"
#include "predix/wal/wal_writer.hpp"

#include <cstdlib>
#include <fstream>
#include <iostream>
#include <memory>
#include <string>

namespace {

std::string trim(const std::string& value) {
    const auto start = value.find_first_not_of(" \t\r\n");
    if (start == std::string::npos) {
        return "";
    }
    const auto end = value.find_last_not_of(" \t\r\n");
    return value.substr(start, end - start + 1);
}

predix::server::CoreConfig loadConfig(const std::string& path) {
    predix::server::CoreConfig config;
    std::ifstream input(path);
    if (!input.is_open()) {
        return config;
    }

    std::string line;
    while (std::getline(input, line)) {
        line = trim(line);
        if (line.empty() || line[0] == '#') {
            continue;
        }

        const auto colon = line.find(':');
        if (colon == std::string::npos) {
            continue;
        }

        const std::string key = trim(line.substr(0, colon));
        const std::string value = trim(line.substr(colon + 1));

        if (key == "listen_port") {
            config.listen_address = "0.0.0.0:" + value;
        } else if (key == "shard_count") {
            config.shard_count = static_cast<std::size_t>(std::stoul(value));
        } else if (key == "wal_path") {
            config.wal_path = value;
        } else if (key == "wal_flush_each_append") {
            config.wal_flush_each_append = value == "true" || value == "1";
        }
    }

    return config;
}

}  // namespace

int main(int argc, char** argv) {
    const char* config_path = std::getenv("PREDIX_CORE_CONFIG");
    if (config_path == nullptr) {
        config_path = "config/core.yaml";
    }
    if (argc > 1) {
        config_path = argv[1];
    }

    const predix::server::CoreConfig config = loadConfig(config_path);
    auto wal = std::make_shared<predix::wal::WalWriter>(config.wal_path, config.wal_flush_each_append);
    auto core = std::make_shared<predix::engine::MatchingCore>(config.shard_count, wal);

    predix::server::GrpcServer server(config, core);
    server.start();

    std::cout << "predix matching core listening on " << config.listen_address << std::endl;
    server.wait();
    return 0;
}
