package com.phu.ecommerceapi.inventory.api;

import com.phu.ecommerceapi.inventory.application.StockEventBroadcaster;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/products")
public class StockEventStreamController {

    private final StockEventBroadcaster stockEventBroadcaster;

    public StockEventStreamController(StockEventBroadcaster stockEventBroadcaster) {
        this.stockEventBroadcaster = stockEventBroadcaster;
    }

    @GetMapping(value = "/{productId}/stock/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStockChanges(@PathVariable long productId) {
        return stockEventBroadcaster.subscribe(productId);
    }
}
