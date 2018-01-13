package com.nija123098.sithreon.backend.command.commands;

import com.nija123098.sithreon.backend.command.Command;
import com.nija123098.sithreon.backend.command.CommandHandler;
import com.nija123098.sithreon.backend.command.CommandMethod;

import java.util.regex.Pattern;

/**
 * A {@link Command} to facilitate multiple commands in one line.
 *
 * @author nija123098
 */
public class MultiCommand extends Command {
    public MultiCommand() {
        super("multi");
    }

    /**
     * The method invoked by the command which splits and invokes commands.
     *
     * @param regex if the command should use regex in it's splitting.
     * @param s     the regex or plain value to use to split commands from one another.
     */
    @CommandMethod
    public void command(Boolean regex, String s) {
        String splitter = s.substring(0, s.indexOf(" "));
        String[] split = s.substring(splitter.length() + 1).split(regex ? splitter : Pattern.quote(splitter));
        for (String command : split) CommandHandler.invokeCommand(command.trim(), false);
    }

    @Override
    protected String getHelp() {
        return "Executes multiple commands in respective order using the first space separated argument as the splitter.\nRegex is not used by default.";
    }
}
