package com.nija123098.sithreon;

import com.nija123098.sithreon.machine.machines.CodeCheckClient;
import com.nija123098.sithreon.machine.machines.GameServer;
import com.nija123098.sithreon.machine.machines.RunnerClient;
import com.nija123098.sithreon.machine.machines.SuperServer;
import com.nija123098.sithreon.util.Log;

import java.nio.file.Files;
import java.nio.file.Paths;

class Main {
    public static void main(String[] args) {
        if (Files.exists(Paths.get("ERROR.txt"))) {
            Log.WARN.log("Shutdown due to existing error file");
            return;
        }
        Config.init();
        try {
            startupMachine(args);
        } catch (Throwable e) {
            Log.ERROR.log("Unable to startup the machine", e);
        }
    }

    private static void startupMachine(String[] args) {
        if (args.length == 0) Log.ERROR.log("Please specify program arguments");
        switch (args[0]) {
            case "machine":
            case "m":
                if (args.length < 2) Log.ERROR.log("No machine type specified");
                switch (args[1]) {
                    case "super":
                    case "s":
                        new SuperServer();
                        break;
                    case "check":
                    case "c":
                        new CodeCheckClient();
                        break;
                    case "game":
                    case "g":
                        new GameServer();
                        break;
                    case "runner":
                    case "r":
                        new RunnerClient();
                }
                break;
            case "generate-config":
            case "g-c":
                Config.generate();
        }
    }
}
