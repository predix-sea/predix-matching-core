#include "predix/server/grpc_server.hpp"

#include "matching_core.grpc.pb.h"
#include "matching_core.pb.h"

#include <grpcpp/grpcpp.h>

#include <fstream>
#include <memory>
#include <sstream>
#include <stdexcept>
#include <string>

namespace {

std::string readFile(const std::string& path) {
    std::ifstream input(path);
    if (!input.is_open()) {
        throw std::runtime_error("failed to read TLS file: " + path);
    }
    std::ostringstream buffer;
    buffer << input.rdbuf();
    return buffer.str();
}

}  // namespace

namespace predix::server {

namespace {

engine::Side toEngineSide(predix::matching::Side side) {
    switch (side) {
        case predix::matching::SIDE_BUY:
            return engine::Side::BUY;
        case predix::matching::SIDE_SELL:
            return engine::Side::SELL;
        default:
            return engine::Side::BUY;
    }
}

predix::matching::Side toProtoSide(engine::Side side) {
    return side == engine::Side::BUY ? predix::matching::SIDE_BUY : predix::matching::SIDE_SELL;
}

engine::OrderType toEngineOrderType(predix::matching::OrderType order_type) {
    switch (order_type) {
        case predix::matching::ORDER_TYPE_MARKET:
            return engine::OrderType::MARKET;
        case predix::matching::ORDER_TYPE_LIMIT:
            return engine::OrderType::LIMIT;
        default:
            return engine::OrderType::LIMIT;
    }
}

predix::matching::OrderType toProtoOrderType(engine::OrderType order_type) {
    return order_type == engine::OrderType::MARKET ? predix::matching::ORDER_TYPE_MARKET
                                                   : predix::matching::ORDER_TYPE_LIMIT;
}

engine::BookOrder toEngineOrder(const predix::matching::BookOrderInput& in) {
    engine::BookOrder order;
    order.id = in.order_id();
    order.user_id = in.user_id();
    order.side = toEngineSide(in.side());
    order.order_type = toEngineOrderType(in.order_type());
    order.price = engine::Decimal::fromRaw(in.price());
    order.remaining_quantity = engine::Decimal::fromRaw(in.remaining_quantity());
    order.created_at_epoch_ms = in.created_at_ms();
    order.sequence = in.sequence();
    return order;
}

void toProtoOrder(const engine::BookOrder& in, predix::matching::BookOrderInput* out) {
    out->set_order_id(in.id);
    out->set_user_id(in.user_id);
    out->set_side(toProtoSide(in.side));
    out->set_order_type(toProtoOrderType(in.order_type));
    out->set_price(in.price.raw());
    out->set_remaining_quantity(in.remaining_quantity.raw());
    out->set_created_at_ms(in.created_at_epoch_ms);
    out->set_sequence(in.sequence);
}

class MatchingCoreServiceImpl final : public predix::matching::MatchingCore::Service {
public:
    explicit MatchingCoreServiceImpl(std::shared_ptr<engine::MatchingCore> core)
        : core_(std::move(core)) {}

    grpc::Status SubmitOrder(grpc::ServerContext*,
                             const predix::matching::SubmitOrderRequest* request,
                             predix::matching::SubmitOrderResponse* response) override {
        if (!request->has_order()) {
            return grpc::Status(grpc::StatusCode::INVALID_ARGUMENT, "order required");
        }
        const engine::MatchResult result = core_->submitOrder(
            request->market_id(), request->outcome_id(), toEngineOrder(request->order()));

        toProtoOrder(result.incoming_order, response->mutable_incoming_order());
        response->set_fully_filled(result.fully_filled);
        response->set_rejected(result.rejected);
        response->set_reject_reason(result.reject_reason);

        for (const auto& fill : result.fills) {
            auto* proto_fill = response->add_fills();
            proto_fill->set_maker_order_id(fill.maker_order_id);
            proto_fill->set_maker_user_id(fill.maker_user_id);
            proto_fill->set_taker_order_id(fill.taker_order_id);
            proto_fill->set_taker_user_id(fill.taker_user_id);
            proto_fill->set_price(fill.price.raw());
            proto_fill->set_quantity(fill.quantity.raw());
            proto_fill->set_buyer_is_taker(fill.buyer_is_taker);
        }
        return grpc::Status::OK;
    }

