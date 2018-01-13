package com.nija123098.sithreon.backend.command.commands;

import com.nija123098.sithreon.backend.Machine;
import com.nija123098.sithreon.backend.command.Command;
import com.nija123098.sithreon.backend.command.CommandMethod;

/**
 * A {@link Command} for closing this or all connected {@link Machine}s.
 *
 * @author nija123098
 */
public class CloseCommand extends Command {
    public CloseCommand() {
        super("close");
        this.registerAlias("end");
        this.registerAlias("exit");
        this.registerAlias("shutdown");
    }

    /**
     * The command method called during invocation.
     *
     * @param all     if all connected machines should be shut down.
     * @param alias   for detection of any additional all aliases.
     * @param machine the current machine.
     */
    @CommandMethod
    public void command(Boolean all, String alias, Machine machine) {
        if (all || alias.equalsIgnoreCase("all")) machine.closeAll();
        else machine.close();
    }

    @Override
    protected String getHelp() {
        return "Closes this server or all connected servers.";
    }
}
