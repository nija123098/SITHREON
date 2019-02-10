package com.nija123098.sithreon.backend.command.commands;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.command.Command;
import com.nija123098.sithreon.backend.command.CommandMethod;
import com.nija123098.sithreon.backend.machines.GameClient;
import com.nija123098.sithreon.backend.objects.Competitor;

import java.util.regex.Pattern;

/**
 * Command for generating a {@link GameClient}.
 */
public class GameClientCommand extends Command {
    public GameClientCommand() {
        super("game", "client");
        this.registerAlias("gc");
    }

    @CommandMethod
    public void command(String code) {
        String[] split = code.split(Pattern.quote(" "));
        new GameClient(new Competitor(split[0]), Config.authenticateMachines ? split[1] : null);
    }
}
