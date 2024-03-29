package com.nija123098.sithreon.backend.machines;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.Machine;
import com.nija123098.sithreon.backend.networking.Action;
import com.nija123098.sithreon.backend.networking.MachineAction;
import com.nija123098.sithreon.backend.networking.ManagedMachineType;
import com.nija123098.sithreon.backend.networking.TransferSocket;
import com.nija123098.sithreon.backend.objects.Repository;
import com.nija123098.sithreon.backend.util.ConnectionUtil;
import com.nija123098.sithreon.backend.util.throwable.NoReturnException;

import java.io.IOException;

/**
 * The {@link Machine} representation of a client to the super
 * server that checks the content of a repository to determine if
 * it is possible and safe to run by the {@link GameClient}.
 *
 * @author nija123098
 */
public class CheckClient extends Machine {
    private final TransferSocket superServerSocket;

    public CheckClient() {
        try {
            this.superServerSocket = new TransferSocket(this, Config.superServerAddress, Config.externalPort);
        } catch (IOException e) {
            ConnectionUtil.throwConnectionException("Unable to establish connection to super server due to IOException", e);
            throw new NoReturnException();
        }
        this.superServerSocket.registerAuthenticationAction((socket) -> socket.write(MachineAction.READY_TO_SERVE, ManagedMachineType.CODE_CHECK));
    }

    @Action(MachineAction.CHECK_REPO)
    public void check(Repository repository) {
        //
        this.superServerSocket.write(MachineAction.REPO_CODE_REPORT, repository, repository.getHeadHash(), true, "report");// todo implement, have logging here
        this.superServerSocket.write(MachineAction.READY_TO_SERVE, ManagedMachineType.CODE_CHECK);
    }
}
