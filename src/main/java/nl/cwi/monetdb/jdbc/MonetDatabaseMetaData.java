/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0.  If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Copyright 2008-2015 MonetDB B.V.
 */

package nl.cwi.monetdb.jdbc;

import java.sql.*;
import java.util.*;

/**
 * A DatabaseMetaData object suitable for the MonetDB database.
 * 
 *
 * @author Fabian Groffen
 * @version 0.5
 */
public class MonetDatabaseMetaData extends MonetWrapper implements DatabaseMetaData {
	private Connection con;
	private Driver driver;
	private static Map<Connection, Map<String,String>> envs = new HashMap<Connection, Map<String,String>>();

	public MonetDatabaseMetaData(Connection parent) {
		con = parent;
		driver = new MonetDriver();
	}

	private synchronized Statement getStmt() throws SQLException {
		// use Statement which allows scrolling both directions through results
		// cannot reuse stmt here, as people may request multiple
		// queries, see for example bug #2703
		return con.createStatement(
					ResultSet.TYPE_SCROLL_INSENSITIVE,
					ResultSet.CONCUR_READ_ONLY);
	}

	/**
	 * Internal cache for environment properties retrieved from the
	 * server.  To avoid querying the server over and over again, once a
	 * value is read, it is kept in a Map for reuse.
	 */
	private synchronized String getEnv(String key) {
		// if due to concurrency on this Class envs is assigned twice, I
		// just don't care here
		Map<String,String> menvs = envs.get(con);
		if (menvs == null) {
			// make the env map, insert all entries from env()
			menvs = new HashMap<String,String>();
			try {
				ResultSet env = getStmt().executeQuery(
						"SELECT \"name\", \"value\" FROM sys.env() as env");
				try {
					while (env.next()) {
						menvs.put(env.getString("name"), env.getString("value"));
					}
				} finally {
					env.close();
				}
			} catch (SQLException e) {
				// ignore
			}
			envs.put(con, menvs);
		}
		
		String ret = menvs.get(key);
		if (ret == null) {
			// It might be some key from the "sessions" table, which is
			// no longer there, but available as variables.  Just query
			// for the variable, and hope that it is ok (not a user
			// variable, etc.)  In general, it should be ok, because
			// it's only used inside this class.
			// The effects of caching a variable, might be
			// undesirable... TODO
			try {
				ResultSet env = getStmt().executeQuery(
						"SELECT @\"" + key + "\" AS \"value\"");
				try {
					if (env.next()) {
						ret = env.getString("value");
						menvs.put(key, ret);
					}
				} finally {
					env.close();
				}
			} catch (SQLException e) {
				// ignore
			}
		}
		return ret;
	}

	/**
	 * Can all the procedures returned by getProcedures be called
	 * by the current user?
	 *
	 * @return true if so
	 */
	@Override
	public boolean allProceduresAreCallable() {
		return true;
	}

	/**
	 * Can all the tables returned by getTable be SELECTed by
	 * the current user?
	 *
	 * @return true because we only have one user a.t.m.
	 */
	@Override
	public boolean allTablesAreSelectable() {
		return true;
	}

	/**
	 * What is the URL for this database?
	 *
	 * @return a reconstructed connection string
	 * @throws SQLException if a database access error occurs
	 */
	@Override
	public String getURL() throws SQLException {
		return ((MonetConnection)con).getJDBCURL();
	}

	/**
	 * What is our user name as known to the database?
	 *
	 * @return sql user
	 * @throws SQLException if a database access error occurs
	 */
	@Override
	public String getUserName() throws SQLException {
		return getEnv("current_user");
	}

	/**
	 * Is the database in read-only mode?
	 *
	 * @return always false for now
	 */
	@Override
	public boolean isReadOnly() {
		return false;
	}

	/**
	 * Are NULL values sorted high?
	 *
	 * @return true because MonetDB puts NULL values on top upon ORDER BY
	 */
	@Override
	public boolean nullsAreSortedHigh() {
		return true;
	}

	/**
	 * Are NULL values sorted low?
	 *
	 * @return negative of nullsAreSortedHigh()
	 * @see #nullsAreSortedHigh()
	 */
	@Override
	public boolean nullsAreSortedLow() {
		return !nullsAreSortedHigh();
	}

	/**
	 * Are NULL values sorted at the start regardless of sort order?
	 *
	 * @return false, since MonetDB doesn't do this
	 */
	@Override
	public boolean nullsAreSortedAtStart() {
		return false;
	}

	/**
	 * Are NULL values sorted at the end regardless of sort order?
	 *
	 * @return false, since MonetDB doesn't do this
	 */
	@Override
	public boolean nullsAreSortedAtEnd() {
		return false;
	}

	/**
	 * What is the name of this database product - this should be MonetDB
	 * of course, so we return that explicitly.
	 *
	 * @return the database product name
	 */
	@Override
	public String getDatabaseProductName() {
		return "MonetDB";
	}

	/**
	 * What is the version of this database product.
	 *
	 * @return a fixed version number, yes it's quick and dirty
	 * @throws SQLException if a database access error occurs
	 */
	@Override
	public String getDatabaseProductVersion() throws SQLException {
		return getEnv("monet_version");
	}

	/**
	 * What is the name of this JDBC driver?  If we don't know this
	 * we are doing something wrong!
	 *
	 * @return the JDBC driver name
	 */
	@Override
	public String getDriverName() {
		return "MonetDB Native Driver";
	}

	/**
	 * What is the version string of this JDBC driver?	Again, this is
	 * static.
	 *
	 * @return the JDBC driver name.
	 */
	@Override
	public String getDriverVersion() {
		return MonetDriver.getDriverVersion();
	}

	/**
	 * What is this JDBC driver's major version number?
	 *
	 * @return the JDBC driver major version
	 */
	@Override
	public int getDriverMajorVersion() {
		return driver.getMajorVersion();
	}

	/**
	 * What is this JDBC driver's minor version number?
	 *
	 * @return the JDBC driver minor version
	 */
	@Override
	public int getDriverMinorVersion() {
		return driver.getMinorVersion();
	}

	/**
	 * Does the database store tables in a local file?	No - it
	 * stores them in a file on the server.
	 *
	 * @return false because that's what MonetDB is for
	 */
	@Override
	public boolean usesLocalFiles() {
		return false;
	}

	/**
	 * Does the database use a local file for each table?  Well, not really,
	 * since it doesn't use local files.
	 *
	 * @return false for it doesn't
	 */
	@Override
	public boolean usesLocalFilePerTable() {
		return false;
	}

	/**
	 * Does the database treat mixed case unquoted SQL identifiers
	 * as case sensitive and as a result store them in mixed case?
	 * A JDBC-Compliant driver will always return false.
	 *
	 * @return false
	 */
	@Override
	public boolean supportsMixedCaseIdentifiers() {
		return false;
	}

	/**
	 * Does the database treat mixed case unquoted SQL identifiers as
	 * case insensitive and store them in upper case?
	 *
	 * @return true if so
	 */
	@Override
	public boolean storesUpperCaseIdentifiers() {
		return false;
	}

	/**
	 * Does the database treat mixed case unquoted SQL identifiers as
	 * case insensitive and store them in lower case?
	 *
	 * @return true if so
	 */
	@Override
	public boolean storesLowerCaseIdentifiers() {
		return true;
	}

	/**
	 * Does the database treat mixed case unquoted SQL identifiers as
	 * case insensitive and store them in mixed case?
	 *
	 * @return true if so
	 */
	@Override
	public boolean storesMixedCaseIdentifiers() {
		return false;
	}

	/**
	 * Does the database treat mixed case quoted SQL identifiers as
	 * case sensitive and as a result store them in mixed case?  A
	 * JDBC compliant driver will always return true.
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsMixedCaseQuotedIdentifiers() {
		return true;
	}

	/**
	 * Does the database treat mixed case quoted SQL identifiers as
	 * case insensitive and store them in upper case?
	 *
	 * @return true if so
	 */
	@Override
	public boolean storesUpperCaseQuotedIdentifiers() {
		return false;
	}

	/**
	 * Does the database treat mixed case quoted SQL identifiers as case
	 * insensitive and store them in lower case?
	 *
	 * @return true if so
	 */
	@Override
	public boolean storesLowerCaseQuotedIdentifiers() {
		return false;
	}

	/**
	 * Does the database treat mixed case quoted SQL identifiers as case
	 * insensitive and store them in mixed case?
	 *
	 * @return true if so
	 */
	@Override
	public boolean storesMixedCaseQuotedIdentifiers() {
		return false;
	}

	/**
	 * What is the string used to quote SQL identifiers?  This returns
	 * a space if identifier quoting isn't supported.  A JDBC Compliant
	 * driver will always use a double quote character.
	 *
	 * @return the quoting string
	 */
	@Override
	public String getIdentifierQuoteString() {
		return "\"";
	}

	/**
	 * Get a comma separated list of all a database's SQL keywords that
	 * are NOT also SQL:2003 keywords.
	 * 
	 *
	 * @return a comma separated list of MonetDB keywords
	 */
	@Override
	public String getSQLKeywords() {
		/* return same list as returned in odbc/driver/SQLGetInfo.c case SQL_KEYWORDS: */
		return  "ADMIN,AFTER,AGGREGATE,ALWAYS,ASYMMETRIC,ATOMIC," +
			"AUTO_INCREMENT,BEFORE,BIGINT,BIGSERIAL,BINARY,BLOB," +
			"CALL,CHAIN,CLOB,COMMITTED,COPY,CORR,CUME_DIST," +
			"CURRENT_ROLE,CYCLE,DATABASE,DELIMITERS,DENSE_RANK," +
			"DO,EACH,ELSEIF,ENCRYPTED,EVERY,EXCLUDE,FOLLOWING," +
			"FUNCTION,GENERATED,IF,ILIKE,INCREMENT,LAG,LEAD," +
			"LIMIT,LOCALTIME,LOCALTIMESTAMP,LOCKED,MAXVALUE," +
			"MEDIAN,MEDIUMINT,MERGE,MINVALUE,NEW,NOCYCLE," +
			"NOMAXVALUE,NOMINVALUE,NOW,OFFSET,OLD,OTHERS,OVER," +
			"PARTITION,PERCENT_RANK,PLAN,PRECEDING,PROD,QUANTILE," +
			"RANGE,RANK,RECORDS,REFERENCING,REMOTE,RENAME," +
			"REPEATABLE,REPLICA,RESTART,RETURN,RETURNS," +
			"ROW_NUMBER,ROWS,SAMPLE,SAVEPOINT,SCHEMA,SEQUENCE," +
			"SERIAL,SERIALIZABLE,SIMPLE,START,STATEMENT,STDIN," +
			"STDOUT,STREAM,STRING,SYMMETRIC,TIES,TINYINT,TRIGGER," +
			"UNBOUNDED,UNCOMMITTED,UNENCRYPTED,WHILE,XMLAGG," +
			"XMLATTRIBUTES,XMLCOMMENT,XMLCONCAT,XMLDOCUMENT," +
			"XMLELEMENT,XMLFOREST,XMLNAMESPACES,XMLPARSE,XMLPI," +
			"XMLQUERY,XMLSCHEMA,XMLTEXT,XMLVALIDATE";
	}

