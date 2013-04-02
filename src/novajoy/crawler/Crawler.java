package novajoy.crawler;

import java.sql.*;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.logging.Logger;

import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;
import novajoy.util.config.IniWorker;
import novajoy.util.db.JdbcManager;

public class Crawler extends Thread {
	private final JdbcManager dbManager;
	private final long sleepMillis = 10 * 60 * 1000;
	private static Logger log = Logger.getLogger(Crawler.class.getName());

	public Crawler(String str, JdbcManager dbManager) {
		super(str);
		this.dbManager = dbManager;
	}

	public void run() {
		log.info("Starting crawl thread: " + Thread.currentThread().getName());
		try {
			while (true) {
				process_reqsts();
				log.info("Process: " + Thread.currentThread().getName()
						+ " went to sleep.");
				sleep(sleepMillis);
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			log.warning(e.getMessage());
		}
	}

	private void process_reqsts() {
		try {
			String query = "Select * FROM Server_rssfeed";
			String pquery = "UPDATE Server_rssfeed  SET pubDate = ? WHERE id = ?";

			Statement stmt = dbManager.createStatement();
			PreparedStatement ps = dbManager.createPreparedStatement(pquery);
			ResultSet rs = stmt.executeQuery(query);
			String addr;
			int items_count = 0;
			while (rs.next()) {
				addr = rs.getString(2);
				log.info("Crawling from: " + addr);
				SyndFeed feed = readfeed(addr);
				for (Iterator i = feed.getEntries().iterator(); i.hasNext();) {
					if (insert_item((SyndEntry) i.next(),
							Long.parseLong(rs.getString(1))))
						++items_count;
				}

				Date time = feed.getPublishedDate();
				if (time == null)
					time = Calendar.getInstance().getTime();

				ps.setTimestamp(1, new java.sql.Timestamp(time.getTime()));
				ps.setLong(2, Long.parseLong(rs.getString(1)));
				ps.executeUpdate();
				log.info(items_count
						+ " items added.\nCrawling and updating finished on: "
						+ addr);
				items_count = 0;
			}
		} catch (SQLException e) {
			log.warning(e.getMessage());
			// Could not find the database driver
		} catch (Exception e) {
			log.warning(e.getMessage());
			// Could not connect to the database
		}
	}

	private SyndFeed readfeed(String addr) throws Exception {
		XmlReader reader = null;
		SyndFeed feed = null;
		try {
			reader = new XmlReader(new URL(addr));
			feed = new SyndFeedInput().build(reader);
		} finally {
			if (reader != null)
				reader.close();
		}
		return feed;
	}

	private boolean insert_item(SyndEntry entry, long feedid)
			throws SQLException {

		if (check_already_in(entry.getLink())) {
			log.info("Entry with link: " + entry.getLink() + " already exists.");
			return false;
		}

		String pquery = "INSERT INTO Server_rssitem (rssfeed_id, title, description, link, author, pubDate) VALUES(?,?,?,?,?,?)";
		PreparedStatement ps = dbManager.createPreparedStatement(pquery);

		ps.setLong(1, feedid);
		ps.setString(2, entry.getTitle());
		ps.setString(3, entry.getDescription().getValue());
		ps.setString(4, entry.getLink());
		ps.setString(5, entry.getAuthor());
		ps.setTimestamp(6, new java.sql.Timestamp(entry.getPublishedDate()
				.getTime()));
		ps.executeUpdate();
		return true;
	}

	private boolean check_already_in(String link) throws SQLException {
		String pquery = "Select * FROM Server_rssitem WHERE link = ?";
		PreparedStatement ps = dbManager.createPreparedStatement(pquery);
		ps.setString(1, link);
		return ps.executeQuery().next();
	}

	public static void main(String[] args) throws Exception {
		IniWorker config = new IniWorker("config/config.ini");
		JdbcManager dbman = new JdbcManager(config.getDBaddress(),
				config.getDBbasename(), config.getDBuser(),
				config.getDBpassword());
		new Crawler("Crawler_1", dbman).start();
	}
}
