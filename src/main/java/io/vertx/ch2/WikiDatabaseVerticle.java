package io.vertx.ch2;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.asyncsql.PostgreSQLClient;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class WikiDatabaseVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LoggerFactory.getLogger(WikiDatabaseVerticle.class);

  public static final String CONFIG_WIKIDB_HOST = "wikidb.host";
  public static final String CONFIG_WIKIDB_USER = "wikidb.username";
  public static final String CONFIG_WIKIDB_PASSWORD = "wikidb.password";
  public static final String CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE = "wikidb.sqlqueries.resource.file";
  public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";

  private final HashMap<SqlQuery, String> sqlQueries = new HashMap<>();
  private SQLClient sqlClient;

  private void loadSqlQueries() throws IOException {
    String queriesFile = config().getString(CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE);
    InputStream queriesInputStream;

    if (queriesFile != null) {
      queriesInputStream = new FileInputStream(queriesFile);
    } else {
      queriesInputStream = getClass().getResourceAsStream("/db-queries.properties");
    }
    Properties queriesProps = new Properties();
    queriesProps.load(queriesInputStream);
    queriesInputStream.close();

    sqlQueries.put(SqlQuery.CREATE_PAGES_TABLE, queriesProps.getProperty("create-pages-table"));
    sqlQueries.put(SqlQuery.ALL_PAGES, queriesProps.getProperty("all-pages"));
    sqlQueries.put(SqlQuery.GET_PAGE, queriesProps.getProperty("get-page"));
    sqlQueries.put(SqlQuery.CREATE_PAGE, queriesProps.getProperty("create-page"));
    sqlQueries.put(SqlQuery.SAVE_PAGE, queriesProps.getProperty("save-page"));
    sqlQueries.put(SqlQuery.DELETE_PAGE, queriesProps.getProperty("delete-page"));
  }

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    loadSqlQueries();

    sqlClient = PostgreSQLClient.createShared(vertx,
      new JsonObject().put("host", "localhost")
        .put("username", "siva")
        .put("password", "siva")
        .put("database", "mydb")
        .put("maxPoolSize", 20));

    sqlClient.getConnection(ar -> {
      if (ar.succeeded()) {
        SQLConnection connection = ar.result();
        connection.execute(sqlQueries.get(SqlQuery.CREATE_PAGES_TABLE), result -> {
          connection.close();
          if (result.succeeded()) {
            System.out.println("table creation success");
            vertx.eventBus().consumer(config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue"), this::onMessage);
          } else {
            System.out.println("table creation failed ");
            startFuture.fail(result.cause());
          }
        });
      } else {
        LOGGER.error("Not able to get SQL connection", ar.cause());
        startFuture.fail(ar.cause());
      }
    });

  }

  private void onMessage(Message<JsonObject> message) {
    if (!message.headers().contains("action")) {
      LOGGER.error("No action header specified for msg with headers {} and body {}",
        message.headers(), message.body());
      message.fail(ErrorCodes.NO_ACTION_SPECIFIED.ordinal(), "No action header specified");
      return;
    }

    String action = message.headers().get("action");

    switch (action) {
      case "all-pages":
        fetchAllPages(message);
        break;
      default:
        message.fail(ErrorCodes.BAD_ACTION.ordinal(), "Bad action: " + action);
    }
  }

  private void fetchAllPages(Message<JsonObject> message) {
    sqlClient.query(sqlQueries.get(SqlQuery.ALL_PAGES), res -> {
      if (res.succeeded()) {
        List<String> pages = res.result().getResults()
          .stream()
          .map(json -> json.getString(0)).sorted().collect(Collectors.toList());
        message.reply(new JsonObject().put("pages", new JsonArray(pages)));
      } else {
        reportQueryError(message, res.cause());
      }
    });
  }

  private void reportQueryError(Message<JsonObject> message, Throwable cause) {
    LOGGER.error("Database query error", cause);
    message.fail(ErrorCodes.DB_ERROR.ordinal(), cause.getMessage());
  }

  public enum ErrorCodes {
    NO_ACTION_SPECIFIED,
    BAD_ACTION,
    DB_ERROR
  }

  private enum SqlQuery {
    CREATE_PAGES_TABLE,
    ALL_PAGES,
    GET_PAGE,
    CREATE_PAGE,
    SAVE_PAGE,
    DELETE_PAGE
  }
}


