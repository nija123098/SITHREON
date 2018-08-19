package com.nija123098.sithreon.backend.command.commands;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.command.Command;
import com.nija123098.sithreon.backend.command.CommandMethod;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * A {@link Command} for setting configs by command line.
 * <p>
 * Configs may appear in logs if done this way.
 */
public class ConfigSetCommand extends Command {
    public ConfigSetCommand() {
        super("config", "set");
        this.registerAlias("cfgs");
    }

    @CommandMethod
    public void command(String args) {
        Config.setValues(Config.class, Arrays.asList(args.split(Pattern.quote(","))));
    }
}
