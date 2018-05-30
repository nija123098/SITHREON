package com.nija123098.sithreon.backend.machines;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.Machine;
import com.nija123098.sithreon.backend.networking.*;
import com.nija123098.sithreon.backend.objects.Match;
import com.nija123098.sithreon.backend.objects.Repository;
import com.nija123098.sithreon.backend.util.Log;

import java.io.IOException;

/**
 * The {@link Machine} representation of the server responsible
 * for the running of a game through {@link RunnerClient} instances.
 *
 * @author nija123098
 */
public class GameServer extends Machine {
    private final TransferSocket superServerSocket;

    public GameServer() {
        new SocketAcceptor(this, Config.internalPort);
        try {
            this.superServerSocket = new TransferSocket(this, Config.superServerAddress, Config.externalPort);
        } catch (IOException e) {
            Log.ERROR.log("Unable to establish connection to super server due to IOException", e);
            throw new RuntimeException();// Won't occur
        }
        this.superServerSocket.registerAuthenticationAction((socket) -> socket.write(MachineAction.READY_TO_SERVE, ManagedMachineType.GAME_SERVER));
    }

    @Action(MachineAction.RUN_GAME)
    public void runGame(Match match) {
        // setup game
        this.superServerSocket.write(MachineAction.MATCH_COMPLETE, match, true);// todo implement, have logging
        this.superServerSocket.write(MachineAction.READY_TO_SERVE, ManagedMachineType.GAME_SERVER);
    }

    @Override
    protected void notifyReady(ManagedMachineType machineType, TransferSocket socket) {
        if (machineType == ManagedMachineType.GAME_RUNNER) {
            //
        } else super.notifyReady(machineType, socket);
    }

    /**
     * A {@link MachineAction} method for stopping a game due to the HEAD
     * of a {@link Repository} no longer matching the {@link Match}.
     *
     * @param repository the repository out of date.
     */
    @Action(MachineAction.MATCH_OUT_OF_DATE)
    public void outOfDate(Repository repository) {
        // stop in process game
    }
}
