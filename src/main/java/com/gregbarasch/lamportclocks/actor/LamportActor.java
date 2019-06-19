package com.gregbarasch.lamportclocks.actor;

import akka.actor.AbstractActor;
import akka.actor.ActorSelection;
import akka.actor.Address;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;
import akka.cluster.MemberStatus;
import com.gregbarasch.lamportclocks.dto.AckDto;
import com.gregbarasch.lamportclocks.dto.LamportMessage;
import com.gregbarasch.lamportclocks.dto.ReleaseDto;
import com.gregbarasch.lamportclocks.dto.RequestDto;
import com.gregbarasch.lamportclocks.dto.StartLamportSystemTrigger;
import com.gregbarasch.lamportclocks.model.LogicalClock;
import com.gregbarasch.lamportclocks.model.Resource;
import org.apache.log4j.Logger;
import scala.collection.JavaConverters;
import scala.collection.immutable.SortedSet;

import java.util.HashSet;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;

public final class LamportActor extends AbstractActor {

    private static final Logger logger = Logger.getLogger(LamportActor.class);

    // A reference to the akka cluster
    private final Cluster cluster = Cluster.get(getContext().getSystem());
    // The list of messages that this actor must process, ordered by each LamportMessage's LogicalClock
    private final PriorityQueue<LamportMessage> messageQueue = new PriorityQueue<>();
    // The local clock
    private final LogicalClock logicalClock = new LogicalClock();
    // Whether or not we own the resource
    private boolean ownsResource = false;
    // A reference to the request for the resource that this actor sent. null if there is no current request
    private RequestDto currentRequest;
    // A set of actors that has not acknowledged our request yet.
    private HashSet<ActorSelection> needAcknowledgementSet;

    public static Props props() {
        return Props.create(LamportActor.class);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(RequestDto.class, this::onRequestDto)
                .match(AckDto.class, this::onAckDto)
                .match(ReleaseDto.class, this::onReleaseDto)
                .match(ClusterEvent.MemberEvent.class, message -> {}) // ignored

                .match(ClusterEvent.MemberUp.class, event -> {
                    logger.info("Member up: " + event.member());
                    final ActorSelection actor = getActorSelection(event.member());

                    if (needAcknowledgementSet != null) {
                        needAcknowledgementSet.add(actor);
                        final RequestDto request = new RequestDto(cluster.selfAddress(), logicalClock.clone());
                        actor.tell(request, getSelf());
                    }
                })

                .match(ClusterEvent.UnreachableMember.class, event -> {
                    logger.info("Member unreachable: " + event.member());
                    final ActorSelection actor = getActorSelection(event.member());
                    if (needAcknowledgementSet != null) needAcknowledgementSet.remove(actor);
                })

                .match(ClusterEvent.MemberRemoved.class, event -> {
                    logger.info("Member removed: " + event.member());
                    final ActorSelection actor = getActorSelection(event.member());
                    if (needAcknowledgementSet != null) needAcknowledgementSet.remove(actor);
                })

                // Hack to match bootstrapping coordinator
                .match(StartLamportSystemTrigger.class, trigger -> {
                    logger.info("Actor received StartLamportSystemTrigger.");

                    // FIXME need to fix startup conditions for joining existing cluster... Not important for POC
                    if (!cluster.selfAddress().equals(firstNodeAddress())) {
                        logger.info("Requesting resource.");
                        requestResource();
                    }
                })

                .matchAny(event -> logger.warn("Received " + event))
                .build();
    }

