package novajoy.janitor;

import java.sql.SQLException;
import java.sql.Statement;

import java.util.logging.Logger;

import novajoy.util.db.JdbcManager;

public class Janitor {
	private final JdbcManager dbManager;
	private static Logger log = new Loggers().getJanitorLogger();

	public Janitor(JdbcManager dbManager) {
		this.dbManager = dbManager;
	}

	public boolean clean_rssitems() {
		log.info("Janitor is trying to clean rss_items table.");
		try {
			log.info(deleteItems() + " rows deleted");
		} catch (SQLException e) {
			log.warning("Error occured: " + e.getMessage());
			return false;
		}
		return true;
	}

	private int deleteItems() throws SQLException {
		String query = "DELETE FROM Server_rssitem WHERE id IN ("
				+ "SELECT ID FROM ("
				+ "SELECT IT.id ID, IT.pubDate PB, MIN(COL.last_update_time) earliest FROM "
				+ "Server_rssfeed RS JOIN Server_rssfeed_collection CONN ON RS.id=CONN.rssfeed_id "
				+ "JOIN Server_collection COL ON COL.id=CONN.collection_id "
				+ "JOIN Server_rssitem IT ON IT.rssfeed_id=RS.id "
				+ "GROUP BY IT.id) AS T " + "WHERE PB < earliest);";
		Statement s = dbManager.createStatement();
		return s.executeUpdate(query);
	}
}
