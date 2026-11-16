package com.ecommerce.order_service.service.impl;

import com.ecommerce.order_service.dto.OrderRequest;
import com.ecommerce.order_service.dto.OrderResponse;
import com.ecommerce.order_service.event.OrderPlacedEvent;
import com.ecommerce.order_service.exception.ResourseNotFoundException;
import com.ecommerce.order_service.mapper.OrderMapper;
import com.ecommerce.order_service.model.Order;
import com.ecommerce.order_service.model.OrderStatus;
import com.ecommerce.order_service.repository.OrderRepository;
import com.ecommerce.order_service.service.OrderService;
import com.ecommerce.order_service.service.OutboxService;
import com.ecommerce.order_service.service.client.InventoryClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@RefreshScope
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    //    private final WebClient.Builder webClientBuilder;
//    private final InventoryClient inventoryClient;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxService outboxService;

    @Value("${order.enabled:true}")
    private boolean ordersEnabled;

    public OrderResponse fallbackMethod(OrderRequest orderRequest, String userId, Throwable throwable) {
        log.error("Circuit Breaker activado. Causa: {}", throwable.getMessage());

        throw new RuntimeException("El servicio de Inventario no responde. Por favor intente mas tarde.");
    }


    @Override
    @Transactional
//    @CircuitBreaker(name = "inventory", fallbackMethod = "fallbackMethod")
//    @Retry(name = "inventory")
//    @TimeLimiter(name = "inventory")
    public OrderResponse placeOrder(OrderRequest orderRequest, String userId) {

        if (!ordersEnabled) {
            log.warn("Pedido rechazado: Servicio deshabilitado por configuracion");
            throw new RuntimeException("El servicio de pedidos esta actualmente en mantenimiento. Intente mas tarde");
        }

        Order order = orderMapper.toOrder(orderRequest);
        order.setUserId(userId);
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setStatus(OrderStatus.PLACED);
        Order saveOrder = orderRepository.save(order);
        log.info("Orden guardado con exito. ID: {}", saveOrder.getId());

        List<OrderPlacedEvent.OrderItemEvent> orderItems =
                order.getOrderLineItemsList().stream()
                        .map(item -> new OrderPlacedEvent.OrderItemEvent(
                                item.getSku(), item.getPrice().toString(), item.getQuantity()
                        )).toList();

        OrderPlacedEvent event = new OrderPlacedEvent(
                saveOrder.getOrderNumber(), orderRequest.getEmail(), orderItems
        );

        boolean sentToRabbit = false;

        try {
            rabbitTemplate.convertAndSend("order-events", "order.placed", event);
            sentToRabbit = true;
            log.info("Mensaje enviado inmediatamente a RabbitMQ: {}", saveOrder.getOrderNumber());
        } catch (AmqpException e) {
            log.error("RabbitMQ caido. El Outbox asegura el envio posterior para la orden {}", saveOrder.getOrderNumber());
        }

        outboxService.saveOrderPlacedEvent(event, sentToRabbit );

        log.info("Evento enviado a RabbitMQ para la orden: {}", saveOrder.getOrderNumber());

        return orderMapper.toOrderResponse(saveOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders(String userId, boolean isAdmin) {
        List<Order> orders;

        if (isAdmin) {
            orders = orderRepository.findAll();
        } else {
            orders = orderRepository.findByUserId(userId);
        }

        return orders.stream()
                .map(orderMapper::toOrderResponse)
                .toList();
    }

//    @Override
//    @Transactional(readOnly = true)
//    public List<OrderResponse> getAllOrders() {
//        return orderRepository.findAll().stream()
//                .map(orderMapper::toOrderResponse)
//                .toList();
//    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourseNotFoundException("Orden", "id", id));

        return orderMapper.toOrderResponse(order);
    }

    @Override
    @Transactional
    public void deleteOrder(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new ResourseNotFoundException("Orden", "id", id);
        }

        orderRepository.deleteById(id);
        log.info("Orden eliminada con exito con el id: {}", id);
    }

    @Override
    @Transactional
    public void updateStatusOrder(String orderNumber, OrderStatus newStatus) {
        orderRepository.findByOrderNumber(orderNumber).ifPresentOrElse(
                order -> {
                    order.setStatus(newStatus);
                    orderRepository.save(order);
                    log.info("Estado actualizado para la orden {}", orderNumber);
                },
                () -> log.error("No se encontro la orden {} para actualizar", orderNumber)
        );

    }
}
