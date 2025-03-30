package com.chat.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "chats")
@Data
public class ChatModelCreation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long chatId;

    @Column(nullable = false)
    private String chatName;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private Long receiverId;

    @Column(nullable = false)
    private String receiverName;

}
