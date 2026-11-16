package io.github.makeforge.clapi.core.network.grpc

interface DynamicGrpcClient {
    /**
     * Executes a unary gRPC call using dynamic protobuf descriptors.
     *
     * @param request The resolved gRPC call configuration (host, port, method, JSON payload, metadata).
     * @return [GrpcCallResponse] containing status code, formatted JSON response, metadata, and latency.
     */
    suspend fun executeUnary(request: GrpcCallRequest): GrpcCallResponse

    /**
     * Shuts down any cached channels cleanly.
     */
    suspend fun shutdown()
}