    @Override
    public void preStart() {
        cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(), ClusterEvent.MemberEvent.class, ClusterEvent.UnreachableMember.class);
    }

    // re-subscribe when restart
    @Override
    public void postStop() {
        logger.info("Shutting down " + getSelf().hashCode() + " local time @" + logicalClock.getClock());
        if (ownsResource) releaseResource();
        cluster.unsubscribe(getSelf());
    }

    /**
     * To request the resource, process Pi sends the message TIn:P/requests resource to every other process,
     * and puts that message on its request queue, where T,~ is the timestamp of the message.
     * (Lamport)
     */
    private void requestResource() {
        logicalClock.increment();

        // Create a new request and add it to my queue
        final RequestDto request = new RequestDto(cluster.selfAddress(), logicalClock.clone());
        currentRequest = request;
        messageQueue.add(request);

        // Create an acknowledgement set and remove self
        needAcknowledgementSet = new HashSet<>(getActorSet());
        needAcknowledgementSet.remove(getActorSelection(cluster.selfAddress()));

        // tell all other actors of my request
        for (final ActorSelection actor : needAcknowledgementSet) {
            actor.tell(request, getSelf());
        }
    }

    /**
     * Assumes we are at the top of the message queue
     */
    private void acquireResource() {
        logicalClock.increment();

        //noinspection ConstantConditions
        logger.debug(getSelf().hashCode() + " acquiring resource requested @" + messageQueue.peek().getTimestamp().getClock());
        Resource.acquire();
        ownsResource = true;
    }

    /**
     * Assumes we are at the top of the message queue
     *
     * To release the resource, process P~ removes any Tm:Pi requests resource message from its request queue and
     * sends a (timestamped) Pi releases resource message to every other process. (Lamport)
     */
    private void releaseResource() {
        logicalClock.increment();

        // remove self from queue
        LamportMessage message = messageQueue.poll(); // we will be at the top of the PQ

        // release resource
        //noinspection ConstantConditions
        logger.trace(getSelf().hashCode() + " releasing resource requested @" + message.getTimestamp().getClock());
        Resource.release();

        currentRequest = null;
        ownsResource = false;

        // tell other actors of release
        final ReleaseDto release = new ReleaseDto(cluster.selfAddress(), logicalClock.clone());
        final ActorSelection self = getActorSelection(cluster.selfAddress());
        for (final ActorSelection actor : getActorSet()) {
            if (actor.equals(self)) continue; // skip self
            actor.tell(release, getSelf());
        }
    }

    /**
     * When process Pj receives the message T,~:P~ requests resource, it places it on its request queue and sends a (timestamped) acknowledgment message to P~.'~ (Lamport)
     */
    private void onRequestDto(RequestDto request) {
        logicalClock.increment();
        logicalClock.sync(request.getTimestamp());

        // add senders request to the queue
        messageQueue.add(request);

        // acknowledge senders request
        final AckDto ack = new AckDto(cluster.selfAddress(), logicalClock.clone());
        getActorSelection(request.getSenderAddress()).tell(ack, getSelf());

        updateAcknowledgements(request);
        manageResource();
    }

    /**
     * Process P/is granted the resource when the following two conditions are satisfied:
     *  (ii) P~ has received a message from every other process timestamped later than Tin. ~ (Lamport)
     */
    private void onAckDto(AckDto ack) {
        logicalClock.increment();
        logicalClock.sync(ack.getTimestamp());
        updateAcknowledgements(ack);
        manageResource();
    }

    /**
     * When process Pj receives a Pi releases resource message, it removes any Tm:P~ requests resource message from its request queue. (Lamport)
     */
    private void onReleaseDto(ReleaseDto release) {
        logicalClock.increment();
        logicalClock.sync(release.getTimestamp());

        messageQueue.removeIf(message -> message.getSenderAddress().equals(release.getSenderAddress()));

        updateAcknowledgements(release);
        manageResource();
    }

    /**
     * (ii) P~ has received a message from every other process time- stamped later than Tin. ~ Note that conditions (i) and (ii) of rule 5 are tested locally by P~.
     * (Lamport)
     */
    private void updateAcknowledgements(LamportMessage message) {
        // waiting for message from this sender
        final ActorSelection sender = getActorSelection(message.getSenderAddress());
        if (needAcknowledgementSet != null && needAcknowledgementSet.contains(sender)) {
            // message is newer than current request
            if (message.getTimestamp().compareTo(currentRequest.getTimestamp()) > 0) {
                needAcknowledgementSet.remove(sender);
            }
        }
    }

    /**
     * Every time a request is received, we should try and manage the resources status
     */
    private void manageResource() {
        // No current request
        if (currentRequest == null) {
            requestResource();

        // "use" resource
        } else if (needAcknowledgementSet != null
                && needAcknowledgementSet.isEmpty()
                && !messageQueue.isEmpty()
                && Objects.equals(messageQueue.peek().getSenderAddress(), cluster.selfAddress())) {

            acquireResource();
            try { Thread.sleep(500); } catch (InterruptedException ignore) {} // FIXME Ignoring.. unimportant at the moment
            releaseResource();
        }
    }

    private Set<ActorSelection> getActorSet() {
        final SortedSet<Member> members = cluster.state().members();
        return JavaConverters.setAsJavaSet(members)
                .stream()
                .filter(member -> member.status() == MemberStatus.up())
                .map(member -> getActorSelection(member.address()))
                .collect(Collectors.toSet());
    }

    private Address firstNodeAddress() {
        return cluster.state().members().firstKey().address();
    }

    private ActorSelection getActorSelection(Member member) {
        return getActorSelection(member.address());
    }

    private ActorSelection getActorSelection(Address remoteAddress) {
        return cluster.system().actorSelection(remoteAddress + "/user/lamportActor");
    }
}
