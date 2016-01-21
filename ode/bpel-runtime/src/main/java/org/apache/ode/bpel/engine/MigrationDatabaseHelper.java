package org.apache.ode.bpel.engine;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * MigrationDatabaseHelper
 * 
 * @author schlieta
 * 
 *         provides methods to change data or to read data out of the database
 */

public class MigrationDatabaseHelper {
	private static final Log __log = LogFactory
			.getLog(MigrationDatabaseHelper.class);

	private Contexts _contexts;

	public MigrationDatabaseHelper(Contexts contexts) {
		this._contexts = contexts;
	}

	// readout Process ID with Version
	public Integer getNewProcessID(long Version) {
		Connection connection = null;

		Integer NewProcessID = null;
		Statement st = null;
		ResultSet rs = null;
		try {
			if (_contexts.dao.getDataSource() != null) {
				connection = _contexts.dao.getDataSource().getConnection();
			}

			if (connection != null) {
				st = connection.createStatement();

				rs = st.executeQuery("SELECT id FROM ode_process WHERE version ="
						+ Version);

				rs.next();
				NewProcessID = rs.getInt("id");
			}
		} catch (SQLException e) {
			__log.error("MigrationDatabaseHandler.getNewProcessID", e);
		} finally {
			try {
				if (rs != null)
					rs.close();
				if (st != null)
					st.close();
				if (connection != null)
					connection.close();
			} catch (SQLException e) {
				__log.error("MigrationDatabaseHandler.getNewProcessID", e);
			}
		}
		return (NewProcessID);
	}

	// readout Correlator ID
	public Integer getCorrID(Integer NewProcessID) {
		Connection connection = null;

		Integer Correlator_ID = null;
		Statement st = null;
		ResultSet rs = null;
		try {
			if (_contexts.dao.getDataSource() != null) {
				connection = _contexts.dao.getDataSource().getConnection();
			}

			if (connection != null) {
				st = connection.createStatement();

				rs = st.executeQuery("SELECT correlator_id FROM ode_correlator WHERE proc_id ="
						+ NewProcessID);

				rs.next();
				Correlator_ID = rs.getInt("correlator_id");
			}

		} catch (SQLException e) {
			__log.error("MigrationDatabaseHandler.getCorrID", e);
		} finally {
			try {
				if (rs != null)
					rs.close();
				if (st != null)
					st.close();
				if (connection != null)
					connection.close();
			} catch (SQLException e) {
				__log.error("MigrationDatabaseHandler.getCorrID", e);
			}
		}
		return (Correlator_ID);
	}

	// Update process_id in ode_process_instance
	public void setOdeProcessInstance(Integer InstanceID, Integer NewProcessID,
			Integer CorrelatorID) {
		Connection connection = null;

		Statement st = null;
		try {
			if (_contexts.dao.getDataSource() != null) {
				connection = _contexts.dao.getDataSource().getConnection();
			}

			if (connection != null) {
				st = connection.createStatement();

				st.executeUpdate("UPDATE ode_process_instance SET process_id="
						+ NewProcessID + " WHERE id = " + InstanceID);

				st.executeUpdate("UPDATE ode_process_instance SET instantiating_correlator_id="
						+ CorrelatorID + " WHERE id = " + InstanceID);
			}

		} catch (SQLException e) {
			__log.error("MigrationDatabaseHandler.setOdeProcessInstance", e);
		} finally {
			try {
				if (st != null)
					st.close();
				if (connection != null)
					connection.close();
			} catch (SQLException e) {
				__log.error("MigrationDatabaseHandler.setOdeProcessInstance", e);
			}
		}

	}

	// Update process_id in ode_event
	public void setOdeEvent(Integer InstanceID, Integer NewProcessID) {
		Connection connection = null;

		Statement st = null;
		try {
			if (_contexts.dao.getDataSource() != null) {
				connection = _contexts.dao.getDataSource().getConnection();
			}

			if (connection != null) {
				st = connection.createStatement();

				st.executeUpdate("UPDATE ode_event SET process_id="
						+ NewProcessID + " WHERE instance_id = " + InstanceID);
			}
		} catch (SQLException e) {
			__log.error("MigrationDatabaseHandler.setOdeEvent", e);
		} finally {
			try {
				if (st != null)
					st.close();
				if (connection != null)
					connection.close();
			} catch (SQLException e) {
				__log.error("MigrationDatabaseHandler.setOdeEvent", e);
			}
		}

	}

	// Update process_id in ode_message_exchange
	public void setOdeMessageExchange(Integer InstanceID, Integer NewProcessID) {
		Connection connection = null;

		Statement st = null;
		try {
			if (_contexts.dao.getDataSource() != null) {
				connection = _contexts.dao.getDataSource().getConnection();
			}

			if (connection != null) {
				st = connection.createStatement();

				st.executeUpdate("UPDATE ode_message_Exchange SET process_id="
						+ NewProcessID + " WHERE process_instance_id = "
						+ InstanceID);
			}
		} catch (SQLException e) {
			__log.error("MigrationDatabaseHandler.setOdeMessageExchange", e);
		} finally {
			try {
				if (st != null)
					st.close();
				if (connection != null)
					connection.close();
			} catch (SQLException e) {
				__log.error("MigrationDatabaseHandler.setOdeMessageExchange", e);
			}
		}

	}
}
