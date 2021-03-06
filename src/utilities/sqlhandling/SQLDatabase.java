package utilities.sqlhandling;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import twitter4j.Status;
import utilities.generalutils.Converter;
import utilities.generalutils.Printer;
import utilities.tweetshandling.TweetCleaning;

/**
 * Class representing a database in the server with a name, a connector object
 * and a proper URl encoded in UTF-8.
 *
 * @author Kanellis Dimitris
 */
public class SQLDatabase {

    /**
     * The constructor initialises the variables with the given values and the
     * URL to the Connector object's URL with the name of the database and the
     * encoding appended.
     *
     * @param name the name of the database
     * @param connector the Connector object
     */
    public SQLDatabase(final String name, final Connector connector) {
        _name = name;
        _connector = connector;
        _url = connector.getURL() + name + URL_ENCODING;
    }

    /**
     *
     * @return the name of the database
     */
    public String getName() {
        return _name;
    }

    /**
     *
     * @return a list of table names within the database
     */
    public List<String> getTables() {
        final List<String> tables = new ArrayList<>();

        Printer.println(Connector.GETTING_DRIVER_MESSAGE);
        try {
            Class.forName(_connector.getDriver());
        } catch (ClassNotFoundException e) {
            Printer.printErrln("Driver Error: " + e.getMessage());
            return tables;
        }

        Printer.println(Connector.CONNECTING_MESSAGE);
        try (Connection con = DriverManager.getConnection(_url,
                _connector.getUser().getUsername(),
                _connector.getUser().getPassword());) {

            Printer.println("Retrieving tables...");
            DatabaseMetaData metadata = con.getMetaData();
            try (ResultSet rs = metadata.getTables(null, null, "%",
                    new String[]{"TABLE"});) {
                while (rs.next()) {
                    tables.add(rs.getString(3));
                }
                return tables;
            }
        } catch (SQLException se) {
            Printer.printErrln("SQL Error " + se.getErrorCode() + " "
                    + se.getMessage());
            return tables;
        }
    }

    /**
     * Creates a new table in the database with the name that was given.
     *
     * @param tableName the name of the table to create
     */
    public void createTable(final String tableName) {
        Printer.println(Connector.GETTING_DRIVER_MESSAGE);
        try {
            Class.forName(_connector.getDriver());
        } catch (ClassNotFoundException e) {
            Printer.printErrln("Driver Error: " + e.getMessage());
        }

        Printer.println(Connector.CONNECTING_MESSAGE);
        try (Connection con = DriverManager.getConnection(_url,
                _connector.getUser().getUsername(),
                _connector.getUser().getPassword());
                Statement stmt = con.createStatement()) {

            Printer.println("Creating table...");
            stmt.executeUpdate(CREATE_TABLE_QUERY + tableName + TABLE_COLUMNS);
        } catch (SQLException se) {
            Printer.printErrln("SQL Error " + se.getErrorCode() + " "
                    + se.getMessage());
        }
    }

    /**
     * Deletes a table from the database with the name that was given.
     *
     * @param tableName the table to delete
     */
    public void deleteTable(final String tableName) {
        Printer.println(Connector.GETTING_DRIVER_MESSAGE);
        try {
            Class.forName(_connector.getDriver());
        } catch (ClassNotFoundException e) {
            Printer.printErrln("Driver Error: " + e.getMessage());
        }

        Printer.println(Connector.CONNECTING_MESSAGE);
        try (Connection con = DriverManager.getConnection(_url,
                _connector.getUser().getUsername(),
                _connector.getUser().getPassword());
                Statement stmt = con.createStatement()) {

            Printer.println("Deleting table...");
            stmt.executeUpdate(DELETE_TABLE_QUERY + tableName);
        } catch (SQLException se) {
            Printer.printErrln("SQL Error " + se.getErrorCode() + " "
                    + se.getMessage());
        }
    }

    /**
     * Inserts a list of statuses (tweets) in the table that is given. If the
     * status is already in the database then it will not be inserted.
     *
     * @param statuses the list of statuses to insert
     * @param tableName the name of the table to insert to
     */
    public void insert(final List<Status> statuses, final String tableName) {
        Printer.println(Connector.GETTING_DRIVER_MESSAGE);
        try {
            Class.forName(_connector.getDriver());
        } catch (ClassNotFoundException e) {
            Printer.printErrln("Driver Error: " + e.getMessage());
        }

        Printer.println(Connector.CONNECTING_MESSAGE);
        try (Connection con = DriverManager.getConnection(_url,
                _connector.getUser().getUsername(),
                _connector.getUser().getPassword());
                PreparedStatement pst = con.prepareStatement(INSERT_INTO_QUERY
                        + tableName + PST_COLUMNS);) {

            Printer.println("Adding tweets into the table...");
            int linesNum = 0;
            for (Status status : statuses) {
                if (!rowExists(status, con, tableName)) {
                    pst.setLong(1, status.getId());

                    pst.setDate(2, new java.sql.Date(
                            status
                            .getCreatedAt()
                            .getTime()));

                    pst.setString(3, status.getUser().getScreenName());
                    pst.setString(4, getTweetText(status));

                    pst.setString(5,
                            TweetCleaning.tweetToWords(getTweetText(status)));

                    if (status.getPlace() != null) {
                        pst.setString(6,
                                status
                                .getPlace()
                                .getName()
                                .replaceAll("[^\\p{ASCII}]", " "));
                    } else {
                        pst.setString(6, null);
                    }

                    pst.setString(7, status
                            .getUser()
                            .getLocation()
                            .replaceAll("[^\\p{ASCII}]", " "));

                    pst.setString(8,
                            status
                            .getSource()
                            .replaceAll("[^\\p{ASCII}]", " "));

                    pst.setString(9, Converter.geolocationToString(
                            status.getGeoLocation()));

                    pst.setString(10, status.getLang());
                    pst.setInt(11, status.getFavoriteCount());
                    pst.setInt(12, status.getRetweetCount());

                    pst.setString(13, Converter.hashtagArrayToString(
                            status.getHashtagEntities()));

                    pst.executeUpdate();
                    linesNum++;
                }
            }
            Printer.println("Total tweets inserted into the table: "
                    + linesNum);
        } catch (SQLException se) {
            Printer.printErrln("SQL Error " + se.getErrorCode() + " "
                    + se.getMessage());
        }
    }

