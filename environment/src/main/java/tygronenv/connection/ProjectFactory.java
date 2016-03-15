package tygronenv.connection;

import java.util.Collection;

import eis.exceptions.ManagementException;
import nl.tytech.core.client.event.EventIDListenerInterface;
import nl.tytech.core.client.event.EventManager;
import nl.tytech.core.client.net.ServicesManager;
import nl.tytech.core.client.net.SlotConnection;
import nl.tytech.core.event.Event;
import nl.tytech.core.event.EventListenerInterface;
import nl.tytech.core.net.Network.AppType;
import nl.tytech.core.net.Network.SessionType;
import nl.tytech.core.net.event.IOServiceEventType;
import nl.tytech.core.net.serializable.JoinReply;
import nl.tytech.core.net.serializable.MapLink;
import nl.tytech.core.net.serializable.ProjectData;
import nl.tytech.core.util.SettingsManager;
import nl.tytech.data.editor.event.EditorEventType;
import nl.tytech.data.editor.event.EditorSettingsEventType;
import nl.tytech.data.editor.event.EditorStakeholderEventType;
import nl.tytech.data.engine.item.Setting;
import nl.tytech.data.engine.item.Stakeholder;
import nl.tytech.locale.TLanguage;
import nl.tytech.util.ThreadUtils;
import nl.tytech.util.logger.TLogger;

/**
 * Factory to fetch existing and create new projects
 * 
 * @author W.Pasman
 *
 */
public class ProjectFactory {

	/**
	 * Join existing project.
	 * 
	 * @param name
	 *            the project name to join/make.
	 * @return project with given name, or null if no project with given name
	 *         exists.
	 * @throws ManagementException
	 */
	public ProjectData getProject(String name) throws ManagementException {
		ProjectData[] projects = ServicesManager.fireServiceEvent(IOServiceEventType.GET_MY_STARTABLE_PROJECTS);
		if (projects != null) {
			for (ProjectData existing : projects) {
				if (existing.getFileName().equals(name)) {
					return ServicesManager.fireServiceEvent(IOServiceEventType.GET_PROJECT_DATA, name);
				}
			}
		}

		return createProject(name);
	}

	/**
	 * Assumes that there is no existing project with given name (eg
	 * {@link #getProject(String)} returned null). Create one and initialize it.
	 * Bit hacky, it does not do much for the map, it probably stays empty.
	 * 
	 * @throws ManagementException
	 */
	public ProjectData createProject(String name) throws ManagementException {
		ProjectData proj = ServicesManager.fireServiceEvent(IOServiceEventType.CREATE_NEW_PROJECT, name, TLanguage.EN);

		Integer slotID = ServicesManager.fireServiceEvent(IOServiceEventType.START_NEW_SESSION, SessionType.EDITOR,
				proj.getFileName(), TLanguage.EN);
		if (slotID == null || slotID < 0) {
			throw new ManagementException("Failed to create edit slot to create new project");
		}

		SlotConnection editSlot = editProject(slotID);
		addCivilianMap(editSlot);

		String result = ServicesManager.fireServiceEvent(IOServiceEventType.SAVE_PROJECT_INIT, slotID);
		if (result != null) {
			throw new ManagementException("Failed to save new project" + result);
		}

		editSlot.disconnect(false);

		return proj;
	}

	/**
	 * open an editor slot
	 * 
	 * @param proj
	 *            the project to open an edit slot for
	 * @return a {@link SlotConnection} that can be used for editing the
	 *         project.
	 * @throws ManagementException
	 */
	private SlotConnection editProject(Integer slotID) throws ManagementException {

		JoinReply reply = ServicesManager.fireServiceEvent(IOServiceEventType.JOIN_SESSION, slotID, AppType.EDITOR);
		if (reply == null) {
			throw new ManagementException("failed to edit project:" + reply);
		}

		SlotConnection slotConnection = new SlotConnection();
		slotConnection.initSettings(AppType.EDITOR, SettingsManager.getServerIP(), slotID, reply.serverToken,
				reply.client.getClientToken());
		if (!slotConnection.connect()) {
			throw new ManagementException("Failed to connect a slot for editing the new project");
		}

		return slotConnection;
	}

	/**
	 * Add map and civilian stakeholder
	 * 
	 * @param slotConnection
	 *            the connection with the editor slot
	 * @throws ManagementException
	 */
	private void addCivilianMap(SlotConnection slotConnection) throws ManagementException {
		EditorEventHandler eventHandler = new EditorEventHandler();
		int mapSizeM = 500;
		slotConnection.fireServerEvent(true, EditorEventType.SET_INITIAL_MAP_SIZE, mapSizeM);
		slotConnection.fireServerEvent(true, EditorSettingsEventType.WIZARD_FINISHED);

		/**
		 * Add a civilian stakeholder
		 */
		slotConnection.fireServerEvent(true, EditorStakeholderEventType.ADD_WITH_TYPE_AND_PLAYABLE,
				Stakeholder.Type.CIVILIAN, true);

		eventHandler.waitCompletion();
	}

	/**
	 * Deletes project on the server.
	 * 
	 * @param project
	 * @throws ManagementException
	 */
	public void deleteProject(ProjectData project) throws ManagementException {
		Boolean result = ServicesManager.fireServiceEvent(IOServiceEventType.DELETE_PROJECT, project.getFileName());
		if (!result) {
			throw new ManagementException("failed to delete project " + project.getFileName() + " on the server");
		}

	}

}

/**
 * Event handler that listens to the editor. Just internal, to hear when the
 * server is ready (the map was properly uploaded). This is important so that we
 * do not proceed using the map before we uploaded it.
 *
 */
class EditorEventHandler implements EventListenerInterface, EventIDListenerInterface {

	private boolean stakeholderUpdate = false, mapUpdate = false;

	public EditorEventHandler() {
		EventManager.addListener(this, MapLink.STAKEHOLDERS);
		EventManager.addEnumListener(this, MapLink.SETTINGS, Setting.Type.MAP_WIDTH_METERS);
	}

	/**
	 * Wait till map and stakeholder have been updated.
	 * 
	 * @throws ManagementException
	 *             if it takes too long (15 seconds)
	 */
	public void waitCompletion() throws ManagementException {
		// wait on first updates (seperate thread)
		boolean updated = false;
		for (int i = 0; i < 15; i++) {
			if (stakeholderUpdate && mapUpdate) {
				updated = true;
				break;
			}
			ThreadUtils.sleepInterruptible(1000);
		}
		if (!updated) {
			throw new ManagementException("Server is not responding on request to update the map");
		}

	}

	@Override
	public void notifyEnumListener(Event event, Enum<?> enhum) {

		if (enhum == Setting.Type.MAP_WIDTH_METERS) {
			Setting setting = EventManager.getItem(MapLink.SETTINGS, Setting.Type.MAP_WIDTH_METERS);
			TLogger.info("Map Width is set to: " + setting.getIntValue());
			mapUpdate = true;
		}
	}

	@Override
	public void notifyIDListener(Event arg0, Integer arg1) {

	}

	@Override
	public void notifyListener(Event event) {

		if (event.getType() == MapLink.STAKEHOLDERS) {
			Collection<Stakeholder> updates = event.getContent(MapLink.UPDATED_COLLECTION);
			TLogger.info("Updated stakeholders: " + updates);
			stakeholderUpdate = true;
		}
	}

}