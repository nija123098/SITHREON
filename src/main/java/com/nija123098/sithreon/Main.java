package com.nija123098.sithreon;

import com.nija123098.sithreon.backend.command.CommandHandler;
import com.nija123098.sithreon.backend.util.Log;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * The main launcher class which will ensure
 * that there is no existing error file, then
 * launch the command specified by the program arguments.
 *
 * @author nija123098
 */
class Main {
    /**
     * Launches with the arguments as the command to be executed.
     *
     * @param args the command arguments to launch with.
     */
    public static void main(String[] args) {
        if (Files.exists(Paths.get("ERROR.log"))) {
            Log.WARN.log("Shutdown due to existing error file");
            return;
        }
        CommandHandler.init(args);
    }
}
