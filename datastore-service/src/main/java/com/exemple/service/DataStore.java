package com.exemple.service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.exemple.domain.Message;

public interface DataStore {

    Mono<Message> saveMessage(Message message);
    Flux<Message> loadMessages(String roomId);

    Flux<Message> loadAllMessages();

}
