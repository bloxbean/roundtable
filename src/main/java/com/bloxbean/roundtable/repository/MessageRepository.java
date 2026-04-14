package com.bloxbean.roundtable.repository;

import com.bloxbean.roundtable.model.Message;
import com.bloxbean.roundtable.model.MessageType;
import com.bloxbean.roundtable.model.Topic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByTopicAndIdGreaterThanOrderByIdAsc(Topic topic, Long sinceId);
    List<Message> findByTopicOrderByIdAsc(Topic topic);
    Optional<Message> findFirstByTopicOrderByIdDesc(Topic topic);
    Optional<Message> findFirstByTopicAndSenderRoleOrderByIdDesc(Topic topic, String senderRole);
    List<Message> findByTopicAndRoundAndTypeIn(Topic topic, int round, List<MessageType> types);
    Optional<Message> findFirstByTopicAndRoundAndTypeInOrderByIdDesc(Topic topic, int round, List<MessageType> types);
}
