package com.exemple.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.util.HtmlUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.exemple.domain.Message;

@Controller
public class MessageController {
    private static final Logger logger = LoggerFactory.getLogger(MessageController.class);
    private static final String TOPIC_TEMPLATE = "/topic/response.";
    private final WebClient datastoreClient;
    private final SimpMessagingTemplate template;
    private static final String ROOM_ID_1408 = "1408";


    public MessageController(WebClient datastoreClient, SimpMessagingTemplate template) {
        this.datastoreClient = datastoreClient;
        this.template = template;
    }

    @MessageMapping("/message.{roomId}")
    public void getMessage(@DestinationVariable String roomId, Message message) {
        logger.info("get message:{}, roomId:{}", message, roomId);

        if (roomId.equals(ROOM_ID_1408)) {
            logger.warn("Message sending to room 1408 is not allowed");
        } else {
            saveMessage(roomId, message)
                    .subscribe(msgId -> logger.info("message send id:{}", msgId));

            template.convertAndSend(String.format("%s%s", TOPIC_TEMPLATE, roomId),
                    new Message(HtmlUtils.htmlEscape(message.messageStr())));

            // Здесь отправляем сообщение в комнату 1408
            template.convertAndSend(String.format("%s1408", TOPIC_TEMPLATE),
                    new Message(HtmlUtils.htmlEscape(message.messageStr())));
        }
    }


    @EventListener
    public void handleSessionSubscribeEvent(SessionSubscribeEvent event) {
        var genericMessage = (GenericMessage<byte[]>) event.getMessage();
        var simpDestination = (String) genericMessage.getHeaders().get("simpDestination");
        if (simpDestination == null) {
            logger.error("Can not get simpDestination header, headers:{}", genericMessage.getHeaders());
            throw new ChatException("Can not get simpDestination header");
        }
        var roomId = parseRoomId(simpDestination);


        if (roomId.equals(ROOM_ID_1408)) {
            getAllMessagesFromAllRooms()
                    .doOnError(ex -> logger.error("Getting messages for all rooms failed", ex))
                    .subscribe(message -> template.convertAndSend(simpDestination, message));
        } else {
            getMessagesByRoomId(roomId)
                    .doOnError(ex -> logger.error("Getting messages for roomId:{} failed", roomId, ex))
                    .subscribe(message -> template.convertAndSend(simpDestination, message));
        }
    }

    private String parseRoomId(String simpDestination) {
        try {
            return simpDestination.replace(TOPIC_TEMPLATE, "");
        } catch (Exception ex) {
            logger.error("Can not get roomId", ex);
            throw new ChatException("Can not get roomId");
        }
    }
    private Mono<Long> saveMessage(String roomId, Message message) {
        return datastoreClient.post().uri(String.format("/msg/%s", roomId))
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(message)
                .exchangeToMono(response -> response.bodyToMono(Long.class));
    }
    private Flux<Message> getMessagesByRoomId(String roomId) {
        return datastoreClient.get().uri(String.format("/msg/%s", roomId))
                .accept(MediaType.APPLICATION_NDJSON)
                .exchangeToFlux(response -> {
                    if (response.statusCode().equals(HttpStatus.OK)) {
                        return response.bodyToFlux(Message.class);
                    } else {
                        return response.createException().flatMapMany(Mono::error);
                    }
                });
    }

    private Flux<String> getAllRoomIds() {
        return datastoreClient.get().uri("/rooms/ids")
                .retrieve()
                .bodyToFlux(String.class);
    }
    private Flux<Message> getAllMessagesFromAllRooms() {
        return datastoreClient.get().uri("/msg/all")
                .accept(MediaType.APPLICATION_NDJSON)
                .exchangeToFlux(response -> {
                    if (response.statusCode().equals(HttpStatus.OK)) {
                        return response.bodyToFlux(Message.class);
                    } else {
                        return response.createException().flatMapMany(Mono::error);
                    }
                });
    }

}
