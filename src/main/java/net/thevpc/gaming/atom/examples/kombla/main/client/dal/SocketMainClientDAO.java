package net.thevpc.gaming.atom.examples.kombla.main.client.dal;

import net.thevpc.gaming.atom.examples.kombla.main.shared.dal.ProtocolConstants;
import net.thevpc.gaming.atom.examples.kombla.main.shared.engine.AppConfig;
import net.thevpc.gaming.atom.examples.kombla.main.shared.model.DynamicGameModel;
import net.thevpc.gaming.atom.examples.kombla.main.shared.model.StartGameInfo;
import net.thevpc.gaming.atom.model.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class SocketMainClientDAO implements MainClientDAO {
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private MainClientDAOListener listener;
    private String serverAddress;
    private int serverPort;

    @Override
    public void start(MainClientDAOListener listener, AppConfig properties) {
        this.listener = listener;
        this.serverAddress = properties.getServerAddress();
        this.serverPort = properties.getServerPort();

        try {
            this.socket = new Socket(serverAddress, serverPort);
            System.out.println("Connected to server at " + serverAddress + ":" + serverPort);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize socket connection", e);
        }
    }

    public StartGameInfo readStartGameInfo() throws IOException {
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());

        out.writeInt(ProtocolConstants.CONNECT);
        System.out.println("message sent to server");
        String playerName = "Player1";
        out.writeUTF(playerName);
        out.flush();

        int response = in.readInt();
        int playerId = in.readInt();
        int mazeSize = in.readInt();
        int mazeSize2 = in.readInt();

        int[][] maze = new int[mazeSize][mazeSize2];

        for (int i = 0; i < mazeSize; i++) {
            for (int j = 0; j < mazeSize2; j++) {
                maze[i][j] = in.readInt();
            }
        }
        return new StartGameInfo(playerId, maze);
    }


    @Override
    public StartGameInfo connect() {
        StartGameInfo startGameInfo;
        try {
            startGameInfo = readStartGameInfo();
            new Thread(this::onLoopReceiveModelChanged).start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to complete connection and retrieve game start info", e);
        }
        return startGameInfo;
    }


    @Override
    public void sendMoveLeft() throws IOException {
        System.out.println("Client: Sending MOVE LEFT command to server");
        out.writeByte(ProtocolConstants.LEFT);
        out.flush();
    }

    @Override
    public void sendMoveRight() throws IOException{
        System.out.println("Client: Sending MOVE Right command to server");
        out.writeInt(ProtocolConstants.RIGHT);
        out.flush();
    }

    @Override
    public void sendMoveUp() throws IOException {
        System.out.println("Client: Sending MOVE Up command to server");
        out.writeInt(ProtocolConstants.UP);
        out.flush();
    }

    @Override
    public void sendMoveDown() throws IOException{
        System.out.println("Client: Sending MOVE Down command to server");
        out.writeInt(ProtocolConstants.DOWN);
        out.flush();
    }

    @Override
    public void sendFire() throws IOException {
        System.out.println("Client: Sending Fire command to server");
        out.writeInt(ProtocolConstants.FIRE);
        out.flush();
    }
    private Player readPlayer() throws IOException {
        int id = in.readInt();
        String name = in.readUTF();

        DefaultPlayer player = new DefaultPlayer();
        player.setId(id);
        player.setName(name);

        int propertiesSize = in.readInt();
        for (int i = 0; i < propertiesSize; i++) {
            String key = in.readUTF();
            String value = in.readUTF();

            player.setProperty(key, value);
        }

        return player;
    }

    private Sprite readSprite() throws IOException {
        int id = in.readInt();
        String kind = in.readUTF();
        String name = in.readUTF();

        DefaultSprite sprite = new DefaultSprite();
        sprite.setId(id);
        sprite.setKind(kind);
        sprite.setName(name);

        double x = in.readDouble();
        double y = in.readDouble();
        double z = in.readDouble();
        sprite.setLocation(new ModelPoint(x, y, z));

        sprite.setDirection(in.readDouble());
        sprite.setPlayerId(in.readInt());

        int propertiesSize = in.readInt();
        for (int i = 0; i < propertiesSize; i++) {
            String key = in.readUTF();
            String value = in.readUTF();
            sprite.setProperty(key, value);
        }
        return sprite;
    }

    public void onLoopReceiveModelChanged() {
        new Thread(() -> {
            try {
                while (true) {
                        long frameTime = in.readLong();
                    System.out.println("received frame " + frameTime);

                        int numberOfPlayers = in.readInt();
                        List<Player> players = new ArrayList<>();
                    for (int i = 0; i < numberOfPlayers; i++) {
                        Player player = readPlayer();
                        if (player != null) {
                            players.add(player);
                            System.out.println("Player added: " + player.getId());
                        } else {
                            System.out.println("Warning: Received null player.");
                        }
                    }

                        int numberOfSprites = in.readInt();
                        List<Sprite> sprites = new ArrayList<>();
                        for (int i = 0; i < numberOfSprites; i++) {
                            sprites.add(readSprite());
                        }

                        DynamicGameModel model = new DynamicGameModel();
                        model.setFrame(frameTime);
                        model.setPlayers(players);
                        model.setSprites(sprites);

                    System.out.println("onLoopReceiveModelChanged: Notifying listener of model change");
                    if (listener != null) {
                        listener.onModelChanged(model);
                    } else {
                        System.out.println("Listener is null! Check if start() initialized it correctly.");
                    }
                }
            } catch (IOException ignored) {
            }
        }).start();
    }

}
