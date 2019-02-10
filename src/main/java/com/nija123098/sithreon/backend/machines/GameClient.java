package com.nija123098.sithreon.backend.machines;

import com.nija123098.sithreon.backend.Config;
import com.nija123098.sithreon.backend.Machine;
import com.nija123098.sithreon.backend.networking.Action;
import com.nija123098.sithreon.backend.networking.MachineAction;
import com.nija123098.sithreon.backend.networking.ManagedMachineType;
import com.nija123098.sithreon.backend.networking.TransferSocket;
import com.nija123098.sithreon.backend.objects.Competitor;
import com.nija123098.sithreon.backend.util.ConnectionUtil;
import com.nija123098.sithreon.backend.util.Log;
import com.nija123098.sithreon.backend.util.throwable.NoReturnException;
import com.nija123098.sithreon.game.management.GameAction;
import com.nija123098.sithreon.game.management.GameArguments;
import com.nija123098.sithreon.game.management.GameUpdate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * The {@link Machine} representation for running a competitor's code
 * which communicates with a {@link GameServer}.  The other
 * competitor's code is running on a parallel runner instance.
 *
 * @author nija123098
 */
public class GameClient extends Machine {
    private final TransferSocket gameServerSocket;
    private final Competitor competitor;

    public GameClient(Competitor competitor, String authCode) {
        this.competitor = competitor;
        try {
            this.gameServerSocket = new TransferSocket(this, Config.gameServerAddress, Config.internalPort);
            if (authCode != null) this.gameServerSocket.authenticateWithTemporaryCode(authCode);
        } catch (IOException e) {
            ConnectionUtil.throwConnectionException("Unable to establish connection to game server due to IOException", e);
            throw new NoReturnException();
        }
        this.gameServerSocket.registerAuthenticationAction((socket) -> socket.write(MachineAction.READY_TO_RECEIVE_COMPETITOR_DATA, competitor));
    }

    @Action(MachineAction.SEND_COMPETITOR_DATA)
    public void sendCompetitorData(String fileLocation, byte[] data, TransferSocket socket) {
        try {
            Path path = Paths.get(this.competitor.getRepository().getLocalRepoLocation(), fileLocation);
            Files.createDirectories(path.getParent());
            Files.write(path, data, StandardOpenOption.CREATE);
            socket.write(MachineAction.READY_FOR_NEXT_FILE);
        } catch (IOException e) {
            Log.ERROR.log("Unable to write file " + fileLocation + " in game runner loading for competitor " + this.competitor);
        }
    }

    @Action(MachineAction.COMPETITOR_DATA_COMPLETE)
    public void competitorDataComplete() {
        // todo final setup
        this.gameServerSocket.write(MachineAction.READY_TO_SERVE, ManagedMachineType.GAME_RUNNER);
    }

    @Action(MachineAction.GAME_UPDATE)
    public void gameUpdate(GameUpdate update, GameArguments gameArguments) {
        try {
            Thread.sleep(((Long) gameArguments.getObject(0)) - System.currentTimeMillis());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        this.gameServerSocket.write(MachineAction.GAME_ACTION, GameAction.SURRENDER, new GameArguments());
    }
}
