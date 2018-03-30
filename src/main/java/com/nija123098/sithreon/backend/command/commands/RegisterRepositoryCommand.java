package com.nija123098.sithreon.backend.command.commands;

import com.nija123098.sithreon.backend.Machine;
import com.nija123098.sithreon.backend.command.Command;
import com.nija123098.sithreon.backend.command.CommandMethod;
import com.nija123098.sithreon.backend.machines.SuperServer;
import com.nija123098.sithreon.backend.objects.Repository;

public class RegisterRepositoryCommand extends Command {
    public RegisterRepositoryCommand() {
        super("register repository");
        this.registerAlias("regr");
        this.registerAlias("rr");
    }

    /**
     * The command method called during invocation.
     *
     * @param repository the repository to register.
     * @param machine the current machine.
     */
    @CommandMethod
    public void command(Repository repository, Machine machine) {
        ((SuperServer) machine).registerRepository(repository);
    }

    @Override
    protected String getHelp() {
        return "Closes this server or all connected servers.";
    }

}
