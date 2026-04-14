package com.bloxbean.roundtable.service;

import com.bloxbean.roundtable.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class WorkflowEngineTest {

    @Autowired TopicService topicService;
    @Autowired AgentService agentService;
    @Autowired MessageService messageService;
    @Autowired WorkflowEngine workflowEngine;

    // Helper: single-phase topic for simple tests
    private Topic createSinglePhaseTopic(String name, QuorumPolicy policy) {
        return topicService.createTopic(name, "test", policy, 300, "PLANNING");
    }

    @Test
    void fullApprovalFlow_singlePhase() {
        createSinglePhaseTopic("test-approval", QuorumPolicy.ALL);
        agentService.registerAgent("test-approval", "planner-1", "planner");
        agentService.registerAgent("test-approval", "reviewer-1", "reviewer");

        var plan = messageService.sendMessage("test-approval", "planner-1", "Here is my plan", MessageType.PLAN, 0);
        var topic = topicService.getByName("test-approval");
        assertEquals(TopicStatus.IN_REVIEW, topic.getStatus());

        messageService.sendMessage("test-approval", "reviewer-1", "Looks good", MessageType.APPROVAL, plan.getId());
        topic = topicService.getByName("test-approval");
        assertEquals(TopicStatus.APPROVED, topic.getStatus());
    }

    @Test
    void multiPhase_planningThenImplementation() {
        // Default phases: PLANNING, IMPLEMENTATION
        topicService.createTopic("test-multi", "test", QuorumPolicy.ALL, 300);
        agentService.registerAgent("test-multi", "planner-1", "planner");
        agentService.registerAgent("test-multi", "reviewer-1", "reviewer");

        // Phase 1: PLANNING
        var topic = topicService.getByName("test-multi");
        assertEquals("PLANNING", topic.getCurrentPhase());

        var plan = messageService.sendMessage("test-multi", "planner-1", "My plan", MessageType.PLAN, 0);
        messageService.sendMessage("test-multi", "reviewer-1", "Plan OK", MessageType.APPROVAL, plan.getId());

        // Should advance to IMPLEMENTATION phase, not APPROVED
        topic = topicService.getByName("test-multi");
        assertEquals(TopicStatus.WAITING_FOR_INPUT, topic.getStatus());
        assertEquals("IMPLEMENTATION", topic.getCurrentPhase());
        assertEquals(1, topic.getCurrentPhaseIndex());

        // Phase 2: IMPLEMENTATION
        var impl = messageService.sendMessage("test-multi", "planner-1", "My implementation", MessageType.PLAN, plan.getId() + 2);
        topic = topicService.getByName("test-multi");
        assertEquals(TopicStatus.IN_REVIEW, topic.getStatus());

        messageService.sendMessage("test-multi", "reviewer-1", "Impl OK", MessageType.APPROVAL, impl.getId());

        // Now fully approved — last phase done
        topic = topicService.getByName("test-multi");
        assertEquals(TopicStatus.APPROVED, topic.getStatus());
    }

    @Test
    void multiPhase_rejectionInSecondPhase() {
        topicService.createTopic("test-multi2", "test", QuorumPolicy.ALL, 300);
        agentService.registerAgent("test-multi2", "planner-1", "planner");
        agentService.registerAgent("test-multi2", "reviewer-1", "reviewer");

        // Approve planning phase
        var plan = messageService.sendMessage("test-multi2", "planner-1", "Plan", MessageType.PLAN, 0);
        messageService.sendMessage("test-multi2", "reviewer-1", "OK", MessageType.APPROVAL, plan.getId());
        var topic = topicService.getByName("test-multi2");
        assertEquals("IMPLEMENTATION", topic.getCurrentPhase());

        // Submit implementation, get rejected
        var impl = messageService.sendMessage("test-multi2", "planner-1", "Impl v1", MessageType.PLAN, plan.getId() + 2);
        var rejection = messageService.sendMessage("test-multi2", "reviewer-1", "Needs work", MessageType.REJECTION, impl.getId());
        topic = topicService.getByName("test-multi2");
        assertEquals(TopicStatus.REVISION_REQUESTED, topic.getStatus());
        assertEquals("IMPLEMENTATION", topic.getCurrentPhase());

        // Revise implementation
        var impl2 = messageService.sendMessage("test-multi2", "planner-1", "Impl v2", MessageType.REVISION, rejection.getId());
        messageService.sendMessage("test-multi2", "reviewer-1", "Now good", MessageType.APPROVAL, impl2.getId());
        topic = topicService.getByName("test-multi2");
        assertEquals(TopicStatus.APPROVED, topic.getStatus());
    }

    @Test
    void customPhases_threePhaseTopic() {
        topicService.createTopic("test-3phase", "test", QuorumPolicy.ALL, 300, "PLANNING,IMPLEMENTATION,TESTING");
        agentService.registerAgent("test-3phase", "p1", "planner");
        agentService.registerAgent("test-3phase", "r1", "reviewer");

        var topic = topicService.getByName("test-3phase");
        assertEquals("PLANNING", topic.getCurrentPhase());

        // Approve planning
        var m1 = messageService.sendMessage("test-3phase", "p1", "Plan", MessageType.PLAN, 0);
        messageService.sendMessage("test-3phase", "r1", "OK", MessageType.APPROVAL, m1.getId());
        topic = topicService.getByName("test-3phase");
        assertEquals("IMPLEMENTATION", topic.getCurrentPhase());
        assertEquals(TopicStatus.WAITING_FOR_INPUT, topic.getStatus());

        // Approve implementation
        var m2 = messageService.sendMessage("test-3phase", "p1", "Impl", MessageType.PLAN, m1.getId() + 2);
        messageService.sendMessage("test-3phase", "r1", "OK", MessageType.APPROVAL, m2.getId());
        topic = topicService.getByName("test-3phase");
        assertEquals("TESTING", topic.getCurrentPhase());
        assertEquals(TopicStatus.WAITING_FOR_INPUT, topic.getStatus());

        // Approve testing — final phase
        var m3 = messageService.sendMessage("test-3phase", "p1", "Tests pass", MessageType.PLAN, m2.getId() + 2);
        messageService.sendMessage("test-3phase", "r1", "OK", MessageType.APPROVAL, m3.getId());
        topic = topicService.getByName("test-3phase");
        assertEquals(TopicStatus.APPROVED, topic.getStatus());
    }

    @Test
    void reviewMessageTriggersRevision() {
        createSinglePhaseTopic("test-review-triggers", QuorumPolicy.ALL);
        agentService.registerAgent("test-review-triggers", "planner-1", "planner");
        agentService.registerAgent("test-review-triggers", "reviewer-1", "reviewer");

        var plan = messageService.sendMessage("test-review-triggers", "planner-1", "Plan v1", MessageType.PLAN, 0);

        // Reviewer sends REVIEW (feedback) instead of REJECTION — should have same effect
        var review = messageService.sendMessage("test-review-triggers", "reviewer-1", "Some feedback", MessageType.REVIEW, plan.getId());
        var topic = topicService.getByName("test-review-triggers");
        assertEquals(TopicStatus.REVISION_REQUESTED, topic.getStatus());

        // Planner can now revise
        var revision = messageService.sendMessage("test-review-triggers", "planner-1", "Plan v2", MessageType.REVISION, review.getId());
        messageService.sendMessage("test-review-triggers", "reviewer-1", "Good now", MessageType.APPROVAL, revision.getId());
        topic = topicService.getByName("test-review-triggers");
        assertEquals(TopicStatus.APPROVED, topic.getStatus());
    }

    @Test
    void rejectionAndRevisionFlow() {
        createSinglePhaseTopic("test-revision", QuorumPolicy.ALL);
        agentService.registerAgent("test-revision", "planner-1", "planner");
        agentService.registerAgent("test-revision", "reviewer-1", "reviewer");

        var plan = messageService.sendMessage("test-revision", "planner-1", "Plan v1", MessageType.PLAN, 0);
        var rejection = messageService.sendMessage("test-revision", "reviewer-1", "Needs work", MessageType.REJECTION, plan.getId());
        var topic = topicService.getByName("test-revision");
        assertEquals(TopicStatus.REVISION_REQUESTED, topic.getStatus());

        var revision = messageService.sendMessage("test-revision", "planner-1", "Plan v2", MessageType.REVISION, rejection.getId());
        messageService.sendMessage("test-revision", "reviewer-1", "Now good", MessageType.APPROVAL, revision.getId());
        topic = topicService.getByName("test-revision");
        assertEquals(TopicStatus.APPROVED, topic.getStatus());
    }

    @Test
    void turnEnforcement_plannerCannotReview() {
        createSinglePhaseTopic("test-turn", QuorumPolicy.ALL);
        agentService.registerAgent("test-turn", "planner-1", "planner");
        agentService.registerAgent("test-turn", "reviewer-1", "reviewer");
        messageService.sendMessage("test-turn", "planner-1", "Plan", MessageType.PLAN, 0);

        var ex = assertThrows(IllegalStateException.class, () ->
                messageService.sendMessage("test-turn", "planner-1", "I approve", MessageType.APPROVAL, 1));
        assertTrue(ex.getMessage().contains("INVALID_STATE_TRANSITION"));
    }

    @Test
    void turnEnforcement_reviewerCannotPlan() {
        createSinglePhaseTopic("test-turn2", QuorumPolicy.ALL);
        agentService.registerAgent("test-turn2", "planner-1", "planner");
        agentService.registerAgent("test-turn2", "reviewer-1", "reviewer");

        var ex = assertThrows(IllegalStateException.class, () ->
                messageService.sendMessage("test-turn2", "reviewer-1", "My plan", MessageType.PLAN, 0));
        assertTrue(ex.getMessage().contains("INVALID_STATE_TRANSITION"));
    }

    @Test
    void freshnessCheck_staleRevision() {
        createSinglePhaseTopic("test-fresh", QuorumPolicy.ALL);
        agentService.registerAgent("test-fresh", "planner-1", "planner");
        agentService.registerAgent("test-fresh", "reviewer-1", "reviewer");

        var plan = messageService.sendMessage("test-fresh", "planner-1", "Plan v1", MessageType.PLAN, 0);
        messageService.sendMessage("test-fresh", "reviewer-1", "Bad", MessageType.REJECTION, plan.getId());

        var ex = assertThrows(IllegalStateException.class, () ->
                messageService.sendMessage("test-fresh", "planner-1", "Plan v2", MessageType.REVISION, plan.getId()));
        assertTrue(ex.getMessage().contains("STALE_STATE"));
    }

    @Test
    void multipleReviewers_allMustApprove() {
        createSinglePhaseTopic("test-quorum", QuorumPolicy.ALL);
        agentService.registerAgent("test-quorum", "planner-1", "planner");
        agentService.registerAgent("test-quorum", "r1", "reviewer");
        agentService.registerAgent("test-quorum", "r2", "reviewer");

        var plan = messageService.sendMessage("test-quorum", "planner-1", "Plan", MessageType.PLAN, 0);

        messageService.sendMessage("test-quorum", "r1", "OK", MessageType.APPROVAL, plan.getId());
        var topic = topicService.getByName("test-quorum");
        assertEquals(TopicStatus.IN_REVIEW, topic.getStatus());

        messageService.sendMessage("test-quorum", "r2", "OK", MessageType.APPROVAL, plan.getId());
        topic = topicService.getByName("test-quorum");
        assertEquals(TopicStatus.APPROVED, topic.getStatus());
    }

    @Test
    void multipleReviewers_oneRejects() {
        createSinglePhaseTopic("test-quorum2", QuorumPolicy.ALL);
        agentService.registerAgent("test-quorum2", "planner-1", "planner");
        agentService.registerAgent("test-quorum2", "r1", "reviewer");
        agentService.registerAgent("test-quorum2", "r2", "reviewer");

        var plan = messageService.sendMessage("test-quorum2", "planner-1", "Plan", MessageType.PLAN, 0);
        messageService.sendMessage("test-quorum2", "r1", "No", MessageType.REJECTION, plan.getId());
        var topic = topicService.getByName("test-quorum2");
        assertEquals(TopicStatus.REVISION_REQUESTED, topic.getStatus());
    }

    @Test
    void majorityQuorum() {
        createSinglePhaseTopic("test-majority", QuorumPolicy.MAJORITY);
        agentService.registerAgent("test-majority", "planner-1", "planner");
        agentService.registerAgent("test-majority", "r1", "reviewer");
        agentService.registerAgent("test-majority", "r2", "reviewer");
        agentService.registerAgent("test-majority", "r3", "reviewer");

        var plan = messageService.sendMessage("test-majority", "planner-1", "Plan", MessageType.PLAN, 0);

        messageService.sendMessage("test-majority", "r1", "No", MessageType.REJECTION, plan.getId());
        var topic = topicService.getByName("test-majority");
        assertEquals(TopicStatus.IN_REVIEW, topic.getStatus());

        messageService.sendMessage("test-majority", "r2", "Yes", MessageType.APPROVAL, plan.getId());
        topic = topicService.getByName("test-majority");
        assertEquals(TopicStatus.IN_REVIEW, topic.getStatus());

        messageService.sendMessage("test-majority", "r3", "Yes", MessageType.APPROVAL, plan.getId());
        topic = topicService.getByName("test-majority");
        assertEquals(TopicStatus.APPROVED, topic.getStatus());
    }

    @Test
    void breakpointFlow() {
        createSinglePhaseTopic("test-bp", QuorumPolicy.ALL);
        topicService.setBreakpoint("test-bp", TopicStatus.APPROVED);
        agentService.registerAgent("test-bp", "planner-1", "planner");
        agentService.registerAgent("test-bp", "reviewer-1", "reviewer");

        var plan = messageService.sendMessage("test-bp", "planner-1", "Plan", MessageType.PLAN, 0);
        messageService.sendMessage("test-bp", "reviewer-1", "OK", MessageType.APPROVAL, plan.getId());

        var topic = topicService.getByName("test-bp");
        assertEquals(TopicStatus.BREAKPOINT, topic.getStatus());

        workflowEngine.approve("test-bp");
        topic = topicService.getByName("test-bp");
        assertEquals(TopicStatus.APPROVED, topic.getStatus());
    }

    @Test
    void exclusivePlanner() {
        createSinglePhaseTopic("test-excl", QuorumPolicy.ALL);
        agentService.registerAgent("test-excl", "planner-1", "planner");

        var ex = assertThrows(IllegalStateException.class, () ->
                agentService.registerAgent("test-excl", "planner-2", "planner"));
        assertTrue(ex.getMessage().contains("PLANNER_ALREADY_ASSIGNED"));
    }

    @Test
    void noReviewers_goesToStalled() {
        createSinglePhaseTopic("test-stalled", QuorumPolicy.ALL);
        agentService.registerAgent("test-stalled", "planner-1", "planner");

        messageService.sendMessage("test-stalled", "planner-1", "Plan", MessageType.PLAN, 0);
        var topic = topicService.getByName("test-stalled");
        assertEquals(TopicStatus.STALLED, topic.getStatus());
    }

    @Test
    void completedTopicRejectsMessages() {
        createSinglePhaseTopic("test-completed", QuorumPolicy.ALL);
        agentService.registerAgent("test-completed", "planner-1", "planner");
        agentService.registerAgent("test-completed", "reviewer-1", "reviewer");

        var plan = messageService.sendMessage("test-completed", "planner-1", "Plan", MessageType.PLAN, 0);
        messageService.sendMessage("test-completed", "reviewer-1", "OK", MessageType.APPROVAL, plan.getId());

        var topic = topicService.getByName("test-completed");
        topic.setStatus(TopicStatus.COMPLETED);

        var ex = assertThrows(IllegalStateException.class, () ->
                messageService.sendMessage("test-completed", "planner-1", "More", MessageType.CHAT, plan.getId()));
        assertTrue(ex.getMessage().contains("TOPIC_COMPLETED"));
    }
}