	/**
	 * getMonetDBSysFunctions(int kind)
	 * args: int kind, value must be 1 or 2 or 3 or 4.
	 * internal utility method to query the MonetDB sys.functions table
	 * to dynamically get the function names (for a specific kind) and
	 * concatenate the function names into a comma separated list.
	 */
	private String getMonetDBSysFunctions(int kind) {
		// where clause part (for num/str/timedate to match only functions whose 1 arg exists and is of a certain type
		String part1 = "WHERE \"id\" IN (SELECT \"func_id\" FROM \"sys\".\"args\" WHERE \"number\" = 1 AND \"name\" = 'arg_1' AND \"type\" IN ";
		String whereClause = "";
		switch (kind) {
			case 1:	/* numeric functions */
				whereClause = part1 +
				"('tinyint', 'smallint', 'int', 'bigint', 'decimal', 'real', 'double') )" +
				// exclude 2 functions which take an int as arg but returns a char or str
				" AND \"name\" NOT IN ('code', 'space')";
				break;
			case 2:	/* string functions */
				whereClause = part1 +
				"('char', 'varchar', 'clob') )" +
				// include 2 functions which take an int as arg but returns a char or str
				" OR \"name\" IN ('code', 'space')";
				break;
			case 3:	/* system functions */
				whereClause = "WHERE \"id\" NOT IN (SELECT \"func_id\" FROM \"sys\".\"args\" WHERE \"number\" = 1)" +
				" AND \"func\" NOT LIKE '%function%(% %)%'" +
				" AND \"func\" NOT LIKE '%procedure%(% %)%'" +
				" AND \"func\" NOT LIKE '%CREATE FUNCTION%RETURNS TABLE(% %)%'" +
				// the next names are also not usable so exclude them
				" AND \"name\" NOT LIKE 'querylog_%'" +
				" AND \"name\" NOT IN ('analyze', 'count', 'count_no_nil', 'initializedictionary', 'times')";
				break;
			case 4:	/* time date functions */
				whereClause = part1 +
				"('date', 'time', 'timestamp', 'timetz', 'timestamptz', 'sec_interval', 'month_interval') )";
				break;
			default: /* internal function called with an invalid kind value */
				return "";
		}

		StringBuilder sb = new StringBuilder(400);
		Statement st = null;
		ResultSet rs = null;
		try {
			String select = "SELECT DISTINCT \"name\" FROM \"sys\".\"functions\" " + whereClause + " ORDER BY 1";
			st = getStmt();
			rs = st.executeQuery(select);
			// Fetch the function names and concatenate them into a StringBuffer separated by comma's
			boolean isfirst = true;
			while (rs.next()) {
				String name = rs.getString(1);
				if (name != null) {
					if (isfirst) {
						isfirst = false;
					} else {
						sb.append(",");
					}
					sb.append(name);
				}
			}
		} catch (SQLException e) {
			// ignore
		} finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (SQLException e) { /* ignore */ }
			}
			if (st != null) {
				try {
					 st.close();
				} catch (SQLException e) { /* ignore */ }
			}
		}

		return sb.toString();
	}

	@Override
	public String getNumericFunctions() {
		return getMonetDBSysFunctions(1);
	}

	@Override
	public String getStringFunctions() {
		return getMonetDBSysFunctions(2);
	}

	@Override
	public String getSystemFunctions() {
		return getMonetDBSysFunctions(3);
	}

	@Override
	public String getTimeDateFunctions() {
		return getMonetDBSysFunctions(4);
	}

	/**
	 * This is the string that can be used to escape '_' and '%' in
	 * a search string pattern style catalog search parameters
	 *
	 * @return the string used to escape wildcard characters
	 */
	@Override
	public String getSearchStringEscape() {
		return "\\";
	}

	/**
	 * Get all the "extra" characters that can be used in unquoted
	 * identifier names (those beyond a-zA-Z0-9 and _)
	 * MonetDB has no extras
	 *
	 * @return a string containing the extra characters
	 */
	@Override
	public String getExtraNameCharacters() {
		return "";
	}

	/**
	 * Is "ALTER TABLE" with an add column supported?
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsAlterTableWithAddColumn() {
		return true;
	}

	/**
	 * Is "ALTER TABLE" with a drop column supported?
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsAlterTableWithDropColumn() {
		return true;
	}

	/**
	 * Is column aliasing supported?
	 *
	 * <p>If so, the SQL AS clause can be used to provide names for
	 * computed columns or to provide alias names for columns as
	 * required.  A JDBC Compliant driver always returns true.
	 *
	 * <p>e.g.
	 *
	 * <br><pre>
	 * select count(C) as C_COUNT from T group by C;
	 *
	 * </pre><br>
	 * should return a column named as C_COUNT instead of count(C)
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsColumnAliasing() {
		return true;
	}

	/**
	 * Are concatenations between NULL and non-NULL values NULL? A
	 * JDBC Compliant driver always returns true
	 *
	 * @return true if so
	 */
	@Override
	public boolean nullPlusNonNullIsNull() {
		return true;
	}

	@Override
	public boolean supportsConvert() {
		return false;
	}

	@Override
	public boolean supportsConvert(int fromType, int toType) {
		return false;
	}

	/**
	 * Are table correlation names supported? A JDBC Compliant
	 * driver always returns true.
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsTableCorrelationNames() {
		return true;
	}

	/**
	 * If table correlation names are supported, are they restricted to
	 * be different from the names of the tables?
	 *
	 * @return true if so; false otherwise
	 */
	@Override
	public boolean supportsDifferentTableCorrelationNames() {
		return false;
	}

	/**
	 * Are expressions in "ORDER BY" lists supported?
	 *
	 * e.g. select * from t order by a + b;
	 *
	 * MonetDB does not support this (yet?)
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsExpressionsInOrderBy() {
		return false;
	}

	/**
	 * Can an "ORDER BY" clause use columns not in the SELECT?
	 * MonetDB differs from SQL03 =&gt; true
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsOrderByUnrelated() {
		return true;
	}

	/**
	 * Is some form of "GROUP BY" clause supported?
	 *
	 * @return true since MonetDB supports it
	 */
	@Override
	public boolean supportsGroupBy() {
		return true;
	}

	/**
	 * Can a "GROUP BY" clause use columns not in the SELECT?
	 *
	 * @return true since that also is supported
	 */
	@Override
	public boolean supportsGroupByUnrelated() {
		return true;
	}

	/**
	 * Can a "GROUP BY" clause add columns not in the SELECT provided
	 * it specifies all the columns in the SELECT?
	 *
	 * (MonetDB already supports the more difficult supportsGroupByUnrelated(),
	 * so this is a piece of cake)
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsGroupByBeyondSelect() {
		return true;
	}

	/**
	 * Is the escape character in "LIKE" clauses supported?  A
	 * JDBC compliant driver always returns true.
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsLikeEscapeClause() {
		return true;
	}

	/**
	 * Are multiple ResultSets from a single execute supported?
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsMultipleResultSets() {
		return true;
	}

	/**
	 * Can we have multiple transactions open at once (on different
	 * connections?)
	 * This is the main idea behind the Connection, is it?
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsMultipleTransactions() {
		return true;
	}

	/**
	 * Can columns be defined as non-nullable.	A JDBC Compliant driver
	 * always returns true.
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsNonNullableColumns() {
		return true;
	}

	/**
	 * Does this driver support the minimum ODBC SQL grammar.  This
	 * grammar is defined at:
	 *
	 * http://msdn.microsoft.com/library/default.asp?url=/library/en-us/odbc/htm/odappcpr.asp
	 * From this description, we seem to support the ODBC minimal (Level 0) grammar.
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsMinimumSQLGrammar() {
		return true;
	}

	/**
	 * Does this driver support the Core ODBC SQL grammar.	We need
	 * SQL-92 conformance for this.
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsCoreSQLGrammar() {
		return true;
	}

	/**
	 * Does this driver support the Extended (Level 2) ODBC SQL
	 * grammar.  We don't conform to the Core (Level 1), so we can't
	 * conform to the Extended SQL Grammar.
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsExtendedSQLGrammar() {
		return false;
	}

	/**
	 * Does this driver support the ANSI-92 entry level SQL grammar?
	 * All JDBC Compliant drivers must return true. We should be this
	 * compliant, so let's 'act' like we are.
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsANSI92EntryLevelSQL() {
		return true;
	}

	/**
	 * Does this driver support the ANSI-92 intermediate level SQL
	 * grammar?
	 * probably not
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsANSI92IntermediateSQL() {
		return false;
	}

	/**
	 * Does this driver support the ANSI-92 full SQL grammar?
	 * Would be good if it was like that
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsANSI92FullSQL() {
		return false;
	}

	/**
	 * Is the SQL Integrity Enhancement Facility supported?
	 * Our best guess is that this means support for constraints
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsIntegrityEnhancementFacility() {
		return true;
	}

	/**
	 * Is some form of outer join supported?
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsOuterJoins(){
		return true;
	}

	/**
	 * Are full nexted outer joins supported?
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsFullOuterJoins() {
		return true;
	}

	/**
	 * Is there limited support for outer joins?
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsLimitedOuterJoins() {
		return false;
	}

	/**
	 * What is the database vendor's preferred term for "schema"?
	 * MonetDB uses the term "schema".
	 *
	 * @return the vendor term
	 */
	@Override
	public String getSchemaTerm() {
		return "schema";
	}

	/**
	 * What is the database vendor's preferred term for "procedure"?
	 * Traditionally, "function" has been used.
	 *
	 * @return the vendor term
	 */
	@Override
	public String getProcedureTerm() {
		return "function";
	}

	/**
	 * What is the database vendor's preferred term for "catalog"?
	 * MonetDB doesn't really have them (from driver accessible) but
	 * from the monetdb.conf file the term "database" sounds best
	 *
	 * @return the vendor term
	 */
	@Override
	public String getCatalogTerm() {
		return "database";
	}

	/**
	 * Does a catalog appear at the start of a qualified table name?
	 * (Otherwise it appears at the end).
	 * Currently there is no catalog support at all in MonetDB
	 *
	 * @return true if so
	 */
	@Override
	public boolean isCatalogAtStart() {
		// return true here; we return false for every other catalog function
		// so it won't matter what we return here
		return true;
	}

	/**
	 * What is the Catalog separator.
	 *
	 * @return the catalog separator string
	 */
	@Override
	public String getCatalogSeparator() {
		// Give them something to work with here
		// everything else returns false so it won't matter what we return here
		return ".";
	}

	/**
	 * Can a schema name be used in a data manipulation statement?
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsSchemasInDataManipulation() {
		return true;
	}

	/**
	 * Can a schema name be used in a procedure call statement?
	 * Ohw probably, but I don't know of procedures in MonetDB
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsSchemasInProcedureCalls() {
		return true;
	}

	/**
	 * Can a schema be used in a table definition statement?
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsSchemasInTableDefinitions() {
		return true;
	}

	/**
	 * Can a schema name be used in an index definition statement?
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsSchemasInIndexDefinitions() {
		return true;
	}

	/**
	 * Can a schema name be used in a privilege definition statement?
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsSchemasInPrivilegeDefinitions() {
		return true;
	}

	/**
	 * Can a catalog name be used in a data manipulation statement?
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsCatalogsInDataManipulation() {
		return false;
	}

	/**
	 * Can a catalog name be used in a procedure call statement?
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsCatalogsInProcedureCalls() {
		return false;
	}

	/**
	 * Can a catalog name be used in a table definition statement?
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsCatalogsInTableDefinitions() {
		return false;
	}

	/**
	 * Can a catalog name be used in an index definition?
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsCatalogsInIndexDefinitions() {
		return false;
	}

	/**
	 * Can a catalog name be used in a privilege definition statement?
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsCatalogsInPrivilegeDefinitions() {
		return false;
	}

	/**
	 * MonetDB doesn't support positioned DELETEs I guess
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsPositionedDelete() {
		return false;
	}

	/**
	 * Is positioned UPDATE supported? (same as above)
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsPositionedUpdate() {
		return false;
	}

	/**
	 * Is SELECT FOR UPDATE supported?
	 * My test resulted in a negative answer
	 *
	 * @return true if so; false otherwise
	 */
	@Override
	public boolean supportsSelectForUpdate(){
		return false;
	}

	/**
	 * Are stored procedure calls using the stored procedure escape
	 * syntax supported?
	 *
	 * @return true if so; false otherwise
	 */
	@Override
	public boolean supportsStoredProcedures() {
		return false;
	}

	/**
	 * Are subqueries in comparison expressions supported? A JDBC
	 * Compliant driver always returns true. MonetDB also supports this
	 *
	 * @return true if so; false otherwise
	 */
	@Override
	public boolean supportsSubqueriesInComparisons() {
		return true;
	}

	/**
	 * Are subqueries in 'exists' expressions supported? A JDBC
	 * Compliant driver always returns true.
	 *
	 * @return true if so; false otherwise
	 */
	@Override
	public boolean supportsSubqueriesInExists() {
		return true;
	}

	/**
	 * Are subqueries in 'in' statements supported? A JDBC
	 * Compliant driver always returns true.
	 *
	 * @return true if so; false otherwise
	 */
	@Override
	public boolean supportsSubqueriesInIns() {
		return true;
	}

	/**
	 * Are subqueries in quantified expressions supported? A JDBC
	 * Compliant driver always returns true.
	 *
	 * (No idea what this is, but we support a good deal of
	 * subquerying.)
	 *
	 * @return true if so; false otherwise
	 */
	@Override
	public boolean supportsSubqueriesInQuantifieds() {
		return true;
	}

	/**
	 * Are correlated subqueries supported? A JDBC Compliant driver
	 * always returns true.
	 *
	 * (a.k.a. subselect in from?)
	 *
	 * @return true if so; false otherwise
	 */
	@Override
	public boolean supportsCorrelatedSubqueries() {
		return true;
	}

	/**
	 * Is SQL UNION supported?
	 * since 2004-03-20
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsUnion() {
		return true;
	}

	/**
	 * Is SQL UNION ALL supported?
	 * since 2004-03-20
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsUnionAll() {
		return true;
	}

	/**
	 * ResultSet objects (cursors) are not closed upon explicit or
	 * implicit commit.
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsOpenCursorsAcrossCommit() {
		return true;
	}

	/**
	 * Same as above
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsOpenCursorsAcrossRollback() {
		return true;
	}

	/**
	 * Can statements remain open across commits?  They may, but
	 * this driver cannot guarentee that.  In further reflection.
	 * we are taking a Statement object here, so the answer is
	 * yes, since the Statement is only a vehicle to execute some SQL
	 *
	 * @return true if they always remain open; false otherwise
	 */
	@Override
	public boolean supportsOpenStatementsAcrossCommit() {
		return true;
	}

	/**
	 * Can statements remain open across rollbacks?  They may, but
	 * this driver cannot guarentee that.  In further contemplation,
	 * we are taking a Statement object here, so the answer is yes again.
	 *
	 * @return true if they always remain open; false otherwise
	 */
	@Override
	public boolean supportsOpenStatementsAcrossRollback() {
		return true;
	}

	/**
	 * How many hex characters can you have in an inline binary literal
	 * I honestly wouldn't know...
	 *
	 * @return the max literal length
	 */
	@Override
	public int getMaxBinaryLiteralLength() {
		return 0; // no limit
	}

	/**
	 * What is the maximum length for a character literal
	 * Is there a max?
	 *
	 * @return the max literal length
	 */
	@Override
	public int getMaxCharLiteralLength() {
		return 0; // no limit
	}

	/**
	 * Whats the limit on column name length.
	 * I take some safety here, but it's just a varchar in MonetDB
	 *
	 * @return the maximum column name length
	 */
	@Override
	public int getMaxColumnNameLength() {
		return 1024;
	}

	/**
	 * What is the maximum number of columns in a "GROUP BY" clause?
	 *
	 * @return the max number of columns
	 */
	@Override
	public int getMaxColumnsInGroupBy() {
		return 0; // no limit
	}

	/**
	 * What's the maximum number of columns allowed in an index?
	 *
	 * @return max number of columns
	 */
	@Override
	public int getMaxColumnsInIndex() {
		return 0;	// unlimited I guess
	}

	/**
	 * What's the maximum number of columns in an "ORDER BY clause?
	 *
	 * @return the max columns
	 */
	@Override
	public int getMaxColumnsInOrderBy() {
		return 0; // unlimited I guess
	}

	/**
	 * What is the maximum number of columns in a "SELECT" list?
	 *
	 * @return the max columns
	 */
	@Override
	public int getMaxColumnsInSelect() {
		return 0; // unlimited I guess
	}

	/**
	 * What is the maximum number of columns in a table?
	 * wasn't MonetDB designed for datamining? (= much columns)
	 *
	 * @return the max columns
	 */
	@Override
	public int getMaxColumnsInTable() {
		return 0;
	}

	/**
	 * How many active connections can we have at a time to this
	 * database?  Well, since it depends on Mserver, which just listens
	 * for new connections and creates a new thread for each connection,
	 * this number can be very high, and theoretically till the system
	 * runs out of resources. However, knowing MonetDB is knowing that you
	 * should handle it a little bit with care, so I give a very minimalistic
	 * number here.
	 *
	 * @return the maximum number of connections
	 */
	@Override
	public int getMaxConnections() {
		int max_clients = 16;
		try {
			String max_clients_val = getEnv("max_clients");
			max_clients = Integer.parseInt(max_clients_val);
		} catch (NumberFormatException nfe) { /* ignore */ }
		return max_clients;
	}

	/**
	 * What is the maximum cursor name length
	 * Actually we do not do named cursors, so I keep the value small as
	 * a precaution for maybe the future.
	 *
	 * @return max cursor name length in bytes
	 */
	@Override
	public int getMaxCursorNameLength() {
		return 1024;
	}

	/**
	 * Retrieves the maximum number of bytes for an index, including all
	 * of the parts of the index.
	 *
	 * @return max index length in bytes, which includes the composite
	 *         of all the constituent parts of the index; a result of zero
	 *         means that there is no limit or the limit is not known
	 */
	@Override
	public int getMaxIndexLength() {
		return 0; // I assume it is large, but I don't know
	}

	/**
	 * Retrieves the maximum number of characters that this database
	 * allows in a schema name.
	 *
	 * @return the number of characters or 0 if there is no limit, or the
	 *         limit is unknown.
	 */
	@Override
	public int getMaxSchemaNameLength() {
		return 1024;
	}

	/**
	 * What is the maximum length of a procedure name
	 *
	 * @return the max name length in bytes
	 */
	@Override
	public int getMaxProcedureNameLength() {
		return 1024;
	}

	/**
	 * What is the maximum length of a catalog
	 *
	 * @return the max length
	 */
	@Override
	public int getMaxCatalogNameLength() {
		return 1024;
	}

	/**
	 * What is the maximum length of a single row?
	 *
	 * @return max row size in bytes
	 */
	@Override
	public int getMaxRowSize() {
		return 0;	// very long I hope...
	}

	/**
	 * Did getMaxRowSize() include LONGVARCHAR and LONGVARBINARY
	 * blobs?
	 * Yes I thought so...
	 *
	 * @return true if so
	 */
	@Override
	public boolean doesMaxRowSizeIncludeBlobs() {
		return true;
	}

	/**
	 * What is the maximum length of a SQL statement?
	 * Till a programmer makes a mistake and causes a segmentation fault
	 * on a string overflow...
	 *
	 * @return max length in bytes
	 */
	@Override
	public int getMaxStatementLength() {
		return 0;		// actually whatever fits in size_t
	}

	/**
	 * How many active statements can we have open at one time to
	 * this database?  Basically, since each Statement downloads
	 * the results as the query is executed, we can have many.
	 *
	 * @return the maximum
	 */
	@Override
	public int getMaxStatements() {
		return 0;
	}

	/**
	 * What is the maximum length of a table name
	 *
	 * @return max name length in bytes
	 */
	@Override
	public int getMaxTableNameLength() {
		return 1024;
	}

	/**
	 * What is the maximum number of tables that can be specified
	 * in a SELECT?
	 *
	 * @return the maximum
	 */
	@Override
	public int getMaxTablesInSelect() {
		return 0; // no limit
	}

	/**
	 * What is the maximum length of a user name
	 *
	 * @return the max name length in bytes
	 */
	@Override
	public int getMaxUserNameLength() {
		return 512;
	}

	/**
	 * What is the database's default transaction isolation level?
	 * We only see commited data, nonrepeatable reads and phantom
	 * reads can occur.
	 *
	 * @return the default isolation level
	 * @see Connection
	 */
	@Override
	public int getDefaultTransactionIsolation() {
		return Connection.TRANSACTION_SERIALIZABLE;
	}

	/**
	 * Are transactions supported?	If not, commit and rollback are noops
	 * and the isolation level is TRANSACTION_NONE.  We do support
	 * transactions.
	 *
	 * @return true if transactions are supported
	 */
	@Override
	public boolean supportsTransactions() {
		return true;
	}

	/**
	 * Does the database support the given transaction isolation level?
	 * We only support TRANSACTION_READ_COMMITTED as far as I know
	 *
	 * @param level the values are defined in java.sql.Connection
	 * @return true if so
	 * @see Connection
	 */
	@Override
	public boolean supportsTransactionIsolationLevel(int level) {
		return level == Connection.TRANSACTION_SERIALIZABLE;
	}

	/**
	 * Are both data definition and data manipulation transactions
	 * supported?
	 * Supposedly that data definition is like CREATE or ALTER TABLE
	 * yes it is.
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsDataDefinitionAndDataManipulationTransactions() {
		return true;
	}

	/**
	 * Are only data manipulation statements within a transaction
	 * supported?
	 *
	 * @return true if so
	 */
	@Override
	public boolean supportsDataManipulationTransactionsOnly() {
		return false;
	}

	/**
	 * Does a data definition statement within a transaction force
	 * the transaction to commit?  I think this means something like:
	 *
	 * <p><pre>
	 * CREATE TABLE T (A INT);
	 * INSERT INTO T (A) VALUES (2);
	 * BEGIN;
	 * UPDATE T SET A = A + 1;
	 * CREATE TABLE X (A INT);
	 * SELECT A FROM T INTO X;
	 * COMMIT;
	 * </pre></p>
	 *
	 * does the CREATE TABLE call cause a commit?  The answer is no.
	 *
	 * @return true if so
	 */
	@Override
	public boolean dataDefinitionCausesTransactionCommit() {
		return false;
	}

	/**
	 * Is a data definition statement within a transaction ignored?
	 *
	 * @return true if so
	 */
	@Override
	public boolean dataDefinitionIgnoredInTransactions() {
		return false;
	}

	/**
	 * Get a description of stored procedures available in a catalog
	 * Currently not applicable and not implemented, returns null
	 *
	 * <p>Only procedure descriptions matching the schema and procedure
	 * name criteria are returned.	They are ordered by PROCEDURE_SCHEM
	 * and PROCEDURE_NAME
	 *
	 * <p>Each procedure description has the following columns:
	 * <ol>
	 * <li><b>PROCEDURE_CAT</b> String => procedure catalog (may be null)
	 * <li><b>PROCEDURE_SCHEM</b> String => procedure schema (may be null)
	 * <li><b>PROCEDURE_NAME</b> String => procedure name
	 * <li><b>Field 4</b> reserved (make it null)
	 * <li><b>Field 5</b> reserved (make it null)
	 * <li><b>Field 6</b> reserved (make it null)
	 * <li><b>REMARKS</b> String => explanatory comment on the procedure
	 * <li><b>PROCEDURE_TYPE</b> short => kind of procedure
	 *	<ul>
	 *	  <li> procedureResultUnknown - May return a result
	 *	<li> procedureNoResult - Does not return a result
	 *	<li> procedureReturnsResult - Returns a result
	 *	  </ul>
	 * </ol>
	 *
	 * @param catalog - a catalog name; "" retrieves those without a
	 *	catalog; null means drop catalog name from criteria
	 * @param schemaPattern - a schema name pattern; "" retrieves those
	 *	without a schema - we ignore this parameter
	 * @param procedureNamePattern - a procedure name pattern
	 * @return ResultSet - each row is a procedure description
	 * @throws SQLException if a database access error occurs
	 */
	@Override
	public ResultSet getProcedures(
		String catalog,
		String schemaPattern,
		String procedureNamePattern
	) throws SQLException
	{
		String query =
			"SELECT cast(null AS varchar(1)) AS \"PROCEDURE_CAT\", " +
				"cast(null AS varchar(1)) AS \"PROCEDURE_SCHEM\", " +
				"'' AS \"PROCEDURE_NAME\", cast(null AS varchar(1)) AS \"Field4\", " +
				"cast(null AS varchar(1)) AS \"Field5\", " +
				"cast(null AS varchar(1)) AS \"Field6\", " +
				"'' AS \"REMARKS\", cast(0 AS smallint) AS \"PROCEDURE_TYPE\" " +
			"WHERE 1 = 0";

		return getStmt().executeQuery(query);
	}

	/**
	 * Get a description of a catalog's stored procedure parameters
	 * and result columns.
	 * Currently not applicable and not implemented, returns null
	 *
	 * <p>Only descriptions matching the schema, procedure and parameter
	 * name criteria are returned. They are ordered by PROCEDURE_SCHEM
	 * and PROCEDURE_NAME. Within this, the return value, if any, is
	 * first. Next are the parameter descriptions in call order. The
	 * column descriptions follow in column number order.
	 *
	 * <p>Each row in the ResultSet is a parameter description or column
	 * description with the following fields:
	 * <ol>
	 * <li><b>PROCEDURE_CAT</b> String => procedure catalog (may be null)
	 * <li><b>PROCEDURE_SCHEM</b> String => procedure schema (may be null)
	 * <li><b>PROCEDURE_NAME</b> String => procedure name
	 * <li><b>COLUMN_NAME</b> String => column/parameter name
	 * <li><b>COLUMN_TYPE</b> Short => kind of column/parameter:
	 * <ul><li>procedureColumnUnknown - nobody knows
	 * <li>procedureColumnIn - IN parameter
	 * <li>procedureColumnInOut - INOUT parameter
	 * <li>procedureColumnOut - OUT parameter
	 * <li>procedureColumnReturn - procedure return value
	 * <li>procedureColumnResult - result column in ResultSet
	 * </ul>
	 * <li><b>DATA_TYPE</b> short => SQL type from java.sql.Types
	 * <li><b>TYPE_NAME</b> String => Data source specific type name
	 * <li><b>PRECISION</b> int => precision
	 * <li><b>LENGTH</b> int => length in bytes of data
	 * <li><b>SCALE</b> short => scale
	 * <li><b>RADIX</b> short => radix
	 * <li><b>NULLABLE</b> short => can it contain NULL?
	 * <ul><li>procedureNoNulls - does not allow NULL values
	 * <li>procedureNullable - allows NULL values
	 * <li>procedureNullableUnknown - nullability unknown
	 * <li><b>REMARKS</b> String => comment describing parameter/column
	 * </ol>
	 * @param catalog   not used
	 * @param schemaPattern not used
	 * @param procedureNamePattern a procedure name pattern
	 * @param columnNamePattern a column name pattern
	 * @return each row is a stored procedure parameter or column description
	 * @throws SQLException if a database-access error occurs
	 * @see #getSearchStringEscape
	 */
	@Override
	public ResultSet getProcedureColumns(
		String catalog,
		String schemaPattern,
		String procedureNamePattern,
		String columnNamePattern
	) throws SQLException {
		String query =
			"SELECT cast(null AS varchar(1)) AS \"PROCEDURE_CAT\", " +
				"cast(null AS varchar(1)) AS \"PROCEDURE_SCHEM\", " +
				"'' AS \"PROCEDURE_NAME\", '' AS \"COLUMN_NAME\", " +
				"cast(0 AS smallint) AS \"COLUMN_TYPE\", " +
				"cast(0 AS smallint) AS \"DATA_TYPE\", " +
				"'' AS \"TYPE_NAME\", 0 AS \"PRECISION\", " +
				"0 AS \"LENGTH\", 0 AS \"SCALE\", 0 AS \"RADIX\", " +
				"cast(0 AS smallint) AS \"NULLABLE\", '' AS \"REMARKS\" " +
			"WHERE 1 = 0";

		return getStmt().executeQuery(query);
	}

	//== this is a helper method which does not belong to the interface

	/**
	 * Returns the given string where all slashes and single quotes are
	 * escaped with a slash.
	 *
	 * @param in the string to escape
	 * @return the escaped string
	 */
	private static final String escapeQuotes(String in) {
		return in.replaceAll("\\\\", "\\\\\\\\").replaceAll("'", "\\\\'");
	}

	/**
	 * Returns the given string between two double quotes for usage as
	 * exact column or table name in SQL queries.
	 *
	 * @param in the string to quote
	 * @return the quoted string
	 */
	@SuppressWarnings("unused")
	private static final String dq(String in) {
		return "\"" + in.replaceAll("\\\\", "\\\\\\\\").replaceAll("\"", "\\\\\"") + "\"";
	}

	//== end helper methods

	/**
	 * Get a description of tables available in a catalog.
	 *
	 * <p>Only table descriptions matching the catalog, schema, table
	 * name and type criteria are returned. They are ordered by
	 * TABLE_TYPE, TABLE_SCHEM and TABLE_NAME.
	 *
	 * <p>Each table description has the following columns:
	 *
	 * <ol>
	 * <li><b>TABLE_CAT</b> String => table catalog (may be null)
	 * <li><b>TABLE_SCHEM</b> String => table schema (may be null)
	 * <li><b>TABLE_NAME</b> String => table name
	 * <li><b>TABLE_TYPE</b> String => table type. Typical types are "TABLE",
	 * "VIEW", "SYSTEM TABLE", "GLOBAL TEMPORARY", "LOCAL
	 * TEMPORARY", "ALIAS", "SYNONYM".
	 * <li><b>REMARKS</b> String => explanatory comment on the table
	 * </ol>
	 *
	 * <p>The valid values for the types parameter are:
	 * "TABLE", "INDEX", "SEQUENCE", "VIEW",
	 * "SYSTEM TABLE", "SYSTEM INDEX", "SYSTEM VIEW",
	 * "SYSTEM TOAST TABLE", "SYSTEM TOAST INDEX",
	 * "TEMPORARY TABLE", and "TEMPORARY VIEW"
	 *
	 * @param catalog a catalog name; this parameter is currently ignored
	 * @param schemaPattern a schema name pattern
	 * @param tableNamePattern a table name pattern. For all tables this should be "%"
	 * @param types a list of table types to include; null returns all types;
	 *              this parameter is currently ignored
	 * @return each row is a table description
	 * @throws SQLException if a database-access error occurs.
	 */
	@Override
	public ResultSet getTables(
		String catalog,
		String schemaPattern,
		String tableNamePattern,
		String types[]
	) throws SQLException
	{
		String cat = getEnv("gdk_dbname");
		// as of Jul2015 release the sys.tables.type values (0 through 6) is extended with new values 10, 11, 20, and 30 (for system and temp tables/views).
		// for correct behavior we need to know if the server is using the old (pre Jul2015) or new sys.tables.type values
		boolean preJul2015 = ("11.19.15".compareTo(getDatabaseProductVersion()) >= 0);
		/* for debug: System.out.println("getDatabaseProductVersion() is " + getDatabaseProductVersion() + "  preJul2015 is " + preJul2015); */

		String query =
			"SELECT * FROM ( " +
			"SELECT '" + cat + "' AS \"TABLE_CAT\", \"schemas\".\"name\" AS \"TABLE_SCHEM\", \"tables\".\"name\" AS \"TABLE_NAME\", " +
				"CASE WHEN \"tables\".\"system\" = true AND \"tables\".\"type\" = " + (preJul2015 ? "0" : "10") + " AND \"tables\".\"temporary\" = 0 THEN 'SYSTEM TABLE' " +
				"WHEN \"tables\".\"system\" = true AND \"tables\".\"type\" = " + (preJul2015 ? "1" : "11") + " AND \"tables\".\"temporary\" = 0 THEN 'SYSTEM VIEW' " +
				"WHEN \"tables\".\"system\" = false AND \"tables\".\"type\" = 0 AND \"tables\".\"temporary\" = 0 THEN 'TABLE' " +
				"WHEN \"tables\".\"system\" = false AND \"tables\".\"type\" = 1 AND \"tables\".\"temporary\" = 0 THEN 'VIEW' " +
				"WHEN \"tables\".\"system\" = true AND \"tables\".\"type\" = " + (preJul2015 ? "0" : "20") + " AND \"tables\".\"temporary\" = 1 THEN 'SYSTEM SESSION TABLE' " +
				"WHEN \"tables\".\"system\" = true AND \"tables\".\"type\" = " + (preJul2015 ? "1" : "21") + " AND \"tables\".\"temporary\" = 1 THEN 'SYSTEM SESSION VIEW' " +
				"WHEN \"tables\".\"system\" = false AND \"tables\".\"type\" = " + (preJul2015 ? "0" : "30") + " AND \"tables\".\"temporary\" = 1 THEN 'SESSION TABLE' " +
				"WHEN \"tables\".\"system\" = false AND \"tables\".\"type\" = " + (preJul2015 ? "1" : "31") + " AND \"tables\".\"temporary\" = 1 THEN 'SESSION VIEW' " +
				"END AS \"TABLE_TYPE\", \"tables\".\"query\" AS \"REMARKS\", null AS \"TYPE_CAT\", null AS \"TYPE_SCHEM\", " +
				"null AS \"TYPE_NAME\", 'rowid' AS \"SELF_REFERENCING_COL_NAME\", 'SYSTEM' AS \"REF_GENERATION\" " +
			"FROM \"sys\".\"tables\" AS \"tables\", \"sys\".\"schemas\" AS \"schemas\" WHERE \"tables\".\"schema_id\" = \"schemas\".\"id\" " +
			") AS \"tables\" WHERE 1 = 1 ";

		if (tableNamePattern != null) {
			query += "AND \"TABLE_NAME\" ILIKE '" + escapeQuotes(tableNamePattern) + "' ";
		}
		if (schemaPattern != null) {
			query += "AND \"TABLE_SCHEM\" ILIKE '" + escapeQuotes(schemaPattern) + "' ";
		}
		if (types != null) {
			query += "AND (";
			for (int i = 0; i < types.length; i++) {
				query += (i == 0 ? "" : " OR ") + "\"TABLE_TYPE\" ILIKE '" + escapeQuotes(types[i]) + "'";
			}
			query += ") ";
		}

		query += "ORDER BY \"TABLE_TYPE\", \"TABLE_SCHEM\", \"TABLE_NAME\"";

		return getStmt().executeQuery(query);
	}

	/**
	 * Get the schema names available in this database.  The results
	 * are ordered by schema name.
	 *
	 * <P>The schema column is:
	 *	<OL>
	 *	<LI><B>TABLE_SCHEM</B> String => schema name
	 *	<LI><B>TABLE_CATALOG</B> String => catalog name (may be null)
	 *	</OL>
	 *
	 * @param catalog a catalog name; must match the catalog name as it
	 *        is stored in the database;"" retrieves those without a
	 *        catalog; null means catalog name should not be used to
	 *        narrow down the search.
	 * @param schemaPattern a schema name; must match the schema name as
	 *        it is stored in the database; null means schema name
	 *        should not be used to narrow down the search.
	 * @return ResultSet each row has a single String column that is a
	 *         schema name
	 * @throws SQLException if a database error occurs
	 */
	@Override
	public ResultSet getSchemas(String catalog, String schemaPattern)
		throws SQLException
	{
		String cat = getEnv("gdk_dbname");
		String query =
			"SELECT \"name\" AS \"TABLE_SCHEM\", " +
				"'" + cat + "' AS \"TABLE_CATALOG\", " +
				"'" + cat + "' AS \"TABLE_CAT\" " +	// SquirrelSQL requests this one...
			"FROM \"sys\".\"schemas\" " +
			"WHERE 1 = 1 ";
		if (catalog != null)
			query += "AND '" + cat + "' ILIKE '" + escapeQuotes(catalog) + "' ";
		if (schemaPattern != null)
			query += "AND \"name\" ILIKE '" + escapeQuotes(schemaPattern) + "' ";
		query += "ORDER BY \"TABLE_SCHEM\"";

		return getStmt().executeQuery(query);
	}

	/**
	 * Get the catalog names available in this database.  The results
	 * are ordered by catalog name.
	 *
	 * <P>The catalog column is:
	 *	<OL>
	 *	<LI><B>TABLE_CAT</B> String => catalog name
	 *	</OL>
	 *
	 *
	 * @return ResultSet each row has a single String column that is a
	 *         catalog name
	 * @throws SQLException if a database error occurs
	 */
	@Override
	public ResultSet getCatalogs() throws SQLException {
		/*
		// doing this with a VirtualResultSet is much more efficient...
		String query =
			"SELECT '" + ((String)env.get("gdk_dbname")) + "' AS \"TABLE_CAT\"";
			// some applications need a database or catalog...

		return getStmt().executeQuery(query);
		*/
		
		String[] columns, types;
		String[][] results;

		columns = new String[1];
		types = new String[1];
		results = new String[1][1];

		columns[0] = "TABLE_CAT";
		types[0] = "varchar";
		results[0][0] = getEnv("gdk_dbname");

		try {
			return new MonetVirtualResultSet(columns, types, results);
		} catch (IllegalArgumentException e) {
			throw new SQLException("Internal driver error: " + e.getMessage(), "M0M03");
		}
	}

	/**
	 * Get the table types available in this database.	The results
	 * are ordered by table type.
	 *
	 * <P>The table type is:
	 *	<OL>
	 *	<LI><B>TABLE_TYPE</B> String => table type.  Typical types are "TABLE",
	 *			"VIEW", "SYSTEM TABLE", "GLOBAL TEMPORARY",
	 *			"LOCAL TEMPORARY", "ALIAS", "SYNONYM".
	 *	</OL>
	 *
	 * @return ResultSet each row has a single String column that is a
	 *         table type
	 * @throws SQLException if a database error occurs
	 */
	@Override
	public ResultSet getTableTypes() throws SQLException {
		String[] columns, types;
		String[][] results;

		columns = new String[1];
		columns[0] = "TABLE_TYPE";

		types = new String[1];
		types[0] = "varchar";

		results = new String[8][1];
		// The results need to be ordered by TABLE_TYPE
		results[0][0] = "SESSION TABLE";
		results[1][0] = "SESSION VIEW";
		results[2][0] = "SYSTEM SESSION TABLE";
		results[3][0] = "SYSTEM SESSION VIEW";
		results[4][0] = "SYSTEM TABLE";
		results[5][0] = "SYSTEM VIEW";
		results[6][0] = "TABLE";
		results[7][0] = "VIEW";

		try {
			return new MonetVirtualResultSet(columns, types, results);
		} catch (IllegalArgumentException e) {
			throw new SQLException("Internal driver error: " + e.getMessage(), "M0M03");
		}
	}

	/**
	 * Get a description of table columns available in a catalog.
	 *
	 * <P>Only column descriptions matching the catalog, schema, table
	 * and column name criteria are returned.  They are ordered by
	 * TABLE_SCHEM, TABLE_NAME and ORDINAL_POSITION.
	 *
	 * <P>Each column description has the following columns:
	 *	<OL>
	 *	<LI><B>TABLE_CAT</B> String => table catalog (may be null)
	 *	<LI><B>TABLE_SCHEM</B> String => table schema (may be null)
	 *	<LI><B>TABLE_NAME</B> String => table name
	 *	<LI><B>COLUMN_NAME</B> String => column name
	 *	<LI><B>DATA_TYPE</B> short => SQL type from java.sql.Types
	 *	<LI><B>TYPE_NAME</B> String => Data source dependent type name
	 *	<LI><B>COLUMN_SIZE</B> int => column size.	For char or date
	 *		types this is the maximum number of characters, for numeric or
	 *		decimal types this is precision.
	 *	<LI><B>BUFFER_LENGTH</B> is not used.
	 *	<LI><B>DECIMAL_DIGITS</B> int => the number of fractional digits
	 *	<LI><B>NUM_PREC_RADIX</B> int => Radix (typically either 10 or 2)
	 *	<LI><B>NULLABLE</B> int => is NULL allowed?
	 *		<UL>
	 *		<LI> columnNoNulls - might not allow NULL values
	 *		<LI> columnNullable - definitely allows NULL values
	 *		<LI> columnNullableUnknown - nullability unknown
	 *		</UL>
	 *	<LI><B>REMARKS</B> String => comment describing column (may be null)
	 *	<LI><B>COLUMN_DEF</B> String => default value (may be null)
	 *	<LI><B>SQL_DATA_TYPE</B> int => unused
	 *	<LI><B>SQL_DATETIME_SUB</B> int => unused
	 *	<LI><B>CHAR_OCTET_LENGTH</B> int => for char types the
	 *		 maximum number of bytes in the column
	 *	<LI><B>ORDINAL_POSITION</B> int => index of column in table
	 *		(starting at 1)
	 *	<LI><B>IS_NULLABLE</B> String => "NO" means column definitely
	 *		does not allow NULL values; "YES" means the column might
	 *		allow NULL values.	An empty string means nobody knows.
	 *	</OL>
	 *
	 * @param catalog a catalog name; "" retrieves those without a catalog;
	 *                currently ignored
	 * @param schemaPattern a schema name pattern; "" retrieves those
	 *                      without a schema
	 * @param tableNamePattern a table name pattern
	 * @param columnNamePattern a column name pattern
	 * @return ResultSet each row is a column description
	 * @see #getSearchStringEscape
	 * @throws SQLException if a database error occurs
	 */
	@Override
	public ResultSet getColumns(
		String catalog,
		String schemaPattern,
		String tableNamePattern,
		String columnNamePattern
	) throws SQLException
	{
		String cat = getEnv("gdk_dbname");
		String query =
			"SELECT '" + cat + "' AS \"TABLE_CAT\", \"schemas\".\"name\" AS \"TABLE_SCHEM\", " +
			"\"tables\".\"name\" AS \"TABLE_NAME\", \"columns\".\"name\" AS \"COLUMN_NAME\", " +
			"cast(" + MonetDriver.getSQLTypeMap("\"columns\".\"type\"") + " " +
			"AS smallint) AS \"DATA_TYPE\", " +
			"\"columns\".\"type\" AS \"TYPE_NAME\", " +
			"\"columns\".\"type_digits\" AS \"COLUMN_SIZE\", " +
			"0 AS \"BUFFER_LENGTH\", \"columns\".\"type_scale\" AS \"DECIMAL_DIGITS\", " +
			"10 AS \"NUM_PREC_RADIX\", " +
			"cast(CASE \"null\" " +
				"WHEN true THEN " + ResultSetMetaData.columnNullable + " " +
				"WHEN false THEN " + ResultSetMetaData.columnNoNulls + " " +
			"END AS int) AS \"NULLABLE\", cast(null AS varchar(1)) AS \"REMARKS\", " +
			"\"columns\".\"default\" AS \"COLUMN_DEF\", 0 AS \"SQL_DATA_TYPE\", " +
			"0 AS \"SQL_DATETIME_SUB\", 0 AS \"CHAR_OCTET_LENGTH\", " +
			"\"columns\".\"number\" + 1 AS \"ORDINAL_POSITION\", " +
			"CASE \"null\" " +
				"WHEN true THEN CAST ('YES' AS varchar(3)) " +
				"WHEN false THEN CAST ('NO' AS varchar(3)) " +
			"END AS \"IS_NULLABLE\", " +
			"cast(null AS varchar(1)) AS \"SCOPE_CATALOG\", " +
			"cast(null AS varchar(1)) AS \"SCOPE_SCHEMA\", " +
			"cast(null AS varchar(1)) AS \"SCOPE_TABLE\", " +
			"cast(" + MonetDriver.getJavaType("other") + " AS smallint) AS \"SOURCE_DATA_TYPE\" " +
				"FROM \"sys\".\"columns\" AS \"columns\", " +
					"\"sys\".\"tables\" AS \"tables\", " +
					"\"sys\".\"schemas\" AS \"schemas\" " +
				"WHERE \"columns\".\"table_id\" = \"tables\".\"id\" " +
					"AND \"tables\".\"schema_id\" = \"schemas\".\"id\" ";

		if (schemaPattern != null) {
			query += "AND \"schemas\".\"name\" ILIKE '" + escapeQuotes(schemaPattern) + "' ";
		}
		if (tableNamePattern != null) {
			query += "AND \"tables\".\"name\" ILIKE '" + escapeQuotes(tableNamePattern) + "' ";
		}
		if (columnNamePattern != null) {
			query += "AND \"columns\".\"name\" ILIKE '" + escapeQuotes(columnNamePattern) + "' ";
		}

		query += "ORDER BY \"TABLE_SCHEM\", \"TABLE_NAME\", \"ORDINAL_POSITION\"";

		return getStmt().executeQuery(query);
	}

	/**
	 * Get a description of the access rights for a table's columns.
	 * MonetDB doesn't have this level of access rights.
	 *
	 * <P>Only privileges matching the column name criteria are
	 * returned.  They are ordered by COLUMN_NAME and PRIVILEGE.
	 *
	 * <P>Each privilige description has the following columns:
	 *	<OL>
	 *	<LI><B>TABLE_CAT</B> String => table catalog (may be null)
	 *	<LI><B>TABLE_SCHEM</B> String => table schema (may be null)
	 *	<LI><B>TABLE_NAME</B> String => table name
	 *	<LI><B>COLUMN_NAME</B> String => column name
	 *	<LI><B>GRANTOR</B> => grantor of access (may be null)
	 *	<LI><B>GRANTEE</B> String => grantee of access
	 *	<LI><B>PRIVILEGE</B> String => name of access (SELECT,
	 *		INSERT, UPDATE, REFRENCES, ...)
	 *	<LI><B>IS_GRANTABLE</B> String => "YES" if grantee is permitted
	 *		to grant to others; "NO" if not; null if unknown
	 *	</OL>
	 *
	 * @param catalog a catalog name; "" retrieves those without a catalog
	 * @param schemaPattern a schema name; "" retrieves those without a schema
	 * @param tableNamePattern a table name
	 * @param columnNamePattern a column name pattern
	 * @return ResultSet each row is a column privilege description
	 * @see #getSearchStringEscape
	 * @throws SQLException if a database error occurs
	 */
	@Override
	public ResultSet getColumnPrivileges(
		String catalog,
		String schemaPattern,
		String tableNamePattern,
		String columnNamePattern
	) throws SQLException
	{
		String cat = getEnv("gdk_dbname");
		String query =
		"SELECT '" + cat + "' AS \"TABLE_CAT\", " +
			"\"schemas\".\"name\" AS \"TABLE_SCHEM\", " +
			"\"tables\".\"name\" AS \"TABLE_NAME\", " +
			"\"columns\".\"name\" AS \"COLUMN_NAME\", " +
			"\"grantors\".\"name\" AS \"GRANTOR\", " +
			"\"grantees\".\"name\" AS \"GRANTEE\", " +
			"CASE \"privileges\".\"privileges\" " +
				"WHEN 1 THEN cast('SELECT' AS varchar(7)) " +
				"WHEN 2 THEN cast('UPDATE' AS varchar(7)) " +
				"WHEN 4 THEN cast('INSERT' AS varchar(7)) " +
				"WHEN 8 THEN cast('DELETE' AS varchar(7)) " +
				"WHEN 16 THEN cast('EXECUTE' AS varchar(7)) " +
				"WHEN 32 THEN cast('GRANT' AS varchar(7)) " +
			"END AS \"PRIVILEGE\", " +
			"CASE \"privileges\".\"grantable\" " +
				"WHEN 0 THEN cast('NO' AS varchar(3)) " +
				"WHEN 1 THEN cast('YES' AS varchar(3)) " +
			"END AS \"IS_GRANTABLE\" " +
		"FROM \"sys\".\"privileges\" AS \"privileges\", " +
			"\"sys\".\"tables\" AS \"tables\", " +
			"\"sys\".\"schemas\" AS \"schemas\", " +
			"\"sys\".\"columns\" AS \"columns\", " +
			"\"sys\".\"auths\" AS \"grantors\", " +
			"\"sys\".\"auths\" AS \"grantees\" " +
		"WHERE \"privileges\".\"obj_id\" = \"columns\".\"id\" " +
			"AND \"columns\".\"table_id\" = \"tables\".\"id\" " +
			"AND \"tables\".\"schema_id\" = \"schemas\".\"id\" " +
			"AND \"privileges\".\"auth_id\" = \"grantees\".\"id\" " +
			"AND \"privileges\".\"grantor\" = \"grantors\".\"id\" ";
		
		if (schemaPattern != null) {
			query += "AND \"schemas\".\"name\" ILIKE '" + escapeQuotes(schemaPattern) + "' ";
		}
		if (tableNamePattern != null) {
			query += "AND \"tables\".\"name\" ILIKE '" + escapeQuotes(tableNamePattern) + "' ";
		}
		if (columnNamePattern != null) {
			query += "AND \"columns\".\"name\" ILIKE '" + escapeQuotes(columnNamePattern) + "' ";
		}

		query += "ORDER BY \"COLUMN_NAME\", \"PRIVILEGE\"";

		return getStmt().executeQuery(query);
	}

	/**
	 * Get a description of the access rights for each table available
	 * in a catalog.
	 *
	 * <P>Only privileges matching the schema and table name
	 * criteria are returned.  They are ordered by TABLE_SCHEM,
	 * TABLE_NAME, and PRIVILEGE.
	 *
	 * <P>Each privilege description has the following columns:
	 *	<OL>
	 *	<LI><B>TABLE_CAT</B> String => table catalog (may be null)
	 *	<LI><B>TABLE_SCHEM</B> String => table schema (may be null)
	 *	<LI><B>TABLE_NAME</B> String => table name
	 *	<LI><B>GRANTOR</B> => grantor of access (may be null)
	 *	<LI><B>GRANTEE</B> String => grantee of access
	 *	<LI><B>PRIVILEGE</B> String => name of access (SELECT,
	 *		INSERT, UPDATE, REFRENCES, ...)
	 *	<LI><B>IS_GRANTABLE</B> String => "YES" if grantee is permitted
	 *		to grant to others; "NO" if not; null if unknown
	 *	</OL>
	 *
	 * @param catalog a catalog name; "" retrieves those without a catalog
	 * @param schemaPattern a schema name pattern; "" retrieves those
	 *                      without a schema
	 * @param tableNamePattern a table name pattern
	 * @return ResultSet each row is a table privilege description
	 * @see #getSearchStringEscape
	 * @throws SQLException if a database error occurs
	 */
	@Override
	public ResultSet getTablePrivileges(
		String catalog,
		String schemaPattern,
		String tableNamePattern
	) throws SQLException
	{
		String cat = getEnv("gdk_dbname");
		String query =
		"SELECT '" + cat + "' AS \"TABLE_CAT\", " +
			"\"schemas\".\"name\" AS \"TABLE_SCHEM\", " +
			"\"tables\".\"name\" AS \"TABLE_NAME\", " +
			"\"grantors\".\"name\" AS \"GRANTOR\", " +
			"\"grantees\".\"name\" AS \"GRANTEE\", " +
			"CASE \"privileges\".\"privileges\" " +
				"WHEN 1 THEN cast('SELECT' AS varchar(7)) " +
				"WHEN 2 THEN cast('UPDATE' AS varchar(7)) " +
				"WHEN 4 THEN cast('INSERT' AS varchar(7)) " +
				"WHEN 8 THEN cast('DELETE' AS varchar(7)) " +
				"WHEN 16 THEN cast('EXECUTE' AS varchar(7)) " +
				"WHEN 32 THEN cast('GRANT' AS varchar(7)) " +
			"END AS \"PRIVILEGE\", " +
			"CASE \"privileges\".\"grantable\" " +
				"WHEN 0 THEN cast('NO' AS varchar(3)) " +
				"WHEN 1 THEN cast('YES' AS varchar(3)) " +
			"END AS \"IS_GRANTABLE\" " +
		"FROM \"sys\".\"privileges\" AS \"privileges\", " +
			"\"sys\".\"tables\" AS \"tables\", " +
			"\"sys\".\"schemas\" AS \"schemas\", " +
			"\"sys\".\"auths\" AS \"grantors\", " +
			"\"sys\".\"auths\" AS \"grantees\" " +
		"WHERE \"privileges\".\"obj_id\" = \"tables\".\"id\" " +
			"AND \"tables\".\"schema_id\" = \"schemas\".\"id\" " +
			"AND \"privileges\".\"auth_id\" = \"grantees\".\"id\" " +
			"AND \"privileges\".\"grantor\" = \"grantors\".\"id\" ";
		
		if (schemaPattern != null) {
			query += "AND \"schemas\".\"name\" ILIKE '" + escapeQuotes(schemaPattern) + "' ";
		}
		if (tableNamePattern != null) {
			query += "AND \"tables\".\"name\" ILIKE '" + escapeQuotes(tableNamePattern) + "' ";
		}

		query += "ORDER BY \"TABLE_SCHEM\", \"TABLE_NAME\", \"PRIVILEGE\"";

		return getStmt().executeQuery(query);
	}

	/**
	 * Get a description of a table's optimal set of columns that
	 * uniquely identifies a row. They are ordered by SCOPE.
	 *
	 * <P>Each column description has the following columns:
	 *	<OL>
	 *	<LI><B>SCOPE</B> short => actual scope of result
	 *		<UL>
	 *		<LI> bestRowTemporary - very temporary, while using row
	 *		<LI> bestRowTransaction - valid for remainder of current transaction
	 *		<LI> bestRowSession - valid for remainder of current session
	 *		</UL>
	 *	<LI><B>COLUMN_NAME</B> String => column name
	 *	<LI><B>DATA_TYPE</B> short => SQL data type from java.sql.Types
	 *	<LI><B>TYPE_NAME</B> String => Data source dependent type name
	 *	<LI><B>COLUMN_SIZE</B> int => precision
	 *	<LI><B>BUFFER_LENGTH</B> int => not used
	 *	<LI><B>DECIMAL_DIGITS</B> short  => scale
	 *	<LI><B>PSEUDO_COLUMN</B> short => is this a pseudo column
	 *		like an Oracle ROWID
	 *		<UL>
	 *		<LI> bestRowUnknown - may or may not be pseudo column
	 *		<LI> bestRowNotPseudo - is NOT a pseudo column
	 *		<LI> bestRowPseudo - is a pseudo column
	 *		</UL>
	 *	</OL>
	 *
	 * @param catalog a catalog name; "" retrieves those without a catalog
	 * @param schema a schema name; "" retrieves those without a schema
	 * @param table a table name
	 * @param scope the scope of interest; use same values as SCOPE
	 * @param nullable include columns that are nullable?
	 * @return ResultSet each row is a column description
	 * @throws SQLException if a database error occurs
	 */
	@Override
	public ResultSet getBestRowIdentifier(
		String catalog,
		String schema,
		String table,
		int scope,
		boolean nullable
	) throws SQLException
	{
		String query = "SELECT " + DatabaseMetaData.bestRowSession + " AS \"SCOPE\", " +
			"\"columns\".\"name\" AS \"COLUMN_NAME\", " +
			MonetDriver.getSQLTypeMap("\"columns\".\"type\"") + " AS \"DATA_TYPE\", " +
			"\"columns\".\"type\" AS \"TYPE_NAME\", " +
			"\"columns\".\"type_digits\" AS \"COLUMN_SIZE\", 0 AS \"BUFFER_LENGTH\", " +
			"\"columns\".\"type_scale\" AS \"DECIMAL_DIGITS\", " +
			DatabaseMetaData.bestRowNotPseudo + " AS \"PSEUDO_COLUMN\" " +
				"FROM \"sys\".\"keys\" AS \"keys\", " +
					"\"sys\".\"objects\" AS \"objects\", " +
					"\"sys\".\"columns\" AS \"columns\", " +
					"\"sys\".\"tables\" AS \"tables\", " +
					"\"sys\".\"schemas\" AS \"schemas\" " +
				"WHERE \"keys\".\"id\" = \"objects\".\"id\" " +
					"AND \"keys\".\"table_id\" = \"tables\".\"id\" " +
					"AND \"keys\".\"table_id\" = \"columns\".\"table_id\" " +
					"AND \"objects\".\"name\" = \"columns\".\"name\" " +
					"AND \"tables\".\"schema_id\" = \"schemas\".\"id\" " +
					"AND \"keys\".\"type\" IN (0, 1) ";	// only primary keys (type = 0) and unique keys (type = 1), not fkeys (type = 2)

		if (schema != null) {
			query += "AND \"schemas\".\"name\" ILIKE '" + escapeQuotes(schema) + "' ";
		}
		if (table != null) {
			query += "AND \"tables\".\"name\" ILIKE '" + escapeQuotes(table) + "' ";
		}
		if (!nullable) {
			query += "AND \"columns\".\"null\" = false ";
		}
		query += "ORDER BY \"keys\".\"type\"";

		return getStmt().executeQuery(query);
	}

	/**
	 * Get a description of a table's columns that are automatically
	 * updated when any value in a row is updated.	They are
	 * unordered.
	 *
	 * <P>Each column description has the following columns:
	 *	<OL>
	 *	<LI><B>SCOPE</B> short => is not used
	 *	<LI><B>COLUMN_NAME</B> String => column name
	 *	<LI><B>DATA_TYPE</B> short => SQL data type from java.sql.Types
	 *	<LI><B>TYPE_NAME</B> String => Data source dependent type name
	 *	<LI><B>COLUMN_SIZE</B> int => precision
	 *	<LI><B>BUFFER_LENGTH</B> int => length of column value in bytes
	 *	<LI><B>DECIMAL_DIGITS</B> short  => scale
	 *	<LI><B>PSEUDO_COLUMN</B> short => is this a pseudo column
	 *		like an Oracle ROWID
	 *		<UL>
	 *		<LI> versionColumnUnknown - may or may not be pseudo column
	 *		<LI> versionColumnNotPseudo - is NOT a pseudo column
	 *		<LI> versionColumnPseudo - is a pseudo column
	 *		</UL>
	 *	</OL>
	 *
	 * @param catalog a catalog name; "" retrieves those without a catalog
	 * @param schema a schema name; "" retrieves those without a schema
	 * @param table a table name
	 * @return ResultSet each row is a column description
	 * @throws SQLException if a database error occurs
	 */
	@Override
	public ResultSet getVersionColumns(
		String catalog,
		String schema,
		String table
	) throws SQLException
	{
		// I don't know of columns which update themselves, except maybe on the
		// system tables

		final String columns[] = {
			"SCOPE", "COLUMN_NAME", "DATA_TYPE", "TYPE_NAME", "COLUMN_SIZE",
			"BUFFER_LENGTH", "DECIMAL_DIGITS", "PSEUDO_COLUMN"
		};

		final String types[] = {
			"int", "varchar", "int", "varchar", "int",
			"int", "int", "int"
		};

		final String[][] results = new String[0][columns.length];

		try {
			return new MonetVirtualResultSet(columns, types, results);
		} catch (IllegalArgumentException e) {
			throw new SQLException("Internal driver error: " + e.getMessage(), "M0M03");
		}
	}

	/**
	 * Get a description of a table's primary key columns.  They
	 * are ordered by COLUMN_NAME.
	 *
	 * <P>Each column description has the following columns:
	 *	<OL>
	 *	<LI><B>TABLE_CAT</B> String => table catalog (may be null)
	 *	<LI><B>TABLE_SCHEM</B> String => table schema (may be null)
	 *	<LI><B>TABLE_NAME</B> String => table name
	 *	<LI><B>COLUMN_NAME</B> String => column name
	 *	<LI><B>KEY_SEQ</B> short => sequence number within primary key
	 *	<LI><B>PK_NAME</B> String => primary key name (may be null)
	 *	</OL>
	 *
	 * @param catalog a catalog name; "" retrieves those without a catalog
	 * @param schema a schema name pattern; "" retrieves those
	 * without a schema
	 * @param table a table name
	 * @return ResultSet each row is a primary key column description
	 * @throws SQLException if a database error occurs
	 */
	@Override
	public ResultSet getPrimaryKeys(
		String catalog,
		String schema,
		String table
	) throws SQLException
	{
		String query =
		"SELECT cast(null AS varchar(1)) AS \"TABLE_CAT\", " +
			"\"schemas\".\"name\" AS \"TABLE_SCHEM\", " +
			"\"tables\".\"name\" AS \"TABLE_NAME\", " +
			"\"objects\".\"name\" AS \"COLUMN_NAME\", " +
			"\"objects\".\"nr\" AS \"KEY_SEQ\", \"keys\".\"name\" AS \"PK_NAME\" " +
		"FROM \"sys\".\"keys\" AS \"keys\", " +
			"\"sys\".\"objects\" AS \"objects\", " +
			"\"sys\".\"tables\" AS \"tables\", " +
			"\"sys\".\"schemas\" AS \"schemas\" " +
		"WHERE \"keys\".\"id\" = \"objects\".\"id\" " +
			"AND \"keys\".\"table_id\" = \"tables\".\"id\" " +
			"AND \"tables\".\"schema_id\" = \"schemas\".\"id\" " +
			"AND \"keys\".\"type\" = 0 ";

		if (schema != null) {
			query += "AND \"schemas\".\"name\" ILIKE '" + escapeQuotes(schema) + "' ";
		}
		if (table != null) {
			query += "AND \"tables\".\"name\" ILIKE '" + escapeQuotes(table) + "' ";
		}

		query += "ORDER BY \"COLUMN_NAME\"";

		return getStmt().executeQuery(query);
	}

	final static String keyQuery1 =
		"' AS \"PKTABLE_CAT\", \"pkschema\".\"name\" AS \"PKTABLE_SCHEM\", " +
		"\"pktable\".\"name\" AS \"PKTABLE_NAME\", \"pkkeycol\".\"name\" AS \"PKCOLUMN_NAME\", '";
	final static String keyQuery2 =
		"' AS \"FKTABLE_CAT\", \"fkschema\".\"name\" AS \"FKTABLE_SCHEM\", " +
		"\"fktable\".\"name\" AS \"FKTABLE_NAME\", \"fkkeycol\".\"name\" AS \"FKCOLUMN_NAME\", " +
		"\"pkkeycol\".\"nr\" AS \"KEY_SEQ\", " +
		DatabaseMetaData.importedKeyNoAction + " AS \"UPDATE_RULE\", " +
		"" + DatabaseMetaData.importedKeyNoAction + " AS \"DELETE_RULE\", " +
		"\"fkkey\".\"name\" AS \"FK_NAME\", \"pkkey\".\"name\" AS \"PK_NAME\", " +
		"" + DatabaseMetaData.importedKeyNotDeferrable + " AS \"DEFERRABILITY\" " +
			"FROM \"sys\".\"keys\" AS \"fkkey\", \"sys\".\"keys\" AS \"pkkey\", \"sys\".\"objects\" AS \"fkkeycol\", " +
			"\"sys\".\"objects\" AS \"pkkeycol\", \"sys\".\"tables\" AS \"fktable\", \"sys\".\"tables\" AS \"pktable\", " +
			"\"sys\".\"schemas\" AS \"fkschema\", \"sys\".\"schemas\" AS \"pkschema\" " +
			"WHERE \"fktable\".\"id\" = \"fkkey\".\"table_id\" AND \"pktable\".\"id\" = \"pkkey\".\"table_id\" AND " +
			"\"fkkey\".\"id\" = \"fkkeycol\".\"id\" AND \"pkkey\".\"id\" = \"pkkeycol\".\"id\" AND " +
			"\"fkschema\".\"id\" = \"fktable\".\"schema_id\" AND \"pkschema\".\"id\" = \"pktable\".\"schema_id\" AND " +
			"\"fkkey\".\"rkey\" > -1 AND \"fkkey\".\"rkey\" = \"pkkey\".\"id\" AND " +
			"\"fkkeycol\".\"nr\" = \"pkkeycol\".\"nr\" ";

	static String keyQuery(String cat) {
		// FIXME: cat should probably be single-quote-escaped
		return "SELECT '" + cat + keyQuery1 + cat + keyQuery2;
	}

	/**
	 * Get a description of the primary key columns that are
	 * referenced by a table's foreign key columns (the primary keys
	 * imported by a table).  They are ordered by PKTABLE_CAT,
	 * PKTABLE_SCHEM, PKTABLE_NAME, and KEY_SEQ.
	 *
	 * <P>Each primary key column description has the following columns:
	 *	<OL>
	 *	<LI><B>PKTABLE_CAT</B> String => primary key table catalog
	 *		being imported (may be null)
	 *	<LI><B>PKTABLE_SCHEM</B> String => primary key table schema
	 *		being imported (may be null)
	 *	<LI><B>PKTABLE_NAME</B> String => primary key table name
	 *		being imported
	 *	<LI><B>PKCOLUMN_NAME</B> String => primary key column name
	 *		being imported
	 *	<LI><B>FKTABLE_CAT</B> String => foreign key table catalog (may be null)
	 *	<LI><B>FKTABLE_SCHEM</B> String => foreign key table schema (may be null)
	 *	<LI><B>FKTABLE_NAME</B> String => foreign key table name
	 *	<LI><B>FKCOLUMN_NAME</B> String => foreign key column name
	 *	<LI><B>KEY_SEQ</B> short => sequence number within foreign key
	 *	<LI><B>UPDATE_RULE</B> short => What happens to
	 *		 foreign key when primary is updated:
	 *		<UL>
	 *		<LI> importedKeyCascade - change imported key to agree
	 *				 with primary key update
	 *		<LI> importedKeyRestrict - do not allow update of primary
	 *				 key if it has been imported
	 *		<LI> importedKeySetNull - change imported key to NULL if
	 *				 its primary key has been updated
	 *		</UL>
	 *	<LI><B>DELETE_RULE</B> short => What happens to
	 *		the foreign key when primary is deleted.
	 *		<UL>
	 *		<LI> importedKeyCascade - delete rows that import a deleted key
	 *		<LI> importedKeyRestrict - do not allow delete of primary
	 *				 key if it has been imported
	 *		<LI> importedKeySetNull - change imported key to NULL if
	 *				 its primary key has been deleted
	 *		</UL>
	 *	<LI><B>FK_NAME</B> String => foreign key name (may be null)
	 *	<LI><B>PK_NAME</B> String => primary key name (may be null)
	 *	</OL>
	 *
	 * @param catalog a catalog name; "" retrieves those without a catalog
	 * @param schema a schema name pattern; "" retrieves those
	 * without a schema
	 * @param table a table name
	 * @return ResultSet each row is a primary key column description
	 * @see #getExportedKeys
	 * @throws SQLException if a database error occurs
	 */
	@Override
	public ResultSet getImportedKeys(String catalog, String schema, String table)
		throws SQLException
	{
		String cat = getEnv("gdk_dbname");
		String query = keyQuery(cat);

		if (schema != null) {
			query += "AND \"fkschema\".\"name\" ILIKE '" + escapeQuotes(schema) + "' ";
		}
		if (table != null) {
			query += "AND \"fktable\".\"name\" ILIKE '" + escapeQuotes(table) + "' ";
		}

		query += "ORDER BY \"PKTABLE_CAT\", \"PKTABLE_SCHEM\", \"PKTABLE_NAME\", \"PK_NAME\", \"KEY_SEQ\"";

		return getStmt().executeQuery(query);
	}

	/**
	 * Get a description of a foreign key columns that reference a
	 * table's primary key columns (the foreign keys exported by a
	 * table).	They are ordered by FKTABLE_CAT, FKTABLE_SCHEM,
	 * FKTABLE_NAME, and KEY_SEQ.
	 *
	 * <P>Each foreign key column description has the following columns:
	 *	<OL>
	 *	<LI><B>PKTABLE_CAT</B> String => primary key table catalog (may be null)
	 *	<LI><B>PKTABLE_SCHEM</B> String => primary key table schema (may be null)
	 *	<LI><B>PKTABLE_NAME</B> String => primary key table name
	 *	<LI><B>PKCOLUMN_NAME</B> String => primary key column name
	 *	<LI><B>FKTABLE_CAT</B> String => foreign key table catalog (may be null)
	 *		being exported (may be null)
	 *	<LI><B>FKTABLE_SCHEM</B> String => foreign key table schema (may be null)
	 *		being exported (may be null)
	 *	<LI><B>FKTABLE_NAME</B> String => foreign key table name
	 *		being exported
	 *	<LI><B>FKCOLUMN_NAME</B> String => foreign key column name
	 *		being exported
	 *	<LI><B>KEY_SEQ</B> short => sequence number within foreign key
	 *	<LI><B>UPDATE_RULE</B> short => What happens to
	 *		 foreign key when primary is updated:
	 *		<UL>
	 *		<LI> importedKeyCascade - change imported key to agree
	 *				 with primary key update
	 *		<LI> importedKeyRestrict - do not allow update of primary
	 *				 key if it has been imported
	 *		<LI> importedKeySetNull - change imported key to NULL if
	 *				 its primary key has been updated
	 *		</UL>
	 *	<LI><B>DELETE_RULE</B> short => What happens to
	 *		the foreign key when primary is deleted.
	 *		<UL>
	 *		<LI> importedKeyCascade - delete rows that import a deleted key
	 *		<LI> importedKeyRestrict - do not allow delete of primary
	 *				 key if it has been imported
	 *		<LI> importedKeySetNull - change imported key to NULL if
	 *				 its primary key has been deleted
	 *		</UL>
	 *	<LI><B>FK_NAME</B> String => foreign key identifier (may be null)
	 *	<LI><B>PK_NAME</B> String => primary key identifier (may be null)
	 *	</OL>
	 *
	 * @param catalog a catalog name; "" retrieves those without a catalog
	 * @param schema a schema name pattern; "" retrieves those
	 * without a schema
	 * @param table a table name
	 * @return ResultSet each row is a foreign key column description
	 * @see #getImportedKeys
	 * @throws SQLException if a database error occurs
	 */
	@Override
	public ResultSet getExportedKeys(String catalog, String schema, String table)
		throws SQLException
	{
		String cat = getEnv("gdk_dbname");
		String query = keyQuery(cat);

		if (schema != null) {
			query += "AND \"pkschema\".\"name\" ILIKE '" + escapeQuotes(schema) + "' ";
		}
		if (table != null) {
			query += "AND \"pktable\".\"name\" ILIKE '" + escapeQuotes(table) + "' ";
		}

		query += "ORDER BY \"FKTABLE_CAT\", \"FKTABLE_SCHEM\", \"FKTABLE_NAME\", \"FK_NAME\", \"KEY_SEQ\"";

		return getStmt().executeQuery(query);
	}

	/**
	 * Get a description of the foreign key columns in the foreign key
	 * table that reference the primary key columns of the primary key
	 * table. (describe how one table imports another's key) This
	 * should normally return a single foreign key/primary key pair
	 * (most tables only import a foreign key from a table once.)  They
	 * are ordered by FKTABLE_CAT, FKTABLE_SCHEM, FKTABLE_NAME, and
	 * KEY_SEQ.
	 *
	 * This method is currently unimplemented.
	 *
	 * <P>Each foreign key column description has the following columns:
	 *	<OL>
	 *	<LI><B>PKTABLE_CAT</B> String => primary key table catalog (may be null)
	 *	<LI><B>PKTABLE_SCHEM</B> String => primary key table schema (may be null)
	 *	<LI><B>PKTABLE_NAME</B> String => primary key table name
	 *	<LI><B>PKCOLUMN_NAME</B> String => primary key column name
	 *	<LI><B>FKTABLE_CAT</B> String => foreign key table catalog (may be null)
	 *		being exported (may be null)
	 *	<LI><B>FKTABLE_SCHEM</B> String => foreign key table schema (may be null)
	 *		being exported (may be null)
	 *	<LI><B>FKTABLE_NAME</B> String => foreign key table name
	 *		being exported
	 *	<LI><B>FKCOLUMN_NAME</B> String => foreign key column name
	 *		being exported
	 *	<LI><B>KEY_SEQ</B> short => sequence number within foreign key
	 *	<LI><B>UPDATE_RULE</B> short => What happens to
	 *		 foreign key when primary is updated:
	 *		<UL>
	 *		<LI> importedKeyCascade - change imported key to agree
	 *				 with primary key update
	 *		<LI> importedKeyRestrict - do not allow update of primary
	 *				 key if it has been imported
	 *		<LI> importedKeySetNull - change imported key to NULL if
	 *				 its primary key has been updated
	 *		</UL>
	 *	<LI><B>DELETE_RULE</B> short => What happens to
	 *		the foreign key when primary is deleted.
	 *		<UL>
	 *		<LI> importedKeyCascade - delete rows that import a deleted key
	 *		<LI> importedKeyRestrict - do not allow delete of primary
	 *				 key if it has been imported
	 *		<LI> importedKeySetNull - change imported key to NULL if
	 *				 its primary key has been deleted
	 *		</UL>
	 *	<LI><B>FK_NAME</B> String => foreign key identifier (may be null)
	 *	<LI><B>PK_NAME</B> String => primary key identifier (may be null)
	 *	</OL>
	 *
	 * @param pcatalog primary key catalog name; "" retrieves those without a catalog
	 * @param pschema primary key schema name pattern; "" retrieves those
	 * without a schema
	 * @param ptable primary key table name
	 * @param fcatalog foreign key catalog name; "" retrieves those without a catalog
	 * @param fschema foreign key schema name pattern; "" retrieves those
	 * without a schema
	 * @param ftable koreign key table name
	 * @return ResultSet each row is a foreign key column description
	 * @throws SQLException if a database error occurs
	 * @see #getImportedKeys
	 */
	@Override
	public ResultSet getCrossReference(
		String pcatalog,
		String pschema,
		String ptable,
		String fcatalog,
		String fschema,
		String ftable
	) throws SQLException
	{
		String cat = getEnv("gdk_dbname");
		String query = keyQuery(cat);

		if (pschema != null) {
			query += "AND \"pkschema\".\"name\" ILIKE '" + escapeQuotes(pschema) + "' ";
		}
		if (ptable != null) {
			query += "AND \"pktable\".\"name\" ILIKE '" + escapeQuotes(ptable) + "' ";
		}
		if (fschema != null) {
			query += "AND \"fkschema\".\"name\" ILIKE '" + escapeQuotes(fschema) + "' ";
		}
		if (ftable != null) {
			query += "AND \"fktable\".\"name\" ILIKE '" + escapeQuotes(ftable) + "' ";
		}

		query += "ORDER BY \"FKTABLE_CAT\", \"FKTABLE_SCHEM\", \"FKTABLE_NAME\", \"FK_NAME\", \"KEY_SEQ\"";

		return getStmt().executeQuery(query);
	}

	/**
	 * Get a description of all the standard SQL types supported by
	 * this database. They are ordered by DATA_TYPE and then by how
	 * closely the data type maps to the corresponding JDBC SQL type.
	 *
	 * <P>Each type description has the following columns:
	 *	<OL>
	 *	<LI><B>TYPE_NAME</B> String => Type name
	 *	<LI><B>DATA_TYPE</B> short => SQL data type from java.sql.Types
	 *	<LI><B>PRECISION</B> int => maximum precision
	 *	<LI><B>LITERAL_PREFIX</B> String => prefix used to quote a literal
	 *		(may be null)
	 *	<LI><B>LITERAL_SUFFIX</B> String => suffix used to quote a literal
	 *  (may be null)
	 *	<LI><B>CREATE_PARAMS</B> String => parameters used in creating
	 *		the type (may be null)
	 *	<LI><B>NULLABLE</B> short => can you use NULL for this type?
	 *		<UL>
	 *		<LI> typeNoNulls - does not allow NULL values
	 *		<LI> typeNullable - allows NULL values
	 *		<LI> typeNullableUnknown - nullability unknown
	 *		</UL>
	 *	<LI><B>CASE_SENSITIVE</B> boolean=> is it case sensitive?
	 *	<LI><B>SEARCHABLE</B> short => can you use "WHERE" based on this type:
	 *		<UL>
	 *		<LI> typePredNone - No support
	 *		<LI> typePredChar - Only supported with WHERE .. LIKE
	 *		<LI> typePredBasic - Supported except for WHERE .. LIKE
	 *		<LI> typeSearchable - Supported for all WHERE ..
	 *		</UL>
	 *	<LI><B>UNSIGNED_ATTRIBUTE</B> boolean => is it unsigned?
	 *	<LI><B>FIXED_PREC_SCALE</B> boolean => can it be a money value?
	 *	<LI><B>AUTO_INCREMENT</B> boolean => can it be used for an
	 *		auto-increment value?
	 *	<LI><B>LOCAL_TYPE_NAME</B> String => localized version of type name
	 *		(may be null)
	 *	<LI><B>MINIMUM_SCALE</B> short => minimum scale supported
	 *	<LI><B>MAXIMUM_SCALE</B> short => maximum scale supported
	 *	<LI><B>SQL_DATA_TYPE</B> int => unused
	 *	<LI><B>SQL_DATETIME_SUB</B> int => unused
	 *	<LI><B>NUM_PREC_RADIX</B> int => usually 2 or 10
	 *	</OL>
	 *
	 * @return ResultSet each row is a SQL type description
	 * @throws Exception if the developer made a Boo-Boo
	 */
	@Override
	public ResultSet getTypeInfo() throws SQLException {
/*
# id,   	systemname, sqlname,        digits, scale,  radix,  module_id # name
[ 1004729,  "bat",      "table",        0,      0,      0,      0       ]
[ 1004730,  "bit",      "boolean",      0,      0,      2,      0       ]
[ 1004731,  "str",      "char",         0,      0,      0,      0       ]
[ 1004732,  "str",      "varchar",      0,      0,      0,      0       ]
[ 1004733,  "str",      "clob",         0,      0,      0,      0       ]
[ 1004734,  "oid",      "oid",          9,      0,      10,     0       ]
...
*/
		String query =
			"SELECT \"sqlname\" AS \"TYPE_NAME\", " +
				"cast(" + MonetDriver.getSQLTypeMap("\"sqlname\"") + " " +
				"AS smallint) AS \"DATA_TYPE\", " +
				"\"digits\" AS \"PRECISION\", " +
				"cast(CASE WHEN \"systemname\" = 'str' THEN cast('" +
				escapeQuotes("'") + "' AS char) " +
					"ELSE cast(NULL AS char) END AS char) AS \"LITERAL_PREFIX\", " +
				"cast(CASE WHEN \"systemname\" = 'str' THEN cast('" +
				escapeQuotes("'") + "' AS char) " +
					"ELSE cast(NULL AS char) END AS char) AS \"LITERAL_SUFFIX\", " +
				"cast(NULL AS varchar(1)) AS \"CREATE_PARAMS\", " +
				"cast(CASE WHEN \"systemname\" = 'oid' THEN " + DatabaseMetaData.typeNoNulls + " " +
					"ELSE " + DatabaseMetaData.typeNullable + " END AS smallint) AS \"NULLABLE\", " +
				"false AS \"CASE_SENSITIVE\", " +
				"cast(CASE \"systemname\" WHEN 'table' THEN " + DatabaseMetaData.typePredNone + " " +
					"WHEN 'str' THEN " + DatabaseMetaData.typePredChar + " " +
					"WHEN 'sqlblob' THEN " + DatabaseMetaData.typePredChar + " " +
					"ELSE " + DatabaseMetaData.typePredBasic + " " +
				"END AS smallint) AS SEARCHABLE, " +
				"false AS \"UNSIGNED_ATTRIBUTE\", " +
				"CASE \"sqlname\" WHEN 'decimal' THEN true " +
					"ELSE false END AS \"FIXED_PREC_SCALE\", " +
				"false AS \"AUTO_INCREMENT\", " +
				"\"systemname\" AS \"LOCAL_TYPE_NAME\", " + 
				"0 AS \"MINIMUM_SCALE\", " +
				"18 AS \"MAXIMUM SCALE\", " +
				"cast(NULL AS int) AS \"SQL_DATA_TYPE\", " +
				"cast(NULL AS int) AS \"SQL_DATETIME_SUB\", " +
				"\"radix\" AS \"NUM_PREC_RADIX\" " +
			"FROM \"sys\".\"types\"";
			
		return getStmt().executeQuery(query);
	}

	/**
	 * Get a description of a table's indices and statistics. They are
	 * ordered by NON_UNIQUE, TYPE, INDEX_NAME, and ORDINAL_POSITION.
	 *
	 * <P>Each index column description has the following columns:
	 *	<OL>
	 *	<LI><B>TABLE_CAT</B> String => table catalog (may be null)
	 *	<LI><B>TABLE_SCHEM</B> String => table schema (may be null)
	 *	<LI><B>TABLE_NAME</B> String => table name
	 *	<LI><B>NON_UNIQUE</B> boolean => Can index values be non-unique?
	 *		false when TYPE is tableIndexStatistic
	 *	<LI><B>INDEX_QUALIFIER</B> String => index catalog (may be null);
	 *		null when TYPE is tableIndexStatistic
	 *	<LI><B>INDEX_NAME</B> String => index name; null when TYPE is
	 *		tableIndexStatistic
	 *	<LI><B>TYPE</B> short => index type:
	 *		<UL>
	 *		<LI> tableIndexStatistic - this identifies table statistics that are
	 *			 returned in conjuction with a table's index descriptions
	 *		<LI> tableIndexClustered - this is a clustered index
	 *		<LI> tableIndexHashed - this is a hashed index
	 *		<LI> tableIndexOther - this is some other style of index
	 *		</UL>
	 *	<LI><B>ORDINAL_POSITION</B> short => column sequence number
	 *		within index; zero when TYPE is tableIndexStatistic
	 *	<LI><B>COLUMN_NAME</B> String => column name; null when TYPE is
	 *		tableIndexStatistic
	 *	<LI><B>ASC_OR_DESC</B> String => column sort sequence, "A" => ascending
	 *		"D" => descending, may be null if sort sequence is not supported;
	 *		null when TYPE is tableIndexStatistic
	 *	<LI><B>CARDINALITY</B> int => When TYPE is tableIndexStatisic then
	 *		this is the number of rows in the table; otherwise it is the
	 *		number of unique values in the index.
	 *	<LI><B>PAGES</B> int => When TYPE is  tableIndexStatisic then
	 *		this is the number of pages used for the table, otherwise it
	 *		is the number of pages used for the current index.
	 *	<LI><B>FILTER_CONDITION</B> String => Filter condition, if any.
	 *		(may be null)
	 *	</OL>
	 *
	 * @param catalog a catalog name; "" retrieves those without a catalog
	 * @param schema a schema name pattern; "" retrieves those without a schema
	 * @param table a table name
	 * @param unique when true, return only indices for unique values;
	 *	   when false, return indices regardless of whether unique or not
	 * @param approximate when true, result is allowed to reflect approximate
	 *	   or out of data values; when false, results are requested to be
	 *	   accurate
	 * @return ResultSet each row is an index column description
	 * @throws SQLException if a database occurs
	 */
	@Override
	public ResultSet getIndexInfo(
		String catalog,
		String schema,
		String table,
		boolean unique,
		boolean approximate
	) throws SQLException
	{
		String cat = getEnv("gdk_dbname");
		String query =
			"SELECT * FROM ( " +
			"SELECT '" + cat + "' AS \"TABLE_CAT\", " +
				"\"idxs\".\"name\" AS \"INDEX_NAME\", " +
				"\"tables\".\"name\" AS \"TABLE_NAME\", " +
				"\"schemas\".\"name\" AS \"TABLE_SCHEM\", " +
				"CASE WHEN \"keys\".\"name\" IS NULL THEN true ELSE false END AS \"NON_UNIQUE\", " +
				"CASE \"idxs\".\"type\" WHEN 0 THEN " + DatabaseMetaData.tableIndexHashed + " ELSE " + DatabaseMetaData.tableIndexOther + " END AS \"TYPE\", " +
				"\"objects\".\"nr\" AS \"ORDINAL_POSITION\", " +
				"\"columns\".\"name\" as \"COLUMN_NAME\", " +
				"cast(null AS varchar(1)) AS \"INDEX_QUALIFIER\", " +
				"cast(null AS varchar(1)) AS \"ASC_OR_DESC\", " +
				"0 AS \"PAGES\", " +
				"cast(null AS varchar(1)) AS \"FILTER_CONDITION\" " +
			"FROM \"sys\".\"idxs\" AS \"idxs\" LEFT JOIN \"sys\".\"keys\" AS \"keys\" ON \"idxs\".\"name\" = \"keys\".\"name\", " +
				"\"sys\".\"schemas\" AS \"schemas\", " +
				"\"sys\".\"objects\" AS \"objects\", " +
				"\"sys\".\"columns\" AS \"columns\", " +
				"\"sys\".\"tables\" AS \"tables\" " +
			"WHERE \"idxs\".\"table_id\" = \"tables\".\"id\" " +
				"AND \"tables\".\"schema_id\" = \"schemas\".\"id\" " +
				"AND \"idxs\".\"id\" = \"objects\".\"id\" " +
				"AND \"tables\".\"id\" = \"columns\".\"table_id\" " +
				"AND \"objects\".\"name\" = \"columns\".\"name\" " +
				"AND (\"keys\".\"type\" IS NULL OR \"keys\".\"type\" = 1) " +
			") AS jdbcquery " +
				"WHERE 1 = 1 ";

		if (schema != null) {
			query += "AND \"TABLE_SCHEM\" ILIKE '" + escapeQuotes(schema) + "' ";
		}
		if (table != null) {
			query += "AND \"TABLE_NAME\" ILIKE '" + escapeQuotes(table) + "' ";
		}
		if (unique) {
			query += "AND \"NON_UNIQUE\" = false ";
		}

		query += "ORDER BY \"NON_UNIQUE\", \"TYPE\", \"INDEX_NAME\", \"ORDINAL_POSITION\"";

		final String columns[] = {
			"TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "NON_UNIQUE",
			"INDEX_QUALIFIER", "INDEX_NAME", "TYPE", "ORDINAL_POSITION",
			"COLUMN_NAME", "ASC_OR_DESC", "CARDINALITY", "PAGES",
			"FILTER_CONDITION"
		};

		final String types[] = {
			"varchar", "varchar", "varchar", "boolean",
			"varchar", "varchar", "int", "int",
			"varchar", "varchar", "int", "int", "varchar"
		};

		List<String[]> tmpRes = new ArrayList<String[]>();

		Statement sub = null;
		if (!approximate) sub = con.createStatement();

		ResultSet rs = getStmt().executeQuery(query);
		try {
			while (rs.next()) {
				String[] result = new String[13];
				result[0]  = null;
				result[1]  = rs.getString("table_schem");
				result[2]  = rs.getString("table_name");
				result[3]  = rs.getString("non_unique");
				result[4]  = rs.getString("index_qualifier");
				result[5]  = rs.getString("index_name");
				result[6]  = rs.getString("type");
				result[7]  = rs.getString("ordinal_position");
				result[8]  = rs.getString("column_name");
				result[9]  = rs.getString("asc_or_desc");
				result[10] = "0";
				if (!approximate && sub != null) {
					/* issue a separate count query for each table its index to get the exact cardinality */
					ResultSet count = sub.executeQuery("SELECT COUNT(*) FROM \"" + result[1] + "\".\"" + result[2] + "\"");
					if (count != null) {
						if (count.next()) {
							result[10] = count.getString(1);
						}
						count.close();
					}
				}
				result[11] = rs.getString("pages");
				result[12] = rs.getString("filter_condition");
				tmpRes.add(result);
			}

			if (sub != null) sub.close();
		} finally {
			rs.close();
		}

		String[][] results = tmpRes.toArray(new String[tmpRes.size()][]);

		try {
			return new MonetVirtualResultSet(columns, types, results);
		} catch (IllegalArgumentException e) {
			throw new SQLException("Internal driver error: " + e.getMessage(), "M0M03");
		}
	}

	//== 1.2 methods (JDBC 2)

	/**
	 * Does the database support the given result set type?
	 *
	 * @param type - defined in java.sql.ResultSet
	 * @return true if so; false otherwise
	 * @throws SQLException - if a database access error occurs
	 */
	@Override
	public boolean supportsResultSetType(int type) throws SQLException {
		// The only type we don't support
		return type != ResultSet.TYPE_SCROLL_SENSITIVE;
	}


	/**
	 * Does the database support the concurrency type in combination
	 * with the given result set type?
	 *
	 * @param type - defined in java.sql.ResultSet
	 * @param concurrency - type defined in java.sql.ResultSet
	 * @return true if so; false otherwise
	 * @throws SQLException - if a database access error occurs
	*/
	@Override
	public boolean supportsResultSetConcurrency(int type, int concurrency)
		throws SQLException
	{
		// These combinations are not supported!
		if (type == ResultSet.TYPE_SCROLL_SENSITIVE)
			return false;

		// We do only support Read Only ResultSets
		if (concurrency != ResultSet.CONCUR_READ_ONLY)
			return false;

		// Everything else we do (well, what's left of it :) )
		return true;
	}


	/* lots of unsupported stuff... (no updatable ResultSet!) */
	@Override
	public boolean ownUpdatesAreVisible(int type) {
		return false;
	}

	@Override
	public boolean ownDeletesAreVisible(int type) {
		return false;
	}

	@Override
	public boolean ownInsertsAreVisible(int type) {
		return false;
	}

	@Override
	public boolean othersUpdatesAreVisible(int type) {
		return false;
	}

	@Override
	public boolean othersDeletesAreVisible(int i) {
		return false;
	}

	@Override
	public boolean othersInsertsAreVisible(int type) {
		return false;
	}

	@Override
	public boolean updatesAreDetected(int type) {
		return false;
	}

	@Override
	public boolean deletesAreDetected(int i) {
		return false;
	}

	@Override
	public boolean insertsAreDetected(int type) {
		return false;
	}

	/**
	 * Indicates whether the driver supports batch updates.
	 */
	@Override
	public boolean supportsBatchUpdates() {
		return true;
	}

	/**
	 * Return user defined types in a schema
	 * Probably not possible within MonetDB
	 *
	 * @throws SQLException if I made a Boo-Boo
	 */
	@Override
	public ResultSet getUDTs(
		String catalog,
		String schemaPattern,
		String typeNamePattern,
		int[] types
	) throws SQLException
	{
		String cat = getEnv("gdk_dbname");
		String query =
			"SELECT '" + cat + "' AS \"TYPE_CAT\", '' AS \"TYPE_SCHEM\", '' AS \"TYPE_NAME\", " +
			"'java.lang.Object' AS \"CLASS_NAME\", 0 AS \"DATA_TYPE\", " +
			"'' AS \"REMARKS\", 0 AS \"BASE_TYPE\" WHERE 1 = 0";

		return getStmt().executeQuery(query);
	}


	/**
	 * Retrieves the connection that produced this metadata object.
	 *
	 * @return the connection that produced this metadata object
	 */
	@Override
	public Connection getConnection() {
		return con;
	}

	/* I don't find these in the spec!?! */
	public boolean rowChangesAreDetected(int type) {
		return false;
	}

	public boolean rowChangesAreVisible(int type) {
		return false;
	}

	//== 1.4 methods (JDBC 3)

	/**
	 * Retrieves whether this database supports savepoints.
	 *
	 * @return <code>true</code> if savepoints are supported;
	 *		   <code>false</code> otherwise
	 */
	@Override
	public boolean supportsSavepoints() {
		return true;
	}

	/**
	 * Retrieves whether this database supports named parameters to callable
	 * statements.
	 *
	 * @return <code>true</code> if named parameters are supported;
	 *		   <code>false</code> otherwise
	 */
	@Override
	public boolean supportsNamedParameters() {
		return false;
	}

	/**
	 * Retrieves whether it is possible to have multiple <code>ResultSet</code> objects
	 * returned from a <code>CallableStatement</code> object
	 * simultaneously.
	 *
	 * @return <code>true</code> if a <code>CallableStatement</code> object
	 *		   can return multiple <code>ResultSet</code> objects
	 *		   simultaneously; <code>false</code> otherwise
	 */
	@Override
	public boolean supportsMultipleOpenResults() {
		return true;
	}

	/**
	 * Retrieves whether auto-generated keys can be retrieved after
	 * a statement has been executed.
	 *
	 * @return <code>true</code> if auto-generated keys can be retrieved
	 *		   after a statement has executed; <code>false</code> otherwise
	 */
	@Override
	public boolean supportsGetGeneratedKeys() {
		return true;
	}

	/**
	 * Retrieves a description of the user-defined type (UDT)
	 * hierarchies defined in a particular schema in this database. Only
	 * the immediate super type/ sub type relationship is modeled.
	 * <P>
	 * Only supertype information for UDTs matching the catalog,
	 * schema, and type name is returned. The type name parameter
	 * may be a fully-qualified name. When the UDT name supplied is a
	 * fully-qualified name, the catalog and schemaPattern parameters are
	 * ignored.
	 * <P>
	 * If a UDT does not have a direct super type, it is not listed here.
	 * A row of the <code>ResultSet</code> object returned by this method
	 * describes the designated UDT and a direct supertype. A row has the following
	 * columns:
	 *	<OL>
	 *	<LI><B>TYPE_CAT</B> String => the UDT's catalog (may be <code>null</code>)
	 *	<LI><B>TYPE_SCHEM</B> String => UDT's schema (may be <code>null</code>)
	 *	<LI><B>TYPE_NAME</B> String => type name of the UDT
	 *	<LI><B>SUPERTYPE_CAT</B> String => the direct super type's catalog
	 *							 (may be <code>null</code>)
	 *	<LI><B>SUPERTYPE_SCHEM</B> String => the direct super type's schema
	 *							   (may be <code>null</code>)
	 *	<LI><B>SUPERTYPE_NAME</B> String => the direct super type's name
	 *	</OL>
	 *
	 * <P><B>Note:</B> If the driver does not support type hierarchies, an
	 * empty result set is returned.
	 *
	 * @param catalog a catalog name; "" retrieves those without a catalog;
	 *		  <code>null</code> means drop catalog name from the selection criteria
	 * @param schemaPattern a schema name pattern; "" retrieves those
	 *		  without a schema
	 * @param typeNamePattern a UDT name pattern; may be a fully-qualified
	 *		  name
	 * @return a <code>ResultSet</code> object in which a row gives information
	 *		   about the designated UDT
	 * @throws SQLException if a database access error occurs
	 */
	@Override
	public ResultSet getSuperTypes(
		String catalog,
		String schemaPattern,
		String typeNamePattern
	) throws SQLException
	{
		String cat = getEnv("gdk_dbname");
		String query =
			"SELECT '" + cat + "' AS \"TYPE_CAT\", '' AS \"TYPE_SCHEM\", '' AS \"TYPE_NAME\", " +
			"'' AS \"SUPERTYPE_CAT\", '' AS \"SUPERTYPE_SCHEM\", " +
			"'' AS \"SUPERTYPE_NAME\" WHERE 1 = 0";

		return getStmt().executeQuery(query);
	}

	/**
	 * Retrieves a description of the table hierarchies defined in a particular
	 * schema in this database.
	 *
	 * <P>Only supertable information for tables matching the catalog, schema
	 * and table name are returned. The table name parameter may be a fully-
	 * qualified name, in which case, the catalog and schemaPattern parameters
	 * are ignored. If a table does not have a super table, it is not listed here.
	 * Supertables have to be defined in the same catalog and schema as the
	 * sub tables. Therefore, the type description does not need to include
	 * this information for the supertable.
	 *
	 * <P>Each type description has the following columns:
	 *	<OL>
	 *	<LI><B>TABLE_CAT</B> String => the type's catalog (may be <code>null</code>)
	 *	<LI><B>TABLE_SCHEM</B> String => type's schema (may be <code>null</code>)
	 *	<LI><B>TABLE_NAME</B> String => type name
	 *	<LI><B>SUPERTABLE_NAME</B> String => the direct super type's name
	 *	</OL>
	 *
	 * <P><B>Note:</B> If the driver does not support type hierarchies, an
	 * empty result set is returned.
	 *
	 * @param catalog a catalog name; "" retrieves those without a catalog;
	 *		  <code>null</code> means drop catalog name from the selection criteria
	 * @param schemaPattern a schema name pattern; "" retrieves those
	 *		  without a schema
	 * @param tableNamePattern a table name pattern; may be a fully-qualified
	 *		  name
	 * @return a <code>ResultSet</code> object in which each row is a type description
	 * @throws SQLException if a database access error occurs
	 */
	@Override
	public ResultSet getSuperTables(
		String catalog,
		String schemaPattern,
		String tableNamePattern
	) throws SQLException
	{
		String cat = getEnv("gdk_dbname");
		String query =
			"SELECT '" + cat + "' AS \"TABLE_CAT\", '' AS \"TABLE_SCHEM\", '' AS \"TABLE_NAME\", " +
			"'' AS \"SUPERTABLE_NAME\" WHERE 1 = 0";

		return getStmt().executeQuery(query);
	}

	/**
	 * Retrieves a description of the given attribute of the given type
	 * for a user-defined type (UDT) that is available in the given schema
	 * and catalog.
	 * <P>
	 * Descriptions are returned only for attributes of UDTs matching the
	 * catalog, schema, type, and attribute name criteria. They are ordered by
	 * TYPE_SCHEM, TYPE_NAME and ORDINAL_POSITION. This description
	 * does not contain inherited attributes.
	 * <P>
	 * The <code>ResultSet</code> object that is returned has the following
	 * columns:
	 * <OL>
	 *	<LI><B>TYPE_CAT</B> String => type catalog (may be <code>null</code>)
	 *	<LI><B>TYPE_SCHEM</B> String => type schema (may be <code>null</code>)
	 *	<LI><B>TYPE_NAME</B> String => type name
	 *	<LI><B>ATTR_NAME</B> String => attribute name
	 *	<LI><B>DATA_TYPE</B> short => attribute type SQL type from java.sql.Types
	 *	<LI><B>ATTR_TYPE_NAME</B> String => Data source dependent type name.
	 *	For a UDT, the type name is fully qualified. For a REF, the type name is
	 *	fully qualified and represents the target type of the reference type.
	 *	<LI><B>ATTR_SIZE</B> int => column size.  For char or date
	 *		types this is the maximum number of characters; for numeric or
	 *		decimal types this is precision.
	 *	<LI><B>DECIMAL_DIGITS</B> int => the number of fractional digits
	 *	<LI><B>NUM_PREC_RADIX</B> int => Radix (typically either 10 or 2)
	 *	<LI><B>NULLABLE</B> int => whether NULL is allowed
	 *		<UL>
	 *		<LI> attributeNoNulls - might not allow NULL values
	 *		<LI> attributeNullable - definitely allows NULL values
	 *		<LI> attributeNullableUnknown - nullability unknown
	 *		</UL>
	 *	<LI><B>REMARKS</B> String => comment describing column (may be <code>null</code>)
	 *	<LI><B>ATTR_DEF</B> String => default value (may be <code>null</code>)
	 *	<LI><B>SQL_DATA_TYPE</B> int => unused
	 *	<LI><B>SQL_DATETIME_SUB</B> int => unused
	 *	<LI><B>CHAR_OCTET_LENGTH</B> int => for char types the
	 *		 maximum number of bytes in the column
	 *	<LI><B>ORDINAL_POSITION</B> int => index of column in table
	 *		(starting at 1)
	 *	<LI><B>IS_NULLABLE</B> String => "NO" means column definitely
	 *		does not allow NULL values; "YES" means the column might
	 *		allow NULL values.	An empty string means unknown.
	 *	<LI><B>SCOPE_CATALOG</B> String => catalog of table that is the
	 *		scope of a reference attribute (<code>null</code> if DATA_TYPE isn't REF)
	 *	<LI><B>SCOPE_SCHEMA</B> String => schema of table that is the
	 *		scope of a reference attribute (<code>null</code> if DATA_TYPE isn't REF)
	 *	<LI><B>SCOPE_TABLE</B> String => table name that is the scope of a
	 *		reference attribute (<code>null</code> if the DATA_TYPE isn't REF)
	 * <LI><B>SOURCE_DATA_TYPE</B> short => source type of a distinct type or user-generated
	 *		Ref type,SQL type from java.sql.Types (<code>null</code> if DATA_TYPE
	 *		isn't DISTINCT or user-generated REF)
	 *	</OL>
	 * @param catalog a catalog name; must match the catalog name as it
	 *		  is stored in the database; "" retrieves those without a catalog;
	 *		  <code>null</code> means that the catalog name should not be used to narrow
	 *		  the search
	 * @param schemaPattern a schema name pattern; must match the schema name
	 *		  as it is stored in the database; "" retrieves those without a schema;
	 *		  <code>null</code> means that the schema name should not be used to narrow
	 *		  the search
	 * @param typeNamePattern a type name pattern; must match the
	 *		  type name as it is stored in the database
	 * @param attributeNamePattern an attribute name pattern; must match the attribute
	 *		  name as it is declared in the database
	 * @return a <code>ResultSet</code> object in which each row is an
	 *		   attribute description
	 * @throws SQLException if a database access error occurs
	 */
	@Override
	public ResultSet getAttributes(
		String catalog,
		String schemaPattern,
		String typeNamePattern,
		String attributeNamePattern
	) throws SQLException
	{
		String cat = getEnv("gdk_dbname");
		String query =
			"SELECT '" + cat + "' AS \"TYPE_CAT\", '' AS \"TYPE_SCHEM\", '' AS \"TYPE_NAME\", " +
			"'' AS \"ATTR_NAME\", '' AS \"ATTR_TYPE_NAME\", 0 AS \"ATTR_SIZE\", " +
			"0 AS \"DECIMAL_DIGITS\", 0 AS \"NUM_PREC_RADIX\", 0 AS \"NULLABLE\", " +
			"'' AS \"REMARKS\", '' AS \"ATTR_DEF\", 0 AS \"SQL_DATA_TYPE\", " +
			"0 AS \"SQL_DATETIME_SUB\", 0 AS \"CHAR_OCTET_LENGTH\", " +
			"0 AS \"ORDINAL_POSITION\", 'YES' AS \"IS_NULLABLE\", " +
			"'' AS \"SCOPE_CATALOG\", '' AS \"SCOPE_SCHEMA\", '' AS \"SCOPE_TABLE\", " +
			"0 AS \"SOURCE_DATA_TYPE\" WHERE 1 = 0";

		return getStmt().executeQuery(query);
	}

	/**
	 * Retrieves whether this database supports the given result set holdability.
	 *
	 * @param holdability one of the following constants:
	 *			<code>ResultSet.HOLD_CURSORS_OVER_COMMIT</code> or
	 *			<code>ResultSet.CLOSE_CURSORS_AT_COMMIT</code>
	 * @return <code>true</code> if so; <code>false</code> otherwise
	 * @see Connection
	 */
	@Override
	public boolean supportsResultSetHoldability(int holdability) {
		// we don't close ResultSets at commit; and we don't do updateable
		// result sets, so comes closest to hold cursors over commit
		return holdability == ResultSet.HOLD_CURSORS_OVER_COMMIT;
	}

	/**
	 * Retrieves the default holdability of this <code>ResultSet</code>
	 * object.
	 *
	 * @return the default holdability; either
	 *		   <code>ResultSet.HOLD_CURSORS_OVER_COMMIT</code> or
	 *		   <code>ResultSet.CLOSE_CURSORS_AT_COMMIT</code>
	 */
	@Override
	public int getResultSetHoldability() {
		return ResultSet.HOLD_CURSORS_OVER_COMMIT;
	}

	/**
	 * Retrieves the major version number of the underlying database.
	 *
	 * @return the underlying database's major version
	 * @throws SQLException if a database access error occurs
	 */
	@Override
	public int getDatabaseMajorVersion() throws SQLException {
		String version = getEnv("monet_version");
		int major = 0;
		try {
			major = Integer.parseInt(version.substring(0, version.indexOf(".")));
		} catch (NumberFormatException e) {
			// baaaaaaaaaa
		}

 		return major;
	}

	/**
	 * Retrieves the minor version number of the underlying database.
	 *
	 * @return underlying database's minor version
	 * @throws SQLException if a database access error occurs
	 */
	@Override
	public int getDatabaseMinorVersion() throws SQLException {
		String version = getEnv("monet_version");
		int minor = 0;
		try {
			int start = version.indexOf(".");
			minor = Integer.parseInt(version.substring(start + 1, version.indexOf(".", start + 1)));
		} catch (NumberFormatException e) {
			// baaaaaaaaaa
		}

 		return minor;
	}

	/**
	 * Retrieves the major JDBC version number for this
	 * driver.
	 *
	 * @return JDBC version major number
	 */
	@Override
	public int getJDBCMajorVersion() {
		return 4; // This class implements JDBC 4.1 (at least we try to)
	}

	/**
	 * Retrieves the minor JDBC version number for this
	 * driver.
	 *
	 * @return JDBC version minor number
	 */
	@Override
	public int getJDBCMinorVersion() {
		return 1; // This class implements JDBC 4.1 (at least we try to)
	}

	/**
	 * Indicates whether the SQLSTATEs returned by <code>SQLException.getSQLState</code>
	 * is X/Open (now known as Open Group) SQL CLI or SQL:2003.
	 * @return the type of SQLSTATEs, one of:
	 *		  sqlStateXOpen or
	 *		  sqlStateSQL
	 */
	@Override
	public int getSQLStateType() {
		// At least this driver conforms with SQLSTATE to the SQL:2003 standard
		return DatabaseMetaData.sqlStateSQL;
	}

	/**
	 * Indicates whether updates made to a LOB are made on a copy or directly
	 * to the LOB.
	 * @return <code>true</code> if updates are made to a copy of the LOB;
	 *		   <code>false</code> if updates are made directly to the LOB
	 */
	@Override
	public boolean locatorsUpdateCopy() {
		// not that we have it, but in a transaction it will be copy-on-write
		return true;
	}

	/**
	 * Retrieves whether this database supports statement pooling.
	 *
	 * @return <code>true</code> is so;
		<code>false</code> otherwise
	 */
	@Override
	public boolean supportsStatementPooling() {
		// For the moment, I don't think so
		return false;
	}

	//== 1.6 methods (JDBC 4)

	/**
	 * Indicates whether or not this data source supports the SQL ROWID
	 * type, and if so the lifetime for which a RowId object remains
	 * valid.
	 *
	 * @return ROWID_UNSUPPORTED for now
	 */
	@Override
	public RowIdLifetime getRowIdLifetime() {
		// I believe we don't do rowids
		return RowIdLifetime.ROWID_UNSUPPORTED;
	}

	/**
	 * Get the schema names available in this database.  The results
	 * are ordered by schema name.
	 *
	 * <P>The schema column is:
	 *	<OL>
	 *	<LI><B>TABLE_SCHEM</B> String => schema name
	 *	<LI><B>TABLE_CATALOG</B> String => catalog name (may be null)
	 *	</OL>
	 *
	 * @return ResultSet each row has a single String column that is a
	 *         schema name
	 * @throws SQLException if a database error occurs
	 */
	@Override
	public ResultSet getSchemas() throws SQLException {
		return getSchemas(null, null);
	}

	/**
	 * Retrieves whether this database supports invoking user-defined or
	 * vendor functions using the stored procedure escape syntax.
	 *
	 * @return true if so; false otherwise
	 */
	@Override
	public boolean supportsStoredFunctionsUsingCallSyntax() {
		return false;
	}

	/**
	 * Retrieves whether a SQLException while autoCommit is true
	 * inidcates that all open ResultSets are closed, even ones that are
	 * holdable. When a SQLException occurs while autocommit is true, it
	 * is vendor specific whether the JDBC driver responds with a commit
	 * operation, a rollback operation, or by doing neither a commit nor
	 * a rollback. A potential result of this difference is in whether
	 * or not holdable ResultSets are closed.
	 *
	 * @return true if so; false otherwise
	 */
	@Override
	public boolean autoCommitFailureClosesAllResultSets() {
		// The driver caches most of it, and as far as I knoww the
		// server doesn't close outstanding result handles on commit
		// failure either.
		return false;
	}

	/**
	 * Retrieves a list of the client info properties that the driver
	 * supports. The result set contains the following columns
	 *
	 *    1. NAME String=> The name of the client info property
	 *    2. MAX_LEN int=> The maximum length of the value for the
	 *       property
	 *    3. DEFAULT_VALUE String=> The default value of the
	 *       property
	 *    4. DESCRIPTION String=> A description of the
	 *       property. This will typically contain information as
	 *       to where this property is stored in the database. 
	 *
	 * The ResultSet is sorted by the NAME column 
	 *
	 * @return A ResultSet object; each row is a supported client info
	 *         property, none in case of MonetDB's current JDBC driver
	 * @throws SQLException if a database access error occurs
	 */
	@Override
	public ResultSet getClientInfoProperties() throws SQLException {
		String[] columns, types;
		String[][] results;

		columns = new String[4];
		types = new String[4];
		results = new String[4][0];

		columns[0] = "NAME";
		types[0] = "varchar";
		columns[1] = "MAX_LEN";
		types[1] = "integer";
		columns[2] = "DEFAULT_VALUE";
		types[2] = "varchar";
		columns[3] = "DESCRIPTION";
		types[3] = "varchar";

		try {
			return new MonetVirtualResultSet(columns, types, results);
		} catch (IllegalArgumentException e) {
			throw new SQLException("Internal driver error: " + e.getMessage(), "M0M03");
		}
	}

	/**
	 * Retrieves a description of the system and user functions
	 * available in the given catalog.
	 *
	 * Only system and user function descriptions matching the schema
	 * and function name criteria are returned. They are ordered by
	 * FUNCTION_CAT, FUNCTION_SCHEM, FUNCTION_NAME and SPECIFIC_ NAME.
	 *
	 * Each function description has the the following columns:
	 *
	 *    1. FUNCTION_CAT String => function catalog (may be null)
	 *    2. FUNCTION_SCHEM String => function schema (may be null)
	 *    3. FUNCTION_NAME String => function name. This is the
	 *       name used to invoke the function
	 *    4. REMARKS String => explanatory comment on the function
	 *    5. FUNCTION_TYPE short => kind of function:
	 *        * functionResultUnknown - Cannot determine if a return
	 *          value or table will be returned
	 *        * functionNoTable- Does not return a table
	 *        * functionReturnsTable - Returns a table 
	 *    6. SPECIFIC_NAME String => the name which uniquely identifies
	 *       this function within its schema. This is a user specified,
	 *       or DBMS generated, name that may be different then the
	 *       FUNCTION_NAME for example with overload functions 
	 *
	 * A user may not have permission to execute any of the functions
	 * that are returned by getFunctions.
	 *
	 * @param catalog a catalog name; must match the catalog name as it
	 *        is stored in the database; "" retrieves those without a
	 *        catalog; null means that the catalog name should not be
	 *        used to narrow the search
	 * @param schemaPattern a schema name pattern; must match the schema
	 *        name as it is stored in the database; "" retrieves those
	 *        without a schema; null means that the schema name should
	 *        not be used to narrow the search
	 * @param functionNamePattern a function name pattern; must match
	 *        the function name as it is stored in the database 
	 * @return ResultSet - each row is a function description
	 * @throws SQLException if a database access error occurs
	 */
	@Override
	public ResultSet getFunctions(
			String catalog,
			String schemaPattern,
			String functionNamePattern)
		throws SQLException
	{
		String cat = getEnv("gdk_dbname");
		String select =
			"SELECT * FROM ( " +
			"SELECT '" + cat + "' AS \"FUNCTION_CAT\", " +
				"\"schemas\".\"name\" AS \"FUNCTION_SCHEM\", " +
				"\"functions\".\"name\" AS \"FUNCTION_NAME\", " +
				"null AS \"REMARKS\", " +
				DatabaseMetaData.functionResultUnknown + " AS \"FUNCTION_TYPE\", " +
				"CASE WHEN \"functions\".\"sql\" = false THEN CAST(\"functions\"/\"mod\" || '.' || \"functions\".\"func\" AS CLOB) ELSE CAST(\"functions\".\"name\" AS CLOB) END AS \"SPECIFIC_NAME\" " +
			"FROM \"sys\".\"functions\" AS \"functions\", \"sys\".\"schemas\" AS \"schemas\" WHERE \"functions\".\"schema_id\" = \"schemas\".\"id\" " +
			") AS \"functions\" WHERE 1 = 1 ";

		if (catalog != null) {
			select += "AND '" + cat + "' ILIKE '" + escapeQuotes(catalog) + "' ";
		}
		if (schemaPattern != null) {
			select += "AND \"FUNCTION_SCHEM\" ILIKE '" + escapeQuotes(schemaPattern) + "' ";
		}
		if (functionNamePattern != null) {
			select += "AND \"FUNCTION_NAME\" ILIKE '" + escapeQuotes(functionNamePattern) + "' ";
		}

		select += "ORDER BY \"FUNCTION_SCHEM\", \"FUNCTION_NAME\", \"SPECIFIC_NAME\"";

		return getStmt().executeQuery(select);
	}

	/**
	 * Retrieves a description of the given catalog's system or user
	 * function parameters and return type.
	 *
	 * We don't implement this, because it is too much work, and left as
	 * an SQL exercise for the future.  This function is here just for
	 * JDBC4.
	 *
	 * @param catalog a catalog name; must match the catalog name as
	 *        it is stored in the database; "" retrieves those without a
	 *        catalog; null means that the catalog name should not be
	 *        used to narrow the search
	 * @param schemaPattern a schema name pattern; must match the schema
	 *        name as it is stored in the database; "" retrieves those
	 *        without a schema; null means that the schema name should
	 *        not be used to narrow the search
	 * @param functionNamePattern a procedure name pattern; must match the
	 *        function name as it is stored in the database
	 * @param columnNamePattern a parameter name pattern; must match the
	 *        parameter or column name as it is stored in the database
	 * @return ResultSet - each row describes a user function parameter,
	 *         column or return type
	 * @throws SQLException - if a database access error occurs
	 */
	@Override
	public ResultSet getFunctionColumns(
			String catalog,
			String schemaPattern,
			String functionNamePattern,
			String columnNamePattern)
		throws SQLException
	{
		throw new SQLException("getFunctionColumns(String, String, String, String) is not implemented", "0A000");
	}

	//== 1.7 methods (JDBC 4.1)
	
	/**
	 * Retrieves a description of the pseudo or hidden columns available
	 * in a given table within the specified catalog and schema.  Pseudo
	 * or hidden columns may not always be stored within a table and are
	 * not visible in a ResultSet unless they are specified in the
	 * query's outermost SELECT list.  Pseudo or hidden columns may not
	 * necessarily be able to be modified.  If there are no pseudo or
	 * hidden columns, an empty ResultSet is returned.
	 *
	 * @param catalog a catalog name
	 * @param schemaPattern a schema name pattern
	 * @param tableNamePattern a table name pattern
	 * @param columnNamePattern a column name pattern
	 * @return ResultSet where each row is a column description
	 * @throws SQLException if a database access error occurs
	 */
	@Override
	public ResultSet getPseudoColumns(
			String catalog,
			String schemaPattern,
			String tableNamePattern,
			String columnNamePattern)
		throws SQLException
	{
		String[] columns, types;
		String[][] results;

		columns = new String[12];
		types = new String[12];
		results = new String[12][0];

		columns[0] = "TABLE_CAT";
		types[0] = "varchar";
		columns[1] = "TABLE_SCHEM";
		types[1] = "varchar";
		columns[2] = "TABLE_NAME";
		types[2] = "varchar";
		columns[3] = "COLUMN_NAME";
		types[3] = "varchar";
		columns[4] = "DATA_TYPE";
		types[4] = "int";
		columns[5] = "COLUMN_SIZE";
		types[5] = "int";
		columns[6] = "DECIMAL_DIGITS";
		types[6] = "int";
		columns[7] = "NUM_PREC_RADIX";
		types[7] = "int";
		columns[8] = "COLUMN_USAGE";
		types[8] = "varchar";
		columns[9] = "REMARKS";
		types[9] = "varchar";
		columns[10] = "CHAR_OCTET_LENGTH";
		types[10] = "int";
		columns[11] = "IS_NULLABLE";
		types[11] = "varchar";

		try {
			return new MonetVirtualResultSet(columns, types, results);
		} catch (IllegalArgumentException e) {
			throw new SQLException("Internal driver error: " + e.getMessage(), "M0M03");
		}
	}

	/**
	 * Retrieves whether a generated key will always be returned if the
	 * column name(s) or index(es) specified for the auto generated key
	 * column(s) are valid and the statement succeeds.  The key that is
	 * returned may or may not be based on the column(s) for the auto
	 * generated key.
	 *
	 * @return true if so, false otherwise
	 * @throws SQLException - if a database access error occurs
	 */
	@Override
	public boolean generatedKeyAlwaysReturned() throws SQLException {
		return true;
	}

	//== end methods interface DatabaseMetaData
}

