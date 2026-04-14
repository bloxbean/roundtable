package com.bloxbean.roundtable.service;

import com.bloxbean.roundtable.model.*;
import com.bloxbean.roundtable.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final TopicRepository topicRepo;
    private final RoundRepository roundRepo;
    private final RoundReviewerRepository roundReviewerRepo;
    private final AgentRegistrationRepository agentRepo;
    private final MessageRepository messageRepo;
    private final BreakpointRepository breakpointRepo;

    public WorkflowEngine(TopicRepository topicRepo, RoundRepository roundRepo,
                          RoundReviewerRepository roundReviewerRepo,
                          AgentRegistrationRepository agentRepo,
                          MessageRepository messageRepo, BreakpointRepository breakpointRepo) {
        this.topicRepo = topicRepo;
        this.roundRepo = roundRepo;
        this.roundReviewerRepo = roundReviewerRepo;
        this.agentRepo = agentRepo;
        this.messageRepo = messageRepo;
        this.breakpointRepo = breakpointRepo;
    }

    /**
     * Called after a PLAN or REVISION message is persisted.
     * Creates a new round, snapshots reviewers, transitions to IN_REVIEW or STALLED.
     */
    public TopicStatus onPlanOrRevision(Topic topic, Message message) {
        int newRoundNumber = topic.getCurrentRound() + 1;
        topic.setCurrentRound(newRoundNumber);

        Instant deadline = Instant.now().plusSeconds(topic.getRoundTimeoutSeconds());
        var round = new Round(topic, newRoundNumber, message.getId(), deadline);
        round = roundRepo.save(round);

        // Snapshot active reviewers eligible for this round
        var reviewerAgents = agentRepo.findByTopicAndRoleAndActiveTrue(topic, "reviewer")
                .stream()
                .filter(a -> a.getJoinedAtRound() <= newRoundNumber)
                .toList();

        for (var agent : reviewerAgents) {
            roundReviewerRepo.save(new RoundReviewer(round, agent.getName()));
        }

        TopicStatus targetStatus;
        if (reviewerAgents.isEmpty()) {
            targetStatus = TopicStatus.STALLED;
            log.info("Topic '{}' round {} has no reviewers, moving to STALLED", topic.getName(), newRoundNumber);
        } else {
            targetStatus = TopicStatus.IN_REVIEW;
            log.info("Topic '{}' round {} started with {} reviewer(s)", topic.getName(), newRoundNumber, reviewerAgents.size());
        }

        return transitionWithBreakpointCheck(topic, targetStatus);
    }

    /**
     * Called after a REVIEW, APPROVAL, or REJECTION message is persisted.
     * Records the verdict and evaluates quorum.
     */
    public TopicStatus onReviewVerdict(Topic topic, Message message) {
        var round = roundRepo.findByTopicAndRoundNumber(topic, topic.getCurrentRound())
                .orElseThrow(() -> new IllegalStateException("No active round for topic '" + topic.getName() + "'"));

        if (round.getStatus() == RoundStatus.DECIDED) {
            throw new IllegalStateException("ROUND_DECIDED: Round " + round.getRoundNumber()
                    + " has already been decided");
        }

        var reviewer = roundReviewerRepo.findByRoundAndAgentName(round, message.getSenderName())
                .orElseThrow(() -> new IllegalStateException("NOT_IN_REVIEWER_SET: Agent '"
                        + message.getSenderName() + "' is not in the reviewer set for round " + round.getRoundNumber()));

        // Record or update verdict
        String verdict = switch (message.getType()) {
            case APPROVAL -> "APPROVE";
            case REJECTION -> "REJECT";
            case REVIEW -> "REJECT";
            default -> throw new IllegalArgumentException("Unexpected message type: " + message.getType());
        };

        reviewer.setVerdict(verdict);
        reviewer.setReviewMessageId(message.getId());
        reviewer.setRespondedAt(Instant.now());
        roundReviewerRepo.save(reviewer);

        // Evaluate quorum
        return evaluateQuorum(topic, round);
    }

    /**
     * Evaluate quorum for the current round and transition state if decided.
     */
    private TopicStatus evaluateQuorum(Topic topic, Round round) {
        var reviewers = roundReviewerRepo.findByRound(round);
        long approvals = reviewers.stream().filter(r -> "APPROVE".equals(r.getVerdict())).count();
        long rejections = reviewers.stream().filter(r -> "REJECT".equals(r.getVerdict())).count();
        long pending = reviewers.stream().filter(r -> r.getVerdict() == null).count();
        int total = reviewers.size();

        QuorumResult result = switch (topic.getQuorumPolicy()) {
            case ALL -> evaluateAllQuorum(approvals, rejections, total);
            case MAJORITY -> evaluateMajorityQuorum(approvals, rejections, pending, total);
            case ANY_ONE -> evaluateAnyOneQuorum(approvals, rejections);
        };

        if (!result.decided) {
            log.debug("Topic '{}' round {}: quorum not yet reached ({} approve, {} reject, {} pending)",
                    topic.getName(), round.getRoundNumber(), approvals, rejections, pending);
            return topic.getStatus();
        }

        round.setStatus(RoundStatus.DECIDED);
        roundRepo.save(round);

        if (!result.approved) {
            log.info("Topic '{}' round {} decided: REJECTED", topic.getName(), round.getRoundNumber());
            return transitionWithBreakpointCheck(topic, TopicStatus.REVISION_REQUESTED);
        }

        // Approved — check if there's a next phase
        if (topic.hasNextPhase()) {
            String completedPhase = topic.getCurrentPhase();
            topic.setCurrentPhaseIndex(topic.getCurrentPhaseIndex() + 1);
            String nextPhase = topic.getCurrentPhase();
            log.info("Topic '{}' phase '{}' approved. Advancing to phase '{}'",
                    topic.getName(), completedPhase, nextPhase);

            // Insert system message about phase transition
            var sysMsg = new Message(topic, "system", "system", MessageType.SYSTEM,
                    "Phase '" + completedPhase + "' APPROVED. Advancing to phase '" + nextPhase
                            + "'. Planner: submit your " + nextPhase.toLowerCase() + ".",
                    topic.getCurrentRound(), nextPhase, 0);
            messageRepo.save(sysMsg);

            return transitionWithBreakpointCheck(topic, TopicStatus.WAITING_FOR_INPUT);
        }

        // Last phase approved — topic is done
        log.info("Topic '{}' final phase '{}' approved. Topic COMPLETED.",
                topic.getName(), topic.getCurrentPhase());
        return transitionWithBreakpointCheck(topic, TopicStatus.APPROVED);
    }

    private QuorumResult evaluateAllQuorum(long approvals, long rejections, int total) {
        if (rejections > 0) return new QuorumResult(true, false);
        if (approvals == total) return new QuorumResult(true, true);
        return QuorumResult.UNDECIDED;
    }

    private QuorumResult evaluateMajorityQuorum(long approvals, long rejections, long pending, int total) {
        long needed = (total / 2) + 1;
        if (approvals >= needed) return new QuorumResult(true, true);
        if (rejections >= needed) return new QuorumResult(true, false);
        // Check if approval is still possible
        if (approvals + pending < needed) return new QuorumResult(true, false);
        if (rejections + pending < needed) return new QuorumResult(true, true);
        return QuorumResult.UNDECIDED;
    }

    private QuorumResult evaluateAnyOneQuorum(long approvals, long rejections) {
        if (approvals >= 1) return new QuorumResult(true, true);
        if (rejections >= 1) return new QuorumResult(true, false);
        return QuorumResult.UNDECIDED;
    }

    /**
     * Human or agent approves, releasing a breakpoint.
     */
    public TopicStatus approve(String topicName) {
        var topic = topicRepo.findByName(topicName)
                .orElseThrow(() -> new IllegalArgumentException("Topic '" + topicName + "' not found"));

        if (topic.getStatus() == TopicStatus.BREAKPOINT) {
            // Find which phase the breakpoint was guarding and advance to it
            var bp = breakpointRepo.findByTopicAndAtPhase(topic, TopicStatus.APPROVED);
            if (bp.isPresent() && bp.get().isTriggered()) {
                bp.get().setTriggered(false);
                breakpointRepo.save(bp.get());
                topic.setStatus(TopicStatus.APPROVED);
                topicRepo.save(topic);
                log.info("Topic '{}' breakpoint released, now APPROVED", topicName);
                return TopicStatus.APPROVED;
            }
            // Check other breakpoint phases
            for (var candidate : List.of(TopicStatus.IN_REVIEW, TopicStatus.COMPLETED)) {
                var bpc = breakpointRepo.findByTopicAndAtPhase(topic, candidate);
                if (bpc.isPresent() && bpc.get().isTriggered()) {
                    bpc.get().setTriggered(false);
                    breakpointRepo.save(bpc.get());
                    topic.setStatus(candidate);
                    topicRepo.save(topic);
                    log.info("Topic '{}' breakpoint released, now {}", topicName, candidate);
                    return candidate;
                }
            }
        }

        throw new IllegalStateException("Topic '" + topicName + "' is not at a breakpoint (current status: " + topic.getStatus() + ")");
    }

    /**
     * Transition topic to target status, checking for breakpoints first.
     */
    private TopicStatus transitionWithBreakpointCheck(Topic topic, TopicStatus targetStatus) {
        var bp = breakpointRepo.findByTopicAndAtPhase(topic, targetStatus);
        if (bp.isPresent() && !bp.get().isTriggered()) {
            bp.get().setTriggered(true);
            breakpointRepo.save(bp.get());
            topic.setStatus(TopicStatus.BREAKPOINT);
            topicRepo.save(topic);
            log.info("Topic '{}' hit breakpoint before {}", topic.getName(), targetStatus);

            // Insert system message about breakpoint
            var sysMsg = new Message(topic, "system", "system", MessageType.SYSTEM,
                    "Breakpoint reached before " + targetStatus + ". Waiting for human approval.",
                    topic.getCurrentRound(), topic.getCurrentPhase(), 0);
            messageRepo.save(sysMsg);

            return TopicStatus.BREAKPOINT;
        }

        topic.setStatus(targetStatus);
        topicRepo.save(topic);
        return targetStatus;
    }

    private record QuorumResult(boolean decided, boolean approved) {
        static final QuorumResult UNDECIDED = new QuorumResult(false, false);
    }
}
