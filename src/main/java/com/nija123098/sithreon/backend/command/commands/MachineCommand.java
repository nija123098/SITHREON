package com.nija123098.sithreon.backend.command.commands;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.command.Command;
import com.nija123098.sithreon.backend.command.CommandMethod;
import com.nija123098.sithreon.backend.machines.*;
import com.nija123098.sithreon.backend.util.Log;

/**
 * A {@link Command} for starting machines.
 *
 * @author nija123098
 */
public class MachineCommand extends Command {
    public MachineCommand() {
        super("machine");
        this.registerAlias("m");
    }

    /**
     * The method invoked to start various machines.
     *
     * @param type the name of the type of machine to start.
     */
    @CommandMethod
    public void command(String type) {
        Config.init();
        Log.INFO.log("Starting up machine " + Config.machineId);
        switch (type) {
            case "super server":
            case "super":
            case "s":
                new SuperServer();
                break;
            case "code checker":
            case "code check":
            case "check":
            case "c":
                new CodeCheckClient();
                break;
            case "game server":
            case "game":
            case "g":
                new GameServer();
                break;
            case "code runner":
            case "runner":
            case "r":
                new RunnerClient();
                break;
            case "command":
            case "command client":
                new CommandClient();
            default:
                Log.ERROR.log("Unrecognized machine type: " + type);
        }
    }

    @Override
    protected String getHelp() {
        return "Starts a machine of super server, code checker, game server, code runner, or command client.";
    }
}