    /**
     * Returns a list of strings by retrieving them from the given field of the
     * given table.
     *
     * @param field the field that was given
     * @param tableName the name of the table that was given
     * @return a list of Strings where each element contains a row of edited
     * text
     */
    public List<String> getField(final String field, final String tableName) {
        final List<String> editedText = new ArrayList<>();
        final String query = "SELECT " + field + " FROM " + tableName;

        Printer.println(Connector.GETTING_DRIVER_MESSAGE);
        try {
            Class.forName(_connector.getDriver());
        } catch (ClassNotFoundException e) {
            Printer.printErrln("Driver Error: " + e.getMessage());
            return editedText;
        }

        Printer.println(Connector.CONNECTING_MESSAGE);
        try (Connection con = DriverManager.getConnection(_url,
                _connector.getUser().getUsername(),
                _connector.getUser().getPassword());
                Statement stmt = con.createStatement()) {

            Printer.println("Getting field from table...");
            try (ResultSet rs = stmt.executeQuery(query);) {
                while (rs.next()) {
                    editedText.add(rs.getString(field));
                }
                return editedText;
            }
        } catch (SQLException se) {
            Printer.printErrln("SQL Error " + se.getErrorCode() + " "
                    + se.getMessage());
            return editedText;
        }
    }

    /**
     * Returns the total number of rows in the given table.
     *
     * @param tableName the name of the given table
     * @return an integer number of the number of rows in the table
     */
    public int getRowsCount(final String tableName) {
        int rowsCount = -1;
        final String query = COUNT_ROWS_QUERY + tableName;

        try {
            Class.forName(_connector.getDriver());
        } catch (ClassNotFoundException e) {
            Printer.printErrln("Driver Error: " + e.getMessage());
            return rowsCount;
        }

        try (Connection con = DriverManager.getConnection(_url,
                _connector.getUser().getUsername(),
                _connector.getUser().getPassword());
                PreparedStatement stmt = con.prepareStatement(query);
                ResultSet rs = stmt.executeQuery(query);) {

            rs.next();
            rowsCount = rs.getInt(1);
            return rowsCount;
        } catch (SQLException se) {
            Printer.printErrln("SQL Error: " + se.getErrorCode() + " "
                    + se.getMessage());
            return rowsCount;
        }
    }

    /**
     * Checks if a given status (tweet) is already in the database.
     *
     * @param status the status to check
     * @param con the connection object to use
     * @return true if it exists, false if not
     */
    private boolean rowExists(final Status status, final Connection con,
            final String tableName) throws SQLException {
        final String query = "SELECT *"
                + " FROM " + tableName
                + " WHERE id = '" + status.getId() + "'";
        try (PreparedStatement ps = con.prepareStatement(query);
                ResultSet resultSet = ps.executeQuery();) {
            return resultSet.next();
        }
    }

    /**
     * Returns the text from a status. If the status is a retweet then the
     * retweeted text will be returned.
     *
     * The text is first cleaned up from non-ASCII characters before returned.
     *
     * @param status the status to get the text from
     * @return the text to return
     */
    private static String getTweetText(final Status status) {
        if (status.isRetweet()) {
            return status
                    .getRetweetedStatus()
                    .getText()
                    .replaceAll("[^\\p{ASCII}]", " ");
        } else {
            return status.getText().replaceAll("[^\\p{ASCII}]", " ");
        }
    }

    private static final String URL_ENCODING = "?characterEncoding=UTF-8";

    private static final String CREATE_TABLE_QUERY = "Create TABLE ";
    private static final String DELETE_TABLE_QUERY = "Drop TABLE ";
    private static final String INSERT_INTO_QUERY = "INSERT INTO ";
    private static final String COUNT_ROWS_QUERY = "SELECT COUNT(*) FROM ";

    private static final String TABLE_COLUMNS
            = " (id BIGINT NOT NULL, "
            + "createdAt DATE NOT NULL, "
            + "userScreenName TINYTEXT NOT NULL, "
            + "text VARCHAR (255) NOT NULL, "
            + "editedText VARCHAR (255) NOT NULL, "
            + "place VARCHAR (255), "
            + "userPlace VARCHAR (255), "
            + "source VARCHAR (255) NOT NULL, "
            + "geolocation VARCHAR (255), "
            + "lang VARCHAR (255), "
            + "favoriteCount INT NOT NULL, "
            + "retweetCount INT NOT NULL, "
            + "hashtags VARCHAR (255) NOT NULL,"
            + "PRIMARY KEY (id)) ";

    private static final String PST_COLUMNS
            = " (id, "
            + "createdAt, "
            + "userScreenName, "
            + "text, "
            + "editedText, "
            + "place, "
            + "userPlace, "
            + "source, "
            + "geolocation, "
            + "lang, "
            + "favoriteCount, "
            + "retweetCount, "
            + "hashtags) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private final String _name;
    private final Connector _connector;
    private final String _url;
}
