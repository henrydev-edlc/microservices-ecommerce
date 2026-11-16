package com.ecommerce.notification_service.listener;

import com.ecommerce.notification_service.event.OrderCancelledEvent;
import com.ecommerce.notification_service.event.OrderConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;



@Component
@Slf4j
@RequiredArgsConstructor
@RabbitListener(queues = "notification-queue")
public class OrderEventsListener {

    private final JavaMailSender mailSender;

    @RabbitHandler
    public void handleOrderConfirmedEvent(OrderConfirmedEvent event){

        log.info("Pedido confirmado para Orden: {}", event.orderNumber());

//        throw new AmqpRejectAndDontRequeueException("Error SMTP");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("pedidos@ecommerce.com");
        message.setTo(event.email());
        message.setSubject("Orden Confirmada - " + event.orderNumber());
        message.setText("Hola!\n\n" +
                "Tu pedido con numero " + event.orderNumber() + " ha  sido recibido exitosamente.\n" +
                "Pronto recibiras mas noticias sobre el envio.\n\n" +
                "Gracias por comprar con nosotros!"
        );
        mailSender.send(message);


        log.info("Enviando correo de confirmacion a: {}", event.email());
        log.info("Correo enviado exitosamente para la orden: {}", event.orderNumber());


    }


    @RabbitHandler
    public void handleOrderCancelledEvent(OrderCancelledEvent event){

        log.warn("Enviando correo de cancelacion para la orden: {}", event.orderNumber());


        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("pedidos@ecommerce.com");
        message.setTo(event.email());
        message.setSubject("Actualizacion de tu pedido - " + event.orderNumber());
        message.setText("Lamentamos informarte que tu pedido ha sido cancelado. \n\n" +
                "Motivo: " + event.reason() + ".\n" +
                "Si se realizo algun cargo, sera reembolsado a la brevedad.");

        mailSender.send(message);


        log.info("Correo de disculpa enviado con exito a: {}", event.email());

    }
}
