package net.thevpc.gaming.atom.examples.kombla.main.server.dal;

import net.thevpc.gaming.atom.examples.kombla.main.shared.dal.ProtocolConstants;
import net.thevpc.gaming.atom.examples.kombla.main.shared.engine.AppConfig;
import net.thevpc.gaming.atom.examples.kombla.main.shared.model.DynamicGameModel;
import net.thevpc.gaming.atom.examples.kombla.main.shared.model.StartGameInfo;
import net.thevpc.gaming.atom.model.ModelPoint;
import net.thevpc.gaming.atom.model.Player;
import net.thevpc.gaming.atom.model.Sprite;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SocketMainServerDAO implements MainServerDAO{
    private ServerSocket serverSocket;
    private MainServerDAOListener listener;
    private final Map<Integer, ClientSession> playerToSocketMap = new HashMap<>();

    @Override
    public void start(MainServerDAOListener listener, AppConfig properties) {
        this.listener = listener;
        int port = properties.getServerPort();
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server started on port " + port);

            new Thread(() -> {
                while (true) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        System.out.println("New client connected: " + clientSocket);

                        ClientSession session = new ClientSession(-1, clientSocket);
                        new Thread(() -> processClient(session)).start();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

        } catch (IOException e) {
            throw new RuntimeException("Failed to start server", e);
        }
    }

    public void sendModelChanged(DynamicGameModel dynamicGameModel) {
        List<Player> players = dynamicGameModel.getPlayers();
        if (players == null) {
            players = new ArrayList<>();
            dynamicGameModel.setPlayers(players);
        }

        List<Sprite> sprites = dynamicGameModel.getSprites();
        if (sprites == null) {
            sprites = new ArrayList<>();
            dynamicGameModel.setSprites(sprites);
        }

        for (ClientSession session : playerToSocketMap.values()) {
            try {
                session.out.writeLong(dynamicGameModel.getFrame());
                System.out.println("Server is sending frameTime: " + dynamicGameModel.getFrame());

                session.out.writeInt(players.size());
                for (Player player : players) {
                    writePlayer(session.out, player);
                }

                session.out.writeInt(sprites.size());
                for (Sprite sprite : sprites) {
                    writeSprite(session.out, sprite);
                }

                session.out.flush();

            } catch (IOException e) {
                e.printStackTrace();
                session.close();
                playerToSocketMap.remove(session.playerId);
            }
        }
    }

    private void processClient(ClientSession session) {
        try {
            while (true) {
                int command = session.in.readInt();
                switch (command) {
                    case ProtocolConstants.CONNECT:
                        System.out.println("received connect command");
                        String playerName = session.in.readUTF();

                        StartGameInfo startGameInfo = listener.onReceivePlayerJoined(playerName);
                        session.playerId = startGameInfo.getPlayerId();

                        playerToSocketMap.put(session.playerId, session);
                        writeStartGameInfo(session, startGameInfo);
                        break;

                    case ProtocolConstants.LEFT:
                        System.out.println("received left command");
                        listener.onReceiveMoveLeft(session.playerId);
                        break;

                    case ProtocolConstants.RIGHT:
                        System.out.println("received right command");
                        listener.onReceiveMoveRight(session.playerId);
                        break;

                    case ProtocolConstants.UP:
                        System.out.println("received up command");
                        listener.onReceiveMoveUp(session.playerId);
                        break;

                    case ProtocolConstants.DOWN:
                        System.out.println("received down command");
                        listener.onReceiveMoveDown(session.playerId);
                        break;

                    case ProtocolConstants.FIRE:
                        System.out.println("received fire command");
                        listener.onReceiveReleaseBomb(session.playerId);
                        break;

                    default:
                        System.out.println("Unknown command: " + command);
                        break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            playerToSocketMap.remove(session.playerId);
            session.close();
        }
    }
    private void writeStartGameInfo(ClientSession session, StartGameInfo startGameInfo) {
        try {
            session.out.writeInt(ProtocolConstants.CONNECT);
            session.out.writeInt(startGameInfo.getPlayerId());
            int[][] maze = startGameInfo.getMaze();
            session.out.writeInt(maze.length);
            session.out.writeInt(maze[0].length);
            for (int[] row : maze) {
                for (int cell : row) {
                    session.out.writeInt(cell);
                }
            }
            session.out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writePlayer(DataOutputStream out, Player player) throws IOException {
        out.writeInt(player.getId());

        out.writeUTF(player.getName());

        Map<String, Object> properties = player.getProperties();
        out.writeInt(properties.size());

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            out.writeUTF(entry.getKey());
            out.writeUTF(entry.getValue().toString());
        }
    }

    private void writeSprite(DataOutputStream out, Sprite sprite) throws IOException {
        out.writeInt(sprite.getId());
        out.writeUTF(sprite.getKind());
        out.writeUTF(sprite.getName());

        ModelPoint location = sprite.getLocation();
        out.writeDouble(location.getX());
        out.writeDouble(location.getY());
        out.writeDouble(location.getZ());

        out.writeDouble(sprite.getDirection());
        out.writeInt(sprite.getPlayerId());

        Map<String, Object> properties = sprite.getProperties();
        out.writeInt(properties.size());

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            out.writeUTF(entry.getKey());
            out.writeUTF(entry.getValue().toString());
        }
    }



    private class ClientSession {
        int playerId;
        Socket socket;
        DataInputStream in;
        DataOutputStream out;

        public ClientSession(int playerId, Socket socket) throws IOException {
            this.playerId = playerId;
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
        }

        public void close() {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
