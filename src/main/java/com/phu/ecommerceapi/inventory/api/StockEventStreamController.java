package com.phu.ecommerceapi.inventory.api;

import com.phu.ecommerceapi.inventory.application.StockEventBroadcaster;
import com.phu.ecommerceapi.inventory.application.StockChangedSseEvent;
import com.phu.ecommerceapi.shared.api.OpenApiExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/products")
@Tag(name = "Stock Events", description = "Server-Sent Events for advisory product stock changes.")
public class StockEventStreamController {

    private final StockEventBroadcaster stockEventBroadcaster;

    public StockEventStreamController(StockEventBroadcaster stockEventBroadcaster) {
        this.stockEventBroadcaster = stockEventBroadcaster;
    }

    @GetMapping(value = "/{productId}/stock/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Stream stock changes",
            description = "Subscribes to advisory stock updates published from the transactional outbox. Checkout still revalidates stock atomically."
    )
    @ApiResponse(
            responseCode = "200",
            description = "SSE stream of stock-changed events.",
            content = @Content(
                    mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(implementation = StockChangedSseEvent.class),
                    examples = @ExampleObject(value = OpenApiExamples.STOCK_EVENT_STREAM)
            )
    )
    public SseEmitter streamStockChanges(@PathVariable long productId) {
        return stockEventBroadcaster.subscribe(productId);
    }
}