/**
 * This class is not intended for normal use. Therefore it is restricted to
 * classes from the very same package only. Because it is mainly used
 * only in the MonetDatabaseMetaData class it is placed here.
 */
class MonetVirtualResultSet extends MonetResultSet {
	private String results[][];
	private boolean closed;

	MonetVirtualResultSet(
		String[] columns,
		String[] types,
		String[][] results
	) throws IllegalArgumentException {
		super(columns, types, results.length);

		this.results = results;
		closed = false;
	}

	/**
	 * This method is overridden in order to let it use the results array
	 * instead of the cache in the Statement object that created it.
	 *
	 * @param row the number of the row to which the cursor should move. A
	 *        positive number indicates the row number counting from the
	 *        beginning of the result set; a negative number indicates the row
	 *        number counting from the end of the result set
	 * @return true if the cursor is on the result set; false otherwise
	 * @throws SQLException if a database error occurs
	 */
 	@Override
	public boolean absolute(int row) throws SQLException {
		if (closed)
			throw new SQLException("ResultSet is closed!", "M1M20");

		// first calculate what the JDBC row is
		if (row < 0) {
			// calculate the negatives...
			row = tupleCount + row + 1;
		}
		// now place the row not farther than just before or after the result
		if (row < 0) row = 0;	// before first
		else if (row > tupleCount + 1) row = tupleCount + 1;	// after last

		// store it
		curRow = row;

		// see if we have the row
		if (row < 1 || row > tupleCount) return false;

		for (int i = 0; i < results[row - 1].length; i++) {
			tlp.values[i] = results[row - 1][i];
		}

		return true;
	}

	/**
	 * Mainly here to prevent errors when the close method is called. There
	 * is no real need for this object to close it. We simply remove our
	 * resultset data.
	 */
	@Override
	public void close() {
		if (!closed) {
			closed = true;
			results = null;
			// types and columns are MonetResultSets private parts
		}
	}

	/**
	 * Retrieves the fetch size for this ResultSet object, which will be
	 * zero, since it's a virtual set.
	 *
	 * @return the current fetch size for this ResultSet object
	 * @throws SQLException if a database access error occurs
	 */
	@Override
	public int getFetchSize() throws SQLException {
		return 0;
	}
}
