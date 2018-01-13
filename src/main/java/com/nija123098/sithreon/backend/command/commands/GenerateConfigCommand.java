package com.nija123098.sithreon.backend.command.commands;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.command.Command;
import com.nija123098.sithreon.backend.command.CommandMethod;

/**
 * A {@link Command} to generate the config with default vales.
 *
 * @author nija123098
 */
public class GenerateConfigCommand extends Command {
    public GenerateConfigCommand() {
        super("generate", "config");
        this.registerAlias("gen", "config");
    }

    /**
     * The command invoked which calls to generate
     * a config file with default settings.
     */
    @CommandMethod
    public void command() {
        Config.generate();
    }

    @Override
    protected String getHelp() {
        return "Generates a config file with default values.";
    }
}
