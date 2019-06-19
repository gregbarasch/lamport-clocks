package com.gregbarasch.lamportclocks;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.management.javadsl.AkkaManagement;
import com.gregbarasch.lamportclocks.actor.LamportActor;
import com.gregbarasch.lamportclocks.dto.StartLamportSystemTrigger;
import org.apache.log4j.Logger;

public class App {

    private static final Logger logger = Logger.getLogger(App.class);


    private App() {

        // Start system and magnagement/bootstrap modules
        final ActorSystem actorSystem = ActorSystem.create("lamport-clocks");
        AkkaManagement.get(actorSystem).start();
        ClusterBootstrap.get(actorSystem).start();

        // start single actor
        final ActorRef actor = actorSystem.actorOf(LamportActor.props(), "lamportActor");
        Cluster.get(actorSystem).registerOnMemberUp(() -> {
            logger.info("Cluster member is up!");
            actor.tell(new StartLamportSystemTrigger(), null);
        });
    }

    public static void main(String[] args) {
        new App();
    }
}
