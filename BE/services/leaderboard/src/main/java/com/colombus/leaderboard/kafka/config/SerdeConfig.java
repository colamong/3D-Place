package com.colombus.leaderboard.kafka.config;

import java.util.UUID;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonSerde;
import com.colombus.common.kafka.paint.event.PaintEvent;
import com.colombus.common.kafka.subject.model.ClanMemberKey;
import com.colombus.common.kafka.subject.model.UserClanState;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class SerdeConfig {
    
    @Bean
    public Serde<UUID> uuidSerde() {
        return new Serdes.WrapperSerde<>(
            new org.apache.kafka.common.serialization.UUIDSerializer(),
            new org.apache.kafka.common.serialization.UUIDDeserializer()
        );
    }

    @Bean
    public Serde<PaintEvent> paintEventSerde(ObjectMapper om) {
        JsonSerde<PaintEvent> serde = new JsonSerde<>(PaintEvent.class, om);
        serde.deserializer().ignoreTypeHeaders();
        return serde;
    }

    @Bean
    public Serde<UserClanState> userClanStateSerde(ObjectMapper om) {
        JsonSerde<UserClanState> serde = new JsonSerde<>(UserClanState.class, om);
        serde.deserializer().ignoreTypeHeaders();
        return serde;
    }

    @Bean
    public Serde<ClanMemberKey> clanMemberKeySerde(ObjectMapper om) {
        JsonSerde<ClanMemberKey> serde = new JsonSerde<>(ClanMemberKey.class, om);
        serde.deserializer().ignoreTypeHeaders();
        return serde;
    }
}