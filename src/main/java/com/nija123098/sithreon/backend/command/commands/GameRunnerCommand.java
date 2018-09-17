package com.nija123098.sithreon.backend.command.commands;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.command.Command;
import com.nija123098.sithreon.backend.command.CommandMethod;
import com.nija123098.sithreon.backend.machines.GameClient;
import com.nija123098.sithreon.backend.networking.TransferSocket;
import com.nija123098.sithreon.backend.objects.Competitor;
import com.nija123098.sithreon.backend.util.Log;

import java.io.IOException;
import java.util.regex.Pattern;

public class GameRunnerCommand extends Command {
    public GameRunnerCommand() {
        super("game", "runner");
        this.registerAlias("gr");
    }
    @CommandMethod
    public void command(String code) {
        String[] split = code.split(Pattern.quote(" "));
        try {
            new TransferSocket(new GameClient(new Competitor(split[0])), "127.0.0.1", Config.internalPort).useTemporaryCodeAuthentication(split[1]);
        } catch (IOException e) {
            Log.ERROR.log("Unable to launch GameClient with authorization code", e);
        }
    }
}
