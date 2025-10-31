package edu.northeastern.hanafeng.chatsystem.client.support;

public class ClientConstants {
    public static final String CHAT_ENDPOINT = "/chat/{roomid}";
    public static final int MAX_ROOMS = 20;
    public static final int MAX_USER_ID = 100000;

    public static String buildRoomWebSocketUrl(String wsBase, int roomId) {
        return wsBase + "/chat/" + roomId;
    }
    
    private ClientConstants() {}
}