    grpc::Status CancelOrder(grpc::ServerContext*,
                             const predix::matching::CancelOrderRequest* request,
                             predix::matching::CancelOrderResponse* response) override {
        const bool removed =
            core_->cancelOrder(request->market_id(), request->outcome_id(), request->order_id());
        response->set_removed(removed);
        return grpc::Status::OK;
    }

    grpc::Status GetDepth(grpc::ServerContext*, const predix::matching::GetDepthRequest* request,
                          predix::matching::GetDepthResponse* response) override {
        const auto levels = core_->getDepth(request->market_id(), request->outcome_id(),
                                            request->levels());
        for (const auto& level : levels) {
            auto* proto_level = response->add_levels();
            proto_level->set_side(toProtoSide(level.side));
            proto_level->set_price(level.price.raw());
            proto_level->set_quantity(level.quantity.raw());
        }
        return grpc::Status::OK;
    }

    grpc::Status WarmupBook(grpc::ServerContext*,
                            const predix::matching::WarmupBookRequest* request,
                            predix::matching::WarmupBookResponse* response) override {
        std::vector<engine::BookOrder> orders;
        orders.reserve(static_cast<std::size_t>(request->orders_size()));
        for (const auto& in : request->orders()) {
            orders.push_back(toEngineOrder(in));
        }
        const int loaded = core_->warmupBook(request->market_id(), request->outcome_id(),
                                             std::move(orders), request->replace_existing());
        response->set_loaded_count(loaded);
        return grpc::Status::OK;
    }

    grpc::Status ResetBook(grpc::ServerContext*,
                           const predix::matching::ResetBookRequest* request,
                           predix::matching::ResetBookResponse* response) override {
        const bool reset = core_->resetBook(request->market_id(), request->outcome_id());
        response->set_reset(reset);
        return grpc::Status::OK;
    }

    grpc::Status Health(grpc::ServerContext*, const predix::matching::HealthRequest*,
                        predix::matching::HealthResponse* response) override {
        response->set_healthy(true);
        response->set_version("1.0.0");
        response->set_shard_count(static_cast<int32_t>(core_->shardCount()));
        return grpc::Status::OK;
    }

private:
    std::shared_ptr<engine::MatchingCore> core_;
};

}  // namespace

struct GrpcServer::Impl {
    CoreConfig config;
    std::shared_ptr<engine::MatchingCore> core;
    std::unique_ptr<MatchingCoreServiceImpl> service;
    std::unique_ptr<grpc::Server> server;
};

GrpcServer::GrpcServer(CoreConfig config, std::shared_ptr<engine::MatchingCore> core)
    : impl_(std::make_unique<Impl>()) {
    impl_->config = std::move(config);
    impl_->core = std::move(core);
}

GrpcServer::~GrpcServer() {
    shutdown();
}

void GrpcServer::start() {
    impl_->service = std::make_unique<MatchingCoreServiceImpl>(impl_->core);
    grpc::ServerBuilder builder;
    if (!impl_->config.tls_cert_path.empty() && !impl_->config.tls_key_path.empty()) {
        grpc::SslServerCredentialsOptions options;
        options.pem_key_cert_pairs.push_back({
            readFile(impl_->config.tls_key_path),
            readFile(impl_->config.tls_cert_path),
        });
        builder.AddListeningPort(impl_->config.listen_address, grpc::SslServerCredentials(options));
    } else {
        builder.AddListeningPort(impl_->config.listen_address, grpc::InsecureServerCredentials());
    }
    builder.RegisterService(impl_->service.get());
    impl_->server = builder.BuildAndStart();
    if (!impl_->server) {
        throw std::runtime_error("failed to start gRPC server on " + impl_->config.listen_address);
    }
}

void GrpcServer::wait() {
    if (impl_->server) {
        impl_->server->Wait();
    }
}

void GrpcServer::shutdown() {
    if (impl_->server) {
        impl_->server->Shutdown();
        impl_->server.reset();
    }
    impl_->service.reset();
}

}  // namespace predix::server
