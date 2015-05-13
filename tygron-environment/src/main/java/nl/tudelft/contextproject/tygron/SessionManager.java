package nl.tudelft.contextproject.tygron;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class SessionManager {

  private static Connection apiConnection;
  
  public SessionManager(Connection localApiConnection){
    apiConnection = localApiConnection;
  }
  
  /**
   * Return a list of joinable tygron sessions.
   * 
   * @return A list of joinable tygron sessions.
   */
  public List<Session> getJoinableSessions() {
    List<Session> returnList = new ArrayList<Session>();

    JSONArray data = apiConnection.callPostEventArray(
        "services/event/IOServicesEventType/GET_JOINABLE_SESSIONS/", null);

    for (int i = 0; i < data.length(); i++) {
      JSONObject row = data.getJSONObject(i);

      // Create new session
      Session localSession = new Session(apiConnection);

      // Set data here
      localSession.setName(row.getString("name"));
      localSession.setType(row.getString("sessionType"));
      localSession.setLanguage(row.getString("sessionType"));
      localSession.setSessionId(row.getInt("id"));

      // Add to datalist
      returnList.add(localSession);
    }

    return returnList;
  }
  
  /**
   * Join a Tygron session and reply whether it was a success or not. 
   * Data is loaded into memory.
   * @return whether the join was successful or not.
   */
  public boolean joinSession(Session session, int slotId) {

    JSONArray dataArray = new JSONArray();
    dataArray.put(slotId);
    dataArray.put("VIEWER");
    dataArray.put("");
    dataArray.put("Tygron-API-Agent");
    dataArray.put("");
    
    JSONObject data = apiConnection.callPostEventObject(
        "services/event/IOServicesEventType/JOIN_SESSION/", dataArray);
    
    session.setClientToken(data.getString("sessionClientToken"));
    session.setServerToken(data.getString("serverToken"));
    
    return true;
  }
  
  /**
   * Return a joinable loaded session.
   * If it does not exist yet, start a session and return it.
   * 
   * @param mapName The mapname you are trying to join.
   */
  public Session createOrJoinSession(String mapName){
    List<Session> availableList = getJoinableSessions();
    for(int i = 0;i<availableList.size();i++){
      if(mapName.equals(availableList.get(i).getName())){
        return availableList.get(i);
      }
    }
    
    // TODO: Create a new session
    
    return new Session(apiConnection);
  }
  
  public int createSession(String mapName){
    
    JSONArray dataArray = new JSONArray();
    dataArray.put("MULTI_PLAYER");
    dataArray.put(mapName);
    
    JSONObject data = apiConnection.callPostEventObject(
        "services/event/IOServicesEventType/START_NEW_SESSION/", dataArray); 
    
    System.out.println(data);
    
    return -1;
  }
  
  /**
   * Hard kill a Tygron session and reply whether it was a success or not. 
   * @return whether the kill was successful or not.
   */
  public boolean killSession(int slotId) {
    
    JSONArray dataArray = new JSONArray();
    dataArray.put(slotId);
    
    JSONObject data = apiConnection.callPostEventObject(
        "services/event/IOServicesEventType/KILL_SESSION/", dataArray);
    
    System.out.println(data);
    
    return true;
  }

}
