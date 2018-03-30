package com.nija123098.sithreon.backend.machines;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.Machine;
import com.nija123098.sithreon.backend.networking.*;
import com.nija123098.sithreon.backend.objects.Match;
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
        // new SocketAcceptor(this);// todo
        try {
            this.superServerSocket = new TransferSocket(this, Config.superServerAddress);
        } catch (IOException e) {
            Log.ERROR.log("Unable to establish connection to super server due to IOException", e);
            throw new RuntimeException();// Won't occur
        }
        this.superServerSocket.registerAuthenticationAction((socket) -> socket.write(MachineAction.READY_TO_SERVE, ManagedMachineType.GAME_SERVER));
    }

    @Action(MachineAction.RUN_GAME)
    public void runGame(Match match) {
        // setup game
        this.superServerSocket.write(MachineAction.MATCH_COMPLETE, match, true);// TODO REMOVE TESTING
        this.superServerSocket.write(MachineAction.READY_TO_SERVE, ManagedMachineType.GAME_SERVER);

        // TODO start runner instances
    }

    @Override
    protected void notifyReady(ManagedMachineType machineType, TransferSocket socket) {
        if (machineType == ManagedMachineType.GAME_RUNNER) {
            //
        } else super.notifyReady(machineType, socket);
    }
}
